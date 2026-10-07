package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.AppExecutorUtil

/** Runs PSI/VFS reads off the UI thread and delivers their result on the UI thread. */
internal fun <T> submitModelRead(
    project: Project,
    read: () -> T,
    onUi: (T) -> Unit
) {
    ReadAction.nonBlocking<T> { read() }
        .expireWith(project)
        .finishOnUiThread(ModalityState.any()) { value -> onUi(value) }
        .submit(AppExecutorUtil.getAppExecutorService())
}

internal fun navigateToPsiElement(
    project: Project,
    description: String,
    element: () -> PsiElement?,
    afterNavigate: (() -> Unit)? = null
) {
    val logger = Logger.getInstance("org.mozilla.reduxnav.navigation")
    submitModelRead(project, read@{
        val target = element()?.takeIf { it.isValid } ?: return@read null
        val file = target.containingFile?.virtualFile ?: return@read null
        OpenFileDescriptor(project, file, target.textOffset)
    }, onUi = ui@{ descriptor ->
        if (project.isDisposed) return@ui
        if (descriptor == null) {
            logger.warn("Navigation target is no longer valid: $description")
            return@ui
        }
        descriptor.navigate(true)
        afterNavigate?.invoke()
    })
}
