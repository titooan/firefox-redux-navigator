package org.mozilla.reduxnav.state

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer

class StateGraphBuilderTest : BasePlatformTestCase() {
    private val builder = StateGraphBuilder()

    fun testBuildCreatesActionReducerStateChain() {
        val action = actionInfo("SelectTabAction")
        val modification = modification(action = action, fileName = "Reducer.kt", filePath = "/work/Reducer.kt", line = 20)

        val graph = builder.build(StateFieldGraph(stateFieldInfo(), listOf(modification)))

        assertEquals(listOf("action_0", "reducer_0", "state"), graph.nodes.map { it.id })
        assertEquals(
            listOf(
                StateGraphEdge("action_0", "reducer_0"),
                StateGraphEdge("reducer_0", "state")
            ),
            graph.edges
        )
    }

    fun testBuildAddsUnknownActionNodeWhenAssociationMissing() {
        val graph = builder.build(StateFieldGraph(stateFieldInfo(), listOf(modification(action = null))))

        assertTrue(graph.nodes.any { it.id == "action_unknown" })
        assertTrue(graph.edges.contains(StateGraphEdge("action_unknown", "reducer_0")))
    }

    fun testBuildDeduplicatesActionNodesButKeepsDistinctReducers() {
        val action = actionInfo("SelectTabAction")
        val graph = builder.build(
            StateFieldGraph(
                stateFieldInfo(),
                listOf(
                    modification(action = action, fileName = "ReducerA.kt", filePath = "/work/ReducerA.kt", line = 20),
                    modification(action = action, fileName = "ReducerB.kt", filePath = "/work/ReducerB.kt", line = 30)
                )
            )
        )

        assertEquals(1, graph.nodes.count { it is StateActionNode && it.label == "SelectTabAction" })
        assertEquals(2, graph.nodes.count { it is StateReducerNode })
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
        fileName: String = "Reducer.kt",
        filePath: String = "/work/Reducer.kt",
        line: Int = 20
    ): StateModification {
        val usage = usage(fileName, filePath, line)
        return StateModification(
            stateField = stateFieldInfo(),
            action = action,
            reducerUsage = usage,
            modificationPointer = usage.element,
            fileName = fileName,
            filePath = filePath,
            lineNumber = line,
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

    private fun usage(
        fileName: String,
        filePath: String,
        line: Int
    ): ReduxUsage {
        val file = myFixture.configureByText(
            fileName,
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
            fileName = fileName,
            filePath = filePath,
            line = line,
            element = element.toSmartPointer()
        )
    }
}
