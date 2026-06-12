package org.mozilla.reduxnav.nativeflow

data class FlowGraph(
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>,
    val testNodeIds: Set<String> = emptySet()
)

enum class FlowNodeKind(val displayName: String) {
    DISPATCH("Dispatch"),
    ACTION("Action"),
    MIDDLEWARE("Middleware"),
    REDUCER("Reducer"),
    UNKNOWN("Node");

    companion object {
        fun fromNodeId(nodeId: String): FlowNodeKind =
            when {
                nodeId == "action" -> ACTION
                nodeId.startsWith("dispatch_") -> DISPATCH
                nodeId.startsWith("middleware_") -> MIDDLEWARE
                nodeId.startsWith("reducer_") -> REDUCER
                else -> UNKNOWN
            }
    }
}

data class FlowNode(
    val id: String,
    val label: String
)

data class FlowEdge(
    val from: String,
    val to: String
)

data class Point2D(
    val x: Double,
    val y: Double
)

data class RenderedGraph(
    val width: Double,
    val height: Double,
    val nodes: List<RenderedNode>,
    val edges: List<RenderedEdge>
)

data class RenderedNode(
    val id: String,
    val label: String,
    val kind: FlowNodeKind,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val isTestNode: Boolean = false
)

data class RenderedEdge(
    val from: String,
    val to: String,
    val points: List<Point2D>
)
