package org.mozilla.reduxnav.popup

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.analysis.ReduxUsageFinder
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import java.awt.Color
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

private val TEST_OCCURRENCE_BACKGROUND = JBColor(
    Color(0x22338833, true),
    Color(0x33306B30, true)
)

private val METADATA_FOREGROUND = JBColor(
    Color(0x777777),
    Color(0xA0A0A0)
)

object ReduxActionPopup {
    private const val MAX_VISIBLE_ROWS = 12
    private val logger = Logger.getInstance(ReduxActionPopup::class.java)
    private var activePopup: PopupHandle? = null

    fun show(project: Project, action: ActionInfo, clickEvent: MouseEvent? = null) {
        logger.info(
            "[redux-nav] popup-show-start action=${action.displayName} project=${project.name} " +
                "point=${clickEvent?.point} component=${clickEvent?.component?.javaClass?.name} active=${activePopup?.debugName()}"
        )
        val graph = ReduxUsageFinder(project).buildGraph(action)
        val entries = buildEntries(graph.usages, action.displayName)
        val list = createList(entries)
        val scrollPane = createContent(list)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, list)
            .setTitle("Redux flow: ${action.displayName}")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(false)
            .setCancelOnClickOutside(false)
            .createPopup()
        val popupHandle = JBPopupHandle(popup)
        registerActivePopup(popupHandle)
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                logger.info(
                    "[redux-nav] popup-closed action=${action.displayName} ok=${event.isOk} active=${activePopup?.debugName()} closed=${popupHandle.debugName()}"
                )
                clearActivePopup(popupHandle)
            }
        })

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
            logger.info("[redux-nav] popup-show-relative action=${action.displayName} point=$popupPoint")
            popup.show(RelativePoint(clickEvent!!.component, popupPoint))
        } else {
            logger.info("[redux-nav] popup-show-centered action=${action.displayName}")
            popup.showCenteredInCurrentWindow(project)
        }
        logger.info("[redux-nav] popup-show-done action=${action.displayName} active=${activePopup?.debugName()}")
    }

    internal fun popupPoint(clickEvent: MouseEvent?): Point? =
        clickEvent?.point

    internal fun registerActivePopup(popup: PopupHandle) {
        if (activePopup === popup) return
        logger.info("[redux-nav] popup-register new=${popup.debugName()} old=${activePopup?.debugName()}")
        activePopup?.cancel()
        activePopup = popup
        logger.info("[redux-nav] popup-register-done active=${activePopup?.debugName()}")
    }

    internal fun resetActivePopupForTests() {
        activePopup = null
    }

    private fun clearActivePopup(popup: PopupHandle) {
        if (activePopup === popup) {
            logger.info("[redux-nav] popup-clear active=${popup.debugName()}")
            activePopup = null
        } else {
            logger.info("[redux-nav] popup-clear-skip active=${activePopup?.debugName()} closed=${popup.debugName()}")
        }
    }

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

    internal fun usagePresentation(
        usage: ReduxUsage,
        actionName: String,
        selected: Boolean
    ): UsagePresentation {
        val background = if (selected) null else testOccurrenceBackground(usage.filePath)
        val icon = FileTypeManager.getInstance().getFileTypeByFileName(usage.fileName).icon
        return UsagePresentation(
            icon = icon,
            fileName = usage.fileName,
            lineNumber = usage.line.toString(),
            codeHtml = highlightedCodeHtml(usage.displayText, actionName),
            background = background
        )
    }

    internal fun highlightedCodeHtml(text: String, highlight: String): String {
        if (highlight.isBlank()) {
            return "<html>${escapeHtml(text)}</html>"
        }

        val index = text.indexOf(highlight)
        if (index < 0) {
            return "<html>${escapeHtml(text)}</html>"
        }

        val prefix = escapeHtml(text.substring(0, index))
        val match = escapeHtml(text.substring(index, index + highlight.length))
        val suffix = escapeHtml(text.substring(index + highlight.length))
        return "<html>$prefix<b>$match</b>$suffix</html>"
    }

    internal fun buildEntries(
        usages: List<ReduxUsage>,
        actionName: String = ""
    ): List<PopupEntry> {
        val result = mutableListOf<PopupEntry>()
        val grouped = usages.groupBy { it.kind }
        listOf(ReduxUsageKind.DISPATCH, ReduxUsageKind.MIDDLEWARE, ReduxUsageKind.REDUCER, ReduxUsageKind.OTHER).forEach { kind ->
            val group = grouped[kind].orEmpty()
            result += PopupEntry.Header("${kind.title} (${group.size})")
            result += group.map { PopupEntry.UsageEntry(it, actionName) }
        }
        return result
    }
}

