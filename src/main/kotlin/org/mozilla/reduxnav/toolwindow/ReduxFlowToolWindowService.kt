package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.mozilla.reduxnav.analysis.ReduxActionGraphCache
import org.mozilla.reduxnav.model.ActionInfo

@Service(Service.Level.PROJECT)
class ReduxFlowToolWindowService(private val project: Project) {
    private val logger = Logger.getInstance(ReduxFlowToolWindowService::class.java)
    val component = ReduxFlowPanel(project) { refreshCurrent() }

    private var selectedAction: ActionInfo? = null

    fun showFlow(action: ActionInfo) {
        selectedAction = action
        focusToolWindow()
        refreshCurrent()
    }

    fun focusToolWindow() {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
    }

    fun refreshCurrent() {
        val action = selectedAction
        if (action == null) {
            component.showEmptyState()
            return
        }

        val declaration = action.declaration?.element
        if (declaration == null || !declaration.isValid) {
            component.showInvalidAction(action)
            return
        }

        try {
            val graph = ReduxActionGraphCache.getInstance(project).getGraph(action)
            component.showGraph(graph)
        } catch (t: Throwable) {
            logger.warn("Could not build Redux flow for action ${action.displayName}", t)
            component.showAnalysisError(action)
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Redux Flow"

        fun getInstance(project: Project): ReduxFlowToolWindowService = project.service()
    }
}
