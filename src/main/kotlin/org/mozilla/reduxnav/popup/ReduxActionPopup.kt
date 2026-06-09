package org.mozilla.reduxnav.popup

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import org.mozilla.reduxnav.analysis.ReduxUsageFinder
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList

object ReduxActionPopup {
    fun show(project: Project, anchor: PsiElement, action: ActionInfo) {
        val graph = ReduxUsageFinder(project).buildGraph(action)
        val entries = buildEntries(graph.usages)
        val list = JBList(entries)
        list.cellRenderer = EntryRenderer()

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(list, list)
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

        val editor = PsiEditorUtil.findEditor(anchor) ?: FileEditorManager.getInstance(project).selectedTextEditor
        if (editor != null) {
            popup.showInBestPositionFor(editor)
        } else {
            popup.showCenteredInCurrentWindow(project)
        }
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
            }
            null -> Unit
        }
    }
}
