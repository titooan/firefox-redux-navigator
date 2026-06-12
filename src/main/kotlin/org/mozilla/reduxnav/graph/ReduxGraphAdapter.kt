package org.mozilla.reduxnav.graph

import org.mozilla.reduxnav.nativeflow.FlowEdge
import org.mozilla.reduxnav.nativeflow.FlowGraph
import org.mozilla.reduxnav.nativeflow.FlowNode
import org.mozilla.reduxnav.nativeflow.FlowNodeKind

class ReduxGraphAdapter {
    fun adapt(graph: ReduxGraph): FlowGraph =
        FlowGraph(
            nodes = graph.nodes.map { node ->
                FlowNode(
                    id = node.id,
                    label = node.label,
                    kind = node.kind.toFlowKind(),
                    isTestNode = node.isTestNode,
                    tooltipText = node.navigationTarget.tooltipText
                )
            },
            edges = graph.edges.map { edge -> FlowEdge(edge.from, edge.to) }
        )

    private fun ReduxGraphNodeKind.toFlowKind(): FlowNodeKind =
        when (this) {
            ReduxGraphNodeKind.DISPATCH -> FlowNodeKind.DISPATCH
            ReduxGraphNodeKind.ACTION -> FlowNodeKind.ACTION
            ReduxGraphNodeKind.MIDDLEWARE -> FlowNodeKind.MIDDLEWARE
            ReduxGraphNodeKind.REDUCER -> FlowNodeKind.REDUCER
        }
}
