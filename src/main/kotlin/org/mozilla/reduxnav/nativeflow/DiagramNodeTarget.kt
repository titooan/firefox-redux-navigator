package org.mozilla.reduxnav.nativeflow

import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage

sealed interface DiagramNodeTarget {
    val tooltipText: String

    data class UsageTarget(
        val usage: ReduxUsage
    ) : DiagramNodeTarget {
        override val tooltipText: String = "Open ${usage.fileName}:${usage.line}"
    }

    data class ActionTarget(
        val action: ActionInfo
    ) : DiagramNodeTarget {
        override val tooltipText: String = "Open ${action.displayName}"
    }
}
