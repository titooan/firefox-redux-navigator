package org.mozilla.reduxnav.nativeflow

import com.intellij.ui.JBColor
import java.awt.Color

object FlowEdgePalette {
    fun colorFor(edge: RenderedEdge, nodesById: Map<String, RenderedNode>): Color {
        val fromNode = nodesById[edge.from]
        val toNode = nodesById[edge.to]
        val paletteNode = when {
            toNode?.kind == FlowNodeKind.ACTION -> fromNode
            else -> toNode ?: fromNode
        }

        return if (paletteNode == null) {
            JBColor.border()
        } else {
            FlowNodePalette.colorsFor(paletteNode.kind, paletteNode.isTestNode).border
        }
    }
}
