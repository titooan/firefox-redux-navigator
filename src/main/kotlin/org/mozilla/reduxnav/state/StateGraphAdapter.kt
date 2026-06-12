package org.mozilla.reduxnav.state

import org.mozilla.reduxnav.nativeflow.FlowEdge
import org.mozilla.reduxnav.nativeflow.FlowGraph
import org.mozilla.reduxnav.nativeflow.FlowNode
import org.mozilla.reduxnav.nativeflow.FlowNodeKind

class StateGraphAdapter {
    fun adapt(graph: StateGraph): FlowGraph =
        FlowGraph(
            nodes = graph.nodes.map { node ->
                FlowNode(
                    id = node.id,
                    label = node.label,
                    kind = node.kind.toFlowKind(),
                    isTestNode = node.isTestNode
                )
            },
            edges = graph.edges.map { edge -> FlowEdge(edge.from, edge.to) }
        )

    private fun StateGraphNodeKind.toFlowKind(): FlowNodeKind =
        when (this) {
            StateGraphNodeKind.ACTION -> FlowNodeKind.ACTION
            StateGraphNodeKind.REDUCER -> FlowNodeKind.REDUCER
            StateGraphNodeKind.STATE -> FlowNodeKind.STATE
        }
}
