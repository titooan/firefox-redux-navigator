package org.mozilla.reduxnav.nativeflow

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class NativeFlowPreviewPanel(
    private val parser: MermaidSubsetParser = MermaidSubsetParser(),
    private val layouter: FlowGraphLayouter = FlowGraphLayouter()
) : JPanel(BorderLayout()) {
    private val graphPanel = FlowGraphPanel()
    private val scrollPane = JBScrollPane(graphPanel)

    init {
        border = JBUI.Borders.empty()
        installZoomSupport()
        showMessage("Select a Redux Action and choose \"Show Redux Flow\".")
    }

    fun setMermaidSource(source: String) {
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
        add(createToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        revalidate()
        repaint()
        SwingUtilities.invokeLater {
            graphPanel.fitTo(scrollPane.viewport.extentSize)
        }
    }

    private fun showMessage(message: String) {
        removeAll()
        graphPanel.setRenderedGraph(null)
        add(JBLabel("<html>${message.replace("\n", "<br/>")}</html>"), BorderLayout.NORTH)
        revalidate()
        repaint()
    }

    private fun showError(message: String) {
        removeAll()
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

    private fun createToolbar(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8)
            isOpaque = false
            add(JButton("-").apply { addActionListener { graphPanel.zoomOut() } })
            add(Box.createHorizontalStrut(6))
            add(JButton("+").apply { addActionListener { graphPanel.zoomIn() } })
            add(Box.createHorizontalStrut(6))
            add(JButton("Reset").apply { addActionListener { graphPanel.resetZoom() } })
            add(Box.createHorizontalStrut(6))
            add(
                JButton("Fit").apply {
                    addActionListener { graphPanel.fitTo(scrollPane.viewport.extentSize) }
                }
            )
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
}
