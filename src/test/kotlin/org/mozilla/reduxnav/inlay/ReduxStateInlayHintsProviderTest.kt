package org.mozilla.reduxnav.inlay

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class ReduxStateInlayHintsProviderTest : BasePlatformTestCase() {
    fun testResolveDeclarationFindsDataClassStateProperty() {
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
        val provider = ReduxStateInlayHintsProvider()
        val field = provider.resolveField(parameter)

        assertNotNull(field)
        assertEquals("BrowserState.selectedTabId", field!!.qualifiedPath)
        assertTrue(provider.acceptsElement(parameter))
        assertTrue(provider.acceptsElement(parameter.nameIdentifier ?: error("Expected parameter name")))
    }

    fun testResolveDeclarationFindsPropertyInsideStateClass() {
        myFixture.configureByText(
            "BrowserState.kt",
            """
            class BrowserState {
                val selectedTabId: String? = null
            }
            """.trimIndent()
        )

        val property = PsiTreeUtil.findChildrenOfType(myFixture.file, KtProperty::class.java)
            .single { it.name == "selectedTabId" }
        val provider = ReduxStateInlayHintsProvider()

        assertEquals("BrowserState.selectedTabId", provider.resolveField(property)?.qualifiedPath)
        assertTrue(provider.acceptsElement(property))
    }

    fun testInlayHintFormatsReducerAndActionCounts() {
        val provider = ReduxStateInlayHintsProvider()

        assertEquals(
            "State: 2 actions | 3 reducer changes",
            provider.buildHintText(modificationCount = 3, actionCount = 2)
        )
        assertEquals(
            "State: no known reducer changes",
            provider.buildHintText(modificationCount = 0, actionCount = 0)
        )
    }

    fun testInlayHintUsesCachedStateGraphCounts() {
        myFixture.configureByText(
            "Reducer.kt",
            """
            sealed interface BrowserAction

            data class SelectTabAction(val tabId: String) : BrowserAction
            data object CloseTabAction : BrowserAction

            data class BrowserState(
                val selectedTabId: String?,
            )

            fun reduce(state: BrowserState, action: BrowserAction): BrowserState =
                when (action) {
                    is SelectTabAction -> state.copy(selectedTabId = action.tabId)
                    CloseTabAction -> state.copy(selectedTabId = null)
                }
            """.trimIndent()
        )

        val parameter = PsiTreeUtil.findChildrenOfType(myFixture.file, KtParameter::class.java)
            .single { it.name == "selectedTabId" }
        val provider = ReduxStateInlayHintsProvider()
        val field = provider.resolveField(parameter) ?: error("Expected state field")

        assertEquals(
            "State: 2 actions | 2 reducer changes",
            provider.buildHintText(project, field)
        )
    }

    fun testInlayProviderRejectsNonStateDeclarations() {
        myFixture.configureByText(
            "Helpers.kt",
            """
            class Helper {
                val selectedTabId: String? = null
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java)
            .single { it.name == "Helper" }
        val property = PsiTreeUtil.findChildrenOfType(myFixture.file, KtProperty::class.java)
            .single { it.name == "selectedTabId" }
        val provider = ReduxStateInlayHintsProvider()

        assertFalse(provider.acceptsElement(declaration))
        assertFalse(provider.acceptsElement(property))
        assertNull(provider.resolveField(property))
    }
}
