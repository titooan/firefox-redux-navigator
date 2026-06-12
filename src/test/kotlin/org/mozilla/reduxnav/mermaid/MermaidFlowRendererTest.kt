package org.mozilla.reduxnav.mermaid

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer
import java.io.File

class MermaidFlowRendererTest : BasePlatformTestCase() {
    private val renderer = MermaidFlowRenderer()

    fun testRenderSimpleGraphIncludesNodesAndEdges() {
        val graph = ActionGraph(
            actionInfo("AddTabAction"),
            listOf(
                usage("Dispatch.kt", "/work/Dispatch.kt", 88, ReduxUsageKind.DISPATCH),
                usage("Middleware.kt", "/work/Middleware.kt", 74, ReduxUsageKind.MIDDLEWARE),
                usage("Reducer.kt", "/work/Reducer.kt", 177, ReduxUsageKind.REDUCER)
            )
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.startsWith("flowchart LR"))
        assertTrue(mermaid.contains("""dispatch_0["Dispatch.kt:88"]"""))
        assertTrue(mermaid.contains("""action["AddTabAction"]"""))
        assertTrue(mermaid.contains("""middleware_0["Middleware.kt:74"]"""))
        assertTrue(mermaid.contains("""reducer_0["Reducer.kt:177"]"""))
        assertTrue(mermaid.contains("dispatch_0 --> action"))
        assertTrue(mermaid.contains("action --> middleware_0"))
        assertTrue(mermaid.contains("middleware_0 --> reducer_0"))
    }

    fun testRenderWithoutMiddlewaresConnectsActionToReducers() {
        val graph = ActionGraph(
            actionInfo("AddTabAction"),
            listOf(
                usage("Reducer.kt", "/work/Reducer.kt", 177, ReduxUsageKind.REDUCER)
            )
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.contains("action --> reducer_0"))
        assertFalse(mermaid.contains("middleware_0 --> reducer_0"))
    }

    fun testRenderWithoutReducersRemainsValid() {
        val graph = ActionGraph(
            actionInfo("AddTabAction"),
            listOf(
                usage("Dispatch.kt", "/work/Dispatch.kt", 88, ReduxUsageKind.DISPATCH),
                usage("Middleware.kt", "/work/Middleware.kt", 74, ReduxUsageKind.MIDDLEWARE)
            )
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.contains("dispatch_0 --> action"))
        assertTrue(mermaid.contains("action --> middleware_0"))
        assertFalse(mermaid.contains("middleware_0 --> reducer_0"))
    }

    fun testRenderEscapesMermaidBreakingCharacters() {
        val graph = ActionGraph(
            actionInfo("""SomeAction("foo")"""),
            emptyList()
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.contains("""action["SomeAction&#40;&quot;foo&quot;&#41;"]"""))
    }

    fun testRenderUsesDeterministicOrderingAndDeduplication() {
        val graph = ActionGraph(
            actionInfo("AddTabAction"),
            listOf(
                usage("ReducerB.kt", "/work/reducers/ReducerB.kt", 30, ReduxUsageKind.REDUCER),
                usage("DispatchB.kt", "/work/dispatch/DispatchB.kt", 22, ReduxUsageKind.DISPATCH),
                usage("DispatchA.kt", "/work/dispatch/DispatchA.kt", 11, ReduxUsageKind.DISPATCH),
                usage("DispatchDup.kt", "/work/dispatch/DispatchA.kt", 11, ReduxUsageKind.DISPATCH),
                usage("MiddlewareB.kt", "/work/middleware/MiddlewareB.kt", 14, ReduxUsageKind.MIDDLEWARE),
                usage("MiddlewareA.kt", "/work/middleware/MiddlewareA.kt", 9, ReduxUsageKind.MIDDLEWARE),
                usage("ReducerA.kt", "/work/reducers/ReducerA.kt", 8, ReduxUsageKind.REDUCER)
            )
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.indexOf("""dispatch_0["DispatchA.kt:11"]""") < mermaid.indexOf("""dispatch_1["DispatchB.kt:22"]"""))
        assertTrue(mermaid.indexOf("""middleware_0["MiddlewareA.kt:9"]""") < mermaid.indexOf("""middleware_1["MiddlewareB.kt:14"]"""))
        assertTrue(mermaid.indexOf("""reducer_0["ReducerA.kt:8"]""") < mermaid.indexOf("""reducer_1["ReducerB.kt:30"]"""))
        assertEquals(1, Regex("""dispatch_\d+\["DispatchA\.kt:11"]""").findAll(mermaid).count())
    }

    fun testRenderMarksTestNodesWithGreenClass() {
        val graph = ActionGraph(
            actionInfo("AddTabAction"),
            listOf(
                usage("DispatchTest.kt", "/work/project/src/test/kotlin/DispatchTest.kt", 14, ReduxUsageKind.DISPATCH),
                usage("Reducer.kt", "/work/project/src/main/kotlin/Reducer.kt", 22, ReduxUsageKind.REDUCER)
            )
        )

        val mermaid = renderer.render(graph)

        assertTrue(mermaid.contains("classDef testNode"))
        assertTrue(mermaid.contains("fill:#d7ead7"))
        assertTrue(mermaid.contains("color:#1e2a1e"))
        assertTrue(mermaid.contains("class dispatch_0 testNode;"))
        assertFalse(mermaid.contains("class reducer_0 testNode;"))
    }

    fun testRenderUsesDarkThemeTestNodeColorsWhenConfigured() {
        val darkRenderer = MermaidFlowRenderer(style = MermaidFlowStyle.dark())
        val graph = ActionGraph(
            actionInfo("AddTabAction"),
            listOf(
                usage("DispatchTest.kt", "/work/project/src/test/kotlin/DispatchTest.kt", 14, ReduxUsageKind.DISPATCH)
            )
        )

        val mermaid = darkRenderer.render(graph)

        assertTrue(mermaid.contains("fill:#214d29"))
        assertTrue(mermaid.contains("stroke:#4aa35f"))
        assertTrue(mermaid.contains("color:#f4fff4"))
    }

    private fun actionInfo(name: String): ActionInfo {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            data object `${name}` : Action
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
