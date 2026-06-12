package org.mozilla.reduxnav.state

import com.intellij.openapi.project.Project

class StateModificationFinder(
    private val project: Project,
    private val cache: StateProjectAnalysisCache = StateProjectAnalysisCache.getInstance(project)
) {
    fun findModifications(field: StateFieldInfo): StateFieldGraph =
        StateFieldGraph(
            stateField = field,
            modifications = cache.allModifications()
                .asSequence()
                .filter { it.stateField.matchesTarget(field) }
                .map { modification ->
                    modification.copy(
                        stateField = modification.stateField.copy(
                            declarationPointer = modification.stateField.declarationPointer ?: field.declarationPointer
                        )
                    )
                }
                .toList()
        )
}
