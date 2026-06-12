package org.mozilla.reduxnav.graph

import com.intellij.openapi.application.ReadAction
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.lineText
import org.mozilla.reduxnav.popup.isTestPath

data class ReduxGraph(
    val nodes: List<ReduxGraphNode>,
    val edges: List<ReduxGraphEdge>
)

data class ReduxGraphEdge(
    val from: String,
    val to: String
)

enum class ReduxGraphNodeKind(val displayName: String) {
    DISPATCH("Dispatch"),
    ACTION("Action"),
    MIDDLEWARE("Middleware"),
    REDUCER("Reducer")
}

sealed interface ReduxGraphNode {
    val id: String
    val label: String
    val kind: ReduxGraphNodeKind
    val navigationTarget: ReduxGraphNavigationTarget
    val isTestNode: Boolean
}

data class DispatchNode(
    val usage: ReduxUsage,
    override val id: String
) : ReduxGraphNode {
    override val label: String = "${usage.fileName}:${usage.line}"
    override val kind: ReduxGraphNodeKind = ReduxGraphNodeKind.DISPATCH
    override val navigationTarget: ReduxGraphNavigationTarget = ReduxGraphNavigationTarget.UsageTarget(usage)
    override val isTestNode: Boolean = isTestPath(usage.filePath)
}

data class ActionNode(
    val action: ActionInfo
) : ReduxGraphNode {
    override val id: String = "action"
    override val label: String = action.displayName
    override val kind: ReduxGraphNodeKind = ReduxGraphNodeKind.ACTION
    override val navigationTarget: ReduxGraphNavigationTarget = ReduxGraphNavigationTarget.ActionTarget(action)
    override val isTestNode: Boolean = false
}

data class MiddlewareNode(
    val usage: ReduxUsage,
    override val id: String
) : ReduxGraphNode {
    override val label: String = "${usage.fileName}:${usage.line}"
    override val kind: ReduxGraphNodeKind = ReduxGraphNodeKind.MIDDLEWARE
    override val navigationTarget: ReduxGraphNavigationTarget = ReduxGraphNavigationTarget.UsageTarget(usage)
    override val isTestNode: Boolean = isTestPath(usage.filePath)
}

data class ReducerNode(
    val usage: ReduxUsage,
    override val id: String
) : ReduxGraphNode {
    override val label: String = "${usage.fileName}:${usage.line}"
    override val kind: ReduxGraphNodeKind = ReduxGraphNodeKind.REDUCER
    override val navigationTarget: ReduxGraphNavigationTarget = ReduxGraphNavigationTarget.UsageTarget(usage)
    override val isTestNode: Boolean = isTestPath(usage.filePath)
}

sealed interface ReduxGraphNavigationTarget {
    val tooltipText: String

    data class UsageTarget(
        val usage: ReduxUsage
    ) : ReduxGraphNavigationTarget {
        override val tooltipText: String
            get() {
                val lineText = ReadAction.compute<String, RuntimeException> {
                    usage.element.element?.lineText() ?: usage.displayText
                }
                return "<html>${usage.fileName}:${usage.line}<br/>${escapeHtml(lineText)}</html>"
            }
    }

    data class ActionTarget(
        val action: ActionInfo
    ) : ReduxGraphNavigationTarget {
        override val tooltipText: String
            get() = "<html>${escapeHtml(action.displayName)}</html>"
    }

    companion object {
        private fun escapeHtml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
    }
}
