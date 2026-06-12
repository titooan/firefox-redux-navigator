package org.mozilla.reduxnav.render

import com.intellij.util.ui.StartupUiUtil

class MermaidHtmlRenderer(
    private val mermaidScriptLoader: () -> String? = { loadBundledScript() }
) {
    fun renderHtml(mermaidSource: String): String {
        val mermaidScript = mermaidScriptLoader()
            ?.replace("</script", "<\\/script")
            ?: return renderErrorHtml(MISSING_RESOURCE_MESSAGE)
        val escapedSource = escapeHtml(mermaidSource)
        val escapedErrorMessage = escapeHtml(MISSING_RENDER_ERROR_MESSAGE)
        val theme = if (StartupUiUtil.isUnderDarcula) "dark" else "default"
        val testNodeFill = if (theme == "dark") "#2f5a34" else "#d7ead7"
        val testNodeText = if (theme == "dark") "#f5fff5" else "#1e2a1e"

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                html, body {
                  height: 100%;
                }
                body {
                  margin: 0;
                  font-family: system-ui, sans-serif;
                  background: ${if (theme == "dark") "#1e1f22" else "#ffffff"};
                  color: ${if (theme == "dark") "#d8d8d8" else "#222222"};
                  overflow: hidden;
                }
                .layout {
                  display: flex;
                  flex-direction: column;
                  height: 100%;
                }
                .toolbar {
                  display: flex;
                  gap: 8px;
                  align-items: center;
                  padding: 12px 16px 8px 16px;
                  border-bottom: 1px solid ${if (theme == "dark") "#3c3f41" else "#d9d9d9"};
                }
                .toolbar button {
                  border: 1px solid ${if (theme == "dark") "#5f6368" else "#c7c7c7"};
                  background: ${if (theme == "dark") "#2b2d30" else "#f6f6f6"};
                  color: inherit;
                  border-radius: 6px;
                  padding: 4px 10px;
                  cursor: pointer;
                }
                .toolbar button:hover {
                  background: ${if (theme == "dark") "#35373b" else "#ececec"};
                }
                .hint {
                  font-size: 12px;
                  opacity: 0.75;
                }
                .viewport {
                  flex: 1;
                  overflow: auto;
                  padding: 16px;
                  touch-action: none;
                }
                .diagram-host {
                  display: inline-block;
                  transform-origin: top left;
                  min-width: max-content;
                }
                .mermaid {
                  width: max-content;
                }
                .testNode rect,
                .testNode polygon,
                .testNode path,
                .testNode .label-container {
                  fill: $testNodeFill !important;
                }
                .testNode .label,
                .testNode .nodeLabel,
                .testNode span,
                .testNode p,
                .testNode foreignObject {
                  color: $testNodeText !important;
                  fill: $testNodeText !important;
                }
                .error {
                  padding: 16px;
                  color: #b00020;
                  white-space: pre-wrap;
                  font-family: monospace;
                }
              </style>
            </head>
            <body>
              <div class="layout">
                <div class="toolbar">
                  <button type="button" onclick="zoomOut()">-</button>
                  <button type="button" onclick="zoomIn()">+</button>
                  <button type="button" onclick="resetZoom()">Reset</button>
                  <span class="hint">Pinch or Ctrl/Cmd + wheel to zoom. Scroll to pan horizontally and vertically.</span>
                </div>
                <div class="viewport" id="viewport">
                  <div class="diagram-host" id="diagram-host">
                    <pre class="mermaid">$escapedSource</pre>
                  </div>
                </div>
              </div>
              <script>
              $mermaidScript
              </script>
              <script>
                let zoomLevel = 1;
                let pinchStartDistance = null;
                let pinchStartZoom = 1;
                let gestureStartZoom = 1;

                function escapeHtml(value) {
                  return value
                    .replace(/&/g, '&amp;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;')
                    .replace(/"/g, '&quot;')
                    .replace(/'/g, '&#39;');
                }

                function formatError(error) {
                  if (error == null) {
                    return "$escapedErrorMessage";
                  }
                  if (typeof error === 'string') {
                    return error;
                  }
                  if (error instanceof Error) {
                    return error.stack || error.message || String(error);
                  }
                  try {
                    return JSON.stringify(error, null, 2);
                  } catch (jsonError) {
                    return String(error);
                  }
                }

                function applyZoom() {
                  const host = document.getElementById('diagram-host');
                  if (!host) return;
                  host.style.transform = 'scale(' + zoomLevel + ')';
                }

                function setZoom(nextZoom) {
                  zoomLevel = Math.min(Math.max(nextZoom, 0.4), 3);
                  applyZoom();
                }

                function zoomIn() {
                  setZoom(zoomLevel + 0.1);
                }

                function zoomOut() {
                  setZoom(zoomLevel - 0.1);
                }

                function resetZoom() {
                  setZoom(1);
                }

                function touchDistance(touches) {
                  const dx = touches[0].clientX - touches[1].clientX;
                  const dy = touches[0].clientY - touches[1].clientY;
                  return Math.sqrt(dx * dx + dy * dy);
                }

                function installViewportInteractions() {
                  const viewport = document.getElementById('viewport');
                  if (!viewport) return;
                  viewport.addEventListener('wheel', function(event) {
                    if (!(event.ctrlKey || event.metaKey)) {
                      return;
                    }
                    event.preventDefault();
                    if (event.deltaY < 0) {
                      zoomIn();
                    } else {
                      zoomOut();
                    }
                  }, { passive: false });
                  viewport.addEventListener('touchstart', function(event) {
                    if (event.touches.length !== 2) {
                      return;
                    }
                    event.preventDefault();
                    pinchStartDistance = touchDistance(event.touches);
                    pinchStartZoom = zoomLevel;
                  }, { passive: false });
                  viewport.addEventListener('touchmove', function(event) {
                    if (event.touches.length !== 2 || pinchStartDistance === null) {
                      return;
                    }
                    event.preventDefault();
                    const currentDistance = touchDistance(event.touches);
                    setZoom(pinchStartZoom * (currentDistance / pinchStartDistance));
                  }, { passive: false });
                  viewport.addEventListener('touchend', function() {
                    if (pinchStartDistance === null) {
                      return;
                    }
                    pinchStartDistance = null;
                    pinchStartZoom = zoomLevel;
                  });
                  viewport.addEventListener('gesturestart', function(event) {
                    event.preventDefault();
                    gestureStartZoom = zoomLevel;
                  }, { passive: false });
                  viewport.addEventListener('gesturechange', function(event) {
                    event.preventDefault();
                    const scale = typeof event.scale === 'number' ? event.scale : 1;
                    setZoom(gestureStartZoom * scale);
                  }, { passive: false });
                  viewport.addEventListener('gestureend', function(event) {
                    event.preventDefault();
                    const scale = typeof event.scale === 'number' ? event.scale : 1;
                    setZoom(gestureStartZoom * scale);
                  }, { passive: false });
                }

                mermaid.initialize({
                  startOnLoad: false,
                  securityLevel: "strict",
                  theme: "$theme"
                });

                async function renderDiagram() {
                  try {
                    await mermaid.run({ querySelector: ".mermaid" });
                    installViewportInteractions();
                    applyZoom();
                  } catch (error) {
                    document.body.innerHTML =
                      '<pre class="error">' + escapeHtml(formatError(error)) + '</pre>';
                  }
                }

                renderDiagram();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun hasBundledScript(): Boolean = mermaidScriptLoader() != null

    private fun renderErrorHtml(message: String): String = """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            body {
              margin: 0;
              padding: 16px;
              font-family: system-ui, sans-serif;
            }
            .error {
              color: #b00020;
              white-space: pre-wrap;
              font-family: monospace;
            }
          </style>
        </head>
        <body>
          <pre class="error">${escapeHtml(message)}</pre>
        </body>
        </html>
    """.trimIndent()

    private fun escapeHtml(text: String): String = buildString {
        text.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

    companion object {
        const val MISSING_RESOURCE_MESSAGE = "Mermaid renderer resource is missing. Use Mermaid Source tab instead."
        private const val MISSING_RENDER_ERROR_MESSAGE = "Mermaid diagram could not be rendered."

        private fun loadBundledScript(): String? =
            MermaidHtmlRenderer::class.java.classLoader
                .getResourceAsStream("mermaid/mermaid.min.js")
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
    }
}