internal fun interface PopupHandle {
    fun cancel()
}

private fun PopupHandle.debugName(): String = when (this) {
    is JBPopupHandle -> debugName()
    else -> javaClass.simpleName
}

private class JBPopupHandle(private val popup: JBPopup) : PopupHandle {
    override fun cancel() {
        Logger.getInstance(ReduxActionPopup::class.java)
            .info("[redux-nav] popup-cancel handle=${debugName()} visible=${popup.isVisible}")
        popup.cancel()
    }

    fun debugName(): String = "JBPopupHandle@" + Integer.toHexString(System.identityHashCode(this))
}

internal sealed interface PopupEntry {
    data class Header(val text: String) : PopupEntry
    data class UsageEntry(val usage: ReduxUsage, val actionName: String) : PopupEntry
}

internal data class UsagePresentation(
    val icon: Icon?,
    val fileName: String,
    val lineNumber: String,
    val codeHtml: String,
    val background: Color?
)

private class EntryRenderer : javax.swing.ListCellRenderer<PopupEntry> {
    override fun getListCellRendererComponent(
        list: JList<out PopupEntry>,
        value: PopupEntry?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ): Component {
        return when (value) {
            is PopupEntry.Header -> createHeaderRow(list, value.text, selected)
            is PopupEntry.UsageEntry -> createUsageRow(list, value, selected)
            null -> createHeaderRow(list, "", selected)
        }
    }

    private fun createHeaderRow(
        list: JList<out PopupEntry>,
        text: String,
        selected: Boolean
    ): Component {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(4, 8)
        panel.isOpaque = true
        panel.background = if (selected) list.selectionBackground else list.background

        val label = JLabel(text)
        label.font = label.font.deriveFont(label.font.style or java.awt.Font.BOLD)
        label.foreground = if (selected) list.selectionForeground else list.foreground
        label.background = panel.background
        label.isOpaque = false

        val constraints = GridBagConstraints().apply {
            gridx = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }
        panel.add(label, constraints)
        return panel
    }

    private fun createUsageRow(
        list: JList<out PopupEntry>,
        entry: PopupEntry.UsageEntry,
        selected: Boolean
    ): Component {
        val presentation = ReduxActionPopup.usagePresentation(entry.usage, entry.actionName, selected)
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(3, 8)
        panel.isOpaque = true
        panel.background = if (selected) list.selectionBackground else presentation.background ?: list.background

        val rowForeground = if (selected) list.selectionForeground else list.foreground

        val iconLabel = JLabel(presentation.icon).apply {
            foreground = rowForeground
            background = panel.background
            isOpaque = false
            border = JBUI.Borders.emptyRight(6)
        }

        val fileLabel = JLabel(presentation.fileName).apply {
            foreground = if (selected) rowForeground else METADATA_FOREGROUND
            background = panel.background
            isOpaque = false
            border = JBUI.Borders.emptyRight(10)
        }

        val lineLabel = JLabel(presentation.lineNumber).apply {
            foreground = if (selected) rowForeground else METADATA_FOREGROUND
            background = panel.background
            isOpaque = false
            horizontalAlignment = SwingConstants.RIGHT
            border = JBUI.Borders.emptyRight(8)
        }

        val codeLabel = JLabel(presentation.codeHtml).apply {
            foreground = rowForeground
            background = panel.background
            isOpaque = false
        }

        panel.add(iconLabel, constraints(0, 0.0, GridBagConstraints.WEST))
        panel.add(fileLabel, constraints(1, 0.0, GridBagConstraints.WEST))
        panel.add(lineLabel, constraints(2, 0.0, GridBagConstraints.WEST))
        panel.add(codeLabel, constraints(3, 1.0, GridBagConstraints.WEST))
        return panel
    }

    private fun constraints(gridx: Int, weightx: Double, anchor: Int): GridBagConstraints =
        GridBagConstraints().apply {
            this.gridx = gridx
            this.weightx = weightx
            this.fill = GridBagConstraints.HORIZONTAL
            this.anchor = anchor
        }
}

private fun escapeHtml(text: String): String =
    buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
