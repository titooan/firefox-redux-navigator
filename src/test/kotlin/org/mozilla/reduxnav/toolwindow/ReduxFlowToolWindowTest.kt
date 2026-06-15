package org.mozilla.reduxnav.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.navigation.History
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.graph.ReduxGraphBuilder
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer
import org.mozilla.reduxnav.nativeflow.DiagramNodeTarget
import org.mozilla.reduxnav.state.StateFieldGraph
import org.mozilla.reduxnav.state.StateFieldInfo
import org.mozilla.reduxnav.state.StateModification
import java.io.File

class ReduxFlowToolWindowTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            ReduxFlowToolWindowService.getInstance(project).resetForTest()
        } finally {
            super.tearDown()
        }
    }

    fun testShowReduxFlowActionResolvesUsageAtCaret() {
        myFixture.configureByText(
            "Usage.kt",
            """
            interface Action

            data object AddTabAction : Action

            interface Store {
                fun dispatch(action: Action)
            }

            fun trigger(store: Store) {
                store.dispatch(<caret>AddTabAction)
            }
            """.trimIndent()
        )

        val action = ShowReduxFlowAction().resolveAction(project, myFixture.editor, myFixture.file)

        assertNotNull(action)
        assertEquals("AddTabAction", action!!.displayName)
    }

    fun testShowReduxFlowActionResolvesDeclarationAtCaret() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            data object <caret>AddTabAction : Action
            """.trimIndent()
        )

        val action = ShowReduxFlowAction().resolveAction(project, myFixture.editor, myFixture.file)

        assertNotNull(action)
        assertEquals("AddTabAction", action!!.displayName)
    }

    fun testReduxFlowPanelGroupsUsagesInExpectedOrder() {
        val action = actionInfo("AddTabAction")
        val graph = ActionGraph(
            action,
            listOf(
                usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER),
                usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH),
                usage("Middleware.kt", "/work/Middleware.kt", 12, ReduxUsageKind.MIDDLEWARE),
                usage("Other.kt", "/work/Other.kt", 30, ReduxUsageKind.OTHER)
            )
        )

        val sections = ReduxFlowPanel.buildSections(graph)

        assertEquals(
            listOf(
                "Dispatches",
                "Action",
                "Middlewares",
                "Reducers",
                "Other references"
            ),
            sections.map {
                when (it) {
                    is ReduxFlowSection.Action -> "Action"
                    is ReduxFlowSection.Usages -> it.kind.title
                }
            }
        )
    }

    fun testNavigationLinkInvokesCallback() {
        var invoked = false

        val link = ReduxFlowPanel.createNavigationLink("Open target") {
            invoked = true
        }
        link.doClick()

        assertTrue(invoked)
    }

    fun testShowGraphUpdatesMermaidView() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val graph = ActionGraph(
                actionInfo("AddTabAction"),
                listOf(
                    usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH),
                    usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER)
                )
            )

            panel.showGraph(graph)

            assertTrue(panel.mermaidText().contains("flowchart LR"))
            assertTrue(panel.mermaidText().contains("dispatch_0 --> action"))
            assertTrue(panel.mermaidText().contains("action --> reducer_0"))
        } finally {
            panel.dispose()
        }
    }

    fun testShowGraphPopulatesCodePreviewPane() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val dispatch = usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH)
            val graph = ActionGraph(
                actionInfo("AddTabAction"),
                listOf(
                    dispatch,
                    usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER)
                )
            )

            panel.showGraph(graph)
            panel.previewCurrentTargetForTest(DiagramNodeTarget.UsageTarget(dispatch))

            assertTrue(panel.previewIsShowingEditorForTest())
            assertEquals("Dispatch.kt", panel.codePreviewFileNameForTest())
            assertTrue(panel.codePreviewFilePathForTest()?.contains("Dispatch.kt") == true)
            assertTrue(panel.codePreviewSelectedTextForTest()?.contains("AddTabAction") == true)
        } finally {
            panel.dispose()
        }
    }

    fun testShowGraphKeepsCodePreviewHiddenUntilNodeSelection() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val graph = ActionGraph(
                actionInfo("AddTabAction"),
                listOf(usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH))
            )

            panel.showGraph(graph)

            assertFalse(panel.isCodePreviewVisibleForTest())
            assertFalse(panel.previewIsShowingEditorForTest())
        } finally {
            panel.dispose()
        }
    }

    fun testCodePreviewScrollsToSelectedNodeLineOnFirstOpen() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val file = myFixture.configureByText(
                "DeepDispatch.kt",
                buildString {
                    appendLine("fun sample() {")
                    repeat(60) { appendLine("    val value$it = $it") }
                    appendLine("    AddTabAction")
                    appendLine("}")
                }
            )
            val offset = file.text.indexOf("AddTabAction")
            val element = file.findElementAt(offset) ?: error("Expected PSI element")
            val dispatch = ReduxUsage(
                kind = ReduxUsageKind.DISPATCH,
                displayText = "AddTabAction",
                fileName = "DeepDispatch.kt",
                filePath = "/work/DeepDispatch.kt",
                line = 62,
                element = element.toSmartPointer()
            )
            val graph = ActionGraph(
                actionInfo("AddTabAction"),
                listOf(dispatch)
            )

            panel.setSize(900, 700)
            panel.doLayout()
            panel.showGraph(graph)
            panel.previewCurrentTargetForTest(DiagramNodeTarget.UsageTarget(dispatch))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            assertEquals(61, panel.codePreviewCurrentLineForTest())
            assertTrue((panel.codePreviewVisibleStartLineForTest() ?: -1) > 0)
        } finally {
            panel.dispose()
        }
    }

    fun testEmptyStateClearsCodePreviewPane() {
        val panel = ReduxFlowPanel(project) {}

        try {
            panel.showEmptyState()

            assertFalse(panel.isCodePreviewVisibleForTest())
            assertTrue(panel.previewMessageForTest()?.contains("Select a Redux flow node") == true)
        } finally {
            panel.dispose()
        }
    }

    fun testBuildDiagramNodeTargetsUsesStableMermaidIds() {
        val action = actionInfo("AddTabAction")
        val dispatch = usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH)
        val middleware = usage("Middleware.kt", "/work/Middleware.kt", 12, ReduxUsageKind.MIDDLEWARE)
        val reducer = usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER)
        val graph = ActionGraph(action, listOf(dispatch, middleware, reducer))

        val targets = ReduxFlowPanel.buildDiagramNodeTargets(ReduxGraphBuilder().build(graph))

        assertEquals(
            setOf("action", "dispatch_0", "middleware_0", "reducer_0"),
            targets.keys
        )
        assertEquals(
            dispatch,
            (targets.getValue("dispatch_0") as DiagramNodeTarget.UsageTarget).usage
        )
        assertEquals(
            middleware,
            (targets.getValue("middleware_0") as DiagramNodeTarget.UsageTarget).usage
        )
        assertEquals(
            reducer,
            (targets.getValue("reducer_0") as DiagramNodeTarget.UsageTarget).usage
        )
        assertEquals(
            action,
            (targets.getValue("action") as DiagramNodeTarget.ActionTarget).action
        )
    }

    fun testShowGraphCanExcludeTestsFromMermaidAndFlowSummary() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val graph = ActionGraph(
                actionInfo("AddTabAction"),
                listOf(
                    usage("Dispatch.kt", "/work/src/main/kotlin/Dispatch.kt", 4, ReduxUsageKind.DISPATCH),
                    usage("DispatchTest.kt", "/work/src/test/kotlin/DispatchTest.kt", 5, ReduxUsageKind.DISPATCH),
                    usage("Reducer.kt", "/work/src/main/kotlin/Reducer.kt", 20, ReduxUsageKind.REDUCER)
                )
            )

            panel.showGraph(graph)
            panel.setIncludeTestsForTest(false)

            assertFalse(panel.includesTestsForTest())
            assertTrue(panel.mermaidText().contains("""dispatch_0["Dispatch.kt:4"]"""))
            assertFalse(panel.mermaidText().contains("DispatchTest.kt:5"))
            assertFalse(panel.mermaidText().contains("classDef testNode"))
            assertEquals(setOf("action", "dispatch_0", "reducer_0"), panel.diagramNodeTargetsForTest().keys)
        } finally {
            panel.dispose()
        }
    }

    fun testPanelStartsOnGraphTab() {
        val panel = ReduxFlowPanel(project) {}

        assertEquals("Graph", panel.selectedTabTitle())
    }

    fun testPanelDisablesIncludeTestsByDefault() {
        val panel = ReduxFlowPanel(project) {}

        assertFalse(panel.includesTestsForTest())
    }

    fun testPanelReturnsHistoryFromDataProvider() {
        val historyController = ReduxPaneHistoryController { _, _ -> }
        val panel = ReduxFlowPanel(project, historyController.history) {}

        assertSame(historyController.history, panel.getData(History.KEY.name))
        assertSame(historyController.history, panel.historyForTest())
    }

    fun testPanelHistoryStatusStartsEmpty() {
        val panel = ReduxFlowPanel(project) {}

        assertEquals("Pane history: back no | forward no | focus no", panel.historyStatusTextForTest())
    }

    fun testPanelUsesFlowGraphMermaidTabOrder() {
        val panel = ReduxFlowPanel(project) {}

        assertEquals(
            listOf("Graph", "Mermaid Source", "Flow"),
            panel.tabTitlesForTest()
        )
    }

    fun testShowStateGraphSelectsStateTabAndRendersRows() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val action = actionInfo("SelectTabAction")
            val modification = StateModification(
                stateField = stateFieldInfo(),
                action = action,
                reducerUsage = null,
                modificationPointer = usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER).element,
                fileName = "Reducer.kt",
                filePath = "/work/Reducer.kt",
                lineNumber = 20,
                snippet = "selectedTabId = action.tabId"
            )

            panel.showStateGraph(
                StateFieldGraph(
                    stateField = stateFieldInfo(),
                    modifications = listOf(modification)
                )
            )

            assertEquals("Graph", panel.selectedTabTitle())
            assertEquals(listOf("Graph", "Mermaid Source", "State"), panel.tabTitlesForTest())
            assertEquals("State Explorer: BrowserState.selectedTabId", panel.stateHeaderTextForTest())
            assertEquals(listOf("SelectTabAction"), panel.stateActionLabelsForTest())
            assertEquals(listOf("SelectTabAction: selectedTabId = action.tabId"), panel.stateModificationLabelsForTest())
            assertTrue(panel.mermaidText().contains("flowchart LR"))
            assertTrue(panel.mermaidText().contains("action_0 --> reducer_0"))
            assertTrue(panel.mermaidText().contains("reducer_0 --> state"))
            assertEquals(setOf("action_0", "reducer_0", "state"), panel.diagramNodeTargetsForTest().keys)
        } finally {
            panel.dispose()
        }
    }

    fun testShowActionGraphRestoresFlowTabAfterStateGraph() {
        val panel = ReduxFlowPanel(project) {}
        try {
            panel.showStateGraph(
                StateFieldGraph(
                    stateField = stateFieldInfo(),
                    modifications = emptyList()
                )
            )

            panel.showGraph(
                ActionGraph(
                    actionInfo("AddTabAction"),
                    listOf(usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH))
                )
            )

            assertEquals(listOf("Graph", "Mermaid Source", "Flow"), panel.tabTitlesForTest())
            assertEquals("Graph", panel.selectedTabTitle())
        } finally {
            panel.dispose()
        }
    }

    fun testStateGraphActionNodesExposeReduxFlowContextMenu() {
        val panel = ReduxFlowPanel(project) {}
        try {
            val action = actionInfo("SelectTabAction")
            val modification = StateModification(
                stateField = stateFieldInfo(),
                action = action,
                reducerUsage = null,
                modificationPointer = usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER).element,
                fileName = "Reducer.kt",
                filePath = "/work/Reducer.kt",
                lineNumber = 20,
                snippet = "selectedTabId = action.tabId"
            )

            panel.showStateGraph(
                StateFieldGraph(
                    stateField = stateFieldInfo(),
                    modifications = listOf(modification)
                )
            )

            assertEquals(
                listOf("Show Redux graph for this action"),
                panel.graphContextMenuLabelsForTest("action_0")
            )
            assertEquals(emptyList<String>(), panel.graphContextMenuLabelsForTest("reducer_0"))
        } finally {
            panel.dispose()
        }
    }

    fun testActionGraphActionNodesDoNotExposeReduxFlowContextMenu() {
        val panel = ReduxFlowPanel(project) {}
        try {
            panel.showGraph(
                ActionGraph(
                    actionInfo("AddTabAction"),
                    listOf(usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH))
                )
            )

            assertEquals(emptyList<String>(), panel.graphContextMenuLabelsForTest("action"))
        } finally {
            panel.dispose()
        }
    }

    fun testHeaderUsesCompactLayoutWhenPanelIsNarrow() {
        val panel = ReduxFlowPanel(project) {}

        panel.setSize(280, 400)
        panel.doLayout()

        assertTrue(panel.headerUsesCompactLayout())
    }

    fun testCompactControlsWrapToMultipleLinesWhenVeryNarrow() {
        val panel = ReduxFlowPanel(project) {}

        panel.setSize(220, 400)
        panel.doLayout()

        assertTrue(panel.headerUsesCompactLayout())
        assertTrue(panel.stackedControlsPreferredHeightForTest(140) > 40)
    }

    fun testHeaderButtonsKeepDefaultFocusPaintingBehavior() {
        val panel = ReduxFlowPanel(project) {}

        assertEquals(
            listOf(true to true, true to true, true to true, true to true),
            panel.controlButtonFocusStatesForTest()
        )
    }

    fun testServiceBackAndForwardRestoreStateAndActionViews() {
        val service = toolWindowService()
        val action = actionInfo("AddTabAction")
        val field = stateFieldInfo()

        service.showState(field)
        service.showFlow(action)

        assertTrue(service.canGoBack())
        assertFalse(service.canGoForward())

        service.goBack()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("State Explorer: BrowserState.selectedTabId", service.component.headerTitleTextForTest())
        assertEquals(listOf("Graph", "Mermaid Source", "State"), service.component.tabTitlesForTest())
        assertTrue(service.component.historyStatusTextForTest().contains("back no"))
        assertTrue(service.component.historyStatusTextForTest().contains("forward yes"))
        assertTrue(service.canGoForward())

        service.goForward()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("Redux Flow: AddTabAction", service.component.headerTitleTextForTest())
        assertEquals(listOf("Graph", "Mermaid Source", "Flow"), service.component.tabTitlesForTest())
        assertTrue(service.component.historyStatusTextForTest().contains("back yes"))
        assertTrue(service.component.historyStatusTextForTest().contains("forward no"))
    }

    fun testServiceClearsForwardHistoryWhenOpeningNewLocationAfterBack() {
        val service = toolWindowService()
        val state = stateFieldInfo()
        val add = actionInfo("AddTabAction")
        val remove = actionInfo("RemoveTabAction")

        service.showState(state)
        service.showFlow(add)
        service.goBack()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(service.canGoForward())

        service.showFlow(remove)

        assertFalse(service.canGoForward())
        assertTrue(service.canGoBack())
    }

    fun testServiceDoesNotPushDuplicateAdjacentLocations() {
        val service = toolWindowService()
        val action = actionInfo("AddTabAction")

        service.showFlow(action)
        service.showFlow(action.copy())

        assertFalse(service.canGoBack())
    }

    fun testPreviewSelectionDoesNotCreateHistoryEntry() {
        val service = toolWindowService()
        val dispatch = usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH)

        service.component.showGraph(
            ActionGraph(
                actionInfo("AddTabAction"),
                listOf(dispatch)
            )
        )
        service.component.previewCurrentTargetForTest(DiagramNodeTarget.UsageTarget(dispatch))

        assertFalse(service.canGoBack())
        assertFalse(service.canGoForward())
    }

    fun testTabSwitchingDoesNotCreateHistoryEntry() {
        val service = toolWindowService()

        service.showState(stateFieldInfo())
        service.component.selectTabForTest("State")
        service.component.selectTabForTest("Graph")

        assertFalse(service.canGoBack())
        assertFalse(service.canGoForward())
    }

    fun testIncludeTestsToggleDoesNotCreateHistoryEntry() {
        val service = toolWindowService()

        service.component.showGraph(
            ActionGraph(
                actionInfo("AddTabAction"),
                listOf(usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH))
            )
        )
        service.component.setIncludeTestsForTest(true)
        service.component.setIncludeTestsForTest(false)

        assertFalse(service.canGoBack())
        assertFalse(service.canGoForward())
    }

    fun testRefreshCurrentDoesNotCreateHistoryEntry() {
        val service = toolWindowService()
        val action = actionInfo("AddTabAction")

        service.showFlow(action)
        service.refreshCurrent()

        assertFalse(service.canGoBack())
        assertFalse(service.canGoForward())
    }

    private fun actionInfo(name: String): ActionInfo {
        myFixture.configureByText(
            "$name.kt",
            """
            interface Action

            data object $name : Action
            """.trimIndent()
        )
        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == name }
        return ActionSymbolResolver().resolveDeclaration(declaration) ?: error("Expected action declaration")
    }

    private fun usage(
        fileName: String,
        filePath: String,
        line: Int,
        kind: ReduxUsageKind
    ): ReduxUsage {
        val file = myFixture.configureByText(
            fileName,
            """
            fun sample() {
                AddTabAction
            }
            """.trimIndent()
        )
        val offset = file.text.indexOf("AddTabAction")
        val element = file.findElementAt(offset) ?: error("Expected PSI element")
        return ReduxUsage(
            kind = kind,
            displayText = "AddTabAction",
            fileName = File(filePath).name,
            filePath = filePath,
            line = line,
            element = element.toSmartPointer()
        )
    }

    private fun stateFieldInfo(): StateFieldInfo =
        StateFieldInfo(
            id = "BrowserState.selectedTabId",
            fieldName = "selectedTabId",
            fieldPath = "selectedTabId",
            stateClassName = "BrowserState",
            qualifiedPath = "BrowserState.selectedTabId",
            declarationPointer = null
        )

    private fun toolWindowService(): ReduxFlowToolWindowService =
        ReduxFlowToolWindowService.getInstance(project).also { it.resetForTest() }
}
