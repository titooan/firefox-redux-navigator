package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
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
        val action = resolveAction(project, editor, file)
        if (action == null) {
            StatusBar.Info.set("No Redux Action found at caret.", project)
            return
        }

        ReduxFlowToolWindowService.getInstance(project).showFlow(action)
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
        val element = ApplicationManager.getApplication().runReadAction<com.intellij.psi.PsiElement?> {
            file.findElementAt(offset)
        } ?: return null
        return resolver.resolve(element) ?: resolver.resolveDeclaration(element)
    }
}
