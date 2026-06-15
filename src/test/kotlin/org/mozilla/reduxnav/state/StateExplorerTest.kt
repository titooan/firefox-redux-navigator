package org.mozilla.reduxnav.state

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtValueArgument

class StateExplorerTest : BasePlatformTestCase() {
    fun testResolverFindsStatePropertyDeclaration() {
        myFixture.configureByText(
            "BrowserState.kt",
            """
            data class BrowserState(
                val selectedTabId: String?,
                val tabs: List<String>,
            )
            """.trimIndent()
        )

        val parameter = PsiTreeUtil.findChildrenOfType(myFixture.file, KtParameter::class.java)
            .single { it.name == "selectedTabId" }

        val field = StateFieldResolver().resolve(parameter)

        assertNotNull(field)
        assertEquals("BrowserState.selectedTabId", field!!.qualifiedPath)
        assertEquals("selectedTabId", field.fieldName)
        assertEquals("BrowserState", field.stateClassName)
    }

    fun testResolverFindsNamedCopyArgument() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState): BrowserState =
                state.copy(
                    selectedTabId = "new-tab"
                )
            """.trimIndent()
        )

        val argument = PsiTreeUtil.findChildrenOfType(myFixture.file, KtValueArgument::class.java)
            .single { it.getArgumentName()?.asName?.identifier == "selectedTabId" }

        val field = StateFieldResolver().resolve(argument)

        assertNotNull(field)
        assertEquals("BrowserState.selectedTabId", field!!.qualifiedPath)
    }

    fun testFinderDetectsCopyAndAssociatesActions() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction

            data class SelectTabAction(val tabId: String) : BrowserAction
            data object CloseTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
                val tabs: List<String>,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction ->
                        state.copy(
                            selectedTabId = action.tabId
                        )

                    CloseTabAction ->
                        state.copy(
                            selectedTabId = null
                        )
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(listOf("CloseTabAction", "SelectTabAction"), graph.modifications.mapNotNull { it.action?.displayName }.sorted())
        assertEquals(
            listOf("selectedTabId = action.tabId", "selectedTabId = null"),
            graph.modifications.map { it.snippet }.sorted()
        )
    }

    fun testFinderDetectsConstructorRebuild() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
                val tabs: List<String>,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction ->
                        BrowserState(
                            selectedTabId = action.tabId,
                            tabs = state.tabs,
                        )
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertEquals("SelectTabAction", graph.modifications.single().action?.displayName)
        assertEquals("selectedTabId = action.tabId,", graph.modifications.single().snippet)
    }

    fun testFinderTracksNestedCopyPathForParentField() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class TabState(
                val selectedTabId: String?,
            )

            data class BrowserState(
                val tabState: TabState,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction ->
                        state.copy(
                            tabState = state.tabState.copy(
                                selectedTabId = action.tabId,
                            )
                        )
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("tabState")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertEquals("BrowserState.tabState.selectedTabId", graph.modifications.single().stateField.qualifiedPath)
        assertEquals("tabState = state.tabState.copy(", graph.modifications.single().snippet)
    }

    fun testFinderFallsBackToTopLevelFieldWhenNestedCopyIsAmbiguous() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class TabState(
                val selectedTabId: String?,
                val lastAccess: Long,
            )

            data class BrowserState(
                val tabState: TabState,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction ->
                        state.copy(
                            tabState = state.tabState.copy(
                                selectedTabId = action.tabId,
                                lastAccess = 42L,
                            )
                        )
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("tabState")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertEquals("BrowserState.tabState", graph.modifications.single().stateField.qualifiedPath)
        assertEquals("tabState = state.tabState.copy(", graph.modifications.single().snippet)
    }

    fun testFinderTracksNestedConstructorPathForParentField() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class TabState(
                val selectedTabId: String?,
            )

            data class BrowserState(
                val tabState: TabState,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction ->
                        state.copy(
                            tabState = TabState(
                                selectedTabId = action.tabId,
                            )
                        )
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("tabState")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertEquals("BrowserState.tabState.selectedTabId", graph.modifications.single().stateField.qualifiedPath)
    }

    fun testFinderAssociatesActionFromLocalHelperFunctionCalledInWhenBranch() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction
            data object CloseTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState {
                fun updateSelection(): BrowserState =
                    state.copy(selectedTabId = (action as SelectTabAction).tabId)

                return when (action) {
                    is SelectTabAction -> updateSelection()
                    CloseTabAction -> state.copy(selectedTabId = null)
                }
            }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(2, graph.modifications.size)
        assertEquals(
            listOf("CloseTabAction", "SelectTabAction"),
            graph.modifications.mapNotNull { it.action?.displayName }.sorted()
        )
    }

    fun testFinderAssociatesActionThroughChainedLocalHelpers() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState {
                fun commit(): BrowserState =
                    state.copy(selectedTabId = (action as SelectTabAction).tabId)

                fun updateSelection(): BrowserState = commit()

                return when (action) {
                    is SelectTabAction -> updateSelection()
                }
            }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertEquals("SelectTabAction", graph.modifications.single().action?.displayName)
    }

    fun testFinderAssociatesActionFromLocalLambdaHelperCalledInIfBranch() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState {
                val updateSelection = {
                    state.copy(selectedTabId = (action as SelectTabAction).tabId)
                }

                return if (action is SelectTabAction) {
                    updateSelection()
                } else {
                    state
                }
            }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertEquals("SelectTabAction", graph.modifications.single().action?.displayName)
    }

    fun testFinderKeepsUnknownActionForSharedHelperAcrossMultipleBranches() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data object SelectTabAction : BrowserAction
            data object CloseTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState {
                fun clearSelection(): BrowserState =
                    state.copy(selectedTabId = null)

                return when (action) {
                    SelectTabAction -> clearSelection()
                    CloseTabAction -> clearSelection()
                }
            }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertNull(graph.modifications.single().action)
    }

    fun testFinderKeepsUnknownActionWhenAssociationIsAmbiguous() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data object SelectTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                state.copy(
                    selectedTabId = "fallback"
                )
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val graph = StateModificationFinder(project).findModifications(field)

        assertEquals(1, graph.modifications.size)
        assertNull(graph.modifications.single().action)
    }

    fun testStateModificationCacheReusesGraphUntilPsiChanges() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data object SelectTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    SelectTabAction -> state.copy(selectedTabId = "tab")
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("selectedTabId")
        val cache = StateModificationCache(project)

        val first = cache.getGraph(field)
        val second = cache.getGraph(field)

        assertSame(first, second)

        myFixture.addFileToProject(
            "Touch.kt",
            """
            fun touch() = Unit
            """.trimIndent()
        )

        val third = cache.getGraph(field)

        assertNotSame(second, third)
    }

    fun testProjectAnalysisCacheOnlyKeepsReducerCandidateFiles() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data object SelectTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    SelectTabAction -> state.copy(selectedTabId = "tab")
                }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "PlainState.kt",
            """
            data class PlainState(
                val selectedTabId: String?,
            )

            fun render(state: PlainState): String? = state.selectedTabId
            """.trimIndent()
        )

        val cache = StateProjectAnalysisCache.getInstance(project)
        val candidateNames = cache.reducerCandidateFiles().map { it.name }

        assertEquals(listOf("Reducer.kt"), candidateNames)
    }

    fun testProjectAnalysisCacheReusesExtractedModificationsUntilPsiChanges() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data object SelectTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    SelectTabAction -> state.copy(selectedTabId = "tab")
                }
            """.trimIndent()
        )

        val cache = StateProjectAnalysisCache.getInstance(project)

        val first = cache.allModifications()
        val second = cache.allModifications()

        assertSame(first, second)

        myFixture.addFileToProject(
            "ExtraReducer.kt",
            """
            sealed interface BrowserAction
            data object CloseTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduceAgain(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    CloseTabAction -> state.copy(selectedTabId = null)
                }
            """.trimIndent()
        )

        val third = cache.allModifications()

        assertNotSame(second, third)
        assertEquals(2, third.size)
    }

    fun testStateModificationFinderReusesSharedAnalysisAcrossDifferentFields() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction
            data class SelectTabAction(val tabId: String) : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
                val lastOpenedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction -> state.copy(
                        selectedTabId = action.tabId,
                        lastOpenedTabId = action.tabId,
                    )
                }
            """.trimIndent()
        )

        val cache = StateProjectAnalysisCache.getInstance(project)
        val finder = StateModificationFinder(project, cache)

        val selectedTabField = resolveFieldByName("selectedTabId")
        val lastOpenedField = resolveFieldByName("lastOpenedTabId")

        val before = cache.allModifications()
        val firstGraph = finder.findModifications(selectedTabField)
        val secondGraph = finder.findModifications(lastOpenedField)
        val after = cache.allModifications()

        assertSame(before, after)
        assertEquals(1, firstGraph.modifications.size)
        assertEquals(1, secondGraph.modifications.size)
    }

    fun testFinderDoesNotLeakSameNamedFieldsAcrossDifferentStateClasses() {
        myFixture.configureByText(
            "DownloadReducer.kt",
            """
            sealed interface DownloadAction
            data object ReplaceDownloads : DownloadAction

            data class DownloadUIState(
                val items: List<String>,
            )

            fun reduceDownloads(state: DownloadUIState, action: DownloadAction): DownloadUIState =
                when (action) {
                    ReplaceDownloads -> state.copy(items = listOf("download"))
                }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "HistoryReducer.kt",
            """
            sealed interface HistoryAction
            data object ReplaceHistory : HistoryAction

            data class HistoryMetadataGroupFragmentState(
                val items: List<String>,
            )

            fun reduceHistory(
                state: HistoryMetadataGroupFragmentState,
                action: HistoryAction,
            ): HistoryMetadataGroupFragmentState =
                when (action) {
                    ReplaceHistory -> state.copy(items = listOf("history"))
                }
            """.trimIndent()
        )

        val field = resolveFieldByName("items")
        val graph = StateModificationFinder(project).findModifications(field)
        val modification = graph.modifications.single()

        assertEquals(1, graph.modifications.size)
        assertEquals("DownloadUIState.items", modification.stateField.qualifiedPath)
        assertEquals("DownloadReducer.kt", modification.fileName)
        assertTrue(modification.snippet.contains("download"))
        assertFalse(modification.snippet.contains("history"))
    }

    private fun resolveFieldByName(name: String): StateFieldInfo {
        val parameter = PsiTreeUtil.findChildrenOfType(myFixture.file, KtParameter::class.java)
            .single { it.name == name }
        return StateFieldResolver().resolve(parameter) ?: error("Expected state field info")
    }
}
