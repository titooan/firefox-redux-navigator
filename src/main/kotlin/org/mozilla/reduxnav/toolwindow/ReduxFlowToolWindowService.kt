package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import org.mozilla.reduxnav.analysis.ReduxActionGraphCache
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.state.StateFieldInfo
import org.mozilla.reduxnav.state.StateModificationCache

@Service(Service.Level.PROJECT)
class ReduxFlowToolWindowService(private val project: Project) {
    private val logger = Logger.getInstance(ReduxFlowToolWindowService::class.java)
    val component = ReduxFlowPanel(project) { refreshCurrent() }

    private var selectedAction: ActionInfo? = null
    private var selectedStateField: StateFieldInfo? = null
    private var stateRefreshRequestId: Long = 0

    fun showFlow(action: ActionInfo) {
        selectedAction = action
        selectedStateField = null
        focusToolWindow()
        refreshCurrent()
    }

    fun showState(field: StateFieldInfo) {
        selectedStateField = field
        selectedAction = null
        focusToolWindow()
        refreshCurrent()
    }

    fun focusToolWindow() {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()
    }

    fun refreshCurrent() {
        val action = selectedAction
        val stateField = selectedStateField
        if (action == null && stateField == null) {
            component.showEmptyState()
            return
        }

        if (action != null) {
            refreshAction(action)
            return
        }

        refreshState(stateField ?: return)
    }

    private fun refreshAction(action: ActionInfo) {
        val declaration = ReadAction.compute<PsiElement?, RuntimeException> {
            action.declaration?.element?.takeIf { it.isValid }
        }
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

    private fun refreshState(field: StateFieldInfo) {
        val declaration = ReadAction.compute<PsiElement?, RuntimeException> {
            field.declarationPointer?.element?.takeIf { it.isValid }
        }
        if (field.declarationPointer != null && (declaration == null || !declaration.isValid)) {
            component.showInvalidState(field)
            return
        }

        val requestId = ++stateRefreshRequestId
        component.showStateLoading(field)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val graph = StateModificationCache.getInstance(project).getGraph(field)
                ApplicationManager.getApplication().invokeLater(
                    {
                        if (!isLatestStateRequest(field, requestId)) return@invokeLater
                        component.showStateGraph(graph)
                    },
                    project.disposed
                )
            } catch (t: Throwable) {
                logger.warn("Could not build state explorer for field ${field.qualifiedPath}", t)
                ApplicationManager.getApplication().invokeLater(
                    {
                        if (!isLatestStateRequest(field, requestId)) return@invokeLater
                        component.showStateAnalysisError(field)
                    },
                    project.disposed
                )
            }
        }
    }

    private fun isLatestStateRequest(field: StateFieldInfo, requestId: Long): Boolean =
        !project.isDisposed &&
            requestId == stateRefreshRequestId &&
            selectedStateField?.id == field.id &&
            selectedAction == null

    companion object {
        const val TOOL_WINDOW_ID = "Redux Flow"

        fun getInstance(project: Project): ReduxFlowToolWindowService = project.service()
    }
}
