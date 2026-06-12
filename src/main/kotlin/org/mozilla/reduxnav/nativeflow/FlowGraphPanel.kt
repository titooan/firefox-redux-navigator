package org.mozilla.reduxnav.nativeflow

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class FlowGraphPanel(
    renderedGraph: RenderedGraph? = null
) : JComponent() {
    private var renderedGraph: RenderedGraph? = renderedGraph
    private var nodeTargets: Map<String, DiagramNodeTarget> = emptyMap()
    private var onNodeClick: ((String) -> Unit)? = null
    private var scale = 1.0

    init {
        isOpaque = true
        border = JBUI.Borders.empty(16)
        ToolTipManager.sharedInstance().registerComponent(this)
        installNodeInteraction()
        updatePreferredSize()
    }

    fun setRenderedGraph(graph: RenderedGraph?) {
        renderedGraph = graph
        scale = 1.0
        updatePreferredSize()
        updateCursorFor(null)
        repaint()
    }

    fun setNodeTargets(
        nodeTargets: Map<String, DiagramNodeTarget>,
        onNodeClick: ((String) -> Unit)? = null
    ) {
        this.nodeTargets = nodeTargets
        this.onNodeClick = onNodeClick
        updateCursorFor(null)
        repaint()
    }

    fun setScale(newScale: Double) {
        scale = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        updatePreferredSize()
        revalidate()
        repaint()
    }

    fun scale(): Double = scale

    fun zoomIn() = setScale(scale * ZOOM_STEP)

    fun zoomOut() = setScale(scale / ZOOM_STEP)

    fun resetZoom() = setScale(1.0)

    fun fitTo(viewport: Dimension) {
        val graph = renderedGraph ?: return
        if (graph.width <= 0.0 || graph.height <= 0.0 || viewport.width <= 0 || viewport.height <= 0) {
            return
        }

        val availableWidth = max(1.0, viewport.width - insets.left - insets.right - 24.0)
        val availableHeight = max(1.0, viewport.height - insets.top - insets.bottom - 24.0)
        val fitScale = minOf(availableWidth / graph.width, availableHeight / graph.height)
        setScale(fitScale.coerceIn(MIN_SCALE, MAX_SCALE))
    }

    override fun getPreferredSize(): Dimension = super.getPreferredSize()

    override fun getToolTipText(event: MouseEvent): String? =
        event.point
            ?.let(::nodeAt)
            ?.takeIf { it.id in nodeTargets }
            ?.let { nodeTargets[it.id]?.tooltipText }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        val graph = renderedGraph ?: return
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.scale(scale, scale)

            graph.edges.forEach { drawEdge(g, it) }
            graph.nodes.forEach { drawNode(g, it) }
        } finally {
            g.dispose()
        }
    }

    private fun drawNode(g: Graphics2D, node: RenderedNode) {
        val colors = FlowNodePalette.colorsFor(node.kind, node.isTestNode)
        val shape = RoundRectangle2D.Double(
            node.x,
            node.y,
            node.width,
            node.height,
            JBUI.scale(16).toDouble(),
            JBUI.scale(16).toDouble()
        )

        g.color = colors.fill
        g.fill(shape)
        g.color = colors.border
        g.stroke = BasicStroke(JBUI.scale(1f))
        g.draw(shape)

        g.color = colors.text
        val metrics = g.fontMetrics
        val textX = node.x + JBUI.scale(12)
        val textY = node.y + ((node.height - metrics.height) / 2.0) + metrics.ascent
        g.drawString(node.label, textX.toFloat(), textY.toFloat())
    }

    private fun drawEdge(g: Graphics2D, edge: RenderedEdge) {
        if (edge.points.size < 2) return

        g.color = edgeColor(edge)
        g.stroke = BasicStroke(
            JBUI.scale(1.5f),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        )

        val path = createRoundedPath(edge.points)
        g.draw(path)
        drawArrowHead(g, edge.points[edge.points.lastIndex - 1], edge.points.last())
    }

    private fun drawArrowHead(g: Graphics2D, from: Point2D, to: Point2D) {
        val angle = atan2(to.y - from.y, to.x - from.x)
        val arrowLength = JBUI.scale(10).toDouble()
        val arrowWidth = JBUI.scale(5).toDouble()

        val left = Point2D(
            x = to.x - arrowLength * cos(angle) + arrowWidth * sin(angle),
            y = to.y - arrowLength * sin(angle) - arrowWidth * cos(angle)
        )
        val right = Point2D(
            x = to.x - arrowLength * cos(angle) - arrowWidth * sin(angle),
            y = to.y - arrowLength * sin(angle) + arrowWidth * cos(angle)
        )

        val arrow = Path2D.Double().apply {
            moveTo(to.x, to.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            closePath()
        }
        g.fill(arrow)
    }

    private fun updatePreferredSize() {
        val graph = renderedGraph
        preferredSize = if (graph == null) {
            Dimension(320, 200)
        } else {
            Dimension(
                ((graph.width * scale) + insets.left + insets.right).toInt().coerceAtLeast(320),
                ((graph.height * scale) + insets.top + insets.bottom).toInt().coerceAtLeast(200)
            )
        }
    }

    private fun installNodeInteraction() {
        val listener = object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                updateCursorFor(event.point)
            }

            override fun mouseExited(event: MouseEvent) {
                updateCursorFor(null)
            }

            override fun mouseClicked(event: MouseEvent) {
                if (event.button != MouseEvent.BUTTON1) return
                val nodeId = nodeAt(event.point)?.id ?: return
                if (nodeId !in nodeTargets) return
                onNodeClick?.invoke(nodeId)
            }
        }
        addMouseMotionListener(listener)
        addMouseListener(listener)
    }

    private fun updateCursorFor(point: Point?) {
        cursor = if (point != null && nodeAt(point)?.id in nodeTargets) {
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        } else {
            Cursor.getDefaultCursor()
        }
    }

    private fun nodeAt(point: Point): RenderedNode? {
        val graph = renderedGraph ?: return null
        val x = point.x / scale
        val y = point.y / scale
        return graph.nodes.lastOrNull { node ->
            x >= node.x &&
                x <= node.x + node.width &&
                y >= node.y &&
                y <= node.y + node.height
        }
    }

    private fun edgeColor(edge: RenderedEdge): Color {
        val graph = renderedGraph ?: return JBColor.border()
        return FlowEdgePalette.colorFor(edge, graph.nodes.associateBy { it.id })
    }

    internal fun edgeColorsForTest(): List<Color> {
        val graph = renderedGraph ?: return emptyList()
        val nodesById = graph.nodes.associateBy { it.id }
        return graph.edges.map { FlowEdgePalette.colorFor(it, nodesById) }
    }

    internal fun nodeIdAtForTest(x: Int, y: Int): String? = nodeAt(Point(x, y))?.id

    internal fun cursorTypeForTest(x: Int, y: Int): Int {
        updateCursorFor(Point(x, y))
        return cursor.type
    }

    private fun createRoundedPath(points: List<Point2D>): Path2D.Double =
        Path2D.Double().apply {
            if (points.isEmpty()) return@apply

            moveTo(points.first().x, points.first().y)
            if (points.size == 1) return@apply
            if (points.size == 2) {
                lineTo(points.last().x, points.last().y)
                return@apply
            }

            for (index in 1 until points.lastIndex) {
                val previous = points[index - 1]
                val current = points[index]
                val next = points[index + 1]
                val radius = min(
                    CORNER_RADIUS,
                    min(distance(previous, current), distance(current, next)) / 2.0
                )

                if (radius <= 0.0 || isCollinear(previous, current, next)) {
                    lineTo(current.x, current.y)
                    continue
                }

                val entry = trimToward(current, previous, radius)
                val exit = trimToward(current, next, radius)
                lineTo(entry.x, entry.y)
                quadTo(current.x, current.y, exit.x, exit.y)
            }

            val last = points.last()
            lineTo(last.x, last.y)
        }

    private fun trimToward(from: Point2D, toward: Point2D, distance: Double): Point2D {
        val dx = toward.x - from.x
        val dy = toward.y - from.y
        val length = hypot(dx, dy)
        if (length == 0.0) return from
        val scale = distance / length
        return Point2D(
            x = from.x + (dx * scale),
            y = from.y + (dy * scale)
        )
    }

    private fun distance(from: Point2D, to: Point2D): Double = hypot(to.x - from.x, to.y - from.y)

    private fun isCollinear(previous: Point2D, current: Point2D, next: Point2D): Boolean {
        val crossProduct = ((current.x - previous.x) * (next.y - current.y)) - ((current.y - previous.y) * (next.x - current.x))
        return kotlin.math.abs(crossProduct) < 0.001
    }

    companion object {
        private const val MIN_SCALE = 0.35
        private const val MAX_SCALE = 3.0
        private const val ZOOM_STEP = 1.1
        private const val CORNER_RADIUS = 18.0

        internal fun segmentTypesForRoundedPathTest(points: List<Point2D>): List<String> {
            val path = FlowGraphPanel().createRoundedPath(points)
            val iterator = path.getPathIterator(null)
            val coords = DoubleArray(6)
            val segments = mutableListOf<String>()
            while (!iterator.isDone) {
                val segmentName = when (iterator.currentSegment(coords)) {
                    PathIterator.SEG_MOVETO -> "MOVE_TO"
                    PathIterator.SEG_LINETO -> "LINE_TO"
                    PathIterator.SEG_QUADTO -> "QUAD_TO"
                    PathIterator.SEG_CUBICTO -> "CUBIC_TO"
                    PathIterator.SEG_CLOSE -> "CLOSE"
                    else -> "UNKNOWN"
                }
                segments += segmentName
                iterator.next()
            }
            return segments
        }
    }
}
