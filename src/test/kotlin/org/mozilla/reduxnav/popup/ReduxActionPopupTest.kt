package org.mozilla.reduxnav.popup

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mozilla.reduxnav.model.ActionId
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.toSmartPointer
import java.awt.Point
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu

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

        val entries = ReduxActionPopup.buildEntries(
            listOf(reducer, other, dispatch, middleware),
            filterState = PopupFilterState()
        )

        assertEquals(
            listOf(
                "▼ Dispatches (1)",
                "dispatch(AddItemForRemoval)",
                "▼ Middlewares (1)",
                "AddItemForRemoval -> middleware",
                "▼ Reducers (1)",
                "AddItemForRemoval -> reducer",
                "▼ Other references (1)",
                "val action = AddItemForRemoval"
            ),
            entries.map { entryLabel(it) }
        )
    }

    fun testBuildEntriesKeepsEmptySectionsForMissingUsageKinds() {
        val dispatch = usage("dispatch(AddItemForRemoval)", ReduxUsageKind.DISPATCH)

        val entries = ReduxActionPopup.buildEntries(
            listOf(dispatch),
            filterState = PopupFilterState()
        )

        assertEquals(
            listOf(
                "▼ Dispatches (1)",
                "dispatch(AddItemForRemoval)",
                "▼ Middlewares (0)",
                "▼ Reducers (0)",
                "▼ Other references (0)"
            ),
            entries.map { entryLabel(it) }
        )
    }

    fun testBuildEntriesCanHideUsageKinds() {
        val dispatch = usage("dispatch(AddItemForRemoval)", ReduxUsageKind.DISPATCH)
        val middleware = usage("AddItemForRemoval -> middleware", ReduxUsageKind.MIDDLEWARE)
        val reducer = usage("AddItemForRemoval -> reducer", ReduxUsageKind.REDUCER)

        val entries = ReduxActionPopup.buildEntries(
            listOf(dispatch, middleware, reducer),
            filterState = PopupFilterState(
                visibleKinds = linkedSetOf(ReduxUsageKind.DISPATCH, ReduxUsageKind.REDUCER)
            )
        )

        assertEquals(
            listOf(
                "▼ Dispatches (1)",
                "dispatch(AddItemForRemoval)",
                "▼ Reducers (1)",
                "AddItemForRemoval -> reducer"
            ),
            entries.map { entryLabel(it) }
        )
    }

    fun testBuildEntriesCanRestrictToProductionFiles() {
        val prodDispatch = usage(
            line = "dispatch(AddItemForRemoval)",
            kind = ReduxUsageKind.DISPATCH,
            filePath = "/work/project/src/main/kotlin/DownloadStore.kt"
        )
        val testDispatch = usage(
            line = "dispatch(AddItemForRemoval)",
            kind = ReduxUsageKind.DISPATCH,
            filePath = "/work/project/src/test/kotlin/DownloadStoreTest.kt"
        )

        val entries = ReduxActionPopup.buildEntries(
            listOf(prodDispatch, testDispatch),
            filterState = PopupFilterState(fileScope = UsageFileScope.PRODUCTION)
        )

        assertEquals(
            listOf(
                "▼ Dispatches (1)",
                "dispatch(AddItemForRemoval)",
                "▼ Middlewares (0)",
                "▼ Reducers (0)",
                "▼ Other references (0)"
            ),
            entries.map { entryLabel(it) }
        )
    }

    fun testBuildEntriesCanRestrictToTestFiles() {
        val prodDispatch = usage(
            line = "dispatch(AddItemForRemoval)",
            kind = ReduxUsageKind.DISPATCH,
            filePath = "/work/project/src/main/kotlin/DownloadStore.kt"
        )
        val testMiddleware = usage(
            line = "AddItemForRemoval -> middleware",
            kind = ReduxUsageKind.MIDDLEWARE,
            filePath = "/work/project/src/test/kotlin/DownloadStoreTest.kt"
        )

        val entries = ReduxActionPopup.buildEntries(
            listOf(prodDispatch, testMiddleware),
            filterState = PopupFilterState(fileScope = UsageFileScope.TEST)
        )

        assertEquals(
            listOf(
                "▼ Dispatches (0)",
                "▼ Middlewares (1)",
                "AddItemForRemoval -> middleware",
                "▼ Reducers (0)",
                "▼ Other references (0)"
            ),
            entries.map { entryLabel(it) }
        )
    }

    fun testBuildEntriesCanCollapseSection() {
        val dispatch = usage("dispatch(AddItemForRemoval)", ReduxUsageKind.DISPATCH)

        val entries = ReduxActionPopup.buildEntries(
            listOf(dispatch),
            filterState = PopupFilterState(
                collapsedKinds = setOf(ReduxUsageKind.DISPATCH)
            )
        )

        assertEquals(
            listOf(
                "▶ Dispatches (1)",
                "▼ Middlewares (0)",
                "▼ Reducers (0)",
                "▼ Other references (0)"
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
        val entries = (0 until 20).map { PopupEntry.Header(ReduxUsageKind.OTHER, "Item $it", expanded = true) }

        val list = ReduxActionPopup.createList(entries)

        assertEquals(12, list.visibleRowCount)
    }

    fun testPopupContentIsWrappedInScrollPane() {
        val entries = listOf(PopupEntry.Header(ReduxUsageKind.OTHER, "Item", expanded = true))
        val list = ReduxActionPopup.createList(entries)

        val content = ReduxActionPopup.createContent(list)

        assertSame(list, content.viewport.view)
    }

    fun testOutsideClickCancellationIgnoresClicksInsidePopupContent() {
        val popupContent = JPanel()
        val child = JLabel("child")
        popupContent.add(child)
        val event = MouseEvent(child, MouseEvent.MOUSE_PRESSED, 0L, 0, 3, 4, 1, false)

        assertFalse(ReduxActionPopup.shouldCancelForOutsideClick(popupContent, event))
    }

    fun testOutsideClickCancellationClosesOnExternalClicks() {
        val popupContent = JPanel()
        val outside = JPanel()
        val event = MouseEvent(outside, MouseEvent.MOUSE_PRESSED, 0L, 0, 3, 4, 1, false)

        assertTrue(ReduxActionPopup.shouldCancelForOutsideClick(popupContent, event))
    }

    fun testOutsideClickCancellationIgnoresComboPopupSelectionsFromToolbar() {
        val popupContent = JPanel()
        val comboBox = JComboBox(arrayOf("All files", "Test files"))
        popupContent.add(comboBox)

        val comboPopup = JPopupMenu().apply {
            invoker = comboBox
        }
        val menuItem = JLabel("Test files")
        comboPopup.add(menuItem)

        val event = MouseEvent(menuItem, MouseEvent.MOUSE_PRESSED, 0L, 0, 3, 4, 1, false)

        assertFalse(ReduxActionPopup.shouldCancelForOutsideClick(popupContent, event))
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

    fun testLoadingEntriesShowsPlaceholderHeader() {
        assertEquals(
            listOf("▼ Loading Redux flow..."),
            ReduxActionPopup.loadingEntries().map { entryLabel(it) }
        )
    }

    fun testTogglingHeaderCollapseUpdatesState() {
        val expanded = PopupFilterState()
        val collapsed = ReduxActionPopup.toggleSection(expanded, ReduxUsageKind.MIDDLEWARE)
        val reopened = ReduxActionPopup.toggleSection(collapsed, ReduxUsageKind.MIDDLEWARE)

        assertTrue(ReduxUsageKind.MIDDLEWARE in collapsed.collapsedKinds)
        assertTrue(ReduxUsageKind.MIDDLEWARE !in reopened.collapsedKinds)
    }

    fun testReplaceEntriesUpdatesListModelAndVisibleRows() {
        val list = ReduxActionPopup.createList(ReduxActionPopup.loadingEntries())
        val entries = listOf(
            PopupEntry.Header(ReduxUsageKind.DISPATCH, "Dispatches (1)", expanded = true),
            PopupEntry.UsageEntry(usage("dispatch(AddItemForRemoval)", ReduxUsageKind.DISPATCH), "AddItemForRemoval")
        )

        ReduxActionPopup.replaceEntries(list, entries)

        assertEquals(2, list.model.size)
        assertEquals("▼ Dispatches (1)", entryLabel(list.model.getElementAt(0)))
        assertEquals("dispatch(AddItemForRemoval)", entryLabel(list.model.getElementAt(1)))
        assertEquals(2, list.visibleRowCount)
    }

    fun testRegisterActivePopupCancelsPreviousPopup() {
        val first = RecordingPopupHandle()
        val second = RecordingPopupHandle()

        ReduxActionPopup.registerActivePopup(first)
        ReduxActionPopup.registerActivePopup(second)

        assertEquals(1, first.cancelCalls)
        assertEquals(0, second.cancelCalls)
    }

    fun testOpenReduxFlowButtonInvokesProvidedCallback() {
        val list = ReduxActionPopup.createList(ReduxActionPopup.loadingEntries())
        val controller = PopupListController(list)
        val action = ActionInfo(ActionId("AddItemForRemoval"), "AddItemForRemoval", null)
        var openFlowCalls = 0
        val toolbar = ReduxActionPopup.createToolbar(project, action, controller) {
            openFlowCalls += 1
        }

        findButton(toolbar, "Open Redux Flow")?.doClick()

        assertEquals(1, openFlowCalls)
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
            is PopupEntry.Header -> "${if (entry.expanded) "▼" else "▶"} ${entry.text}"
            is PopupEntry.UsageEntry -> entry.usage.displayText
        }
    }

    private fun findButton(component: Component, text: String): JButton? {
        if (component is JButton && component.text == text) {
            return component
        }
        if (component is Container) {
            component.components.forEach { child ->
                val match = findButton(child, text)
                if (match != null) {
                    return match
                }
            }
        }
        return null
    }

    private class RecordingPopupHandle : PopupHandle {
        var cancelCalls: Int = 0

        override fun cancel() {
            cancelCalls += 1
        }
    }
}
