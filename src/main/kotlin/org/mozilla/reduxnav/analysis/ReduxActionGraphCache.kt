package org.mozilla.reduxnav.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiModificationTracker
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo

@Service(Service.Level.PROJECT)
class ReduxActionGraphCache(private val project: Project) {
    private data class CacheEntry(
        val modificationCount: Long,
        val graph: ActionGraph
    )

    private val graphs = linkedMapOf<String, CacheEntry>()

    @Synchronized
    fun getGraph(action: ActionInfo): ActionGraph {
        val modificationCount = PsiModificationTracker.getInstance(project).modificationCount
        if (graphs.isNotEmpty() && graphs.values.first().modificationCount != modificationCount) {
            graphs.clear()
        }

        val cached = graphs[action.id.value]
        if (cached != null && cached.modificationCount == modificationCount) {
            return cached.graph
        }

        val graph = ReduxUsageFinder(project).buildGraph(action)
        graphs[action.id.value] = CacheEntry(modificationCount, graph)
        return graph
    }

    companion object {
        fun getInstance(project: Project): ReduxActionGraphCache = project.service()
    }
}
