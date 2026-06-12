package org.mozilla.reduxnav.nativeflow

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

object FlowNodePalette {
    fun colorsFor(kind: FlowNodeKind, isTestNode: Boolean): NodeColors =
        if (kind == FlowNodeKind.DISPATCH && isTestNode) {
            testDispatchColors()
        } else {
            when (kind) {
                FlowNodeKind.DISPATCH -> dispatchColors()
                FlowNodeKind.ACTION -> actionColors()
                FlowNodeKind.MIDDLEWARE -> middlewareColors()
                FlowNodeKind.REDUCER -> reducerColors()
                FlowNodeKind.STATE -> stateColors()
                FlowNodeKind.UNKNOWN -> defaultColors()
            }
        }

    fun legendEntries(): List<LegendEntry> = listOf(
        LegendEntry("Dispatch", colorsFor(FlowNodeKind.DISPATCH, isTestNode = false)),
        LegendEntry("Dispatch (Test)", colorsFor(FlowNodeKind.DISPATCH, isTestNode = true)),
        LegendEntry("Action", colorsFor(FlowNodeKind.ACTION, isTestNode = false)),
        LegendEntry("Middleware", colorsFor(FlowNodeKind.MIDDLEWARE, isTestNode = false)),
        LegendEntry("Reducer", colorsFor(FlowNodeKind.REDUCER, isTestNode = false)),
        LegendEntry("State", colorsFor(FlowNodeKind.STATE, isTestNode = false))
    )

    private fun defaultColors(): NodeColors = NodeColors(
        fill = UIUtil.getPanelBackground(),
        border = JBColor.border(),
        text = UIUtil.getLabelForeground()
    )

    private fun dispatchColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xDC, 0xEB, 0xFA), Color(0x1F, 0x42, 0x60)),
        border = JBColor(Color(0x4B, 0x89, 0xC8), Color(0x63, 0xA3, 0xE6)),
        text = JBColor(Color(0x16, 0x32, 0x4F), Color(0xF3, 0xF9, 0xFF))
    )

    private fun testDispatchColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xD7, 0xEA, 0xD7), Color(0x21, 0x4D, 0x29)),
        border = JBColor(Color(0x33, 0x88, 0x33), Color(0x4A, 0xA3, 0x5F)),
        text = JBColor(Color(0x1E, 0x2A, 0x1E), Color(0xF4, 0xFF, 0xF4))
    )

    private fun actionColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xF7, 0xE7, 0xBF), Color(0x5A, 0x43, 0x15)),
        border = JBColor(Color(0xB8, 0x87, 0x1A), Color(0xE1, 0xB2, 0x4A)),
        text = JBColor(Color(0x4A, 0x32, 0x00), Color(0xFF, 0xF7, 0xE6))
    )

    private fun middlewareColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xE7, 0xDD, 0xF7), Color(0x3E, 0x2A, 0x5E)),
        border = JBColor(Color(0x7A, 0x59, 0xB5), Color(0xA9, 0x8A, 0xE0)),
        text = JBColor(Color(0x35, 0x21, 0x4F), Color(0xF8, 0xF2, 0xFF))
    )

    private fun reducerColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xF7, 0xD9, 0xD6), Color(0x5A, 0x2F, 0x2A)),
        border = JBColor(Color(0xC5, 0x6A, 0x5D), Color(0xE0, 0x8A, 0x7E)),
        text = JBColor(Color(0x4E, 0x21, 0x1C), Color(0xFF, 0xF5, 0xF4))
    )

    private fun stateColors(): NodeColors = NodeColors(
        fill = JBColor(Color(0xD8, 0xF1, 0xE4), Color(0x1E, 0x53, 0x45)),
        border = JBColor(Color(0x4A, 0xA6, 0x82), Color(0x6F, 0xCF, 0xAA)),
        text = JBColor(Color(0x14, 0x3A, 0x30), Color(0xEE, 0xFF, 0xF8))
    )
}

data class NodeColors(
    val fill: Color,
    val border: Color,
    val text: Color
)

data class LegendEntry(
    val label: String,
    val colors: NodeColors
)
