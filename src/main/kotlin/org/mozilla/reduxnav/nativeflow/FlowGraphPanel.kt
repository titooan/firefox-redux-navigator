package org.mozilla.reduxnav.nativeflow

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class FlowGraphPanel(
    renderedGraph: RenderedGraph? = null
) : JComponent() {
    private var renderedGraph: RenderedGraph? = renderedGraph
    private var scale = 1.0

    init {
        isOpaque = true
        border = JBUI.Borders.empty(16)
        updatePreferredSize()
    }

    fun setRenderedGraph(graph: RenderedGraph?) {
        renderedGraph = graph
        scale = 1.0
        updatePreferredSize()
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
        val colors = if (node.isTestNode) testNodeColors() else standardNodeColors()
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

        g.color = arrowColor()
        g.stroke = BasicStroke(
            JBUI.scale(1.5f),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        )

        val path = Path2D.Double().apply {
            moveTo(edge.points.first().x, edge.points.first().y)
            edge.points.drop(1).forEach { point -> lineTo(point.x, point.y) }
        }
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

    private fun standardNodeColors(): NodeColors = NodeColors(
        fill = UIUtil.getPanelBackground(),
        border = JBColor.border(),
        text = UIUtil.getLabelForeground()
    )

    private fun testNodeColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xD7, 0xEA, 0xD7), Color(0x21, 0x4D, 0x29)),
        border = JBColor(Color(0x33, 0x88, 0x33), Color(0x4A, 0xA3, 0x5F)),
        text = JBColor(Color(0x1E, 0x2A, 0x1E), Color(0xF4, 0xFF, 0xF4))
    )

    private fun arrowColor(): Color = JBColor.border()

    private data class NodeColors(
        val fill: Color,
        val border: Color,
        val text: Color
    )

    companion object {
        private const val MIN_SCALE = 0.35
        private const val MAX_SCALE = 3.0
        private const val ZOOM_STEP = 1.1
    }
}
