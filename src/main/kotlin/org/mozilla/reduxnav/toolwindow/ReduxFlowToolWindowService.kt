package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.navigation.HistoryListener
import com.intellij.ui.navigation.Place
import org.mozilla.reduxnav.analysis.ReduxActionGraphCache
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.state.StateFieldInfo
import org.mozilla.reduxnav.state.StateModificationCache

@Service(Service.Level.PROJECT)
class ReduxFlowToolWindowService(private val project: Project) {
    private val logger = Logger.getInstance(ReduxFlowToolWindowService::class.java)
    private val historyController = ReduxPaneHistoryController(::navigateFromHistory)
    val component = ReduxFlowPanel(project, historyController.history) { refreshCurrent() }

    private var selectedAction: ActionInfo? = null
    private var selectedStateField: StateFieldInfo? = null
    private var refreshRequestId: Long = 0

    init {
        historyController.history.addListener(
            object : HistoryListener {
                override fun navigationFinished(from: Place?, to: Place?) {
                    component.updatePaneHistoryStatus(canGoBack(), canGoForward())
                }
            },
            component
        )
    }

    fun showFlow(action: ActionInfo) {
        showLocation(ReduxPaneLocation.Action(action), recordHistory = true, requestFocus = true)
    }

    fun showState(field: StateFieldInfo) {
        showLocation(ReduxPaneLocation.State(field), recordHistory = true, requestFocus = true)
    }

    fun canGoBack(): Boolean = historyController.canGoBack()

    fun canGoForward(): Boolean = historyController.canGoForward()

    fun goBack() {
        historyController.goBack()
    }

    fun goForward() {
        historyController.goForward()
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
        val requestId = ++refreshRequestId
        component.showActionLoading(action)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                val graph = ReadAction.nonBlocking<org.mozilla.reduxnav.model.ActionGraph?> {
                    if (action.declaration?.element?.isValid != true) null
                    else ReduxActionGraphCache.getInstance(project).getGraph(action)
                }.executeSynchronously()
                if (graph == null) ActionRefreshResult.Invalid else ActionRefreshResult.Graph(graph)
            } catch (t: Throwable) {
                logger.warn("Could not build Redux flow for action ${action.displayName}", t)
                ActionRefreshResult.Error
            }
            ApplicationManager.getApplication().invokeLater({
                if (!isLatestActionRequest(action, requestId)) return@invokeLater
                when (result) {
                    is ActionRefreshResult.Graph -> component.showGraph(result.graph)
                    ActionRefreshResult.Invalid -> component.showInvalidAction(action)
                    ActionRefreshResult.Error -> component.showAnalysisError(action)
                }
            }, project.disposed)
        }
    }

    private fun refreshState(field: StateFieldInfo) {
        val requestId = ++refreshRequestId
        component.showStateLoading(field)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val graph = ReadAction.nonBlocking<org.mozilla.reduxnav.state.StateFieldGraph?> {
                    if (field.declarationPointer != null && field.declarationPointer.element?.isValid != true) null
                    else StateModificationCache.getInstance(project).getGraph(field)
                }.executeSynchronously()
                if (graph == null) {
                    ApplicationManager.getApplication().invokeLater({
                        if (isLatestStateRequest(field, requestId)) component.showInvalidState(field)
                    }, project.disposed)
                    return@executeOnPooledThread
                }
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
            requestId == refreshRequestId &&
            selectedStateField?.id == field.id &&
            selectedAction == null

    private fun isLatestActionRequest(action: ActionInfo, requestId: Long): Boolean =
        !project.isDisposed &&
            requestId == refreshRequestId &&
            selectedAction?.id == action.id &&
            selectedStateField == null

    private fun navigateFromHistory(location: ReduxPaneLocation, requestFocus: Boolean) {
        showLocation(location, recordHistory = false, requestFocus = requestFocus)
    }

    internal fun showLocation(
        location: ReduxPaneLocation,
        recordHistory: Boolean,
        requestFocus: Boolean = true
    ) {
        if (recordHistory) {
            historyController.record(location)
        }
        when (location) {
            is ReduxPaneLocation.Action -> {
                selectedAction = location.action
                selectedStateField = null
            }
            is ReduxPaneLocation.State -> {
                selectedStateField = location.field
                selectedAction = null
            }
        }
        if (requestFocus) {
            focusToolWindow()
        }
        refreshCurrent()
        component.updatePaneHistoryStatus(canGoBack(), canGoForward())
    }

    internal fun resetForTest() {
        selectedAction = null
        selectedStateField = null
        refreshRequestId = 0
        historyController.clear()
        component.showEmptyState()
        component.updatePaneHistoryStatus(canGoBack(), canGoForward())
    }

    companion object {
        const val TOOL_WINDOW_ID = "Redux Flow"

        fun getInstance(project: Project): ReduxFlowToolWindowService = project.service()
    }

    private sealed interface ActionRefreshResult {
        data class Graph(val graph: org.mozilla.reduxnav.model.ActionGraph) : ActionRefreshResult
        data object Invalid : ActionRefreshResult
        data object Error : ActionRefreshResult
    }
}
