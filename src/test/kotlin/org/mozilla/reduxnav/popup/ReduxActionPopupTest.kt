package org.mozilla.reduxnav.popup

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer
import java.awt.Point
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JPanel

class ReduxActionPopupTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            ReduxActionPopup.resetActivePopupForTests()
        } finally {
            super.tearDown()
        }
    }

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

    fun testPopupPointUsesClickCoordinates() {
        val component = JPanel()
        val event = MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0L, 0, 17, 29, 1, false)

        assertEquals(Point(17, 29), ReduxActionPopup.popupPoint(event))
    }

    fun testCreateListCapsVisibleRows() {
        val entries = (0 until 20).map { PopupEntry.Header("Item $it") }

        val list = ReduxActionPopup.createList(entries)

        assertEquals(12, list.visibleRowCount)
    }

    fun testPopupContentIsWrappedInScrollPane() {
        val entries = listOf(PopupEntry.Header("Item"))
        val list = ReduxActionPopup.createList(entries)

        val content = ReduxActionPopup.createContent(list)

        assertSame(list, content.viewport.view)
    }

    fun testTestOccurrencesUseGreenBackground() {
        val usage = usage(
            line = "dispatch(AddItemForRemoval)",
            kind = ReduxUsageKind.DISPATCH,
            filePath = "/work/project/src/test/kotlin/DownloadActionsTest.kt"
        )
        val presentation = ReduxActionPopup.usagePresentation(usage, "AddItemForRemoval", false)

        assertEquals("DownloadActionsTest.kt", usage.fileName)
        assertEquals(ReduxActionPopup.testOccurrenceBackground(usage.filePath), presentation.background)
        assertEquals("<html>dispatch(<b>AddItemForRemoval</b>)</html>", presentation.codeHtml)
    }

    fun testRegisterActivePopupCancelsPreviousPopup() {
        val first = RecordingPopupHandle()
        val second = RecordingPopupHandle()

        ReduxActionPopup.registerActivePopup(first)
        ReduxActionPopup.registerActivePopup(second)

        assertEquals(1, first.cancelCalls)
        assertEquals(0, second.cancelCalls)
    }

    private fun usage(line: String, kind: ReduxUsageKind, filePath: String? = null): ReduxUsage {
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
            fileName = File(filePath ?: file.virtualFile.path).name,
            filePath = filePath ?: file.virtualFile.path,
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

    private class RecordingPopupHandle : PopupHandle {
        var cancelCalls: Int = 0

        override fun cancel() {
            cancelCalls += 1
        }
    }
}
