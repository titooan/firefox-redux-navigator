package org.mozilla.reduxnav.state

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.lineText
import org.mozilla.reduxnav.model.safeLineNumber
import org.mozilla.reduxnav.popup.ReduxActionPopup
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class StateExplorerPanel(
    private val project: Project,
    private val onActionSelected: (ActionInfo) -> Unit,
    private val onActionNavigate: (ActionInfo) -> Unit,
    private val onModificationSelected: (StateModification) -> Unit,
    private val onModificationNavigate: (StateModification) -> Unit,
    private val onActivated: (() -> Unit)? = null
) : JPanel() {
    private val rowBackground = JBColor(
        java.awt.Color(0x000000, true),
        java.awt.Color(0x000000, true)
    )
    private val rowHoverBackground = JBColor(
        java.awt.Color(0x12000000, true),
        java.awt.Color(0x22FFFFFF, true)
    )
    private val rowSelectedBackground = JBColor(
        java.awt.Color(0x1F6FEB33, true),
        java.awt.Color(0x2F6CB6FF, true)
    )
    private var currentHeaderText: String = "State Explorer"
    private var currentMessage: String? = null
    private var currentActions: List<String> = emptyList()
    private var currentModificationSnippets: List<String> = emptyList()
    private var selectedRow: JComponent? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
        isOpaque = false
        showEmptyState()
    }

    fun showEmptyState() {
        currentHeaderText = "State Explorer"
        currentMessage = "Select a Redux state field and choose \"Show Redux State Changes\"."
        currentActions = emptyList()
        currentModificationSnippets = emptyList()
        selectedRow = null
        renderMessage(currentMessage!!)
    }

    fun showInvalidState(field: StateFieldInfo) {
        currentHeaderText = "State Explorer: ${field.qualifiedPath}"
        currentMessage = "The selected state field is no longer valid. Re-run Show Redux State Changes."
        currentActions = emptyList()
        currentModificationSnippets = emptyList()
        selectedRow = null
        renderMessage(currentMessage!!)
    }

    fun showAnalysisError(field: StateFieldInfo) {
        currentHeaderText = "State Explorer: ${field.qualifiedPath}"
        currentMessage = "Could not build state modification results for this field."
        currentActions = emptyList()
        currentModificationSnippets = emptyList()
        selectedRow = null
        renderMessage(currentMessage!!)
    }

    fun showLoadingState(field: StateFieldInfo) {
        currentHeaderText = "State Explorer: ${field.qualifiedPath}"
        currentMessage = "Analyzing Redux state changes..."
        currentActions = emptyList()
        currentModificationSnippets = emptyList()
        selectedRow = null
        renderMessage(currentMessage!!)
    }

    fun showStateGraph(graph: StateFieldGraph) {
        currentHeaderText = "State Explorer: ${graph.stateField.qualifiedPath}"
        currentMessage = null
        val distinctActions = graph.modifications.mapNotNull { it.action }.distinctBy { it.id.value }
        currentActions = distinctActions.map { it.displayName }
        currentModificationSnippets = graph.modifications.map { formatModificationLabel(it) }
        selectedRow = null

        removeAll()
        add(fixedHeight(sectionHeader(currentHeaderText)))
        add(Box.createVerticalStrut(8))
        add(
            fixedHeight(
                JBLabel(
                    "${countLabel(distinctActions.size, "action", "actions")} | " +
                        "${countLabel(graph.modifications.size, "reducer change", "reducer changes")}"
                ).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                }
            )
        )
        add(Box.createVerticalStrut(12))
        add(fixedHeight(sectionHeader("Modified by Actions (${distinctActions.size})")))
        add(Box.createVerticalStrut(4))
        if (distinctActions.isEmpty()) {
            add(fixedHeight(JBLabel("No action associations found.").apply { alignmentX = Component.LEFT_ALIGNMENT }))
        } else {
            distinctActions.forEach { action -> add(fixedHeight(createActionRow(action))) }
        }
        add(Box.createVerticalStrut(12))
        add(fixedHeight(sectionHeader("Reducer modifications (${graph.modifications.size})")))
        add(Box.createVerticalStrut(4))
        if (graph.modifications.isEmpty()) {
            add(fixedHeight(JBLabel("No reducer modifications found.").apply { alignmentX = Component.LEFT_ALIGNMENT }))
        } else {
            graph.modifications.forEach { modification -> add(fixedHeight(createModificationRow(graph.stateField, modification))) }
        }
        add(Box.createVerticalGlue())
        revalidate()
        repaint()
    }

    internal fun headerTextForTest(): String = currentHeaderText

    internal fun messageForTest(): String? = currentMessage

    internal fun actionLabelsForTest(): List<String> = currentActions

    internal fun modificationLabelsForTest(): List<String> = currentModificationSnippets

    private fun renderMessage(message: String) {
        removeAll()
        add(fixedHeight(sectionHeader(currentHeaderText)))
        add(Box.createVerticalStrut(8))
        add(fixedHeight(JBLabel(message).apply { alignmentX = Component.LEFT_ALIGNMENT }))
        add(Box.createVerticalGlue())
        revalidate()
        repaint()
    }

    private fun createActionRow(action: ActionInfo): JComponent {
        val rowData = ReadAction.compute<ActionRowData, RuntimeException> {
            val declaration = action.declaration?.element
            val anchor = (declaration as? KtClassOrObject)?.nameIdentifier ?: declaration
            ActionRowData(
                icon = FileTypeManager.getInstance().getFileTypeByFileName(
                    declaration?.containingFile?.virtualFile?.name ?: "Action.kt"
                ).icon,
                fileName = declaration?.containingFile?.virtualFile?.name ?: "<unknown>",
                lineNumber = anchor?.safeLineNumber()?.toString() ?: "-",
                codeHtml = noWrapHtml(
                    ReduxActionPopup.highlightedCodeHtml(
                        anchor?.lineText() ?: action.displayName,
                        action.displayName
                    )
                )
            )
        }
        return createInteractiveRow(
            icon = rowData.icon,
            fileName = rowData.fileName,
            lineNumber = rowData.lineNumber,
            codeHtml = rowData.codeHtml,
            onSingleClick = { onActionSelected(action) },
            onDoubleClick = { onActionNavigate(action) }
        )
    }

    private fun createModificationRow(field: StateFieldInfo, modification: StateModification): JComponent {
        val actionLabel = modification.action?.displayName ?: "Unknown action"
        val html = noWrapHtml(
            "<html><b>${StringUtil.escapeXmlEntities(actionLabel)}</b>: " +
                ReduxActionPopup.highlightedCodeHtml(modification.snippet, field.fieldName)
                    .removePrefix("<html>")
                    .removeSuffix("</html>") +
                "</html>"
        )
        return createInteractiveRow(
            icon = FileTypeManager.getInstance().getFileTypeByFileName(modification.fileName).icon,
            fileName = modification.fileName,
            lineNumber = modification.lineNumber.toString(),
            codeHtml = html,
            onSingleClick = { onModificationSelected(modification) },
            onDoubleClick = { onModificationNavigate(modification) }
        )
    }

    private fun noWrapHtml(html: String): String =
        if (!html.startsWith("<html>")) {
            "<html><nobr>${StringUtil.escapeXmlEntities(html)}</nobr></html>"
        } else {
            "<html><nobr>${html.removePrefix("<html>").removeSuffix("</html>")}</nobr></html>"
        }

    private fun createInteractiveRow(
        icon: Icon?,
        fileName: String,
        lineNumber: String,
        codeHtml: String,
        onSingleClick: () -> Unit,
        onDoubleClick: () -> Unit
    ): JComponent =
        JPanel(GridBagLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(1, 8)
            isOpaque = true
            background = rowBackground

            add(JLabel(icon).apply { border = JBUI.Borders.emptyRight(6) }, rowConstraints(0, 0.0))
            add(
                JLabel(fileName).apply {
                    foreground = com.intellij.ui.JBColor.GRAY
                    border = JBUI.Borders.emptyRight(10)
                },
                rowConstraints(1, 0.0)
            )
            add(
                JLabel(lineNumber).apply {
                    foreground = com.intellij.ui.JBColor.GRAY
                    horizontalAlignment = SwingConstants.RIGHT
                    border = JBUI.Borders.emptyRight(8)
                },
                rowConstraints(2, 0.0)
            )
            add(JLabel(codeHtml), rowConstraints(3, 1.0))

            val listener = object : MouseAdapter() {
                override fun mouseEntered(event: MouseEvent) {
                    if (selectedRow !== this@apply) {
                        background = rowHoverBackground
                    }
                }

                override fun mouseExited(event: MouseEvent) {
                    if (selectedRow !== this@apply) {
                        background = rowBackground
                    }
                }

                override fun mouseClicked(event: MouseEvent) {
                    if (event.button != MouseEvent.BUTTON1) return
                    requestFocusInWindow()
                    onActivated?.invoke()
                    setSelectedRow(this@apply)
                    onSingleClick()
                    if (event.clickCount >= 2) {
                        onDoubleClick()
                    }
                }
            }
            installInteractionRecursively(this, listener)
        }

    private fun installInteractionRecursively(component: Component, listener: MouseAdapter) {
        component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        component.addMouseListener(listener)
        if (component is Container) {
            component.components.forEach { child -> installInteractionRecursively(child, listener) }
        }
    }

    private fun sectionHeader(text: String): JBLabel =
        JBLabel(text).apply {
            font = JBFont.label().asBold()
            alignmentX = Component.LEFT_ALIGNMENT
        }

    private fun <T : JComponent> fixedHeight(component: T): T =
        component.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

    private fun countLabel(count: Int, singular: String, plural: String): String =
        if (count == 1) "1 $singular" else "$count $plural"

    private fun formatModificationLabel(modification: StateModification): String =
        "${modification.action?.displayName ?: "Unknown action"}: ${modification.snippet}"

    private fun setSelectedRow(row: JComponent) {
        if (selectedRow === row) return
        selectedRow?.background = rowBackground
        selectedRow = row
        selectedRow?.background = rowSelectedBackground
    }

    private data class ActionRowData(
        val icon: Icon?,
        val fileName: String,
        val lineNumber: String,
        val codeHtml: String
    )

    companion object {
        private fun rowConstraints(gridx: Int, weightx: Double): GridBagConstraints =
            GridBagConstraints().apply {
                this.gridx = gridx
                this.weightx = weightx
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            }
    }
}
