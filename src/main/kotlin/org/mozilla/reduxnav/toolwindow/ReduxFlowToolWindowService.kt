package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
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
    private var stateRefreshRequestId: Long = 0

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
        stateRefreshRequestId = 0
        historyController.clear()
        component.showEmptyState()
        component.updatePaneHistoryStatus(canGoBack(), canGoForward())
    }

    companion object {
        const val TOOL_WINDOW_ID = "Redux Flow"

        fun getInstance(project: Project): ReduxFlowToolWindowService = project.service()
    }
}
