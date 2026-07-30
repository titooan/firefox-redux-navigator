package org.mozilla.reduxnav.state

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.mozilla.reduxnav.toolwindow.ReduxFlowToolWindowService

class StateExplorerAction : AnAction() {
    private val resolver = StateFieldResolver()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val field = resolveStateField(project, editor, file)
        if (field == null) {
            StatusBar.Info.set("No Redux state field found at caret.", project)
            return
        }

        ReduxFlowToolWindowService.getInstance(project).showState(field)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val file = event.getData(CommonDataKeys.PSI_FILE)
        event.presentation.isEnabledAndVisible =
            project != null && editor != null && file != null && resolveStateField(project, editor, file) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    internal fun resolveStateField(project: Project, editor: Editor, file: PsiFile): StateFieldInfo? {
        val offset = editor.caretModel.offset
        val element = ReadAction.nonBlocking<PsiElement?> {
            file.findElementAt(offset)
        }.executeSynchronously() ?: return null
        return resolver.resolve(element)
    }
}
