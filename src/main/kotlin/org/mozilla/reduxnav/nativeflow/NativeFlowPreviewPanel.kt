package org.mozilla.reduxnav.nativeflow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.graph.ReduxGraphAdapter
import org.mozilla.reduxnav.graph.ReduxGraph
import org.mozilla.reduxnav.toolwindow.ReduxFlowPanel
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
import javax.swing.JMenuItem
import javax.swing.MenuElement
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.JViewport

class NativeFlowPreviewPanel(
    private val onNodeNavigate: ((DiagramNodeTarget) -> Unit)? = null,
    private val onNodeSelected: ((DiagramNodeTarget) -> Unit)? = null,
    private val createNodeContextMenu: ((DiagramNodeTarget) -> javax.swing.JPopupMenu?)? = null,
    private val adapter: ReduxGraphAdapter = ReduxGraphAdapter(),
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

    fun setReduxGraph(
        graph: ReduxGraph?,
        nodeTargets: Map<String, DiagramNodeTarget> = emptyMap()
    ) {
        setFlowGraph(graph?.let(adapter::adapt), nodeTargets, emptyMessage = "Select a Redux Action and choose \"Show Redux Flow\".")
    }

    fun setFlowGraph(
        graph: FlowGraph?,
        nodeTargets: Map<String, DiagramNodeTarget> = emptyMap(),
        emptyMessage: String = "Select a Redux Action and choose \"Show Redux Flow\"."
    ) {
        this.nodeTargets = nodeTargets
        if (graph == null) {
            showMessage(emptyMessage)
            return
        }

        try {
            val renderedGraph = layouter.layout(graph)
            showGraph(renderedGraph)
        } catch (error: Exception) {
            LOG.warn("Could not build Redux graph.", error)
            showError(error.message ?: "Could not build Redux graph.")
        }
    }

    private fun showGraph(renderedGraph: RenderedGraph) {
        removeAll()
        graphPanel.setRenderedGraph(renderedGraph)
        graphPanel.setInteractiveNodeIds(
            nodeTargets.keys,
            onNodeSelected = { nodeId ->
                val target = nodeTargets[nodeId] ?: return@setInteractiveNodeIds
                onNodeSelected?.invoke(target)
            },
            onNodeDoubleClick = { nodeId ->
                val target = nodeTargets[nodeId] ?: return@setInteractiveNodeIds
                onNodeNavigate?.invoke(target)
            },
            createNodeContextMenu = { nodeId ->
                val target = nodeTargets[nodeId] ?: return@setInteractiveNodeIds null
                createNodeContextMenu?.invoke(target)
            }
        )
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
        graphPanel.setInteractiveNodeIds(emptySet())
        add(JBLabel("<html>${message.replace("\n", "<br/>")}</html>"), BorderLayout.NORTH)
        revalidate()
        repaint()
    }

    private fun showError(message: String) {
        removeAll()
        nodeTargets = emptyMap()
        graphPanel.setRenderedGraph(null)
        graphPanel.setInteractiveNodeIds(emptySet())
        add(
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(12)
                add(JBLabel("Could not build Redux graph."), BorderLayout.NORTH)
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

    fun showErrorMessage(message: String) {
        showError(message)
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

    internal fun contextMenuLabelsForTest(nodeId: String): List<String> =
        graphPanel.contextMenuForTest(nodeId)
            ?.let { menu ->
                @Suppress("UNCHECKED_CAST")
                (menu.getClientProperty(ReduxFlowPanel.CONTEXT_MENU_LABELS_TEST_KEY) as? List<String>)
                    ?: menu.subElements.toList().flatMap(::collectMenuItemLabels)
            }
            .orEmpty()

    private fun collectMenuItemLabels(element: MenuElement): List<String> =
        buildList {
            val component = element.component
            if (component is JMenuItem) {
                component.text?.takeIf { it.isNotBlank() }?.let(::add)
            }
            element.subElements.forEach { child -> addAll(collectMenuItemLabels(child)) }
        }

    companion object {
        private val LOG = Logger.getInstance(NativeFlowPreviewPanel::class.java)
    }
}
