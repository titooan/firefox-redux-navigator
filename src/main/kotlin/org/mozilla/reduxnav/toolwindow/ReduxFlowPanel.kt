package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.mozilla.reduxnav.mermaid.MermaidFlowRenderer
import org.mozilla.reduxnav.mermaid.MermaidFlowStyle
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.nativeflow.NativeFlowPreviewPanel
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ReduxFlowPanel(
    private val project: Project,
    private val onRefresh: () -> Unit
) : JPanel(BorderLayout()), Disposable {
    private val mermaidRenderer = MermaidFlowRenderer(
        style = if (StartupUiUtil.isUnderDarcula) MermaidFlowStyle.dark() else MermaidFlowStyle.light()
    )
    private val mermaidPreviewPanel = NativeFlowPreviewPanel()
    private val headerTitle = JBLabel("Redux Flow").apply {
        font = JBFont.h3().asBold()
    }
    private val refreshButton = JButton("Refresh").apply {
        addActionListener { onRefresh() }
    }
    private val copyMermaidButton = JButton("Copy Mermaid").apply {
        addActionListener { copyMermaid() }
    }
    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
    }
    private val mermaidTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        border = JBEmptyBorder(12)
    }
    private val tabs = JBTabbedPane().apply {
        border = JBUI.Borders.empty()
        addTab("Flow", JBScrollPane(contentPanel))
        addTab("Diagram", mermaidPreviewPanel)
        addTab("Mermaid Source", JBScrollPane(mermaidTextArea))
    }

    private var currentAction: ActionInfo? = null
    private var currentMermaid: String = ""

    init {
        border = JBUI.Borders.empty()
        add(createHeader(), BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        showEmptyState()
    }

    fun showEmptyState() {
        currentAction = null
        currentMermaid = ""
        headerTitle.text = "Redux Flow"
        renderMessage("Select a Redux Action and choose \"Show Redux Flow\".")
        renderMermaid("")
        setButtonsEnabled(false)
        tabs.selectedIndex = FLOW_TAB_INDEX
    }

    fun showInvalidAction(action: ActionInfo) {
        currentAction = action
        currentMermaid = ""
        headerTitle.text = "Redux Flow: ${action.displayName}"
        renderMessage("The selected Redux Action is no longer valid. Re-run Show Redux Flow.")
        renderMermaid("")
        setButtonsEnabled(false)
        tabs.selectedIndex = FLOW_TAB_INDEX
    }

    fun showAnalysisError(action: ActionInfo) {
        currentAction = action
        currentMermaid = ""
        headerTitle.text = "Redux Flow: ${action.displayName}"
        renderMessage("Could not build Redux flow for this action.")
        renderMermaid("")
        setButtonsEnabled(false)
        tabs.selectedIndex = FLOW_TAB_INDEX
    }

    fun showGraph(graph: ActionGraph) {
        currentAction = graph.action
        currentMermaid = mermaidRenderer.render(graph)
        headerTitle.text = "Redux Flow: ${graph.action.displayName}"
        contentPanel.removeAll()
        contentPanel.add(summaryLabel(graph))
        contentPanel.add(Box.createVerticalStrut(12))

        val sections = buildSections(graph)
        sections.forEachIndexed { index, section ->
            contentPanel.add(ReduxFlowNodePanel(project, section))
            if (index < sections.lastIndex) {
                contentPanel.add(arrowLabel())
            }
        }

        renderMermaid(currentMermaid)
        setButtonsEnabled(true)
        revalidate()
        repaint()
    }

    internal fun renderMessage(message: String) {
        contentPanel.removeAll()
        contentPanel.add(JBLabel(message))
        revalidate()
        repaint()
    }

    private fun createHeader(): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(headerTitle, BorderLayout.WEST)
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    isOpaque = false
                    add(refreshButton)
                    add(Box.createHorizontalStrut(8))
                    add(copyMermaidButton)
                },
                BorderLayout.EAST
            )
        }

    private fun copyMermaid() {
        if (currentMermaid.isBlank()) return

        CopyPasteManager.getInstance().setContents(StringSelection(currentMermaid))
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification("Mermaid diagram copied to clipboard.", NotificationType.INFORMATION)
            .notify(project)
    }

    private fun renderMermaid(mermaid: String) {
        mermaidTextArea.text = mermaid
        mermaidTextArea.caretPosition = 0
        mermaidPreviewPanel.setMermaidSource(mermaid)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        copyMermaidButton.isEnabled = enabled
    }

    internal fun mermaidText(): String = mermaidTextArea.text

    internal fun selectedTabTitle(): String = tabs.getTitleAt(tabs.selectedIndex)

    override fun dispose() {
    }

    private fun summaryLabel(graph: ActionGraph): JComponent {
        val dispatches = graph.usages.count { it.kind == ReduxUsageKind.DISPATCH }
        val middlewares = graph.usages.count { it.kind == ReduxUsageKind.MIDDLEWARE }
        val reducers = graph.usages.count { it.kind == ReduxUsageKind.REDUCER }
        return JBLabel(
            "${countLabel(dispatches, "dispatch", "dispatches")} | " +
                "${countLabel(middlewares, "middleware", "middlewares")} | " +
                "${countLabel(reducers, "reducer", "reducers")}"
        )
    }

    private fun arrowLabel(): JComponent =
        JBLabel("\u2193").apply {
            border = JBUI.Borders.empty(8, 4)
        }

    companion object {
        private const val FLOW_TAB_INDEX = 0
        private const val NOTIFICATION_GROUP_ID = "Redux Navigator"
        private val sectionOrder = listOf(
            ReduxUsageKind.DISPATCH,
            ReduxUsageKind.MIDDLEWARE,
            ReduxUsageKind.REDUCER,
            ReduxUsageKind.OTHER
        )

        internal fun buildSections(graph: ActionGraph): List<ReduxFlowSection> {
            val grouped = graph.usages.groupBy { it.kind }
            val sections = mutableListOf<ReduxFlowSection>()

            sections += ReduxFlowSection.Action(graph.action)

            sectionOrder.forEach { kind ->
                val usages = grouped[kind].orEmpty()
                    .sortedWith(compareBy<ReduxUsage> { it.filePath }.thenBy { it.line })
                if (kind == ReduxUsageKind.OTHER && usages.isEmpty()) return@forEach
                sections += ReduxFlowSection.Usages(kind, usages)
            }

            return listOf(sections[1], sections[0], sections[2], sections[3]) +
                sections.drop(4)
        }

        internal fun createNavigationLink(
            text: String,
            onNavigate: () -> Unit
        ): ActionLink = ActionLink(text) { onNavigate() }

        internal fun navigateToUsage(project: Project, usage: ReduxUsage) {
            val element = usage.element.element ?: return
            val file = element.containingFile?.virtualFile ?: return
            OpenFileDescriptor(project, file, element.textOffset).navigate(true)
        }

        internal fun navigateToAction(project: Project, action: ActionInfo) {
            val element = action.declaration?.element ?: return
            val file = element.containingFile?.virtualFile ?: return
            OpenFileDescriptor(project, file, element.textOffset).navigate(true)
        }

        private fun countLabel(count: Int, singular: String, plural: String): String =
            if (count == 1) "1 $singular" else "$count $plural"
    }
}

sealed interface ReduxFlowSection {
    data class Action(val action: ActionInfo) : ReduxFlowSection
    data class Usages(val kind: ReduxUsageKind, val usages: List<ReduxUsage>) : ReduxFlowSection
}
