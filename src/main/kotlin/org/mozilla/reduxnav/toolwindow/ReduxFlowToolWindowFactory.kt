package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ReduxFlowToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = ReduxFlowToolWindowService.getInstance(project)
        val content = ContentFactory.getInstance().createContent(service.component, "", false)
        content.setDisposer(service.component)
        toolWindow.contentManager.addContent(content)
    }
}
