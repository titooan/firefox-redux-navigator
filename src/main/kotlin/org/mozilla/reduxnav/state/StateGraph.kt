package org.mozilla.reduxnav.state

import org.mozilla.reduxnav.popup.isTestPath

data class StateGraph(
    val nodes: List<StateGraphNode>,
    val edges: List<StateGraphEdge>
)

data class StateGraphEdge(
    val from: String,
    val to: String
)

enum class StateGraphNodeKind {
    ACTION,
    REDUCER,
    STATE
}

sealed interface StateGraphNode {
    val id: String
    val label: String
    val kind: StateGraphNodeKind
    val isTestNode: Boolean
}

data class StateActionNode(
    val actionLabel: String,
    override val id: String
) : StateGraphNode {
    override val label: String = actionLabel
    override val kind: StateGraphNodeKind = StateGraphNodeKind.ACTION
    override val isTestNode: Boolean = false
}

data class StateReducerNode(
    val modification: StateModification,
    override val id: String
) : StateGraphNode {
    override val label: String = "${modification.fileName}:${modification.lineNumber}"
    override val kind: StateGraphNodeKind = StateGraphNodeKind.REDUCER
    override val isTestNode: Boolean = isTestPath(modification.filePath)
}

data class StateFieldNode(
    val field: StateFieldInfo
) : StateGraphNode {
    override val id: String = "state"
    override val label: String = field.qualifiedPath
    override val kind: StateGraphNodeKind = StateGraphNodeKind.STATE
    override val isTestNode: Boolean = false
}
