package org.mozilla.reduxnav.inlay

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.mozilla.reduxnav.state.StateFieldInfo
import org.mozilla.reduxnav.state.StateFieldResolver
import org.mozilla.reduxnav.state.StateModificationCache
import org.mozilla.reduxnav.toolwindow.ReduxFlowToolWindowService
import java.awt.event.MouseEvent

class ReduxStateInlayHintsProvider : CodeVisionProviderBase() {
    private val logger = Logger.getInstance(ReduxStateInlayHintsProvider::class.java)
    private val resolver = StateFieldResolver()

    override fun acceptsFile(file: PsiFile): Boolean = file.language == KotlinLanguage.INSTANCE

    override fun acceptsElement(element: PsiElement): Boolean =
        when (element) {
            is KtParameter -> element.hasValOrVar() && resolveField(element) != null
            is KtProperty -> resolveField(element) != null
            else -> {
                val parent = element.parent
                when (parent) {
                    is KtParameter -> parent.nameIdentifier == element && parent.hasValOrVar() && resolveField(element) != null
                    is KtProperty -> parent.nameIdentifier == element && resolveField(element) != null
                    else -> false
                }
            }
        }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val field = resolveField(element) ?: return null
        logger.info(
            "[redux-nav] state-lens-get-hint field=${field.qualifiedPath} file=${file.virtualFile?.path} " +
                "project=${element.project.name}"
        )
        return buildHintText(element.project, field)
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val field = resolveField(element) ?: return
        ReduxFlowToolWindowService.getInstance(element.project).showState(field)
    }

    override val name: String = "Redux state changes"

    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top

    override val id: String = PROVIDER_ID

    internal fun buildHintText(project: Project, field: StateFieldInfo): String {
        val graph = StateModificationCache.getInstance(project).getGraph(field)
        return buildHintText(graph.modifications.size, graph.modifications.mapNotNull { it.action?.id }.distinct().size)
    }

    internal fun buildHintText(modificationCount: Int, actionCount: Int): String {
        if (modificationCount == 0) {
            return "State: no known reducer changes"
        }

        val actionText = formatCount(actionCount, "action")
        val reducerText = formatCount(modificationCount, "reducer change")
        return "State: $actionText | $reducerText"
    }

    internal fun resolveField(element: PsiElement): StateFieldInfo? =
        resolver.resolveDeclaration(element)?.takeIf { it.stateClassName?.contains("State", ignoreCase = true) == true }

    private fun formatCount(count: Int, singular: String): String =
        if (count == 1) {
            "1 $singular"
        } else {
            "$count ${pluralize(singular)}"
        }

    private fun pluralize(word: String): String = when (word) {
        "action" -> "actions"
        "reducer change" -> "reducer changes"
        else -> "${word}s"
    }

    companion object {
        const val PROVIDER_ID = "redux.state.changes"
    }
}
