package org.mozilla.reduxnav.state

class StateGraphBuilder {
    fun build(graph: StateFieldGraph): StateGraph {
        val stateNode = StateFieldNode(graph.stateField)

        val actionNodes = graph.modifications
            .mapNotNull { it.action }
            .distinctBy { it.id }
            .sortedBy { it.displayName }
            .mapIndexed { index, action ->
                StateActionNode(action.displayName, "action_$index")
            }

        val actionNodeIdsByLabel = actionNodes.associateBy({ it.label }, { it.id })
        val includeUnknownAction = graph.modifications.any { it.action == null }
        val unknownActionNode = if (includeUnknownAction) {
            StateActionNode("Unknown action", "action_unknown")
        } else {
            null
        }

        val reducerNodes = graph.modifications
            .distinctBy { "${it.filePath}:${it.lineNumber}:${it.snippet}" }
            .sortedWith(compareBy<StateModification> { it.filePath }.thenBy { it.lineNumber })
            .mapIndexed { index, modification ->
                StateReducerNode(modification, "reducer_$index")
            }

        val reducerNodeIdsByKey = reducerNodes.associateBy(
            { "${it.modification.filePath}:${it.modification.lineNumber}:${it.modification.snippet}" },
            { it.id }
        )

        val edges = buildList {
            reducerNodes.forEach { reducer ->
                val modification = reducer.modification
                val actionNodeId = modification.action?.displayName?.let(actionNodeIdsByLabel::get) ?: unknownActionNode?.id
                if (actionNodeId != null) {
                    add(StateGraphEdge(actionNodeId, reducer.id))
                }
                add(StateGraphEdge(reducer.id, stateNode.id))
            }
        }.distinct()

        return StateGraph(
            nodes = buildList {
                addAll(actionNodes)
                unknownActionNode?.let(::add)
                addAll(reducerNodes)
                add(stateNode)
            },
            edges = edges
        )
    }
}
