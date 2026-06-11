package org.mozilla.reduxnav.inlay

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.analysis.ReduxActionGraphCache
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.popup.ReduxActionPopup
import java.awt.event.MouseEvent

class ReduxActionInlayHintsProvider : CodeVisionProviderBase() {
    private val resolver = ActionSymbolResolver()

    override fun acceptsFile(file: PsiFile): Boolean = file.language == KotlinLanguage.INSTANCE

    override fun acceptsElement(element: PsiElement): Boolean =
        when (element) {
            is KtClassOrObject -> resolveAction(element) != null
            else -> {
                val parent = element.parent as? KtClassOrObject ?: return false
                parent.nameIdentifier == element && resolveAction(element) != null
            }
        }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val action = resolveAction(element) ?: return null
        return buildHintText(element.project, action)
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val action = resolveAction(element) ?: return
        ReduxActionPopup.show(element.project, action, event)
    }

    override val name: String = "Redux action usages"

    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top

    override val id: String = PROVIDER_ID

    internal fun buildHintText(project: Project, action: ActionInfo): String {
        val graph = project.service<ReduxActionGraphCache>().getGraph(action)
        val dispatchCount = graph.usages.count { it.kind == ReduxUsageKind.DISPATCH }
        val middlewareCount = graph.usages.count { it.kind == ReduxUsageKind.MIDDLEWARE }
        val reducerCount = graph.usages.count { it.kind == ReduxUsageKind.REDUCER }

        val parts = buildList {
            if (dispatchCount > 0) add(formatCount(dispatchCount, "dispatch"))
            if (middlewareCount > 0) add(formatCount(middlewareCount, "middleware"))
            if (reducerCount > 0) add(formatCount(reducerCount, "reducer"))
        }

        return if (parts.isEmpty()) {
            "Redux: no related usages found"
        } else {
            "Redux: ${parts.joinToString(" | ")}"
        }
    }

    internal fun resolveAction(element: PsiElement): ActionInfo? =
        resolver.resolveDeclaration(element)

    private fun formatCount(count: Int, singular: String): String =
        if (count == 1) {
            "1 $singular"
        } else {
            "$count ${pluralize(singular)}"
        }

    private fun pluralize(word: String): String = when (word) {
        "dispatch" -> "dispatches"
        "middleware" -> "middlewares"
        "reducer" -> "reducers"
        else -> "${word}s"
    }

    companion object {
        const val PROVIDER_ID = "redux.action.usages"
    }
}
