package org.mozilla.reduxnav.nativeflow

import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.math.ElkPadding
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.util.ElkGraphUtil
import org.eclipse.elk.alg.layered.options.LayeredOptions
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.alg.layered.options.OrderingStrategy
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.graph.ElkEdge
import org.eclipse.elk.graph.ElkNode

class FlowGraphLayouter {
    fun layout(graph: FlowGraph): RenderedGraph {
        ElkRuntime.ensureLayeredAlgorithmRegistered()

        val root = ElkGraphUtil.createGraph().apply {
            setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered")
            setProperty(CoreOptions.DIRECTION, Direction.RIGHT)
            setProperty(CoreOptions.PADDING, ElkPadding(PADDING))
            setProperty(CoreOptions.SPACING_NODE_NODE, NODE_SPACING)
            setProperty(CoreOptions.SPACING_EDGE_EDGE, EDGE_SPACING)
            setProperty(CoreOptions.SPACING_EDGE_NODE, EDGE_NODE_SPACING)
            setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, LAYER_SPACING)
            setProperty(LayeredOptions.CROSSING_MINIMIZATION_FORCE_NODE_MODEL_ORDER, true)
            setProperty(LayeredOptions.CONSIDER_MODEL_ORDER_STRATEGY, OrderingStrategy.PREFER_NODES)
        }

        val elkNodes: Map<String, ElkNode> = graph.nodes.associate { node ->
            node.id to ElkGraphUtil.createNode(root).apply {
                identifier = node.id
                val size = estimateNodeSize(node.label)
                width = size.width
                height = size.height
            }
        }

        val elkEdges: List<ElkEdge> = graph.edges.map { edge ->
            val source = elkNodes[edge.from] ?: error("Missing source node ${edge.from}")
            val target = elkNodes[edge.to] ?: error("Missing target node ${edge.to}")
            ElkGraphUtil.createSimpleEdge(source, target).apply {
                identifier = "${edge.from}->${edge.to}"
            }
        }

        RecursiveGraphLayoutEngine().layout(root, BasicProgressMonitor())

        val renderedNodes = graph.nodes.map { node ->
            val elkNode = elkNodes.getValue(node.id)
            RenderedNode(
                id = node.id,
                label = node.label,
                kind = node.kind,
                x = elkNode.x,
                y = elkNode.y,
                width = elkNode.width,
                height = elkNode.height,
                isTestNode = node.isTestNode,
                tooltipText = node.tooltipText
            )
        }

        val renderedEdges = graph.edges.mapIndexed { index, edge ->
            val elkEdge = elkEdges[index]
            val section = elkEdge.sections.firstOrNull()
            val points = buildList {
                if (section != null) {
                    add(Point2D(section.startX, section.startY))
                    section.bendPoints.forEach { add(Point2D(it.x, it.y)) }
                    add(Point2D(section.endX, section.endY))
                } else {
                    val source = renderedNodes.first { it.id == edge.from }
                    val target = renderedNodes.first { it.id == edge.to }
                    add(Point2D(source.x + source.width, source.y + source.height / 2.0))
                    add(Point2D(target.x, target.y + target.height / 2.0))
                }
            }

            RenderedEdge(
                from = edge.from,
                to = edge.to,
                points = points
            )
        }

        val width = listOf(
            root.width,
            renderedNodes.maxOfOrNull { it.x + it.width } ?: 0.0,
            renderedEdges.flatMap { it.points }.maxOfOrNull { it.x } ?: 0.0
        ).max()
        val height = listOf(
            root.height,
            renderedNodes.maxOfOrNull { it.y + it.height } ?: 0.0,
            renderedEdges.flatMap { it.points }.maxOfOrNull { it.y } ?: 0.0
        ).max()

        return RenderedGraph(
            width = width,
            height = height,
            nodes = renderedNodes,
            edges = renderedEdges
        )
    }

    private fun estimateNodeSize(label: String): NodeSize {
        val longestLine = label.lineSequence().maxOfOrNull { it.length } ?: 0
        val lineCount = label.lineSequence().count().coerceAtLeast(1)
        val width = (longestLine * CHARACTER_WIDTH) + (HORIZONTAL_PADDING * 2)
        val height = (lineCount * LINE_HEIGHT) + (VERTICAL_PADDING * 2)
        return NodeSize(
            width = width.coerceAtLeast(MIN_NODE_WIDTH),
            height = height.coerceAtLeast(MIN_NODE_HEIGHT)
        )
    }

    private data class NodeSize(
        val width: Double,
        val height: Double
    )

    companion object {
        private const val PADDING = 24.0
        private const val NODE_SPACING = 32.0
        private const val LAYER_SPACING = 72.0
        private const val EDGE_SPACING = 24.0
        private const val EDGE_NODE_SPACING = 18.0
        private const val CHARACTER_WIDTH = 7.2
        private const val LINE_HEIGHT = 18.0
        private const val HORIZONTAL_PADDING = 16.0
        private const val VERTICAL_PADDING = 10.0
        private const val MIN_NODE_WIDTH = 120.0
        private const val MIN_NODE_HEIGHT = 40.0
    }
}

private object ElkRuntime {
    @Volatile
    private var initialized = false

    fun ensureLayeredAlgorithmRegistered() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val service = LayoutMetaDataService.getInstance()
            if (service.getAlgorithmData("org.eclipse.elk.layered") == null) {
                service.registerLayoutMetaDataProviders(LayeredMetaDataProvider())
            }
            initialized = true
        }
    }
}
