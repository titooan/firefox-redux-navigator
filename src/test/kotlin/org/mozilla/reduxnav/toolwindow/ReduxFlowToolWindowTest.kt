package org.mozilla.reduxnav.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer
import java.io.File

class ReduxFlowToolWindowTest : BasePlatformTestCase() {
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
    }

    fun testShowGraphCanExcludeTestsFromMermaidAndFlowSummary() {
        val panel = ReduxFlowPanel(project) {}
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
    }

    fun testPanelStartsOnFlowTab() {
        val panel = ReduxFlowPanel(project) {}

        assertEquals("Flow", panel.selectedTabTitle())
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
}
