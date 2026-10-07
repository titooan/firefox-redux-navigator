package org.mozilla.reduxnav.popup

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.analysis.ReduxUsageFinder
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.toolwindow.ReduxFlowToolWindowService
import org.mozilla.reduxnav.toolwindow.navigateToPsiElement
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
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
        val list = createList(loadingEntries())
        val controller = PopupListController(list)
        lateinit var popupHandle: PopupHandle
        val content = createPopupContent(project, action, controller) {
            ReduxFlowToolWindowService.getInstance(project).showFlow(action)
            popupHandle.cancel()
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, list)
            .setTitle("Redux flow: ${action.displayName}")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(false)
            .setCancelOnClickOutside(false)
            .createPopup()
        popupHandle = JBPopupHandle(popup)
        registerActivePopup(popupHandle)
        popupHandle.installOutsideClickCancellation()
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                logger.info(
                    "[redux-nav] popup-closed action=${action.displayName} ok=${event.isOk} active=${activePopup?.debugName()} closed=${popupHandle.debugName()}"
                )
                popupHandle.removeOutsideClickCancellation()
                popupHandle.cancelLoading()
                clearActivePopup(popupHandle)
            }
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = list.locationToIndex(e.point)
                if (index < 0) return

                when (val entry = list.model.getElementAt(index)) {
                    is PopupEntry.Header -> {
                        if (e.clickCount == 1) {
                            controller.toggle(entry.kind)
                            popup.setSize(popup.content.preferredSize)
                        }
                    }
                    is PopupEntry.UsageEntry -> {
                        if (e.clickCount < 2) return
                        logger.info(
                            "[redux-nav] popup-navigate-start action=${action.displayName} " +
                                "target=${entry.usage.filePath}:${entry.usage.line}"
                        )
                        navigateToPsiElement(
                            project,
                            "${entry.usage.filePath}:${entry.usage.line}",
                            { entry.usage.element.element },
                            afterNavigate = { if (popup.isVisible) popup.cancel() }
                        )
                    }
                }
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

        popupHandle.loadEntries(project, action, controller, popup)
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
        val list = JBList(createModel(entries))
        list.visibleRowCount = visibleRowCount(entries)
        list.cellRenderer = EntryRenderer()
        return list
    }

    internal fun replaceEntries(list: JBList<PopupEntry>, entries: List<PopupEntry>) {
        list.model = createModel(entries)
        list.visibleRowCount = visibleRowCount(entries)
        list.revalidate()
        list.repaint()
    }

    internal fun createContent(list: JBList<PopupEntry>) = JBScrollPane(list)

    internal fun createPopupContent(
        project: Project,
        action: ActionInfo,
        controller: PopupListController,
        onOpenFlow: () -> Unit = { ReduxFlowToolWindowService.getInstance(project).showFlow(action) }
    ): JComponent {
        val toolbar = createToolbar(project, action, controller, onOpenFlow)
        val toolbarHeight = toolbar.preferredSize.height
        toolbar.maximumSize = Dimension(Int.MAX_VALUE, toolbarHeight)
        toolbar.minimumSize = Dimension(0, toolbarHeight)
        toolbar.preferredSize = Dimension(toolbar.preferredSize.width, toolbarHeight)

        return JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(createContent(controller.list), BorderLayout.CENTER)
        }
    }

    internal fun loadingEntries(): List<PopupEntry> = listOf(
        PopupEntry.Header(ReduxUsageKind.OTHER, "Loading Redux flow...", expanded = true)
    )

    internal fun testOccurrenceBackground(filePath: String): Color? =
        if (isTestPath(filePath)) TEST_OCCURRENCE_BACKGROUND else null

    internal fun usagePresentation(
        usage: ReduxUsage,
        actionName: String,
        selected: Boolean
    ): UsagePresentation {
        val background = if (selected) null else testOccurrenceBackground(usage.filePath)
        val icon = FileTypeManager.getInstance().getFileTypeByFileName(usage.fileName).icon
        val handledActions = usage.handledActions.joinToString(", ") { match ->
            if (match.coversDescendants) "${match.actionName} (all child actions)" else match.actionName
        }
        val codeHtml = highlightedCodeHtml(usage.displayText, actionName)
        val attributedCodeHtml = if (handledActions.isBlank()) {
            codeHtml
        } else {
            val relationship = if (usage.kind == ReduxUsageKind.OTHER) "references" else "handles"
            "${codeHtml.removeSuffix("</html>")} <span style='color:#808080'>$relationship ${escapeHtml(handledActions)}</span></html>"
        }
        return UsagePresentation(
            icon = icon,
            fileName = usage.fileName,
            lineNumber = usage.line.toString(),
            codeHtml = attributedCodeHtml,
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
        actionName: String = "",
        filterState: PopupFilterState = PopupFilterState()
    ): List<PopupEntry> {
        val result = mutableListOf<PopupEntry>()
        val filteredUsages = usages.filter { filterState.fileScope.accepts(it.filePath) }
        val grouped = filteredUsages.groupBy { it.kind }

        orderedUsageKinds().forEach { kind ->
            if (kind !in filterState.visibleKinds) return@forEach

            val group = grouped[kind].orEmpty()
            val expanded = kind !in filterState.collapsedKinds
            result += PopupEntry.Header(kind, "${kind.title} (${group.size})", expanded)
            if (expanded) {
                result += group.map { PopupEntry.UsageEntry(it, actionName) }
            }
        }

        return result
    }

    internal fun toggleSection(
        filterState: PopupFilterState,
        kind: ReduxUsageKind
    ): PopupFilterState {
        val collapsedKinds = filterState.collapsedKinds.toMutableSet()
        if (!collapsedKinds.add(kind)) {
            collapsedKinds.remove(kind)
        }
        return filterState.copy(collapsedKinds = collapsedKinds)
    }

    internal fun shouldCancelForOutsideClick(
        popupContent: Component,
        mouseEvent: MouseEvent
    ): Boolean {
        val source = mouseEvent.component ?: return false
        if (SwingUtilities.isDescendingFrom(source, popupContent)) {
            return false
        }

        val popupMenu = SwingUtilities.getAncestorOfClass(JPopupMenu::class.java, source) as? JPopupMenu
        val invoker = popupMenu?.invoker
        return invoker == null || !SwingUtilities.isDescendingFrom(invoker, popupContent)
    }

    internal fun createToolbar(
        project: Project,
        action: ActionInfo,
        controller: PopupListController,
        onOpenFlow: () -> Unit = { ReduxFlowToolWindowService.getInstance(project).showFlow(action) }
    ): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(6, 8, 4, 8)
        }

        val openFlowButton = JButton("Open Redux Flow").apply {
            addActionListener { onOpenFlow() }
        }
        panel.add(openFlowButton, constraints(0, 0.0, GridBagConstraints.WEST))

        val scopeSelector = JComboBox(UsageFileScope.entries.toTypedArray()).apply {
            selectedItem = controller.filterState.fileScope
            addActionListener {
                val scope = selectedItem as? UsageFileScope ?: return@addActionListener
                controller.updateScope(scope)
            }
        }
        panel.add(scopeSelector, constraints(1, 0.0, GridBagConstraints.WEST))

        orderedUsageKinds().forEachIndexed { index, kind ->
            val checkbox = JCheckBox(kind.title, kind in controller.filterState.visibleKinds).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(8)
                addActionListener { controller.setKindVisible(kind, isSelected) }
            }
            panel.add(checkbox, constraints(index + 2, 0.0, GridBagConstraints.WEST))
        }

        panel.add(JPanel().apply { isOpaque = false }, constraints(orderedUsageKinds().size + 2, 1.0, GridBagConstraints.WEST))
        return panel
    }

    private fun createModel(entries: List<PopupEntry>): DefaultListModel<PopupEntry> =
        DefaultListModel<PopupEntry>().also { model ->
            entries.forEach(model::addElement)
        }

    private fun visibleRowCount(entries: List<PopupEntry>): Int =
        minOf(entries.size, MAX_VISIBLE_ROWS)

    private fun orderedUsageKinds(): List<ReduxUsageKind> = listOf(
        ReduxUsageKind.DISPATCH,
        ReduxUsageKind.MIDDLEWARE,
        ReduxUsageKind.REDUCER,
        ReduxUsageKind.OTHER
    )
}

