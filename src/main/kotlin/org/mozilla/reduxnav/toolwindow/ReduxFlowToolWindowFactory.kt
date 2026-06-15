package org.mozilla.reduxnav.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

class ReduxFlowToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = ReduxFlowToolWindowService.getInstance(project)
        val host = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(service.component, BorderLayout.CENTER)
        }
        DataManager.registerDataProvider(host, service.component)
        val content = ContentFactory.getInstance().createContent(host, "", false)
        content.setDisposer(service.component)
        toolWindow.contentManager.addContent(content)
    }
}
