package org.mozilla.reduxnav.popup

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.awt.RelativePoint
import org.mozilla.reduxnav.analysis.ReduxUsageFinder
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.Point
import javax.swing.JList

private val TEST_OCCURRENCE_BACKGROUND = JBColor(
    Color(0x22338833, true),
    Color(0x33306B30, true)
)

object ReduxActionPopup {
    private const val MAX_VISIBLE_ROWS = 12

    fun show(project: Project, action: ActionInfo, clickEvent: MouseEvent? = null) {
        val graph = ReduxUsageFinder(project).buildGraph(action)
        val entries = buildEntries(graph.usages)
        val list = createList(entries)
        val scrollPane = createContent(list)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, list)
            .setTitle("Redux flow: ${action.displayName}")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2) return
                val entry = list.selectedValue as? PopupEntry.UsageEntry ?: return
                val target = entry.usage.element.element ?: return
                val file = target.containingFile?.virtualFile ?: return
                OpenFileDescriptor(project, file, target.textOffset).navigate(true)
                popup.cancel()
            }
        })

        val popupPoint = popupPoint(clickEvent)
        if (popupPoint != null) {
            popup.show(RelativePoint(clickEvent!!.component, popupPoint))
        } else {
            popup.showCenteredInCurrentWindow(project)
        }
    }

    internal fun popupPoint(clickEvent: MouseEvent?): Point? =
        clickEvent?.point

    internal fun createList(entries: List<PopupEntry>): JBList<PopupEntry> {
        val list = JBList(entries)
        list.visibleRowCount = minOf(entries.size, MAX_VISIBLE_ROWS)
        list.cellRenderer = EntryRenderer()
        return list
    }

    internal fun createContent(list: JBList<PopupEntry>) = JBScrollPane(list)

    internal fun testOccurrenceBackground(filePath: String): Color? {
        val normalized = filePath.replace('\\', '/')
        if ("/src/test/" in normalized || "/src/androidTest/" in normalized) {
            return TEST_OCCURRENCE_BACKGROUND
        }
        if (normalized.contains("/test/") || normalized.endsWith("Test.kt") || normalized.endsWith("Tests.kt")) {
            return TEST_OCCURRENCE_BACKGROUND
        }
        return null
    }

    internal fun buildEntries(usages: List<ReduxUsage>): List<PopupEntry> {
        val result = mutableListOf<PopupEntry>()
        val grouped = usages.groupBy { it.kind }
        listOf(ReduxUsageKind.DISPATCH, ReduxUsageKind.MIDDLEWARE, ReduxUsageKind.REDUCER, ReduxUsageKind.OTHER).forEach { kind ->
            val group = grouped[kind].orEmpty()
            result += PopupEntry.Header("${kind.title} (${group.size})")
            result += group.map { PopupEntry.UsageEntry(it) }
        }
        return result
    }
}

internal sealed interface PopupEntry {
    data class Header(val text: String) : PopupEntry
    data class UsageEntry(val usage: ReduxUsage) : PopupEntry
}

private class EntryRenderer : ColoredListCellRenderer<PopupEntry>() {
    override fun customizeCellRenderer(
        list: JList<out PopupEntry>,
        value: PopupEntry?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        when (value) {
            is PopupEntry.Header -> append(value.text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            is PopupEntry.UsageEntry -> {
                append("  ${value.usage.displayText}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (!selected) {
                    ReduxActionPopup.testOccurrenceBackground(value.usage.filePath)?.let { background = it }
                }
            }
            null -> Unit
        }
    }
}
