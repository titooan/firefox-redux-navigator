package org.mozilla.reduxnav.render

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MermaidHtmlRendererTest : BasePlatformTestCase() {
    fun testRenderHtmlEscapesMermaidSource() {
        val renderer = MermaidHtmlRenderer { "window.mermaid = { initialize() {}, run: async function() {} };" }

        val html = renderer.renderHtml("""flowchart LR
A["x < y & z "quote" 'apostrophe'"]""")

        assertTrue(html.contains("x &lt; y &amp; z &quot;quote&quot; &#39;apostrophe&#39;"))
    }

    fun testRenderHtmlContainsMermaidInitialization() {
        val renderer = MermaidHtmlRenderer { "window.mermaid = { initialize() {}, run: async function() {} };" }

        val html = renderer.renderHtml("flowchart LR\naction[\"AddTabAction\"]")

        assertTrue(html.contains("mermaid.initialize"))
        assertTrue(html.contains("securityLevel: \"strict\""))
        assertTrue(html.contains("renderDiagram()"))
        assertTrue(html.contains("installViewportInteractions()"))
        assertTrue(html.contains("formatError(error)"))
    }

    fun testRenderHtmlContainsDiagramSource() {
        val renderer = MermaidHtmlRenderer { "window.mermaid = { initialize() {}, run: async function() {} };" }

        val html = renderer.renderHtml("flowchart LR\naction[\"AddTabAction\"]")

        assertTrue(html.contains("flowchart LR"))
        assertTrue(html.contains("AddTabAction"))
        assertTrue(html.contains("class=\"viewport\""))
        assertTrue(html.contains(".testNode rect"))
        assertTrue(html.contains(".testNode .label"))
        assertTrue(html.contains("zoomIn()"))
        assertTrue(html.contains("zoomOut()"))
        assertTrue(html.contains("resetZoom()"))
        assertTrue(html.contains("gesturestart"))
        assertTrue(html.contains("gesturechange"))
        assertTrue(html.contains("touchstart"))
        assertTrue(html.contains("touchmove"))
    }

    fun testMissingMermaidResourceIsHandledGracefully() {
        val renderer = MermaidHtmlRenderer { null }

        val html = renderer.renderHtml("flowchart LR\naction[\"AddTabAction\"]")

        assertTrue(html.contains(MermaidHtmlRenderer.MISSING_RESOURCE_MESSAGE))
    }
}
