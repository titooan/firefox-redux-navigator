package org.mozilla.reduxnav.state

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class StateModificationCache(private val project: Project) {
    private data class CacheEntry(
        val modificationCount: Long,
        val graph: StateFieldGraph
    )

    private val graphs = linkedMapOf<String, CacheEntry>()
    private val analysisCache = StateProjectAnalysisCache.getInstance(project)

    @Synchronized
    fun getGraph(field: StateFieldInfo): StateFieldGraph {
        val modificationCount = PsiModificationTracker.getInstance(project).modificationCount
        if (graphs.isNotEmpty() && graphs.values.first().modificationCount != modificationCount) {
            graphs.clear()
        }

        val cached = graphs[field.id]
        if (cached != null && cached.modificationCount == modificationCount) {
            return cached.graph
        }

        val graph = StateFieldGraph(
            stateField = field,
            modifications = analysisCache.allModifications()
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
        graphs[field.id] = CacheEntry(modificationCount, graph)
        return graph
    }

    @Synchronized
    fun invalidate() {
        graphs.clear()
        analysisCache.invalidate()
    }

    companion object {
        fun getInstance(project: Project): StateModificationCache = project.service()
    }
}
