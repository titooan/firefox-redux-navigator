package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.locationLabel
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class ReduxFlowNodePanel(
    private val project: Project,
    private val section: ReduxFlowSection
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
        val declarationLocation = action.declaration?.element?.locationLabel()
        val text = declarationLocation?.let { "$it    ${action.displayName}" } ?: action.displayName
        add(
            ReduxFlowPanel.createNavigationLink(text) {
                ReduxFlowPanel.navigateToAction(project, action)
            }.apply { alignmentX = Component.LEFT_ALIGNMENT }
        )
    }

    private fun buildUsageSection(kind: ReduxUsageKind, usages: List<ReduxUsage>) {
        add(sectionHeader(headerText(kind, usages.size)))
        add(Box.createVerticalStrut(4))

        if (usages.isEmpty()) {
            add(JBLabel(emptyText(kind)).apply { alignmentX = Component.LEFT_ALIGNMENT })
            return
        }

        usages.forEach { usage ->
            add(
                ReduxFlowPanel.createNavigationLink(linkText(usage)) {
                    ReduxFlowPanel.navigateToUsage(project, usage)
                }.apply { alignmentX = Component.LEFT_ALIGNMENT }
            )
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

    internal fun linkText(usage: ReduxUsage): String = "${usage.fileName}:${usage.line}    ${usage.displayText}"
}
