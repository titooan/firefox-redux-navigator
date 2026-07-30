package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.lineText
import org.mozilla.reduxnav.model.safeLineNumber
import org.mozilla.reduxnav.nativeflow.DiagramNodeTarget
import org.mozilla.reduxnav.popup.ReduxActionPopup
import org.mozilla.reduxnav.popup.isTestPath
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
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

private val FLOW_METADATA_FOREGROUND = JBColor(
    java.awt.Color(0x777777),
    java.awt.Color(0xA0A0A0)
)

private val FLOW_TEST_BACKGROUND = JBColor(
    java.awt.Color(0x22338833, true),
    java.awt.Color(0x33306B30, true)
)

class ReduxFlowNodePanel(
    private val project: Project,
    private val section: ReduxFlowSection,
    private val onTargetSelected: ((DiagramNodeTarget) -> Unit)? = null,
    private val onTargetNavigate: ((DiagramNodeTarget) -> Unit)? = null
) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(0, 0, 8, 0)
        isOpaque = false

        when (section) {
            is ReduxFlowSection.Action -> buildActionSection(section.action)
            is ReduxFlowSection.Usages -> buildUsageSection(section.kind, section.usages)
        }
    }

    private fun buildActionSection(action: ActionInfo) {
        add(sectionHeader("Action"))
        add(Box.createVerticalStrut(4))
        add(createActionRow(action))
    }

    private fun buildUsageSection(kind: ReduxUsageKind, usages: List<ReduxUsage>) {
        add(sectionHeader(headerText(kind, usages.size)))
        add(Box.createVerticalStrut(4))

        if (usages.isEmpty()) {
            add(JBLabel(emptyText(kind)).apply { alignmentX = Component.LEFT_ALIGNMENT })
            return
        }

        usages.forEach { usage ->
            add(createUsageRow(usage))
        }
    }

    private fun createActionRow(action: ActionInfo): JComponent {
        val rowData = ReadAction.nonBlocking<ActionRowData> {
            val declaration = action.declaration?.element
            ActionRowData(
                fileName = declaration?.containingFile?.virtualFile?.name ?: "<unknown>",
                lineNumber = declaration?.safeLineNumber()?.toString() ?: "-",
                codeHtml = ReduxActionPopup.highlightedCodeHtml(
                    declaration?.lineText() ?: action.displayName,
                    action.displayName
                )
            )
        }.executeSynchronously()
        val icon = FileTypeManager.getInstance().getFileTypeByFileName(rowData.fileName).icon
        val row = createRowComponent(
            icon = icon,
            fileName = rowData.fileName,
            lineNumber = rowData.lineNumber,
            codeHtml = rowData.codeHtml,
            rowBackground = null
        )
        installInteraction(
            row,
            DiagramNodeTarget.ActionTarget(action)
        )
        return row
    }

    private fun createUsageRow(usage: ReduxUsage): JComponent {
        val presentation = ReduxActionPopup.usagePresentation(usage, usage.displayText, selected = false)
        val row = createRowComponent(
            icon = presentation.icon,
            fileName = presentation.fileName,
            lineNumber = presentation.lineNumber,
            codeHtml = presentation.codeHtml,
            rowBackground = if (isTestPath(usage.filePath)) FLOW_TEST_BACKGROUND else presentation.background
        )
        installInteraction(
            row,
            DiagramNodeTarget.UsageTarget(usage)
        )
        return row
    }

    private fun createRowComponent(
        icon: Icon?,
        fileName: String,
        lineNumber: String,
        codeHtml: String,
        rowBackground: java.awt.Color?
    ): JComponent =
        JPanel(GridBagLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(1, 8)
            isOpaque = true
            val actualBackground = rowBackground ?: background
            background = actualBackground

            add(
                JLabel(icon).apply {
                    this.background = actualBackground
                    isOpaque = false
                    border = JBUI.Borders.emptyRight(6)
                },
                rowConstraints(0, 0.0)
            )
            add(
                JLabel(fileName).apply {
                    foreground = FLOW_METADATA_FOREGROUND
                    this.background = actualBackground
                    isOpaque = false
                    border = JBUI.Borders.emptyRight(10)
                },
                rowConstraints(1, 0.0)
            )
            add(
                JLabel(lineNumber).apply {
                    foreground = FLOW_METADATA_FOREGROUND
                    this.background = actualBackground
                    isOpaque = false
                    horizontalAlignment = SwingConstants.RIGHT
                    border = JBUI.Borders.emptyRight(8)
                },
                rowConstraints(2, 0.0)
            )
            add(
                JLabel(codeHtml).apply {
                    this.background = actualBackground
                    isOpaque = false
                },
                rowConstraints(3, 1.0)
            )
        }

    private fun installInteraction(component: JComponent, target: DiagramNodeTarget) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.button != MouseEvent.BUTTON1) return
                onTargetSelected?.invoke(target)
                if (event.clickCount >= 2) {
                    onTargetNavigate?.invoke(target)
                }
            }
        }
        installInteractionRecursively(component, listener)
    }

    private fun installInteractionRecursively(component: Component, listener: MouseAdapter) {
        component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        component.addMouseListener(listener)
        if (component is Container) {
            component.components.forEach { child ->
                installInteractionRecursively(child, listener)
            }
        }
    }

    private fun sectionHeader(text: String): JBLabel =
        JBLabel(text).apply {
            font = JBFont.label().asBold()
            alignmentX = Component.LEFT_ALIGNMENT
        }

    internal fun headerText(kind: ReduxUsageKind, count: Int): String = when (kind) {
        ReduxUsageKind.DISPATCH -> if (count == 1) "Dispatches (1)" else "Dispatches ($count)"
        ReduxUsageKind.MIDDLEWARE -> if (count == 1) "Middlewares (1)" else "Middlewares ($count)"
        ReduxUsageKind.REDUCER -> if (count == 1) "Reducers (1)" else "Reducers ($count)"
        ReduxUsageKind.OTHER -> if (count == 1) "Other references (1)" else "Other references ($count)"
    }

    internal fun emptyText(kind: ReduxUsageKind): String = when (kind) {
        ReduxUsageKind.DISPATCH -> "No dispatches found."
        ReduxUsageKind.MIDDLEWARE -> "No middleware handlers found."
        ReduxUsageKind.REDUCER -> "No reducers found."
        ReduxUsageKind.OTHER -> "No other references found."
    }

    companion object {
        private data class ActionRowData(
            val fileName: String,
            val lineNumber: String,
            val codeHtml: String
        )

        private fun rowConstraints(gridx: Int, weightx: Double): GridBagConstraints =
            GridBagConstraints().apply {
                this.gridx = gridx
                this.weightx = weightx
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            }
    }
}
