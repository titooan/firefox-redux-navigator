package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.mozilla.reduxnav.graph.ReduxGraphBuilder
import org.mozilla.reduxnav.mermaid.MermaidFlowRenderer
import org.mozilla.reduxnav.mermaid.MermaidFlowStyle
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.nativeflow.DiagramNodeTarget
import org.mozilla.reduxnav.nativeflow.NativeFlowPreviewPanel
import org.mozilla.reduxnav.popup.isTestPath
import org.mozilla.reduxnav.state.StateGraph
import org.mozilla.reduxnav.state.StateGraphAdapter
import org.mozilla.reduxnav.state.StateGraphBuilder
import org.mozilla.reduxnav.state.StateExplorerPanel
import org.mozilla.reduxnav.state.StateFieldGraph
import org.mozilla.reduxnav.state.StateFieldInfo
import org.mozilla.reduxnav.state.StateMermaidRenderer
import org.mozilla.reduxnav.state.StateModification
import org.mozilla.reduxnav.ui.withTransientFocusRing
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton

class ReduxFlowPanel(
    private val project: Project,
    private val onRefresh: () -> Unit
) : JPanel(BorderLayout()), Disposable {
    private val reduxGraphBuilder = ReduxGraphBuilder()
    private val stateGraphBuilder = StateGraphBuilder()
    private val stateGraphAdapter = StateGraphAdapter()
    private val mermaidRenderer = MermaidFlowRenderer(
        style = if (StartupUiUtil.isUnderDarcula) MermaidFlowStyle.dark() else MermaidFlowStyle.light()
    )
    private val stateMermaidRenderer = StateMermaidRenderer(
        style = if (StartupUiUtil.isUnderDarcula) MermaidFlowStyle.dark() else MermaidFlowStyle.light()
    )
    private val graphPreviewPanel = NativeFlowPreviewPanel(
        onNodeNavigate = ::navigateFromDiagramNode,
        onNodeSelected = ::previewDiagramNode,
        createNodeContextMenu = ::createDiagramNodeContextMenu
    )
    private val codePreviewPanel = ReduxCodePreviewPanel(project)
    private val headerTitle = JBLabel("Redux Flow").apply {
        font = JBFont.h3().asBold()
    }
    private val includeTestsModel = JToggleButton.ToggleButtonModel().apply {
        isSelected = false
    }
    private val inlineControls = createControlsRow(wrap = false)
    private val stackedControls = createControlsRow(wrap = true)
    private val secondaryHeaderRow = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(stackedControls, BorderLayout.CENTER)
    }
    private val headerPanel = createHeader()
    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
    }
    private val stateExplorerPanel = StateExplorerPanel(
        project = project,
        onActionSelected = { showCodePreviewForAction(it) },
        onActionNavigate = { navigateToAction(project, it) },
        onModificationSelected = { showCodePreviewForModification(it) },
        onModificationNavigate = { navigateToModification(project, it) }
    )
    private val mermaidTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        border = JBEmptyBorder(12)
    }
    private val flowTabComponent = JBScrollPane(contentPanel)
    private val stateTabComponent = JBScrollPane(stateExplorerPanel)
    private val tabs = JBTabbedPane().apply {
        border = JBUI.Borders.empty()
        addTab("Graph", graphPreviewPanel)
        addTab("Mermaid Source", JBScrollPane(mermaidTextArea))
        addTab("Flow", flowTabComponent)
        addTab("State", stateTabComponent)
        selectedIndex = GRAPH_TAB_INDEX
    }
    private val mainSplitter = OnePixelSplitter(true, 0.68f).apply {
        firstComponent = tabs
        secondComponent = codePreviewPanel
    }

    private var currentAction: ActionInfo? = null
    private var currentGraph: ActionGraph? = null
    private var currentMermaid: String = ""
    private var currentDiagramTargets: Map<String, DiagramNodeTarget> = emptyMap()
    private var currentPreviewTarget: DiagramNodeTarget? = null
    private var currentStateGraph: StateFieldGraph? = null

    init {
        border = JBUI.Borders.empty()
        includeTestsModel.addActionListener { rerenderCurrentGraph() }
        add(headerPanel, BorderLayout.NORTH)
        add(mainSplitter, BorderLayout.CENTER)
        showEmptyState()
    }

    fun showEmptyState() {
        currentAction = null
        currentGraph = null
        currentStateGraph = null
        currentMermaid = ""
        currentDiagramTargets = emptyMap()
        currentPreviewTarget = null
        headerTitle.text = "Redux Flow"
        renderMessage("Select a Redux Action and choose \"Show Redux Flow\".")
        stateExplorerPanel.showEmptyState()
        renderActionGraph(null)
        resetCodePreview()
        ensureFlowTabVisible(true)
        ensureStateTabVisible(false)
        setButtonsEnabled(false)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showInvalidAction(action: ActionInfo) {
        currentAction = action
        currentGraph = null
        currentStateGraph = null
        currentMermaid = ""
        currentDiagramTargets = emptyMap()
        currentPreviewTarget = null
        headerTitle.text = "Redux Flow: ${action.displayName}"
        renderMessage("The selected Redux Action is no longer valid. Re-run Show Redux Flow.")
        renderActionGraph(null)
        resetCodePreview()
        ensureFlowTabVisible(true)
        ensureStateTabVisible(false)
        setButtonsEnabled(false)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showAnalysisError(action: ActionInfo) {
        currentAction = action
        currentGraph = null
        currentStateGraph = null
        currentMermaid = ""
        currentDiagramTargets = emptyMap()
        currentPreviewTarget = null
        headerTitle.text = "Redux Flow: ${action.displayName}"
        renderMessage("Could not build Redux flow for this action.")
        graphPreviewPanel.showErrorMessage("Could not build Redux graph.")
        resetCodePreview()
        ensureFlowTabVisible(true)
        ensureStateTabVisible(false)
        setButtonsEnabled(false)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showGraph(graph: ActionGraph) {
        currentGraph = graph
        currentStateGraph = null
        currentAction = graph.action
        currentPreviewTarget = null
        ensureFlowTabVisible(true)
        ensureStateTabVisible(false)
        renderCurrentGraph(graph)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showInvalidState(field: StateFieldInfo) {
        currentStateGraph = null
        currentAction = null
        currentGraph = null
        currentPreviewTarget = null
        headerTitle.text = "State Explorer: ${field.qualifiedPath}"
        stateExplorerPanel.showInvalidState(field)
        renderMessage("State graph is unavailable for this selection.")
        renderFlowGraph(null, emptyMessage = "State graph is unavailable for this selection.")
        renderMermaid("")
        hideCodePreview()
        ensureFlowTabVisible(false)
        ensureStateTabVisible(true)
        setButtonsEnabled(false)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showStateAnalysisError(field: StateFieldInfo) {
        currentStateGraph = null
        currentAction = null
        currentGraph = null
        currentPreviewTarget = null
        headerTitle.text = "State Explorer: ${field.qualifiedPath}"
        stateExplorerPanel.showAnalysisError(field)
        renderMessage("Could not build the state graph for this field.")
        renderFlowGraph(null, emptyMessage = "Could not build the state graph for this field.")
        renderMermaid("")
        hideCodePreview()
        ensureFlowTabVisible(false)
        ensureStateTabVisible(true)
        setButtonsEnabled(false)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showStateLoading(field: StateFieldInfo) {
        currentStateGraph = null
        currentAction = null
        currentGraph = null
        currentPreviewTarget = null
        headerTitle.text = "State Explorer: ${field.qualifiedPath}"
        stateExplorerPanel.showLoadingState(field)
        renderMessage("Loading state graph...")
        renderFlowGraph(null, emptyMessage = "Loading state graph...")
        renderMermaid("")
        hideCodePreview()
        ensureFlowTabVisible(false)
        ensureStateTabVisible(true)
        setButtonsEnabled(false)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    fun showStateGraph(graph: StateFieldGraph) {
        currentStateGraph = graph
        currentGraph = null
        currentAction = null
        currentPreviewTarget = null
        headerTitle.text = "State Explorer: ${graph.stateField.qualifiedPath}"
        renderCurrentStateGraph(graph)
        stateExplorerPanel.showStateGraph(graph)
        hideCodePreview()
        ensureFlowTabVisible(false)
        ensureStateTabVisible(true)
        setButtonsEnabled(currentMermaid.isNotBlank())
        tabs.selectedIndex = GRAPH_TAB_INDEX
        updateHeaderLayout()
    }

    internal fun renderCurrentGraph(graph: ActionGraph) {
        val visibleGraph = filteredGraph(graph)
        try {
            val reduxGraph = reduxGraphBuilder.build(visibleGraph)
            currentDiagramTargets = buildDiagramNodeTargets(reduxGraph)
            currentMermaid = mermaidRenderer.render(visibleGraph)
            renderActionGraph(reduxGraph, currentDiagramTargets)
        } catch (error: Exception) {
            LOG.warn("Could not build Redux graph", error)
            currentDiagramTargets = emptyMap()
            currentMermaid = mermaidRenderer.render(visibleGraph)
            graphPreviewPanel.showErrorMessage("Could not build Redux graph.")
        }
        headerTitle.text = "Redux Flow: ${graph.action.displayName}"
        contentPanel.removeAll()
        contentPanel.add(summaryLabel(visibleGraph))
        contentPanel.add(Box.createVerticalStrut(12))

        val sections = buildSections(visibleGraph)
        sections.forEachIndexed { index, section ->
            contentPanel.add(
                ReduxFlowNodePanel(
                    project,
                    section,
                    onTargetSelected = ::previewDiagramNode,
                    onTargetNavigate = ::navigateFromDiagramNode
                )
            )
            if (index < sections.lastIndex) {
                contentPanel.add(arrowLabel())
            }
        }

        hideCodePreview()
        renderMermaid(currentMermaid)
        setButtonsEnabled(true)
        tabs.selectedIndex = GRAPH_TAB_INDEX
        revalidate()
        repaint()
    }

    internal fun renderCurrentStateGraph(graph: StateFieldGraph) {
        val visibleGraph = filteredStateGraph(graph)
        try {
            val stateGraph = stateGraphBuilder.build(visibleGraph)
            currentDiagramTargets = buildStateDiagramNodeTargets(stateGraph)
            currentMermaid = stateMermaidRenderer.render(stateGraph)
            renderStateGraph(stateGraph, currentDiagramTargets)
        } catch (error: Exception) {
            LOG.warn("Could not build state graph", error)
            currentDiagramTargets = emptyMap()
            currentMermaid = ""
            graphPreviewPanel.showErrorMessage("Could not build state graph.")
        }
        renderMermaid(currentMermaid)
        renderMessage("Use the State tab for the detailed reducer/action list.")
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
            val primaryRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(headerTitle, BorderLayout.WEST)
                add(inlineControls, BorderLayout.EAST)
            }
            add(primaryRow, BorderLayout.NORTH)
            add(secondaryHeaderRow, BorderLayout.SOUTH)
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    updateHeaderLayout()
                }
            })
        }

    private fun createControlsRow(wrap: Boolean): JComponent =
        JPanel().apply {
            layout = if (wrap) WrapLayout(FlowLayout.RIGHT, 8, 8) else FlowLayout(FlowLayout.RIGHT, 8, 0)
            isOpaque = false
            add(
                JBCheckBox("Include Tests").apply {
                    isOpaque = false
                    model = includeTestsModel
                }
            )
            add(
                JButton("Refresh").apply {
                    addActionListener { onRefresh() }
                }.withTransientFocusRing()
            )
            add(
                JButton("Copy Mermaid").apply {
                    addActionListener { copyMermaid() }
                }.withTransientFocusRing()
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

    private fun renderActionGraph(
        graph: org.mozilla.reduxnav.graph.ReduxGraph?,
        diagramTargets: Map<String, DiagramNodeTarget> = emptyMap()
    ) {
        graphPreviewPanel.setReduxGraph(graph, diagramTargets)
    }

    private fun renderStateGraph(
        graph: StateGraph?,
        diagramTargets: Map<String, DiagramNodeTarget> = emptyMap()
    ) {
        renderFlowGraph(graph?.let(stateGraphAdapter::adapt), diagramTargets, emptyMessage = "Select a Redux state field to view its graph.")
    }

    private fun renderFlowGraph(
        graph: org.mozilla.reduxnav.nativeflow.FlowGraph?,
        diagramTargets: Map<String, DiagramNodeTarget> = emptyMap(),
        emptyMessage: String = "Select a Redux Action and choose \"Show Redux Flow\"."
    ) {
        graphPreviewPanel.setFlowGraph(graph, diagramTargets, emptyMessage)
    }

    private fun renderMermaid(mermaid: String) {
        mermaidTextArea.text = mermaid
        mermaidTextArea.caretPosition = 0
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        controlsButtons().filter { it.text == "Copy Mermaid" }.forEach { it.isEnabled = enabled }
    }

    private fun rerenderCurrentGraph() {
        currentGraph?.let {
            renderCurrentGraph(it)
            return
        }
        currentStateGraph?.let {
            renderCurrentStateGraph(it)
        }
    }

    private fun navigateFromDiagramNode(target: DiagramNodeTarget) {
        when (target) {
            is DiagramNodeTarget.ActionTarget -> navigateToAction(project, target.action)
            is DiagramNodeTarget.ModificationTarget -> navigateToModification(project, target.modification)
            is DiagramNodeTarget.StateFieldTarget -> navigateToStateField(project, target.field)
            is DiagramNodeTarget.UsageTarget -> navigateToUsage(project, target.usage)
        }
    }

    private fun createDiagramNodeContextMenu(target: DiagramNodeTarget): JPopupMenu? {
        if (currentStateGraph == null) return null
        val action = (target as? DiagramNodeTarget.ActionTarget)?.action ?: return null
        val group = DefaultActionGroup().apply {
            add(
                object : DumbAwareAction("Show Redux graph for this action") {
                    override fun actionPerformed(event: AnActionEvent) {
                        ReduxFlowToolWindowService.getInstance(project).showFlow(action)
                    }
                }
            )
        }
        return ActionManager.getInstance()
            .createActionPopupMenu("ReduxFlow.StateGraphNode", group)
            .component
            .apply {
                putClientProperty(CONTEXT_MENU_LABELS_TEST_KEY, listOf("Show Redux graph for this action"))
            }
    }

    private fun previewDiagramNode(target: DiagramNodeTarget) {
        currentPreviewTarget = target
        showCodePreview(target)
    }

    private fun showCodePreviewForAction(action: ActionInfo) {
        currentPreviewTarget = null
        codePreviewPanel.isVisible = true
        codePreviewPanel.showAction(action)
        mainSplitter.revalidate()
        mainSplitter.repaint()
    }

    private fun showCodePreviewForModification(modification: StateModification) {
        currentPreviewTarget = null
        codePreviewPanel.isVisible = true
        codePreviewPanel.showElement(modification.modificationPointer.element)
        mainSplitter.revalidate()
        mainSplitter.repaint()
    }

    private fun filteredGraph(graph: ActionGraph): ActionGraph =
        if (includeTestsModel.isSelected) {
            graph
        } else {
            graph.copy(usages = graph.usages.filterNot { isTestPath(it.filePath) })
        }

    private fun filteredStateGraph(graph: StateFieldGraph): StateFieldGraph =
        if (includeTestsModel.isSelected) {
            graph
        } else {
            graph.copy(modifications = graph.modifications.filterNot { isTestPath(it.filePath) })
        }
    
    private fun updateHeaderLayout() {
        val contentWidth = (headerPanel.width - headerPanel.insets.left - headerPanel.insets.right).coerceAtLeast(0)
        if (contentWidth == 0) return

        val shouldStackControls =
            headerTitle.preferredSize.width + inlineControls.preferredSize.width + HEADER_GAP > contentWidth
        inlineControls.isVisible = !shouldStackControls
        secondaryHeaderRow.isVisible = shouldStackControls
        headerPanel.revalidate()
        headerPanel.repaint()
    }

    internal fun mermaidText(): String = mermaidTextArea.text

    internal fun diagramNodeTargetsForTest(): Map<String, DiagramNodeTarget> = currentDiagramTargets

    internal fun selectedTabTitle(): String = tabs.getTitleAt(tabs.selectedIndex)

    internal fun tabTitlesForTest(): List<String> =
        (0 until tabs.tabCount).map(tabs::getTitleAt)

    internal fun graphContextMenuLabelsForTest(nodeId: String): List<String> =
        graphPreviewPanel.contextMenuLabelsForTest(nodeId)

    internal fun setIncludeTestsForTest(include: Boolean) {
        includeTestsModel.isSelected = include
        rerenderCurrentGraph()
    }

    internal fun includesTestsForTest(): Boolean = includeTestsModel.isSelected

    internal fun headerUsesCompactLayout(): Boolean = secondaryHeaderRow.isVisible

    internal fun controlButtonFocusStatesForTest(): List<Pair<Boolean, Boolean>> =
        controlsButtons().map { it.isFocusable to it.isFocusPainted }

    internal fun previewMessageForTest(): String? = codePreviewPanel.currentMessageForTest()

    internal fun previewIsShowingEditorForTest(): Boolean = codePreviewPanel.isShowingEditorForTest()

    internal fun previewCurrentTargetForTest(target: DiagramNodeTarget?) {
        currentPreviewTarget = target
        if (target == null) {
            hideCodePreview()
        } else {
            showCodePreview(target)
        }
    }

    internal fun codePreviewFileNameForTest(): String? = codePreviewPanel.currentFileNameForTest()

    internal fun codePreviewFilePathForTest(): String? = codePreviewPanel.currentFilePathForTest()

    internal fun codePreviewSelectedTextForTest(): String? = codePreviewPanel.selectedTextForTest()

    internal fun codePreviewCurrentLineForTest(): Int? = codePreviewPanel.currentPreviewLineForTest()

    internal fun codePreviewVisibleStartLineForTest(): Int? = codePreviewPanel.visibleStartLineForTest()

    internal fun isCodePreviewVisibleForTest(): Boolean = codePreviewPanel.isVisible

    internal fun stateHeaderTextForTest(): String = stateExplorerPanel.headerTextForTest()

    internal fun stateActionLabelsForTest(): List<String> = stateExplorerPanel.actionLabelsForTest()

    internal fun stateModificationLabelsForTest(): List<String> = stateExplorerPanel.modificationLabelsForTest()

    internal fun stackedControlsPreferredHeightForTest(width: Int): Int {
        stackedControls.setSize(width, Int.MAX_VALUE)
        return stackedControls.preferredSize.height
    }

    override fun dispose() {
        codePreviewPanel.dispose()
    }

    private fun controlsButtons(): List<JButton> =
        listOf(inlineControls, stackedControls)
            .flatMap { panel -> panel.components.toList() }
            .filterIsInstance<JButton>()

    private fun ensureFlowTabVisible(visible: Boolean) {
        val currentIndex = indexOfTab("Flow")
        if (visible) {
            if (currentIndex == -1) {
                tabs.insertTab("Flow", null, flowTabComponent, null, FLOW_TAB_INDEX)
            }
        } else if (currentIndex != -1) {
            tabs.removeTabAt(currentIndex)
        }
    }

    private fun ensureStateTabVisible(visible: Boolean) {
        val currentIndex = indexOfTab("State")
        if (visible) {
            if (currentIndex == -1) {
                tabs.addTab("State", stateTabComponent)
            }
        } else if (currentIndex != -1) {
            tabs.removeTabAt(currentIndex)
        }
    }

    private fun indexOfTab(title: String): Int =
        (0 until tabs.tabCount).firstOrNull { tabs.getTitleAt(it) == title } ?: -1

    private fun resetCodePreview() {
        renderMermaid("")
        hideCodePreview()
    }

    private fun hideCodePreview() {
        currentPreviewTarget = null
        codePreviewPanel.showTarget(null)
        codePreviewPanel.isVisible = false
        mainSplitter.revalidate()
        mainSplitter.repaint()
    }

    private fun showCodePreview(target: DiagramNodeTarget) {
        codePreviewPanel.isVisible = true
        codePreviewPanel.showTarget(target)
        mainSplitter.revalidate()
        mainSplitter.repaint()
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
        private val LOG = Logger.getInstance(ReduxFlowPanel::class.java)
        internal const val CONTEXT_MENU_LABELS_TEST_KEY = "reduxNav.contextMenuLabels"
        private const val GRAPH_TAB_INDEX = 0
        private const val FLOW_TAB_INDEX = 2
        private const val NOTIFICATION_GROUP_ID = "Redux Navigator"
        private const val HEADER_GAP = 16
        private val sectionOrder = listOf(
            ReduxUsageKind.DISPATCH,
            ReduxUsageKind.MIDDLEWARE,
            ReduxUsageKind.REDUCER,
            ReduxUsageKind.OTHER
        )

        internal fun buildDiagramNodeTargets(
            graph: org.mozilla.reduxnav.graph.ReduxGraph
        ): Map<String, DiagramNodeTarget> {
            return buildMap {
                graph.nodes.forEach { node ->
                    when (val target = node.navigationTarget) {
                        is org.mozilla.reduxnav.graph.ReduxGraphNavigationTarget.ActionTarget -> put(node.id, DiagramNodeTarget.ActionTarget(target.action))
                        is org.mozilla.reduxnav.graph.ReduxGraphNavigationTarget.UsageTarget -> put(node.id, DiagramNodeTarget.UsageTarget(target.usage))
                    }
                }
            }
        }

        internal fun buildStateDiagramNodeTargets(
            graph: StateGraph
        ): Map<String, DiagramNodeTarget> =
            buildMap {
                graph.nodes.forEach { node ->
                    when (node) {
                        is org.mozilla.reduxnav.state.StateActionNode -> {
                            val action = (node.label.takeIf { it != "Unknown action" })
                            val matchingAction = when {
                                action == null -> null
                                else -> graph.nodes
                                    .filterIsInstance<org.mozilla.reduxnav.state.StateReducerNode>()
                                    .mapNotNull { it.modification.action }
                                    .distinctBy { it.id }
                                    .firstOrNull { it.displayName == action }
                            }
                            if (matchingAction != null) {
                                put(node.id, DiagramNodeTarget.ActionTarget(matchingAction))
                            }
                        }
                        is org.mozilla.reduxnav.state.StateFieldNode -> put(node.id, DiagramNodeTarget.StateFieldTarget(node.field))
                        is org.mozilla.reduxnav.state.StateReducerNode -> put(node.id, DiagramNodeTarget.ModificationTarget(node.modification))
                    }
                }
            }

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
            val descriptor = ReadAction.compute<OpenFileDescriptor?, RuntimeException> {
                val element = usage.element.element ?: return@compute null
                val file = element.containingFile?.virtualFile ?: return@compute null
                OpenFileDescriptor(project, file, element.textOffset)
            } ?: return
            descriptor.navigate(true)
        }

        internal fun navigateToAction(project: Project, action: ActionInfo) {
            val descriptor = ReadAction.compute<OpenFileDescriptor?, RuntimeException> {
                val element = action.declaration?.element ?: return@compute null
                val file = element.containingFile?.virtualFile ?: return@compute null
                OpenFileDescriptor(project, file, element.textOffset)
            } ?: return
            descriptor.navigate(true)
        }

        internal fun navigateToModification(project: Project, modification: StateModification) {
            val descriptor = ReadAction.compute<OpenFileDescriptor?, RuntimeException> {
                val element = modification.modificationPointer.element ?: return@compute null
                val file = element.containingFile?.virtualFile ?: return@compute null
                OpenFileDescriptor(project, file, element.textOffset)
            } ?: return
            descriptor.navigate(true)
        }

        internal fun navigateToStateField(project: Project, field: StateFieldInfo) {
            val descriptor = ReadAction.compute<OpenFileDescriptor?, RuntimeException> {
                val element = field.declarationPointer?.element ?: return@compute null
                val file = element.containingFile?.virtualFile ?: return@compute null
                OpenFileDescriptor(project, file, element.textOffset)
            } ?: return
            descriptor.navigate(true)
        }

        private fun countLabel(count: Int, singular: String, plural: String): String =
            if (count == 1) "1 $singular" else "$count $plural"
    }
}

private class WrapLayout(
    align: Int,
    hgap: Int,
    vgap: Int
) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, preferred = false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val horizontalInsetsAndGap = target.insets.left + target.insets.right + (hgap * 2)
            val maxWidth = (target.width.takeIf { it > 0 } ?: Int.MAX_VALUE) - horizontalInsetsAndGap

            var dimension = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0

            for (component in target.components) {
                if (!component.isVisible) continue

                val size = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth != 0 && rowWidth + hgap + size.width > maxWidth) {
                    addRow(dimension, rowWidth, rowHeight)
                    rowWidth = 0
                    rowHeight = 0
                }

                if (rowWidth != 0) {
                    rowWidth += hgap
                }
                rowWidth += size.width
                rowHeight = maxOf(rowHeight, size.height)
            }

            addRow(dimension, rowWidth, rowHeight)
            dimension.width += horizontalInsetsAndGap
            dimension.height += target.insets.top + target.insets.bottom + vgap * 2
            return dimension
        }
    }

    private fun addRow(dimension: Dimension, rowWidth: Int, rowHeight: Int) {
        dimension.width = maxOf(dimension.width, rowWidth)
        if (dimension.height > 0) {
            dimension.height += vgap
        }
        dimension.height += rowHeight
    }
}

sealed interface ReduxFlowSection {
    data class Action(val action: ActionInfo) : ReduxFlowSection
    data class Usages(val kind: ReduxUsageKind, val usages: List<ReduxUsage>) : ReduxFlowSection
}
