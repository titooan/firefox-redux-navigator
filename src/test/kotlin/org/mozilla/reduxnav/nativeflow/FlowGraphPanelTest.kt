package org.mozilla.reduxnav.nativeflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Cursor

class FlowGraphPanelTest {
    @Test
    fun edgeToActionUsesDispatchPaletteColor() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_0", FlowNodeKind.DISPATCH, isTestNode = false),
                    node("action", FlowNodeKind.ACTION)
                ),
                edges = listOf(RenderedEdge("dispatch_0", "action", listOf(Point2D(0.0, 0.0), Point2D(10.0, 0.0))))
            )
        )

        assertEquals(
            Color(0x4B, 0x89, 0xC8),
            panel.edgeColorsForTest().single()
        )
    }

    @Test
    fun edgeFromTestDispatchToActionUsesTestDispatchColor() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_2", FlowNodeKind.DISPATCH, isTestNode = true),
                    node("action", FlowNodeKind.ACTION)
                ),
                edges = listOf(RenderedEdge("dispatch_2", "action", listOf(Point2D(0.0, 0.0), Point2D(10.0, 0.0))))
            )
        )

        assertEquals(
            Color(0x33, 0x88, 0x33),
            panel.edgeColorsForTest().single()
        )
    }

    @Test
    fun edgeAfterActionUsesDestinationPaletteColor() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("action", FlowNodeKind.ACTION),
                    node("middleware_0", FlowNodeKind.MIDDLEWARE),
                    node("reducer_0", FlowNodeKind.REDUCER)
                ),
                edges = listOf(
                    RenderedEdge("action", "middleware_0", listOf(Point2D(0.0, 0.0), Point2D(10.0, 0.0))),
                    RenderedEdge("middleware_0", "reducer_0", listOf(Point2D(10.0, 0.0), Point2D(20.0, 0.0)))
                )
            )
        )

        assertEquals(
            listOf(Color(0x7A, 0x59, 0xB5), Color(0xC5, 0x6A, 0x5D)),
            panel.edgeColorsForTest()
        )
    }

    @Test
    fun roundedEdgePathUsesQuadraticSegmentAtBends() {
        val segmentTypes = FlowGraphPanel.segmentTypesForRoundedPathTest(
            listOf(
                Point2D(10.0, 10.0),
                Point2D(60.0, 10.0),
                Point2D(60.0, 50.0),
                Point2D(110.0, 50.0)
            )
        )

        assertTrue(segmentTypes.contains("QUAD_TO"))
    }

    @Test
    fun straightEdgePathStaysStraight() {
        val segmentTypes = FlowGraphPanel.segmentTypesForRoundedPathTest(
            listOf(
                Point2D(10.0, 10.0),
                Point2D(110.0, 10.0)
            )
        )

        assertEquals(listOf("MOVE_TO", "LINE_TO"), segmentTypes)
    }

    @Test
    fun hitTestingReturnsNodeInsideBounds() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_0", FlowNodeKind.DISPATCH).copy(x = 20.0, y = 10.0)
                ),
                edges = emptyList()
            )
        )

        assertEquals("dispatch_0", panel.nodeIdAtForTest(40, 20))
    }

    @Test
    fun hitTestingReturnsNullOutsideBounds() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_0", FlowNodeKind.DISPATCH).copy(x = 20.0, y = 10.0)
                ),
                edges = emptyList()
            )
        )

        assertNull(panel.nodeIdAtForTest(5, 5))
    }

    @Test
    fun hoverUsesHandCursorOnlyForClickableNodes() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_0", FlowNodeKind.DISPATCH).copy(x = 20.0, y = 10.0)
                ),
                edges = emptyList()
            )
        )
        panel.setInteractiveNodeIds(setOf("dispatch_0"))

        assertEquals(Cursor.HAND_CURSOR, panel.cursorTypeForTest(40, 20))
        assertEquals(Cursor.DEFAULT_CURSOR, panel.cursorTypeForTest(5, 5))
    }

    @Test
    fun selectionTracksClickedNode() {
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_0", FlowNodeKind.DISPATCH).copy(x = 20.0, y = 10.0)
                ),
                edges = emptyList()
            )
        )

        panel.dispatchEvent(
            java.awt.event.MouseEvent(
                panel,
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                40,
                20,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1
            )
        )

        assertEquals("dispatch_0", panel.selectedNodeIdForTest())
    }

    @Test
    fun selectionCallbackReceivesClickedNode() {
        var selected: String? = null
        val panel = FlowGraphPanel(
            RenderedGraph(
                width = 300.0,
                height = 120.0,
                nodes = listOf(
                    node("dispatch_0", FlowNodeKind.DISPATCH).copy(x = 20.0, y = 10.0)
                ),
                edges = emptyList()
            )
        )
        panel.setInteractiveNodeIds(setOf("dispatch_0"), onNodeSelected = { selected = it })

        panel.dispatchEvent(
            java.awt.event.MouseEvent(
                panel,
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                40,
                20,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1
            )
        )

        assertEquals("dispatch_0", selected)
    }

    private fun node(id: String, kind: FlowNodeKind, isTestNode: Boolean = false): RenderedNode =
        RenderedNode(
            id = id,
            label = id,
            kind = kind,
            x = 0.0,
            y = 0.0,
            width = 100.0,
            height = 40.0,
            isTestNode = isTestNode
        )
}
