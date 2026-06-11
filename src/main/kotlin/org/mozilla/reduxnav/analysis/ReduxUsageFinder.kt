package org.mozilla.reduxnav.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.lineText
import org.mozilla.reduxnav.model.safeLineNumber
import org.mozilla.reduxnav.model.toSmartPointer

class ReduxUsageFinder(
    private val project: Project,
    private val classifier: ReduxUsageClassifier = ReduxUsageClassifier()
) {
    fun buildGraph(action: ActionInfo): ActionGraph = ReadAction.compute<ActionGraph, RuntimeException> {
        computeGraph(action)
    }

    internal fun computeGraph(action: ActionInfo): ActionGraph {
        val declaration = action.declaration?.element ?: return ActionGraph(action, emptyList())
        return CachedValuesManager.getCachedValue(declaration) {
            CachedValueProvider.Result.create(
                doComputeGraph(action, declaration),
                declaration.containingFile,
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun doComputeGraph(action: ActionInfo, declaration: com.intellij.psi.PsiElement): ActionGraph {
        val scope = GlobalSearchScope.projectScope(project)
        val usages = ReferencesSearch.search(declaration, scope)
            .findAll()
            .mapNotNull { reference ->
                val element = reference.element ?: return@mapNotNull null
                val file = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: "<unknown>"
                ReduxUsage(
                    kind = classifier.classify(element),
                    displayText = element.lineText(),
                    fileName = element.containingFile?.virtualFile?.name ?: element.containingFile?.name ?: "<unknown>",
                    filePath = file,
                    line = element.safeLineNumber(),
                    element = element.toSmartPointer()
                )
            }
            .sortedWith(compareBy<ReduxUsage> { it.kind.ordinal }.thenBy { it.filePath }.thenBy { it.line })

        return ActionGraph(action, usages)
    }
}
