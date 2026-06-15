package org.mozilla.reduxnav.toolwindow

import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.state.StateFieldInfo

sealed interface ReduxPaneLocation {
    val stableId: String

    data class Action(val action: ActionInfo) : ReduxPaneLocation {
        override val stableId: String = "action:${action.id.value}"
    }

    data class State(val field: StateFieldInfo) : ReduxPaneLocation {
        override val stableId: String = "state:${field.id}"
    }
}