internal enum class UsageFileScope(private val label: String) {
    ALL("All files") {
        override fun accepts(filePath: String): Boolean = true
    },
    PRODUCTION("Production files") {
        override fun accepts(filePath: String): Boolean = !isTestPath(filePath)
    },
    TEST("Test files") {
        override fun accepts(filePath: String): Boolean = isTestPath(filePath)
    };

    abstract fun accepts(filePath: String): Boolean

    override fun toString(): String = label
}

internal data class PopupFilterState(
    val fileScope: UsageFileScope = UsageFileScope.ALL,
    val visibleKinds: Set<ReduxUsageKind> = linkedSetOf(
        ReduxUsageKind.DISPATCH,
        ReduxUsageKind.MIDDLEWARE,
        ReduxUsageKind.REDUCER,
        ReduxUsageKind.OTHER
    ),
    val collapsedKinds: Set<ReduxUsageKind> = emptySet()
)

internal class PopupListController(
    val list: JBList<PopupEntry>,
    private var actionName: String = "",
    private var allUsages: List<ReduxUsage> = emptyList(),
    internal var filterState: PopupFilterState = PopupFilterState()
) {
    fun replaceUsages(usages: List<ReduxUsage>, actionName: String = this.actionName) {
        this.allUsages = usages
        this.actionName = actionName
        refresh()
    }

    fun updateScope(scope: UsageFileScope) {
        filterState = filterState.copy(fileScope = scope)
        refresh()
    }

    fun setKindVisible(kind: ReduxUsageKind, visible: Boolean) {
        val visibleKinds = LinkedHashSet(filterState.visibleKinds)
        if (visible) {
            visibleKinds.add(kind)
        } else {
            visibleKinds.remove(kind)
        }
        filterState = filterState.copy(visibleKinds = visibleKinds)
        refresh()
    }

    fun toggle(kind: ReduxUsageKind) {
        filterState = ReduxActionPopup.toggleSection(filterState, kind)
        refresh()
    }

    private fun refresh() {
        ReduxActionPopup.replaceEntries(
            list,
            ReduxActionPopup.buildEntries(allUsages, actionName, filterState)
        )
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
    private var loadingRequest: com.intellij.openapi.progress.util.ProgressIndicatorBase? = null
    private var outsideClickListener: AWTEventListener? = null

    override fun cancel() {
        Logger.getInstance(ReduxActionPopup::class.java)
            .info("[redux-nav] popup-cancel handle=${debugName()} visible=${popup.isVisible}")
        popup.cancel()
    }

    fun debugName(): String = "JBPopupHandle@" + Integer.toHexString(System.identityHashCode(this))

    fun cancelLoading() {
        loadingRequest?.cancel()
        loadingRequest = null
    }

    fun installOutsideClickCancellation() {
        removeOutsideClickCancellation()
        val popupContent = popup.content
        val listener = AWTEventListener { event ->
            val mouseEvent = event as? MouseEvent ?: return@AWTEventListener
            if (mouseEvent.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
            if (!popup.isVisible) return@AWTEventListener
            if (!ReduxActionPopup.shouldCancelForOutsideClick(popupContent, mouseEvent)) return@AWTEventListener

            Logger.getInstance(ReduxActionPopup::class.java).info(
                "[redux-nav] popup-cancel-outside-click handle=${debugName()} component=${mouseEvent.component?.javaClass?.name} point=${mouseEvent.point}"
            )
            popup.cancel()
        }
        outsideClickListener = listener
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK)
    }

    fun removeOutsideClickCancellation() {
        outsideClickListener?.let { Toolkit.getDefaultToolkit().removeAWTEventListener(it) }
        outsideClickListener = null
    }

    fun loadEntries(
        project: Project,
        action: ActionInfo,
        controller: PopupListController,
        popup: JBPopup
    ) {
        val indicator = com.intellij.openapi.progress.util.ProgressIndicatorBase()
        loadingRequest = indicator
        ReadAction
            .nonBlocking<ActionGraph> { ReduxUsageFinder(project).computeGraph(action) }
            .expireWith(project)
            .wrapProgress(indicator)
            .finishOnUiThread(ModalityState.any()) { graph ->
                if (!popup.isVisible) return@finishOnUiThread
                controller.replaceUsages(graph.usages, action.displayName)
                popup.setSize(popup.content.preferredSize)
                loadingRequest = null
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}

internal sealed interface PopupEntry {
    data class Header(
        val kind: ReduxUsageKind,
        val text: String,
        val expanded: Boolean
    ) : PopupEntry

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
            is PopupEntry.Header -> createHeaderRow(list, value.text, value.expanded, selected)
            is PopupEntry.UsageEntry -> createUsageRow(list, value, selected)
            null -> createHeaderRow(list, "", true, selected)
        }
    }

    private fun createHeaderRow(
        list: JList<out PopupEntry>,
        text: String,
        expanded: Boolean,
        selected: Boolean
    ): Component {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(4, 8)
        panel.isOpaque = true
        panel.background = if (selected) list.selectionBackground else list.background

        val label = JLabel("${if (expanded) "\u25be" else "\u25b8"} $text")
        label.font = label.font.deriveFont(label.font.style or java.awt.Font.BOLD)
        label.foreground = if (selected) list.selectionForeground else list.foreground
        label.background = panel.background
        label.isOpaque = false

        panel.add(label, constraints(0, 1.0, GridBagConstraints.WEST))
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
}

private fun constraints(gridx: Int, weightx: Double, anchor: Int): GridBagConstraints =
    GridBagConstraints().apply {
        this.gridx = gridx
        this.weightx = weightx
        this.fill = GridBagConstraints.HORIZONTAL
        this.anchor = anchor
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

internal fun isTestPath(filePath: String): Boolean {
    val normalized = filePath.replace('\\', '/')
    if ("/src/test/" in normalized || "/src/androidTest/" in normalized) {
        return true
    }
    return normalized.contains("/test/") ||
        normalized.endsWith("Test.kt") ||
        normalized.endsWith("Tests.kt")
}
