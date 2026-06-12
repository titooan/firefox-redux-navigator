package org.mozilla.reduxnav.nativeflow

import org.junit.Assert.assertEquals
import org.mozilla.reduxnav.model.ActionId
import org.mozilla.reduxnav.model.ActionInfo
import org.junit.Test
import org.mozilla.reduxnav.graph.ActionNode
import org.mozilla.reduxnav.graph.ReduxGraph
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

        panel.setReduxGraph(
            ReduxGraph(nodes = listOf(ActionNode(ActionInfo(ActionId("AddTabAction"), "AddTabAction", null))), edges = emptyList()),
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

        panel.setReduxGraph(
            ReduxGraph(nodes = listOf(ActionNode(ActionInfo(ActionId("AddTabAction"), "AddTabAction", null))), edges = emptyList()),
            mapOf(
                "action" to DiagramNodeTarget.ActionTarget(
                    ActionInfo(ActionId("AddTabAction"), "AddTabAction", null)
                )
            )
        )
        panel.setReduxGraph(null)

        assertEquals(emptySet<String>(), panel.interactiveNodeIdsForTest())
    }
}
