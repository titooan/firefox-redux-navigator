package org.mozilla.reduxnav.graph

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer

class ReduxGraphBuilderTest : BasePlatformTestCase() {
    private val builder = ReduxGraphBuilder()

    fun testBuildCreatesExpectedNodesAndEdges() {
        val action = actionInfo("AddTabAction")
        val dispatch = usage("Dispatch.kt", "/work/Dispatch.kt", 4, ReduxUsageKind.DISPATCH)
        val middlewareA = usage("MiddlewareA.kt", "/work/MiddlewareA.kt", 12, ReduxUsageKind.MIDDLEWARE)
        val middlewareB = usage("MiddlewareB.kt", "/work/MiddlewareB.kt", 18, ReduxUsageKind.MIDDLEWARE)
        val reducer = usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER)

        val graph = builder.build(ActionGraph(action, listOf(dispatch, middlewareA, middlewareB, reducer)))

        assertEquals(
            listOf("dispatch_0", "action", "middleware_0", "middleware_1", "reducer_0"),
            graph.nodes.map { it.id }
        )
        assertEquals(
            listOf(
                ReduxGraphEdge("dispatch_0", "action"),
                ReduxGraphEdge("action", "middleware_0"),
                ReduxGraphEdge("action", "middleware_1"),
                ReduxGraphEdge("middleware_0", "reducer_0"),
                ReduxGraphEdge("middleware_1", "reducer_0")
            ),
            graph.edges
        )
    }

    fun testBuildIsDeterministic() {
        val action = actionInfo("AddTabAction")
        val dispatch = usage("Dispatch.kt", "/b/Dispatch.kt", 8, ReduxUsageKind.DISPATCH)
        val reducer = usage("Reducer.kt", "/a/Reducer.kt", 20, ReduxUsageKind.REDUCER)
        val input = ActionGraph(action, listOf(reducer, dispatch))

        val first = builder.build(input)
        val second = builder.build(input)

        assertEquals(first, second)
    }

    fun testNavigationTargetMappingUsesBackingPsiTargets() {
        val action = actionInfo("AddTabAction")
        val reducer = usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER)

        val graph = builder.build(ActionGraph(action, listOf(reducer)))

        val actionNode = graph.nodes.single { it.id == "action" } as ActionNode
        val reducerNode = graph.nodes.single { it.id == "reducer_0" } as ReducerNode

        assertEquals(action, (actionNode.navigationTarget as ReduxGraphNavigationTarget.ActionTarget).action)
        assertEquals(reducer, (reducerNode.navigationTarget as ReduxGraphNavigationTarget.UsageTarget).usage)
    }

    fun testEmptyGraphContainsOnlyActionNode() {
        val graph = builder.build(ActionGraph(actionInfo("AddTabAction"), emptyList()))

        assertEquals(listOf("action"), graph.nodes.map { it.id })
        assertEmpty(graph.edges)
    }

    fun testUsageTooltipUsesHtmlAndIncludesLineSnippet() {
        val reducer = usage("Reducer.kt", "/work/Reducer.kt", 20, ReduxUsageKind.REDUCER)

        val tooltip = ReduxGraphNavigationTarget.UsageTarget(reducer).tooltipText

        assertTrue(tooltip.startsWith("<html>Reducer.kt:20<br/>"))
        assertTrue(tooltip.contains("AddTabAction"))
        assertTrue(tooltip.endsWith("</html>"))
    }

    fun testActionTooltipUsesHtml() {
        val action = actionInfo("AddTabAction")

        val tooltip = ReduxGraphNavigationTarget.ActionTarget(action).tooltipText

        assertEquals("<html>AddTabAction</html>", tooltip)
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
            fileName = fileName,
            filePath = filePath,
            line = line,
            element = element.toSmartPointer()
        )
    }
}
