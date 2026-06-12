package org.mozilla.reduxnav.mermaid

import org.mozilla.reduxnav.model.ActionGraph

class MermaidFlowRenderer(
    private val nodeBuilder: MermaidNodeBuilder = MermaidNodeBuilder(),
    private val style: MermaidFlowStyle = MermaidFlowStyle.light()
) {
    fun render(actionGraph: ActionGraph): String {
        val graph = nodeBuilder.build(actionGraph)
        val testNodes = (graph.dispatches + graph.middlewares + graph.reducers)
            .filter { it.isTestNode }

        return buildString {
            appendLine("flowchart LR")
            appendLine()

            appendLine("""${graph.action.id}["${graph.action.label}"]""")

            if (graph.dispatches.isNotEmpty()) {
                appendLine()
                graph.dispatches.forEach { appendLine("""${it.id}["${it.label}"]""") }
            }

            if (graph.middlewares.isNotEmpty()) {
                appendLine()
                graph.middlewares.forEach { appendLine("""${it.id}["${it.label}"]""") }
            }

            if (graph.reducers.isNotEmpty()) {
                appendLine()
                graph.reducers.forEach { appendLine("""${it.id}["${it.label}"]""") }
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

data class MermaidFlowStyle(
    val testNodeFill: String,
    val testNodeStroke: String,
    val testNodeText: String
) {
    companion object {
        fun light(): MermaidFlowStyle = MermaidFlowStyle(
            testNodeFill = "#d7ead7",
            testNodeStroke = "#338833",
            testNodeText = "#1e2a1e"
        )

        fun dark(): MermaidFlowStyle = MermaidFlowStyle(
            testNodeFill = "#214d29",
            testNodeStroke = "#4aa35f",
            testNodeText = "#f4fff4"
        )
    }
}
