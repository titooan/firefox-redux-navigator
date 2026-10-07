package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.psi.PsiFile
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.model.ActionInfo

class ShowReduxFlowAction : AnAction() {
    private val resolver = ActionSymbolResolver()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = editor.caretModel.offset
        submitModelRead(project, {
            if (!file.isValid) null else file.findElementAt(offset)?.let { element ->
                resolver.resolve(element) ?: resolver.resolveDeclaration(element)
            }
        }) { action ->
            if (project.isDisposed) return@submitModelRead
            if (action == null) {
                StatusBar.Info.set("No Redux Action found at caret.", project)
            } else {
                ReduxFlowToolWindowService.getInstance(project).showFlow(action)
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.PSI_FILE)
        event.presentation.isEnabledAndVisible =
            project != null && editor != null && file != null && resolveAction(project, editor, file) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    internal fun resolveAction(project: Project, editor: Editor, file: PsiFile): ActionInfo? {
        val offset = editor.caretModel.offset
        return ReadAction.nonBlocking<ActionInfo?> {
            val element = file.findElementAt(offset) ?: return@nonBlocking null
            resolver.resolve(element) ?: resolver.resolveDeclaration(element)
        }.executeSynchronously()
    }
}
