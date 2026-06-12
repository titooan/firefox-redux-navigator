package org.mozilla.reduxnav

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtUserType
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.analysis.ReduxUsageFinder
import org.mozilla.reduxnav.gutter.ReduxActionLineMarkerProvider
import org.mozilla.reduxnav.model.ReduxUsageKind

class ReduxNavigatorTest : BasePlatformTestCase() {
    fun testResolvesNestedActionInsteadOfParentActionFamily() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            
            sealed class DownloadUIAction : Action {
                data object AddItemForRemoval : DownloadUIAction()
                data object RemoveItem : DownloadUIAction()
            }
            
            interface Store {
                fun dispatch(action: Action)
            }
            
            fun trigger(store: Store) {
                store.dispatch(DownloadUIAction.<caret>AddItemForRemoval)
            }
            """.trimIndent()
        )

        val action = ActionSymbolResolver().resolve(referenceAtCaret())

        assertNotNull(action)
        assertEquals("AddItemForRemoval", action!!.displayName)
        assertTrue(action.id.value.endsWith("DownloadUIAction.AddItemForRemoval"))
    }

    fun testLineMarkerAppearsOnlyForSpecificQualifiedAction() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            
            sealed class DownloadUIAction : Action {
                data object AddItemForRemoval : DownloadUIAction()
            }
            
            interface Store {
                fun dispatch(action: Action)
            }
            
            fun trigger(store: Store) {
                store.dispatch(DownloadUIAction.AddItemForRemoval)
            }
            """.trimIndent()
        )

        val qualifiedExpression = PsiTreeUtil.findChildrenOfType(myFixture.file, KtDotQualifiedExpression::class.java)
            .single { it.text == "DownloadUIAction.AddItemForRemoval" }
        val receiver = qualifiedExpression.receiverExpression as KtNameReferenceExpression
        val selector = qualifiedExpression.selectorExpression as KtNameReferenceExpression
        val provider = ReduxActionLineMarkerProvider()

        assertNull(provider.markerForElement(receiver.firstChild))
        assertNotNull(provider.markerForElement(selector.firstChild))
    }

    fun testEditorShowsOnlyOneGutterForQualifiedActionReference() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            
            sealed class DownloadUIAction : Action {
                data object AddItemForRemoval : DownloadUIAction()
            }
            
            interface Store {
                fun dispatch(action: Action)
            }
            
            fun trigger(store: Store) {
                store.dispatch(DownloadUIAction.<caret>AddItemForRemoval)
            }
            """.trimIndent()
        )

        val gutters = myFixture.findGuttersAtCaret()

        assertSize(1, gutters)
    }

    fun testGutterDoesNotAppearOnImportLines() {
        myFixture.addFileToProject(
            "sample/Actions.kt",
            """
            package sample

            sealed class DownloadUIAction {
                data object AddItemForRemoval : DownloadUIAction()
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "Usage.kt",
            """
            package sample.other

            import sample.DownloadUIAction

            fun trigger() {
                println(DownloadUIAction::class)
            }
            """.trimIndent()
        )

        val importDirective = PsiTreeUtil.findChildrenOfType(myFixture.file, KtImportDirective::class.java)
            .single { it.text.contains("DownloadUIAction") }
        val importedReference = importDirective.importedReference ?: error("Expected imported reference")
        val provider = ReduxActionLineMarkerProvider()

        assertNull(provider.markerForElement(importedReference.firstChild))
    }

    fun testGutterDoesNotAppearOnQualifiedImportSelector() {
        myFixture.addFileToProject(
            "sample/Actions.kt",
            """
            package sample

            sealed class DownloadUIAction {
                data object AddItemForRemoval : DownloadUIAction()
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "Usage.kt",
            """
            package sample.other

            import sample.DownloadUIAction.AddItemForRemoval

            fun trigger() {
                println(AddItemForRemoval::class)
            }
            """.trimIndent()
        )

        val importDirective = PsiTreeUtil.findChildrenOfType(myFixture.file, KtImportDirective::class.java)
            .single { it.text.contains("AddItemForRemoval") }
        val importedReference = importDirective.importedReference ?: error("Expected imported reference")
        val importedLeaf = PsiTreeUtil.findChildrenOfType(importedReference, KtNameReferenceExpression::class.java)
            .single { it.text == "AddItemForRemoval" }
        val provider = ReduxActionLineMarkerProvider()

        assertNull(provider.markerForElement(importedLeaf.firstChild))
    }

    fun testDeclarationWithActionNamedSupertypeIsNotTreatedAsReduxAction() {
        myFixture.configureByText(
            "HomeActivity.kt",
            """
            interface CrashActionDispatcher

            open class <caret>HomeActivity : CrashActionDispatcher
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildOfType(myFixture.file, KtObjectDeclaration::class.java)
        assertNull(declaration)

        val classNameLeaf = PsiTreeUtil.findChildOfType(myFixture.file, KtClassOrObject::class.java)
            ?.nameIdentifier
            ?: error("Expected HomeActivity declaration")
        val provider = ReduxActionLineMarkerProvider()

        assertNull(ActionSymbolResolver().resolveDeclaration(classNameLeaf))
        assertNull(provider.markerForElement(classNameLeaf))
        assertEmpty(myFixture.findGuttersAtCaret())
    }

    fun testUsageGraphContainsOnlySpecificActionUsages() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            
            sealed class DownloadUIAction : Action {
                data object AddItemForRemoval : DownloadUIAction()
                data object RemoveItem : DownloadUIAction()
            }
            
            interface Store {
                fun dispatch(action: Action)
            }
            
            fun dispatchAdd(store: Store) {
                store.dispatch(DownloadUIAction.<caret>AddItemForRemoval)
            }
            
            fun dispatchRemove(store: Store) {
                store.dispatch(DownloadUIAction.RemoveItem)
            }
            
            class DownloadMiddleware {
                fun handle(action: Action) = when (action) {
                    DownloadUIAction.AddItemForRemoval -> "middleware:add"
                    DownloadUIAction.RemoveItem -> "middleware:remove"
                    else -> "noop"
                }
            }
            
            class DownloadReducer {
                fun reduce(action: Action) = when (action) {
                    DownloadUIAction.AddItemForRemoval -> "reducer:add"
                    DownloadUIAction.RemoveItem -> "reducer:remove"
                    else -> "noop"
                }
            }
            """.trimIndent()
        )

        val action = ActionSymbolResolver().resolve(referenceAtCaret()) ?: error("Action should resolve")
        val graph = ReduxUsageFinder(project).buildGraph(action)

        assertSize(3, graph.usages)
        assertEmpty(graph.usages.filter { it.displayText.contains("RemoveItem") })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.DISPATCH })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.MIDDLEWARE })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.REDUCER })
    }

    fun testUsageGraphForNestedDataClassContainsOnlySpecificActionUsages() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            
            sealed class DownloadUIAction : Action {
                data class AddItemForRemoval(val id: String) : DownloadUIAction()
                data class RemoveItem(val id: String) : DownloadUIAction()
            }
            
            interface Store {
                fun dispatch(action: Action)
            }
            
            fun dispatchAdd(store: Store) {
                store.dispatch(DownloadUIAction.<caret>AddItemForRemoval("a"))
            }
            
            fun dispatchRemove(store: Store) {
                store.dispatch(DownloadUIAction.RemoveItem("b"))
            }
            
            class DownloadMiddleware {
                fun handle(action: Action) = when (action) {
                    is DownloadUIAction.AddItemForRemoval -> "middleware:add"
                    is DownloadUIAction.RemoveItem -> "middleware:remove"
                    else -> "noop"
                }
            }
            
            class DownloadReducer {
                fun reduce(action: Action) = when (action) {
                    is DownloadUIAction.AddItemForRemoval -> "reducer:add"
                    is DownloadUIAction.RemoveItem -> "reducer:remove"
                    else -> "noop"
                }
            }
            """.trimIndent()
        )

        val action = ActionSymbolResolver().resolve(referenceAtCaret()) ?: error("Action should resolve")
        val graph = ReduxUsageFinder(project).buildGraph(action)

        assertEquals("AddItemForRemoval", action.displayName)
        assertSize(3, graph.usages)
        assertEmpty(graph.usages.filter { it.displayText.contains("RemoveItem") })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.DISPATCH })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.MIDDLEWARE })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.REDUCER })
    }

    fun testGutterAppearsOnDeclarationNameNotOnSupertypeReference() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            sealed class DownloadUIAction : Action {
                data object ExitEditMode : DownloadUIAction()
            }
            """.trimIndent()
        )

        val objectDecl = PsiTreeUtil.findChildrenOfType(myFixture.file, KtObjectDeclaration::class.java)
            .single { it.name == "ExitEditMode" }
        val provider = ReduxActionLineMarkerProvider()

        assertNotNull("Expected gutter on ExitEditMode declaration name",
            provider.markerForElement(objectDecl.nameIdentifier!!))

        val supertypeLeaf = PsiTreeUtil.getDeepestFirst(objectDecl.superTypeListEntries.first())
        assertNull("Expected no gutter on DownloadUIAction supertype reference",
            provider.markerForElement(supertypeLeaf))
    }

    fun testIsPatternShowsGutterOnlyOnQualifiedActionSelector() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            sealed class DownloadUIAction : Action {
                data class SettingsIconClicked(val tabId: String) : DownloadUIAction()
            }

            fun handle(action: Action) = when (action) {
                is DownloadUIAction.SettingsIconClicked -> action.tabId
                else -> ""
            }
            """.trimIndent()
        )

        val userType = PsiTreeUtil.findChildrenOfType(myFixture.file, KtUserType::class.java)
            .single { it.text == "DownloadUIAction.SettingsIconClicked" }
        val references = PsiTreeUtil.findChildrenOfType(userType, KtNameReferenceExpression::class.java)
        val receiver = references.single { it.text == "DownloadUIAction" }
        val selector = references.single { it.text == "SettingsIconClicked" }
        val provider = ReduxActionLineMarkerProvider()

        assertNull(provider.markerForElement(receiver.firstChild))
        assertNotNull(provider.markerForElement(selector.firstChild))
    }

    fun testActionVariablesDoNotShowGuttersInMiddlewareFlow() {
        myFixture.configureByText(
            "DownloadNavigationMiddleware.kt",
            """
            interface Middleware<S, A>
            interface Store<S, A>

            sealed class DownloadUIAction {
                data object SettingsIconClicked : DownloadUIAction()
            }

            class DownloadUIState

            class DownloadNavigationMiddleware : Middleware<DownloadUIState, DownloadUIAction> {
                fun invoke(
                    store: Store<DownloadUIState, DownloadUIAction>,
                    next: (DownloadUIAction) -> Unit,
                    action: DownloadUIAction,
                ) {
                    next(action)
                    when (action) {
                        is DownloadUIAction.SettingsIconClicked -> Unit
                    }
                }
            }
            """.trimIndent()
        )

        val references = PsiTreeUtil.findChildrenOfType(myFixture.file, KtNameReferenceExpression::class.java)
        val actionReferences = references.filter { it.text == "action" }
        val provider = ReduxActionLineMarkerProvider()

        assertEquals(2, actionReferences.size)
        actionReferences.forEach { reference ->
            assertNull(provider.markerForElement(reference.firstChild))
        }
    }

    fun testGutterOnDeclarationResolveShowsOnlyThatActionUsages() {
        myFixture.configureByText(
            "DownloadActions.kt",
            """
            interface Action
            sealed class DownloadUIAction : Action {
                data object ExitEditMode : DownloadUIAction()
                data object AddItem : DownloadUIAction()
            }
            interface Store { fun dispatch(action: Action) }
            fun trigger(store: Store) {
                store.dispatch(DownloadUIAction.ExitEditMode)
            }
            class DownloadMiddleware {
                fun handle(action: Action) = when (action) {
                    DownloadUIAction.ExitEditMode -> "exit"
                    DownloadUIAction.AddItem -> "add"
                    else -> "noop"
                }
            }
            class DownloadReducer {
                fun reduce(action: Action) = when (action) {
                    DownloadUIAction.ExitEditMode -> "exit"
                    DownloadUIAction.AddItem -> "add"
                    else -> "noop"
                }
            }
            """.trimIndent()
        )

        val objectDecl = PsiTreeUtil.findChildrenOfType(myFixture.file, KtObjectDeclaration::class.java)
            .single { it.name == "ExitEditMode" }
        val action = ActionSymbolResolver().resolveDeclaration(objectDecl)
            ?: error("Expected action info from declaration")
        val graph = ReduxUsageFinder(project).buildGraph(action)

        assertEquals("ExitEditMode", action.displayName)
        assertSize(3, graph.usages)
        assertEmpty(graph.usages.filter { it.displayText.contains("AddItem") })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.DISPATCH })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.MIDDLEWARE })
        assertEquals(1, graph.usages.count { it.kind == ReduxUsageKind.REDUCER })
    }

    private fun referenceAtCaret(): KtNameReferenceExpression {
        return PsiTreeUtil.getParentOfType(myFixture.file.findReferenceAt(myFixture.caretOffset)?.element, KtNameReferenceExpression::class.java, false)
            ?: error("Expected a Kotlin name reference at caret")
    }
}
