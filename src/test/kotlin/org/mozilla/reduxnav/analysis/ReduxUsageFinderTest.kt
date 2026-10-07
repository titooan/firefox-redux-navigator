package org.mozilla.reduxnav.analysis

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.model.ReduxUsageActionMatch
import org.mozilla.reduxnav.model.ReduxUsageKind

class ReduxUsageFinderTest : BasePlatformTestCase() {
    fun testSealedActionFindsMiddlewareForDirectAndNestedChildren() {
        myFixture.addFileToProject(
            "AppAction.kt",
            """
            sealed class AppAction {
                sealed class MessagingAction : AppAction() {
                    data object Restore : MessagingAction()
                    data class Evaluate(val value: String) : MessagingAction()
                    sealed class MicrosurveyAction : MessagingAction() {
                        data object Started : MicrosurveyAction()
                    }
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "MessagingMiddleware.kt",
            """
            class MessagingMiddleware {
                fun handle(action: AppAction) {
                    when (action) {
                        is AppAction.MessagingAction -> Unit
                        is AppAction.MessagingAction.Evaluate -> Unit
                        is AppAction.MessagingAction.MicrosurveyAction.Started -> Unit
                        else -> Unit
                    }
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "MessagingHandlers.kt",
            """
            class MessagingDispatcher {
                fun update(store: Store) {
                    store.dispatch(AppAction.MessagingAction.Evaluate("value"))
                }
            }
            class MessagingReducer {
                fun reduce(action: AppAction) {
                    when (action) {
                        is AppAction.MessagingAction.Evaluate -> Unit
                        else -> Unit
                    }
                }
            }
            class Store { fun dispatch(action: Any) = Unit }
            """.trimIndent()
        )

        val graph = ReduxUsageFinder(project).computeGraph(resolveAction("AppAction.kt", "MessagingAction"))
        val middlewareUsages = graph.usages.filter { it.kind == ReduxUsageKind.MIDDLEWARE }

        assertEquals(3, middlewareUsages.size)
        assertEquals(
            setOf(
                ReduxUsageActionMatch("MessagingAction", coversDescendants = true),
                ReduxUsageActionMatch("MessagingAction.Evaluate"),
                ReduxUsageActionMatch("MessagingAction.MicrosurveyAction.Started")
            ),
            middlewareUsages.flatMap { it.handledActions }.toSet()
        )
        assertFalse(graph.usages.any { it.displayText.contains(": MessagingAction()") })
        assertFalse(graph.usages.any { it.displayText.contains(": MicrosurveyAction()") })
        assertTrue(
            graph.usages.joinToString { "${it.kind}:${it.displayText}:${it.handledActions}" },
            graph.usages.any { usage ->
                usage.kind == ReduxUsageKind.DISPATCH &&
                    usage.handledActions.map { it.actionName } == listOf("MessagingAction.Evaluate")
            }
        )
        assertTrue(
            graph.usages.any { usage ->
                usage.kind == ReduxUsageKind.REDUCER &&
                    usage.handledActions.map { it.actionName } == listOf("MessagingAction.Evaluate")
            }
        )
        val childMiddleware = middlewareUsages.single { it.displayText.contains("MessagingAction.Evaluate") }
        assertEquals(listOf("MessagingAction.Evaluate"), childMiddleware.handledActions.map { it.actionName })
    }

    fun testChildMatchesOnSameLineAreMergedWithBothActionLabels() {
        myFixture.addFileToProject(
            "Actions.kt",
            """
            sealed class ParentAction {
                data object First : ParentAction()
                data object Second : ParentAction()
            }
            class ParentMiddleware {
                fun handle(action: ParentAction) {
                    when (action) {
                        is ParentAction.First,
                        is ParentAction.Second -> Unit
                        else -> Unit
                    }
                }
            }
            class ParentDispatcher {
                fun run(store: Store) { store.dispatch(ParentAction.First); store.dispatch(ParentAction.Second) }
            }
            class Store { fun dispatch(action: Any) = Unit }
            """.trimIndent()
        )

        val graph = ReduxUsageFinder(project).computeGraph(resolveAction("Actions.kt", "ParentAction"))
        val middlewareUsages = graph.usages.filter { it.kind == ReduxUsageKind.MIDDLEWARE }

        assertEquals(1, middlewareUsages.size)
        assertEquals(
            setOf("ParentAction.First", "ParentAction.Second"),
            middlewareUsages.single().handledActions.map { it.actionName }.toSet()
        )

        val dispatchUsages = graph.usages.filter { it.kind == ReduxUsageKind.DISPATCH }
        assertEquals(2, dispatchUsages.size)
        assertEquals(
            setOf(setOf("ParentAction.First"), setOf("ParentAction.Second")),
            dispatchUsages.map { usage -> usage.handledActions.map { it.actionName }.toSet() }.toSet()
        )
    }

    fun testNonSealedActionHasNoHierarchyAttribution() {
        myFixture.addFileToProject(
            "OrdinaryAction.kt",
            """
            class OrdinaryAction
            class OrdinaryMiddleware {
                fun handle(action: OrdinaryAction) {
                    when (action) {
                        is OrdinaryAction -> Unit
                    }
                }
            }
            """.trimIndent()
        )

        val graph = ReduxUsageFinder(project).computeGraph(resolveAction("OrdinaryAction.kt", "OrdinaryAction"))

        assertTrue(graph.usages.isNotEmpty())
        assertTrue(graph.usages.all { it.handledActions.isEmpty() })
    }

    private fun resolveAction(fileName: String, actionName: String) = run {
        val file = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(fileName))
            ?: error("Could not load $fileName")
        val declaration = PsiTreeUtil.findChildrenOfType(file, KtClassOrObject::class.java)
            .firstOrNull { it.name == actionName } ?: error("Could not locate $actionName")
        ActionSymbolResolver().resolveDeclaration(declaration) ?: error("Could not resolve $actionName")
    }
}
