package org.mozilla.reduxnav.popup

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer

class ReduxActionPopupTest : BasePlatformTestCase() {
    fun testBuildEntriesGroupsUsagesInExpectedSections() {
        val dispatch = usage("dispatch(AddItemForRemoval)", ReduxUsageKind.DISPATCH)
        val middleware = usage("AddItemForRemoval -> middleware", ReduxUsageKind.MIDDLEWARE)
        val reducer = usage("AddItemForRemoval -> reducer", ReduxUsageKind.REDUCER)
        val other = usage("val action = AddItemForRemoval", ReduxUsageKind.OTHER)

        val entries = ReduxActionPopup.buildEntries(listOf(reducer, other, dispatch, middleware))

        assertEquals(
            listOf(
                "Dispatches (1)",
                "dispatch(AddItemForRemoval)",
                "Middlewares (1)",
                "AddItemForRemoval -> middleware",
                "Reducers (1)",
                "AddItemForRemoval -> reducer",
                "Other references (1)",
                "val action = AddItemForRemoval"
            ),
            entries.map { entryLabel(it) }
        )
    }

    fun testBuildEntriesKeepsEmptySectionsForMissingUsageKinds() {
        val dispatch = usage("dispatch(AddItemForRemoval)", ReduxUsageKind.DISPATCH)

        val entries = ReduxActionPopup.buildEntries(listOf(dispatch))

        assertEquals(
            listOf(
                "Dispatches (1)",
                "dispatch(AddItemForRemoval)",
                "Middlewares (0)",
                "Reducers (0)",
                "Other references (0)"
            ),
            entries.map { entryLabel(it) }
        )
    }

    private fun usage(line: String, kind: ReduxUsageKind): ReduxUsage {
        val file = myFixture.configureByText(
            "${kind.name.lowercase()}.kt",
            """
            fun sample() {
                $line
            }
            """.trimIndent()
        )
        val target = file.text.indexOf(line).takeIf { it >= 0 } ?: error("Expected test usage line")
        val element = file.findElementAt(target) ?: error("Expected PSI element at usage line")
        return ReduxUsage(
            kind = kind,
            displayText = line,
            filePath = file.virtualFile.path,
            line = 2,
            element = element.toSmartPointer()
        )
    }

    private fun entryLabel(entry: PopupEntry): String {
        return when (entry) {
            is PopupEntry.Header -> entry.text
            is PopupEntry.UsageEntry -> entry.usage.displayText
        }
    }
}
