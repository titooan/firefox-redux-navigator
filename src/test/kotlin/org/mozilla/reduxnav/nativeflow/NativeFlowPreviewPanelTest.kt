package org.mozilla.reduxnav.nativeflow

import org.junit.Assert.assertEquals
import org.junit.Test
import javax.swing.JViewport

class NativeFlowPreviewPanelTest {
    @Test
    fun previewUsesSimpleViewportScrollingToAvoidScrollArtifacts() {
        val panel = NativeFlowPreviewPanel()

        assertEquals(JViewport.SIMPLE_SCROLL_MODE, panel.viewportScrollMode())
    }
}
