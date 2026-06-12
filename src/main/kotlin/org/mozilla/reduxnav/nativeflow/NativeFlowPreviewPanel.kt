package org.mozilla.reduxnav.nativeflow

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.ui.withTransientFocusRing
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.JViewport

class NativeFlowPreviewPanel(
    private val onNodeClick: ((DiagramNodeTarget) -> Unit)? = null,
    private val parser: MermaidSubsetParser = MermaidSubsetParser(),
    private val layouter: FlowGraphLayouter = FlowGraphLayouter()
) : JPanel(BorderLayout()) {
    private val graphPanel = FlowGraphPanel()
    private val scrollPane = JBScrollPane(graphPanel)
    private var nodeTargets: Map<String, DiagramNodeTarget> = emptyMap()

    init {
        border = JBUI.Borders.empty()
        scrollPane.viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
        installZoomSupport()
        showMessage("Select a Redux Action and choose \"Show Redux Flow\".")
    }

    fun setMermaidSource(
        source: String,
        nodeTargets: Map<String, DiagramNodeTarget> = emptyMap()
    ) {
        this.nodeTargets = nodeTargets
        if (source.isBlank()) {
            showMessage("Select a Redux Action and choose \"Show Redux Flow\".")
            return
        }

        try {
            val parsedGraph = parser.parse(source)
            val renderedGraph = layouter.layout(parsedGraph)
            showGraph(renderedGraph)
        } catch (error: Exception) {
            showError(error.message ?: "Could not render diagram.")
        }
    }

    private fun showGraph(renderedGraph: RenderedGraph) {
        removeAll()
        graphPanel.setRenderedGraph(renderedGraph)
        graphPanel.setNodeTargets(nodeTargets) { nodeId ->
            val target = nodeTargets[nodeId] ?: return@setNodeTargets
            onNodeClick?.invoke(target)
        }
        add(createHeader(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        revalidate()
        repaint()
        SwingUtilities.invokeLater {
            graphPanel.fitTo(scrollPane.viewport.extentSize)
        }
    }

    private fun showMessage(message: String) {
        removeAll()
        nodeTargets = emptyMap()
        graphPanel.setRenderedGraph(null)
        graphPanel.setNodeTargets(emptyMap())
        add(JBLabel("<html>${message.replace("\n", "<br/>")}</html>"), BorderLayout.NORTH)
        revalidate()
        repaint()
    }

    private fun showError(message: String) {
        removeAll()
        nodeTargets = emptyMap()
        graphPanel.setRenderedGraph(null)
        graphPanel.setNodeTargets(emptyMap())
        add(
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(12)
                add(JBLabel("Diagram could not be rendered."), BorderLayout.NORTH)
                add(
                    JBScrollPane(
                        JBTextArea(message).apply {
                            isEditable = false
                            lineWrap = true
                            wrapStyleWord = true
                            border = JBUI.Borders.empty(8)
                        }
                    ),
                    BorderLayout.CENTER
                )
            },
            BorderLayout.CENTER
        )
        revalidate()
        repaint()
    }

    private fun createHeader(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(createToolbar())
            add(createLegend())
        }

    private fun createToolbar(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8)
            isOpaque = false
            add(createToolbarButton("-") { graphPanel.zoomOut() })
            add(Box.createHorizontalStrut(6))
            add(createToolbarButton("+") { graphPanel.zoomIn() })
            add(Box.createHorizontalStrut(6))
            add(createToolbarButton("Reset") { graphPanel.resetZoom() })
            add(Box.createHorizontalStrut(6))
            add(createToolbarButton("Fit") { graphPanel.fitTo(scrollPane.viewport.extentSize) })
        }

    private fun createToolbarButton(text: String, onClick: () -> Unit): JButton =
        JButton(text).apply {
            addActionListener { onClick() }
        }.withTransientFocusRing()

    private fun createLegend(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(0, 8, 8, 8)
            isOpaque = false

            add(JBLabel("Legend:"))
            FlowNodePalette.legendEntries().forEachIndexed { index, entry ->
                add(Box.createHorizontalStrut(if (index == 0) 10 else 8))
                add(createLegendSwatch(entry))
                add(Box.createHorizontalStrut(4))
                add(JBLabel(entry.label))
            }
        }

    private fun createLegendSwatch(entry: LegendEntry): JComponent =
        JPanel().apply {
            preferredSize = Dimension(14, 14)
            minimumSize = preferredSize
            maximumSize = preferredSize
            background = entry.colors.fill
            border = BorderFactory.createLineBorder(entry.colors.border, 1)
            toolTipText = entry.label
        }

    private fun installZoomSupport() {
        scrollPane.addMouseWheelListener { event ->
            if (!event.isZoomGesture()) return@addMouseWheelListener

            event.consume()
            if (event.preciseWheelRotation < 0) {
                graphPanel.zoomIn()
            } else {
                graphPanel.zoomOut()
            }
        }
    }

    private fun MouseWheelEvent.isZoomGesture(): Boolean =
        modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK) != 0

    internal fun viewportScrollMode(): Int = scrollPane.viewport.scrollMode

    internal fun legendLabelsForTest(): List<String> =
        FlowNodePalette.legendEntries().map { it.label }

    internal fun toolbarButtonFocusStatesForTest(): List<Pair<Boolean, Boolean>> =
        createToolbar().components
            .filterIsInstance<JButton>()
            .map { it.isFocusable to it.isFocusPainted }

    internal fun interactiveNodeIdsForTest(): Set<String> = nodeTargets.keys
}
