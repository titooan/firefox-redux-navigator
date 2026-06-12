package org.mozilla.reduxnav.state

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.mozilla.reduxnav.analysis.FirefoxReduxConventions

@Service(Service.Level.PROJECT)
class StateProjectAnalysisCache(
    private val project: Project
) {
    private data class CacheEntry<T>(
        val modificationCount: Long,
        val value: T
    )

    private val psiManager = PsiManager.getInstance(project)
    private val analyzer = StateModificationAnalyzer(project)
    private val conventions = FirefoxReduxConventions()
    private var reducerCandidateFilesEntry: CacheEntry<List<KtFile>>? = null
    private var allModificationsEntry: CacheEntry<List<StateModification>>? = null
    private val fileModifications = linkedMapOf<String, CacheEntry<List<StateModification>>>()

    @Synchronized
    fun reducerCandidateFiles(): List<KtFile> {
        val modificationCount = modificationCount()
        reducerCandidateFilesEntry?.takeIf { it.modificationCount == modificationCount }?.let { return it.value }

        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .asSequence()
            .mapNotNull { file -> psiManager.findFile(file) as? KtFile }
            .filter { it.mayContainReducer() }
            .toList()

        reducerCandidateFilesEntry = CacheEntry(modificationCount, files)
        return files
    }

    @Synchronized
    fun allModifications(): List<StateModification> {
        val modificationCount = modificationCount()
        allModificationsEntry?.takeIf { it.modificationCount == modificationCount }?.let { return it.value }

        val modifications = reducerCandidateFiles()
            .asSequence()
            .flatMap { file -> fileModifications(file).asSequence() }
            .distinctBy { "${it.filePath}:${it.lineNumber}:${it.snippet}" }
            .sortedWith(compareBy<StateModification> { it.filePath }.thenBy { it.lineNumber })
            .toList()

        allModificationsEntry = CacheEntry(modificationCount, modifications)
        return modifications
    }

    @Synchronized
    fun fileModifications(file: KtFile): List<StateModification> {
        val modificationCount = modificationCount()
        val filePath = file.virtualFile?.path ?: file.name
        fileModifications[filePath]?.takeIf { it.modificationCount == modificationCount }?.let { return it.value }

        val modifications = analyzer.collectFromFile(file)
        fileModifications[filePath] = CacheEntry(modificationCount, modifications)
        return modifications
    }

    @Synchronized
    fun invalidate() {
        reducerCandidateFilesEntry = null
        allModificationsEntry = null
        fileModifications.clear()
    }

    private fun modificationCount(): Long {
        val modificationCount = PsiModificationTracker.getInstance(project).modificationCount
        if (reducerCandidateFilesEntry?.modificationCount != null &&
            reducerCandidateFilesEntry?.modificationCount != modificationCount
        ) {
            invalidate()
        }
        return modificationCount
    }

    private fun KtFile.mayContainReducer(): Boolean {
        val text = text
        return conventions.reducerNameHints.any { hint -> text.contains(hint, ignoreCase = true) }
    }

    companion object {
        fun getInstance(project: Project): StateProjectAnalysisCache = project.service()
    }
}
