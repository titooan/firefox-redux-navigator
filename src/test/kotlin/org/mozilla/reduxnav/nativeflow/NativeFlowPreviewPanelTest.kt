package org.mozilla.reduxnav.nativeflow

import org.junit.Assert.assertEquals
import org.mozilla.reduxnav.model.ActionId
import org.mozilla.reduxnav.model.ActionInfo
import org.junit.Test
import javax.swing.JViewport

class NativeFlowPreviewPanelTest {
    @Test
    fun previewUsesSimpleViewportScrollingToAvoidScrollArtifacts() {
        val panel = NativeFlowPreviewPanel()

        assertEquals(JViewport.SIMPLE_SCROLL_MODE, panel.viewportScrollMode())
    }

    @Test
    fun previewShowsLegendForNodeColors() {
        val panel = NativeFlowPreviewPanel()

        assertEquals(
            listOf("Dispatch", "Dispatch (Test)", "Action", "Middleware", "Reducer"),
            panel.legendLabelsForTest()
        )
    }

    @Test
    fun previewToolbarButtonsDoNotKeepFocusPainting() {
        val panel = NativeFlowPreviewPanel()

        assertEquals(
            listOf(true to true, true to true, true to true, true to true),
            panel.toolbarButtonFocusStatesForTest()
        )
    }

    @Test
    fun previewTracksInteractiveNodesForRenderedGraph() {
        val panel = NativeFlowPreviewPanel()

        panel.setMermaidSource(
            """
            flowchart LR

            action["AddTabAction"]
            dispatch_0["Dispatch.kt:4"]

            dispatch_0 --> action
            """.trimIndent(),
            mapOf(
                "action" to DiagramNodeTarget.ActionTarget(
                    ActionInfo(ActionId("AddTabAction"), "AddTabAction", null)
                )
            )
        )

        assertEquals(setOf("action"), panel.interactiveNodeIdsForTest())
    }

    @Test
    fun previewClearsInteractiveNodesOnRenderError() {
        val panel = NativeFlowPreviewPanel()

        panel.setMermaidSource(
            """
            flowchart LR

            action["AddTabAction"]
            dispatch_0["Dispatch.kt:4"]

            dispatch_0 --> action
            """.trimIndent(),
            mapOf(
                "action" to DiagramNodeTarget.ActionTarget(
                    ActionInfo(ActionId("AddTabAction"), "AddTabAction", null)
                )
            )
        )
        panel.setMermaidSource("flowchart TD", mapOf("action" to DiagramNodeTarget.ActionTarget(ActionInfo(ActionId("Broken"), "Broken", null))))

        assertEquals(emptySet<String>(), panel.interactiveNodeIdsForTest())
    }
}
