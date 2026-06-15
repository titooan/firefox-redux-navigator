package org.mozilla.reduxnav.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mozilla.reduxnav.model.ActionId
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.state.StateFieldInfo

class ReduxPaneHistoryControllerTest : BasePlatformTestCase() {
    fun testBackAndForwardNavigateBetweenStateAndActionLocations() {
        val navigated = mutableListOf<ReduxPaneLocation>()
        val controller = ReduxPaneHistoryController { location, _ ->
            navigated += location
        }

        val state = ReduxPaneLocation.State(stateField("BrowserState.items"))
        val action = ReduxPaneLocation.Action(action("AddTabAction"))

        controller.record(state)
        controller.record(action)

        assertTrue(controller.canGoBack())
        assertFalse(controller.canGoForward())

        controller.goBack()

        assertEquals(listOf(state), navigated)
        assertEquals(state, controller.currentLocation())
        assertTrue(controller.canGoForward())

        controller.goForward()

        assertEquals(listOf(state, action), navigated)
        assertEquals(action, controller.currentLocation())
    }

    fun testRecordingAfterBackClearsForwardHistory() {
        val controller = ReduxPaneHistoryController { _, _ -> }
        val state = ReduxPaneLocation.State(stateField("BrowserState.items"))
        val action = ReduxPaneLocation.Action(action("AddTabAction"))
        val replacement = ReduxPaneLocation.Action(action("RemoveTabAction"))

        controller.record(state)
        controller.record(action)
        controller.goBack()

        assertTrue(controller.canGoForward())

        controller.record(replacement)

        assertFalse(controller.canGoForward())
        assertTrue(controller.canGoBack())
    }

    fun testRecordingSameLocationTwiceDoesNotCreateDuplicateBackEntry() {
        val controller = ReduxPaneHistoryController { _, _ -> }
        val action = ReduxPaneLocation.Action(action("AddTabAction"))

        controller.record(action)
        controller.record(action.copy(action = action.action.copy()))

        assertFalse(controller.canGoBack())
        assertFalse(controller.canGoForward())
    }

    private fun action(name: String): ActionInfo =
        ActionInfo(
            id = ActionId(name),
            displayName = name,
            declaration = null
        )

    private fun stateField(id: String): StateFieldInfo =
        StateFieldInfo(
            id = id,
            fieldName = id.substringAfterLast('.'),
            fieldPath = id.substringAfterLast('.'),
            stateClassName = id.substringBeforeLast('.'),
            qualifiedPath = id,
            declarationPointer = null
        )
}
