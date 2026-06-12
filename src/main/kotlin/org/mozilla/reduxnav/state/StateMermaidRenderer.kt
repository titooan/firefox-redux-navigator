package org.mozilla.reduxnav.state

import org.mozilla.reduxnav.mermaid.MermaidEscaper
import org.mozilla.reduxnav.mermaid.MermaidFlowStyle

class StateMermaidRenderer(
    private val style: MermaidFlowStyle = MermaidFlowStyle.light()
) {
    fun render(graph: StateGraph): String {
        val actionNodes = graph.nodes.filterIsInstance<StateActionNode>()
        val reducerNodes = graph.nodes.filterIsInstance<StateReducerNode>()
        val stateNode = graph.nodes.filterIsInstance<StateFieldNode>().singleOrNull()
        val testNodes = reducerNodes.filter { it.isTestNode }

        return buildString {
            appendLine("flowchart LR")
            appendLine()

            actionNodes.forEach { appendLine("""${it.id}["${MermaidEscaper.escape(it.label)}"]""") }
            if (actionNodes.isNotEmpty()) appendLine()

            reducerNodes.forEach { appendLine("""${it.id}["${MermaidEscaper.escape(it.label)}"]""") }
            if (reducerNodes.isNotEmpty()) appendLine()

            if (stateNode != null) {
                appendLine("""${stateNode.id}["${MermaidEscaper.escape(stateNode.label)}"]""")
            }

            if (graph.edges.isNotEmpty()) {
                appendLine()
                graph.edges.forEach { appendLine("${it.from} --> ${it.to}") }
            }

            if (testNodes.isNotEmpty()) {
                appendLine()
                appendLine(
                    "classDef testNode " +
                        "fill:${style.testNodeFill}," +
                        "stroke:${style.testNodeStroke}," +
                        "color:${style.testNodeText}," +
                        "stroke-width:1px;"
                )
                testNodes.forEach { appendLine("class ${it.id} testNode;") }
            }
        }.trimEnd()
    }
}
