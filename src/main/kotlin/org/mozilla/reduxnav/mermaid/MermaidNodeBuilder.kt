package org.mozilla.reduxnav.mermaid

import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.popup.isTestPath

class MermaidNodeBuilder {
    data class MermaidNode(
        val id: String,
        val label: String,
        val isTestNode: Boolean = false
    )

    data class MermaidEdge(
        val from: String,
        val to: String
    )

    data class MermaidUsageNode(
        val id: String,
        val label: String,
        val usage: ReduxUsage,
        val isTestNode: Boolean = false
    )

    data class MermaidGraph(
        val action: MermaidNode,
        val dispatches: List<MermaidUsageNode>,
        val middlewares: List<MermaidUsageNode>,
        val reducers: List<MermaidUsageNode>,
        val edges: List<MermaidEdge>
    )

    fun build(actionGraph: ActionGraph): MermaidGraph {
        val dispatches = nodesFor(actionGraph, ReduxUsageKind.DISPATCH, "dispatch")
        val middlewares = nodesFor(actionGraph, ReduxUsageKind.MIDDLEWARE, "middleware")
        val reducers = nodesFor(actionGraph, ReduxUsageKind.REDUCER, "reducer")
        val action = MermaidNode(
            id = "action",
            label = MermaidEscaper.escape(actionGraph.action.displayName)
        )

        val edges = buildList {
            dispatches.forEach { add(MermaidEdge(it.id, action.id)) }

            if (middlewares.isNotEmpty()) {
                middlewares.forEach { add(MermaidEdge(action.id, it.id)) }
                middlewares.forEach { middleware ->
                    reducers.forEach { reducer ->
                        add(MermaidEdge(middleware.id, reducer.id))
                    }
                }
            } else {
                reducers.forEach { add(MermaidEdge(action.id, it.id)) }
            }
        }

        return MermaidGraph(
            action = action,
            dispatches = dispatches,
            middlewares = middlewares,
            reducers = reducers,
            edges = edges
        )
    }

    private fun nodesFor(
        actionGraph: ActionGraph,
        kind: ReduxUsageKind,
        prefix: String
    ): List<MermaidUsageNode> =
        actionGraph.usages
            .asSequence()
            .filter { it.kind == kind }
            .distinctBy { "${it.filePath}:${it.line}" }
            .sortedWith(compareBy<ReduxUsage> { it.filePath }.thenBy { it.line })
            .mapIndexed { index, usage ->
                MermaidUsageNode(
                    id = "${prefix}_$index",
                    label = MermaidEscaper.escape("${usage.fileName}:${usage.line}"),
                    usage = usage,
                    isTestNode = isTestPath(usage.filePath)
                )
            }
            .toList()
}
