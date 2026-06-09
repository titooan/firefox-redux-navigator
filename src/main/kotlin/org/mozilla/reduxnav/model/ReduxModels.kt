package org.mozilla.reduxnav.model

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

@JvmInline
value class ActionId(val value: String)

enum class ReduxUsageKind(val title: String) {
    DISPATCH("Dispatches"),
    MIDDLEWARE("Middlewares"),
    REDUCER("Reducers"),
    OTHER("Other references")
}

data class ActionInfo(
    val id: ActionId,
    val displayName: String,
    val declaration: SmartPsiElementPointer<PsiElement>?
)

data class ReduxUsage(
    val kind: ReduxUsageKind,
    val displayText: String,
    val filePath: String,
    val line: Int,
    val element: SmartPsiElementPointer<PsiElement>
)

data class ActionGraph(
    val action: ActionInfo,
    val usages: List<ReduxUsage>
) {
    fun grouped(): Map<ReduxUsageKind, List<ReduxUsage>> = usages.groupBy { it.kind }
}

fun PsiElement.toSmartPointer(): SmartPsiElementPointer<PsiElement> =
    SmartPointerManager.createPointer(this)

fun PsiElement.locationLabel(): String {
    val file = containingFile?.virtualFile?.name ?: containingFile?.name ?: "<unknown>"
    val document = containingFile?.viewProvider?.document
    val line = document?.getLineNumber(textRange.startOffset)?.plus(1) ?: 1
    return "$file:$line"
}

fun PsiElement.safeLineNumber(): Int {
    val document = containingFile?.viewProvider?.document ?: return 1
    return document.getLineNumber(textRange.startOffset) + 1
}

fun PsiElement.lineText(): String {
    val document = containingFile?.viewProvider?.document ?: return text.take(80)
    val line = document.getLineNumber(textRange.startOffset)
    val start = document.getLineStartOffset(line)
    val end = document.getLineEndOffset(line)
    return document.getText(TextRange(start, end)).trim()
}
