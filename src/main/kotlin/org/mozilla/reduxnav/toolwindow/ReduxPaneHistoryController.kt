package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place

class ReduxPaneHistoryController(
    private val onNavigateToLocation: (ReduxPaneLocation, Boolean) -> Unit
) {
    private var currentLocation: ReduxPaneLocation? = null
    private var suppressRecording: Boolean = false

    val history: History = History(
        object : Place.Navigator {
            override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
                val location = placeLocation(place) ?: return ActionCallback.REJECTED
                currentLocation = location
                onNavigateToLocation(location, requestFocus)
                return ActionCallback.DONE
            }

            override fun queryPlace(place: Place) {
                currentLocation?.let { location -> place.putPath(PLACE_LOCATION_PATH, location) }
            }

            override fun isValid(place: Place): Boolean = placeLocation(place) != null
        }
    ).also { it.clear() }

    fun record(location: ReduxPaneLocation) {
        if (suppressRecording) {
            currentLocation = location
            return
        }
        if (currentLocation?.stableId == location.stableId) {
            currentLocation = location
            return
        }
        currentLocation = location
        history.pushQueryPlace()
    }

    fun canGoBack(): Boolean = history.canGoBack()

    fun canGoForward(): Boolean = history.canGoForward()

    fun goBack() {
        if (!history.canGoBack()) return
        suppressRecording = true
        try {
            history.back()
        } finally {
            suppressRecording = false
        }
    }

    fun goForward() {
        if (!history.canGoForward()) return
        suppressRecording = true
        try {
            history.forward()
        } finally {
            suppressRecording = false
        }
    }

    fun clear() {
        currentLocation = null
        suppressRecording = false
        history.clear()
    }

    fun currentLocation(): ReduxPaneLocation? = currentLocation

    companion object {
        internal const val PLACE_LOCATION_PATH = "reduxPane.location"

        internal fun placeLocation(place: Place?): ReduxPaneLocation? =
            place?.getPath(PLACE_LOCATION_PATH) as? ReduxPaneLocation
    }
}
