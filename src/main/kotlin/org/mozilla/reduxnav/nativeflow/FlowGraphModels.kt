package org.mozilla.reduxnav.nativeflow

data class FlowGraph(
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>,
    val testNodeIds: Set<String> = emptySet()
)

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
