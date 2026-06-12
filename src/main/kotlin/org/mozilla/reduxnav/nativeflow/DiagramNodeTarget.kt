package org.mozilla.reduxnav.nativeflow

import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.state.StateFieldInfo
import org.mozilla.reduxnav.state.StateModification

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

    data class ModificationTarget(
        val modification: StateModification
    ) : DiagramNodeTarget {
        override val tooltipText: String = "Open ${modification.fileName}:${modification.lineNumber}"
    }

    data class StateFieldTarget(
        val field: StateFieldInfo
    ) : DiagramNodeTarget {
        override val tooltipText: String = "Open ${field.qualifiedPath}"
    }
}
