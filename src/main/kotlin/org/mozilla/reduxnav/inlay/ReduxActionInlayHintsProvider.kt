package org.mozilla.reduxnav.inlay

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
import org.mozilla.reduxnav.popup.isTestPath
import org.mozilla.reduxnav.settings.ReduxNavigatorSettingsService
import java.awt.event.MouseEvent

class ReduxActionInlayHintsProvider : CodeVisionProviderBase() {
    private val logger = Logger.getInstance(ReduxActionInlayHintsProvider::class.java)
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
        logger.info(
            "[redux-nav] lens-get-hint action=${action.displayName} file=${file.virtualFile?.path} " +
                "project=${element.project.name}"
        )
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
        val includeTestFiles = com.intellij.openapi.components.service<ReduxNavigatorSettingsService>().includeTestFilesInLens
        logger.info(
            "[redux-nav] lens-build-hint action=${action.displayName} includeTestFiles=$includeTestFiles " +
                "graphUsages=${graph.usages.size}"
        )

        return buildHintText(graph.usages, includeTestFiles)
    }

    internal fun buildHintText(usages: List<org.mozilla.reduxnav.model.ReduxUsage>, includeTestFiles: Boolean): String {
        val filteredUsages = if (includeTestFiles) {
            usages
        } else {
            usages.filterNot { isTestPath(it.filePath) }
        }

        logger.info(
            "[redux-nav] lens-build-hint-filtered includeTestFiles=$includeTestFiles " +
                "before=${usages.size} after=${filteredUsages.size}"
        )

        val dispatchCount = filteredUsages.count { it.kind == ReduxUsageKind.DISPATCH }
        val middlewareCount = filteredUsages.count { it.kind == ReduxUsageKind.MIDDLEWARE }
        val reducerCount = filteredUsages.count { it.kind == ReduxUsageKind.REDUCER }

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
