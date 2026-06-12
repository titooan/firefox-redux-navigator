package org.mozilla.reduxnav.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class MermaidPreviewPanel(
    private val project: Project,
    private val htmlRenderer: MermaidHtmlRenderer = MermaidHtmlRenderer()
) : JPanel(BorderLayout()), Disposable {
    private val logger = Logger.getInstance(MermaidPreviewPanel::class.java)
    private val jcefDiagnostics = JcefAvailability.diagnostics()
    private val browser: JBCefBrowser? = if (jcefDiagnostics.supported) JBCefBrowser() else null

    init {
        border = JBUI.Borders.empty()
        when {
            browser == null -> {
                logger.info("JCEF unavailable for Mermaid preview:\n${jcefDiagnostics.details}")
                showFallbackDiagnostics(jcefDiagnostics)
            }
            !htmlRenderer.hasBundledScript() -> {
                logger.warn("Mermaid renderer resource is missing.")
                showMessage(MermaidHtmlRenderer.MISSING_RESOURCE_MESSAGE)
            }
            else -> add(browser.component, BorderLayout.CENTER)
        }
    }

    fun setMermaidSource(source: String) {
        val browser = browser ?: return
        if (source.isBlank()) {
            showMessage("Select a Redux Action and choose \"Show Redux Flow\".")
            return
        }
        if (!htmlRenderer.hasBundledScript()) {
            logger.warn("Mermaid renderer resource is missing.")
            showMessage(MermaidHtmlRenderer.MISSING_RESOURCE_MESSAGE)
            return
        }
        removeAll()
        add(browser.component, BorderLayout.CENTER)
        revalidate()
        repaint()
        browser.loadHTML(htmlRenderer.renderHtml(source))
    }

    override fun dispose() {
        browser?.dispose()
    }

    private fun showMessage(message: String) {
        removeAll()
        add(JBLabel("<html>${message.replace("\n", "<br/>")}</html>"), BorderLayout.NORTH)
        revalidate()
        repaint()
    }

    private fun showFallbackDiagnostics(diagnostics: JcefDiagnostics) {
        removeAll()
        add(createFallbackContent(diagnostics), BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun createFallbackContent(diagnostics: JcefDiagnostics): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(JBLabel("<html>${diagnostics.summary}</html>"))
                    add(Box.createVerticalStrut(8))
                    add(JBLabel("<html>You can try installing the <a href=\"\">Web Browser JCEF</a> plugin for this IDE.</html>"))
                    add(Box.createVerticalStrut(8))
                    add(JBLabel("Diagnostics"))
                },
                BorderLayout.NORTH
            )
            add(
                JBScrollPane(
                    JBTextArea(diagnostics.details).apply {
                        isEditable = false
                        lineWrap = false
                        wrapStyleWord = false
                    }
                ),
                BorderLayout.CENTER
            )
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    add(
                        JButton("Open JCEF Plugin Page").apply {
                            addActionListener { BrowserUtil.browse(JCEF_PLUGIN_URL) }
                        }
                    )
                    add(Box.createHorizontalStrut(8))
                    add(
                        JButton("Copy Diagnostics").apply {
                            addActionListener {
                                CopyPasteManager.getInstance().setContents(StringSelection(diagnostics.details))
                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("Redux Navigator")
                                    .createNotification("JCEF diagnostics copied to clipboard.", NotificationType.INFORMATION)
                                    .notify(project)
                            }
                        }
                    )
                },
                BorderLayout.SOUTH
            )
        }

    companion object {
        private const val JCEF_PLUGIN_URL = "https://plugins.jetbrains.com/plugin/31360-web-browser-jcef-"
    }
}
