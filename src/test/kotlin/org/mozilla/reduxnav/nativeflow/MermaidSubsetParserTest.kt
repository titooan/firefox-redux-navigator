package org.mozilla.reduxnav.nativeflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidSubsetParserTest {
    private val parser = MermaidSubsetParser()

    @Test
    fun parseSimpleActionReducerGraph() {
        val graph = parser.parse(
            """
            flowchart LR

            action["AddTabAction"]
            reducer_0["BrowserStore.kt:44"]

            action --> reducer_0
            """.trimIndent()
        )

        assertEquals(listOf("action", "reducer_0"), graph.nodes.map { it.id })
        assertEquals(listOf(FlowEdge("action", "reducer_0")), graph.edges)
    }

    @Test
    fun parseDispatchActionMiddlewareReducerGraph() {
        val graph = parser.parse(
            """
            flowchart LR

            action["UndoPendingDeletion"]
            dispatch_0["DownloadsScreen.kt:128"]
            middleware_0["DownloadDeleteMiddleware.kt:80"]
            reducer_0["DownloadUIStore.kt:109"]

            dispatch_0 --> action
            action --> middleware_0
            middleware_0 --> reducer_0
            """.trimIndent()
        )

        assertEquals(4, graph.nodes.size)
        assertEquals(3, graph.edges.size)
    }

    @Test
    fun parseCommentsBlankLinesAndTestNodeClasses() {
        val graph = parser.parse(
            """
            %% comment before header
            flowchart LR

            %% nodes
            action["RenameFileConfirmed"]
            dispatch_0["DownloadUIStoreTest.kt:1430"]

            dispatch_0 --> action

            classDef testNode fill:#214d29,stroke:#4aa35f,color:#f4fff4,stroke-width:1px;
            class dispatch_0 testNode;
            """.trimIndent()
        )

        assertEquals(setOf("dispatch_0"), graph.nodes.filter { it.isTestNode }.map { it.id }.toSet())
        assertEquals("DownloadUIStoreTest.kt:1430", graph.nodes.single { it.id == "dispatch_0" }.label)
    }

    @Test
    fun rejectDuplicateNodeId() {
        val error = parseFailure(
            """
            flowchart LR
            action["A"]
            action["B"]
            """.trimIndent()
        )

        assertTrue(error.message!!.contains("Duplicate node id"))
    }

    @Test
    fun rejectEdgeToMissingNode() {
        val error = parseFailure(
            """
            flowchart LR
            action["A"]
            action --> reducer_0
            """.trimIndent()
        )

        assertTrue(error.message!!.contains("missing node"))
    }

    @Test
    fun rejectUnsupportedMermaidSyntax() {
        val error = parseFailure(
            """
            flowchart LR
            action{Decision}
            """.trimIndent()
        )

        assertTrue(error.message!!.contains("Unsupported Mermaid syntax"))
    }

    private fun parseFailure(source: String): MermaidSubsetParseException =
        try {
            parser.parse(source)
            error("Expected parser failure")
        } catch (error: MermaidSubsetParseException) {
            error
        }
}
