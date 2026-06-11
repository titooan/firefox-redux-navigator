package org.mozilla.reduxnav.inlay

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.analysis.ReduxActionGraphCache
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer
import org.mozilla.reduxnav.settings.ReduxNavigatorSettingsService
import com.intellij.openapi.components.service

class ReduxActionInlayHintsProviderTest : BasePlatformTestCase() {
    fun testResolveDeclarationFindsTopLevelActionDeclarationsByNameLeaf() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            data class AddTabAction(val tabId: String) : Action
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "AddTabAction" }
        val nameLeaf = declaration.nameIdentifier ?: error("Expected a declaration name")
        val action = ActionSymbolResolver().resolveDeclaration(nameLeaf)

        assertNotNull(action)
        assertEquals("AddTabAction", action!!.displayName)
        assertTrue(action.id.value.endsWith("AddTabAction"))
    }

    fun testResolveDeclarationFindsNestedSealedActionVariantsByNameLeaf() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            sealed class TabsTrayAction : Action {
                data object CloseTray : TabsTrayAction()
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "CloseTray" }
        val nameLeaf = declaration.nameIdentifier ?: error("Expected a declaration name")
        val action = ActionSymbolResolver().resolveDeclaration(nameLeaf)

        assertNotNull(action)
        assertEquals("CloseTray", action!!.displayName)
        assertTrue(action.id.value.endsWith("TabsTrayAction.CloseTray"))
    }

    fun testInlayHintFormatsRelevantUsageCounts() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            sealed class DownloadUIAction : Action {
                data object AddItemForRemoval : DownloadUIAction()
            }

            interface Store {
                fun dispatch(action: Action)
            }

            fun dispatchAdd(store: Store) {
                store.dispatch(DownloadUIAction.AddItemForRemoval)
            }

            fun dispatchAddAgain(store: Store) {
                store.dispatch(DownloadUIAction.AddItemForRemoval)
            }

            class DownloadMiddleware {
                fun handle(action: Action) = when (action) {
                    DownloadUIAction.AddItemForRemoval -> "middleware:add"
                    else -> "noop"
                }

                fun handleAgain(action: Action) = when (action) {
                    DownloadUIAction.AddItemForRemoval -> "middleware:again"
                    else -> "noop"
                }
            }

            class DownloadReducer {
                fun reduce(action: Action) = when (action) {
                    DownloadUIAction.AddItemForRemoval -> "reducer:add"
                    else -> "noop"
                }
            }

            fun unrelated() {
                println(DownloadUIAction::class)
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "AddItemForRemoval" }
        val action = ActionSymbolResolver().resolveDeclaration(declaration)
            ?: error("Expected action info from declaration")
        val provider = ReduxActionInlayHintsProvider()

        assertTrue(provider.acceptsElement(declaration))
        assertEquals(
            "Redux: 2 dispatches | 2 middlewares | 1 reducer",
            provider.buildHintText(project, action)
        )
    }

    fun testInlayHintShowsEmptyStateWhenNoRelevantUsagesExist() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            data object RefreshAction : Action
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "RefreshAction" }
        val action = ActionSymbolResolver().resolveDeclaration(declaration)
            ?: error("Expected action info from declaration")
        val provider = ReduxActionInlayHintsProvider()

        assertEquals(
            "Redux: no related usages found",
            provider.buildHintText(project, action)
        )
    }

    fun testInlayHintCanExcludeTestFilesFromCounts() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            data object RefreshAction : Action
            """.trimIndent()
        )
        val provider = ReduxActionInlayHintsProvider()
        val settings = com.intellij.openapi.components.service<ReduxNavigatorSettingsService>()

        settings.setIncludeTestFilesInLens(false)
        try {
            assertEquals(
                "Redux: 1 dispatch | 1 reducer",
                provider.buildHintText(
                    listOf(
                        usage(ReduxUsageKind.DISPATCH, "/Users/titouan/project/app/src/main/java/Foo.kt"),
                        usage(ReduxUsageKind.MIDDLEWARE, "/Users/titouan/project/app/src/test/java/FooTest.kt"),
                        usage(ReduxUsageKind.REDUCER, "/Users/titouan/project/app/src/main/java/Bar.kt")
                    ),
                    includeTestFiles = settings.includeTestFilesInLens
                )
            )
        } finally {
            settings.setIncludeTestFilesInLens(true)
        }
    }

    fun testInlayProviderRejectsNonActionDeclarations() {
        myFixture.configureByText(
            "Actions.kt",
            """
            class Helper
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "Helper" }
        val provider = ReduxActionInlayHintsProvider()

        assertFalse(provider.acceptsElement(declaration))
    }

    fun testGraphCacheReusesGraphUntilPsiChanges() {
        myFixture.configureByText(
            "Actions.kt",
            """
            interface Action

            data object RefreshAction : Action

            fun dispatch(action: Action) {
                when (action) {
                    RefreshAction -> Unit
                }
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "RefreshAction" }
        val action = ActionSymbolResolver().resolveDeclaration(declaration)
            ?: error("Expected action info from declaration")
        val cache = ReduxActionGraphCache(project)

        val first = cache.getGraph(action)
        val second = cache.getGraph(action)

        assertSame(first, second)

        myFixture.addFileToProject(
            "Touch.kt",
            """
            fun touch() = Unit
            """.trimIndent()
        )

        val third = cache.getGraph(action)

        assertNotSame(second, third)
    }

    private fun usage(kind: ReduxUsageKind, filePath: String): ReduxUsage =
        ReduxUsage(
            kind = kind,
            displayText = "RefreshAction",
            fileName = filePath.substringAfterLast('/'),
            filePath = filePath,
            line = 1,
            element = myFixture.file.toSmartPointer()
        )
}
