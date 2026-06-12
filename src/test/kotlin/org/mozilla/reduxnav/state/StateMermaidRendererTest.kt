package org.mozilla.reduxnav.state

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer

class StateMermaidRendererTest : BasePlatformTestCase() {
    private val renderer = StateMermaidRenderer()

    fun testRenderIncludesActionReducerAndStateNodes() {
        val action = actionInfo("SelectTabAction")
        val graph = StateGraphBuilder().build(StateFieldGraph(stateFieldInfo(), listOf(modification(action))))

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.startsWith("flowchart LR"))
        assertTrue(mermaid.contains("""action_0["SelectTabAction"]"""))
        assertTrue(mermaid.contains("""reducer_0["Reducer.kt:20"]"""))
        assertTrue(mermaid.contains("""state["BrowserState.selectedTabId"]"""))
        assertTrue(mermaid.contains("action_0 --> reducer_0"))
        assertTrue(mermaid.contains("reducer_0 --> state"))
    }

    fun testRenderIncludesUnknownActionNodeAndTestStyling() {
        val graph = StateGraphBuilder().build(
            StateFieldGraph(stateFieldInfo(), listOf(modification(action = null, filePath = "/work/src/test/kotlin/ReducerTest.kt")))
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.contains("""action_unknown["Unknown action"]"""))
        assertTrue(mermaid.contains("classDef testNode"))
        assertTrue(mermaid.contains("class reducer_0 testNode;"))
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

    private fun modification(
        action: ActionInfo?,
        filePath: String = "/work/Reducer.kt"
    ): StateModification {
        val usage = usage(filePath)
        return StateModification(
            stateField = stateFieldInfo(),
            action = action,
            reducerUsage = usage,
            modificationPointer = usage.element,
            fileName = usage.fileName,
            filePath = usage.filePath,
            lineNumber = usage.line,
            snippet = "selectedTabId = value"
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

    private fun usage(filePath: String): ReduxUsage {
        val file = myFixture.configureByText(
            "Reducer.kt",
            """
            fun sample() {
                SelectTabAction
            }
            """.trimIndent()
        )
        val offset = file.text.indexOf("SelectTabAction")
        val element = file.findElementAt(offset) ?: error("Expected PSI element")
        return ReduxUsage(
            kind = ReduxUsageKind.REDUCER,
            displayText = "SelectTabAction",
            fileName = filePath.substringAfterLast('/'),
            filePath = filePath,
            line = 20,
            element = element.toSmartPointer()
        )
    }
}
