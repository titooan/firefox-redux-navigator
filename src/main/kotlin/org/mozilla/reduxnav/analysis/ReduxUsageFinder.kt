package org.mozilla.reduxnav.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
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
        val declaration = action.declaration?.element ?: return@compute ActionGraph(action, emptyList())
        val scope = GlobalSearchScope.projectScope(project)
        val usages = ReferencesSearch.search(declaration, scope)
            .findAll()
            .mapNotNull { reference ->
                val element = reference.element ?: return@mapNotNull null
                val file = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: "<unknown>"
                ReduxUsage(
                    kind = classifier.classify(element),
                    displayText = "${element.containingFile?.name ?: "<unknown>"}:${element.safeLineNumber()}  ${element.lineText()}",
                    filePath = file,
                    line = element.safeLineNumber(),
                    element = element.toSmartPointer()
                )
            }
            .sortedWith(compareBy<ReduxUsage> { it.kind.ordinal }.thenBy { it.filePath }.thenBy { it.line })

        ActionGraph(action, usages)
    }
}
