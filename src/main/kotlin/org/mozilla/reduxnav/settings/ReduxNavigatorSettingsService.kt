package org.mozilla.reduxnav.settings

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.ProjectManager

@Service(Service.Level.APP)
@State(
    name = "ReduxNavigatorSettings",
    storages = [Storage("redux-navigator-settings.xml")]
)
class ReduxNavigatorSettingsService : PersistentStateComponent<ReduxNavigatorSettingsService.State> {
    data class State(
        var includeTestFilesInLens: Boolean = true
    )

    private var state = State()

    private val logger = Logger.getInstance(ReduxNavigatorSettingsService::class.java)

    val includeTestFilesInLens: Boolean
        get() = state.includeTestFilesInLens

    fun setIncludeTestFilesInLens(includeTestFiles: Boolean) {
        logger.info("[redux-nav] settings-update requested includeTestFilesInLens=$includeTestFiles current=${state.includeTestFilesInLens}")
        if (state.includeTestFilesInLens == includeTestFiles) return
        state.includeTestFilesInLens = includeTestFiles
        logger.info("[redux-nav] settings-update applied includeTestFilesInLens=${state.includeTestFilesInLens}")
        refreshOpenProjects()
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        logger.info("[redux-nav] settings-load includeTestFilesInLens=${state.includeTestFilesInLens}")
    }

    private fun refreshOpenProjects() {
        logger.info("[redux-nav] settings-refresh codeVision start openProjects=${ProjectManager.getInstance().openProjects.size}")
        ProjectManager.getInstance().openProjects.forEach { project ->
            logger.info("[redux-nav] settings-refresh codeVision project=${project.name} path=${project.basePath}")
            val host = project.service<CodeVisionHost>()
            logger.info("[redux-nav] settings-refresh codeVision host=${host.javaClass.name}")
            host.recollectAndRearrangeProviders()
            val fileEditorManager = FileEditorManager.getInstance(project)
            val fileDocumentManager = FileDocumentManager.getInstance()
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            logger.info("[redux-nav] settings-refresh daemon restart project=${project.name} openFiles=${fileEditorManager.openFiles.size}")
            fileEditorManager.openFiles.forEach { virtualFile ->
                val document = fileDocumentManager.getDocument(virtualFile)
                val psiFile: PsiFile? = document?.let { psiDocumentManager.getPsiFile(it) }
                logger.info(
                    "[redux-nav] settings-refresh daemon file=${virtualFile.path} " +
                        "psi=${psiFile?.virtualFile?.path ?: "null"}"
                )
                if (psiFile != null) {
                    // CodeVisionPassFactory skips pass creation when the PSI modification stamp
                    // stored on the editor matches the current file stamp. Since a settings change
                    // doesn't modify the file, we must clear the stored stamp so the factory
                    // creates a new pass on the next daemon run.
                    fileEditorManager.getEditors(virtualFile).forEach { fileEditor ->
                        if (fileEditor is TextEditor) {
                            ModificationStampUtil.clearModificationStamp(fileEditor.editor)
                            logger.info("[redux-nav] settings-refresh stamp-cleared editor=${virtualFile.path}")
                        }
                    }
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                }
            }
        }
        logger.info("[redux-nav] settings-refresh codeVision done")
    }
}
