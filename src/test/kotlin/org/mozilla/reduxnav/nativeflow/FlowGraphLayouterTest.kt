package org.mozilla.reduxnav.nativeflow

import org.eclipse.elk.core.data.LayoutMetaDataService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color

class FlowGraphLayouterTest {
    private val layouter = FlowGraphLayouter()

    @Test
    fun layoutRegistersLayeredAlgorithm() {
        layouter.layout(sampleGraph())

        assertTrue(LayoutMetaDataService.getInstance().getAlgorithmData("org.eclipse.elk.layered") != null)
    }

    @Test
    fun layoutAssignsPositiveNodeSizes() {
        val rendered = layouter.layout(sampleGraph())

        rendered.nodes.forEach { node ->
            assertTrue(node.width > 0.0)
            assertTrue(node.height > 0.0)
        }
    }

    @Test
    fun layoutAssignsEdgePoints() {
        val rendered = layouter.layout(sampleGraph())

        rendered.edges.forEach { edge ->
            assertTrue(edge.points.size >= 2)
        }
    }

    @Test
    fun layoutProducesPositiveBounds() {
        val rendered = layouter.layout(sampleGraph())

        assertTrue(rendered.width > 0.0)
        assertTrue(rendered.height > 0.0)
    }

    @Test
    fun layoutAssignsNodeKindsFromCurrentMermaidIds() {
        val rendered = layouter.layout(dispatchGraphWithTests())

        assertEquals(FlowNodeKind.ACTION, rendered.nodes.single { it.id == "action" }.kind)
        assertEquals(FlowNodeKind.DISPATCH, rendered.nodes.single { it.id == "dispatch_0" }.kind)
        assertEquals(FlowNodeKind.MIDDLEWARE, rendered.nodes.single { it.id == "middleware_0" }.kind)
        assertEquals(FlowNodeKind.REDUCER, rendered.nodes.single { it.id == "reducer_0" }.kind)
    }

    @Test
    fun paletteUsesBlueForNonTestDispatchAndGreenForTestDispatch() {
        val dispatchColors = FlowNodePalette.colorsFor(FlowNodeKind.DISPATCH, isTestNode = false)
        val testDispatchColors = FlowNodePalette.colorsFor(FlowNodeKind.DISPATCH, isTestNode = true)

        assertEquals(Color(0xDC, 0xEB, 0xFA), dispatchColors.fill)
        assertEquals(Color(0xD7, 0xEA, 0xD7), testDispatchColors.fill)
    }

    @Test
    fun layoutKeepsTestDispatchNodesAtBottomOfTheirLayer() {
        val rendered = layouter.layout(dispatchGraphWithTests())
        val dispatchesByY = rendered.nodes
            .filter { it.id.startsWith("dispatch_") }
            .sortedBy { it.y }
            .map { it.label.substringBefore('.') }

        assertEquals(
            listOf(
                "DownloadsScreen",
                "DownloadUIRenameMiddleware",
                "DownloadUIStoreTest",
                "DownloadUIStoreTest",
                "DownloadUIStoreTest",
                "DownloadUIStoreTest"
            ),
            dispatchesByY
        )
    }

    private fun sampleGraph(): FlowGraph =
        FlowGraph(
            nodes = listOf(
                FlowNode("dispatch_0", "DownloadsScreen.kt:128", isTestNode = true),
                FlowNode("action", "UndoPendingDeletion"),
                FlowNode("middleware_0", "DownloadDeleteMiddleware.kt:80"),
                FlowNode("reducer_0", "DownloadUIStore.kt:109")
            ),
            edges = listOf(
                FlowEdge("dispatch_0", "action"),
                FlowEdge("action", "middleware_0"),
                FlowEdge("middleware_0", "reducer_0")
            )
        )

    private fun dispatchGraphWithTests(): FlowGraph =
        FlowGraph(
            nodes = listOf(
                FlowNode("action", "RenameFileConfirmed"),
                FlowNode("dispatch_0", "DownloadsScreen.kt:294"),
                FlowNode("dispatch_1", "DownloadUIRenameMiddleware.kt:59"),
                FlowNode("dispatch_2", "DownloadUIStoreTest.kt:1430", isTestNode = true),
                FlowNode("dispatch_3", "DownloadUIStoreTest.kt:1477", isTestNode = true),
                FlowNode("dispatch_4", "DownloadUIStoreTest.kt:1526", isTestNode = true),
                FlowNode("dispatch_5", "DownloadUIStoreTest.kt:1577", isTestNode = true),
                FlowNode("middleware_0", "DownloadUIRenameMiddleware.kt:46"),
                FlowNode("reducer_0", "DownloadUIStore.kt:91")
            ),
            edges = listOf(
                FlowEdge("dispatch_0", "action"),
                FlowEdge("dispatch_1", "action"),
                FlowEdge("dispatch_2", "action"),
                FlowEdge("dispatch_3", "action"),
                FlowEdge("dispatch_4", "action"),
                FlowEdge("dispatch_5", "action"),
                FlowEdge("action", "middleware_0"),
                FlowEdge("middleware_0", "reducer_0")
            )
        )
}
