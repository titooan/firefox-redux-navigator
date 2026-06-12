package org.mozilla.reduxnav.nativeflow

class MermaidSubsetParser {
    fun parse(mermaidText: String): FlowGraph {
        val nodesById = linkedMapOf<String, FlowNode>()
        val edges = mutableListOf<ParsedEdge>()
        val testNodeAssignments = mutableListOf<ParsedClassAssignment>()
        var seenHeader = false
        var testClassDefined = false

        mermaidText.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()

            if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
                return@forEachIndexed
            }

            if (!seenHeader) {
                if (line != HEADER) {
                    throw MermaidSubsetParseException(
                        lineNumber,
                        "Expected \"$HEADER\" as the first non-empty Mermaid line."
                    )
                }
                seenHeader = true
                return@forEachIndexed
            }

            parseNode(line)?.let { node ->
                if (nodesById.putIfAbsent(node.id, node) != null) {
                    throw MermaidSubsetParseException(lineNumber, "Duplicate node id \"${node.id}\".")
                }
                return@forEachIndexed
            }

            parseEdge(line)?.let { edge ->
                edges += ParsedEdge(edge = edge, lineNumber = lineNumber)
                return@forEachIndexed
            }

            parseClassDef(line)?.let { className ->
                if (className != TEST_NODE_CLASS) {
                    throw MermaidSubsetParseException(
                        lineNumber,
                        "Only \"$TEST_NODE_CLASS\" class definitions are supported."
                    )
                }
                testClassDefined = true
                return@forEachIndexed
            }

            parseClassAssignment(line)?.let { assignment ->
                val (className, nodeIds) = assignment
                if (className != TEST_NODE_CLASS) {
                    throw MermaidSubsetParseException(
                        lineNumber,
                        "Only \"$TEST_NODE_CLASS\" class assignments are supported."
                    )
                }
                if (!testClassDefined) {
                    throw MermaidSubsetParseException(
                        lineNumber,
                        "Class \"$TEST_NODE_CLASS\" must be defined before it is assigned."
                    )
                }
                testNodeAssignments += ParsedClassAssignment(
                    nodeIds = nodeIds,
                    lineNumber = lineNumber
                )
                return@forEachIndexed
            }

            throw MermaidSubsetParseException(
                lineNumber,
                "Unsupported Mermaid syntax: $line"
            )
        }

        if (!seenHeader) {
            throw MermaidSubsetParseException(1, "Expected \"$HEADER\" as the first non-empty Mermaid line.")
        }

        edges.forEach { parsedEdge ->
            val edge = parsedEdge.edge
            if (edge.from !in nodesById) {
                throw MermaidSubsetParseException(
                    lineNumber = parsedEdge.lineNumber,
                    message = "Edge references missing node \"${edge.from}\"."
                )
            }
            if (edge.to !in nodesById) {
                throw MermaidSubsetParseException(
                    lineNumber = parsedEdge.lineNumber,
                    message = "Edge references missing node \"${edge.to}\"."
                )
            }
        }

        val testNodeIds = linkedSetOf<String>()
        testNodeAssignments.forEach { assignment ->
            assignment.nodeIds.forEach { nodeId ->
                if (nodeId !in nodesById) {
                    throw MermaidSubsetParseException(
                        assignment.lineNumber,
                        "Class assignment references missing node \"$nodeId\"."
                    )
                }
                testNodeIds += nodeId
            }
        }

        return FlowGraph(
            nodes = nodesById.values.map { node ->
                if (node.id in testNodeIds) {
                    node.copy(isTestNode = true)
                } else {
                    node
                }
            },
            edges = edges.map { it.edge }
        )
    }

    private data class ParsedEdge(
        val edge: FlowEdge,
        val lineNumber: Int
    )

    private data class ParsedClassAssignment(
        val nodeIds: List<String>,
        val lineNumber: Int
    )

    private fun parseNode(line: String): FlowNode? {
        val match = NODE_REGEX.matchEntire(line) ?: return null
        return FlowNode(
            id = match.groupValues[1],
            label = decodeLabel(match.groupValues[2])
        )
    }

    private fun parseEdge(line: String): FlowEdge? {
        val match = EDGE_REGEX.matchEntire(line) ?: return null
        return FlowEdge(
            from = match.groupValues[1],
            to = match.groupValues[2]
        )
    }

    private fun parseClassDef(line: String): String? =
        CLASS_DEF_REGEX.matchEntire(line)?.groupValues?.get(1)

    private fun parseClassAssignment(line: String): Pair<String, List<String>>? {
        val match = CLASS_ASSIGNMENT_REGEX.matchEntire(line) ?: return null
        val nodeIds = match.groupValues[1]
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return match.groupValues[2] to nodeIds
    }

    private fun decodeLabel(text: String): String =
        text
            .replace("\\\\", "\\")
            .replace("&quot;", "\"")
            .replace("&#91;", "[")
            .replace("&#93;", "]")
            .replace("&#40;", "(")
            .replace("&#41;", ")")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    companion object {
        private const val HEADER = "flowchart LR"
        private const val COMMENT_PREFIX = "%%"
        private const val TEST_NODE_CLASS = "testNode"
        private val NODE_REGEX = Regex("""^([A-Za-z0-9_]+)\["((?:[^"\\]|\\\\)*)"]$""")
        private val EDGE_REGEX = Regex("""^([A-Za-z0-9_]+)\s+-->\s+([A-Za-z0-9_]+)$""")
        private val CLASS_DEF_REGEX = Regex("""^classDef\s+([A-Za-z0-9_]+)\s+.+;$""")
        private val CLASS_ASSIGNMENT_REGEX =
            Regex("""^class\s+([A-Za-z0-9_,\s]+)\s+([A-Za-z0-9_]+);$""")
    }
}

class MermaidSubsetParseException(
    val lineNumber: Int,
    message: String
) : IllegalArgumentException("Line $lineNumber: $message")
