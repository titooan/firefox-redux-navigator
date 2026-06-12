package org.mozilla.reduxnav.graph

import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind

class ReduxGraphBuilder {
    fun build(actionGraph: ActionGraph): ReduxGraph {
        val action = ActionNode(actionGraph.action)
        val dispatches = nodesFor(actionGraph, ReduxUsageKind.DISPATCH, "dispatch") { usage, id -> DispatchNode(usage, id) }
        val middlewares = nodesFor(actionGraph, ReduxUsageKind.MIDDLEWARE, "middleware") { usage, id -> MiddlewareNode(usage, id) }
        val reducers = nodesFor(actionGraph, ReduxUsageKind.REDUCER, "reducer") { usage, id -> ReducerNode(usage, id) }

        val edges = buildList {
            dispatches.forEach { add(ReduxGraphEdge(it.id, action.id)) }
            if (middlewares.isNotEmpty()) {
                middlewares.forEach { add(ReduxGraphEdge(action.id, it.id)) }
                middlewares.forEach { middleware ->
                    reducers.forEach { reducer ->
                        add(ReduxGraphEdge(middleware.id, reducer.id))
                    }
                }
            } else {
                reducers.forEach { add(ReduxGraphEdge(action.id, it.id)) }
            }
        }

        return ReduxGraph(
            nodes = dispatches + action + middlewares + reducers,
            edges = edges
        )
    }

    private fun <T : ReduxGraphNode> nodesFor(
        actionGraph: ActionGraph,
        kind: ReduxUsageKind,
        prefix: String,
        builder: (ReduxUsage, String) -> T
    ): List<T> =
        actionGraph.usages
            .asSequence()
            .filter { it.kind == kind }
            .distinctBy { "${it.filePath}:${it.line}" }
            .sortedWith(compareBy<ReduxUsage> { it.filePath }.thenBy { it.line })
            .mapIndexed { index, usage ->
                builder(usage, "${prefix}_$index")
            }
            .toList()
}
