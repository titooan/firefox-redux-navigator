package org.mozilla.reduxnav.analysis

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.mozilla.reduxnav.model.ReduxUsageKind

class ReduxUsageClassifier(
    private val conventions: FirefoxReduxConventions = FirefoxReduxConventions()
) {
    fun classify(referenceElement: PsiElement): ReduxUsageKind {
        return when {
            isDispatch(referenceElement) -> ReduxUsageKind.DISPATCH
            isMiddleware(referenceElement) -> ReduxUsageKind.MIDDLEWARE
            isReducer(referenceElement) -> ReduxUsageKind.REDUCER
            else -> ReduxUsageKind.OTHER
        }
    }

    private fun isDispatch(element: PsiElement): Boolean {
        return element.ancestors()
            .filterIsInstance<KtCallExpression>()
            .any { call ->
                val calleeName = call.calleeExpression?.text
                if (calleeName in conventions.dispatchMethodNames) return@any true

                val parentText = call.parent?.text.orEmpty()
                conventions.dispatchMethodNames.any { parentText.contains(".$it(") }
            }
    }

    private fun isMiddleware(element: PsiElement): Boolean {
        val inWhenBranch = element.ancestors().any { it is KtWhenEntry }
        if (!inWhenBranch) return false

        val klass = element.ancestors().filterIsInstance<KtClassOrObject>().firstOrNull()
        if (klass != null) {
            val name = klass.name.orEmpty()
            if (conventions.middlewareNameHints.any { name.contains(it) }) return true
            if (klass.superTypeListEntries.any { superType -> conventions.middlewareNameHints.any { superType.text.contains(it) } }) return true
        }

        val function = element.ancestors().filterIsInstance<KtFunction>().firstOrNull()
        return function?.name?.contains("middleware", ignoreCase = true) == true
    }

    private fun isReducer(element: PsiElement): Boolean {
        val inWhenBranch = element.ancestors().any { it is KtWhenEntry }
        if (!inWhenBranch) return false

        val function = element.ancestors().filterIsInstance<KtFunction>().firstOrNull()
        if (function != null && conventions.reducerNameHints.any { function.name?.contains(it, ignoreCase = true) == true }) {
            return true
        }

        val klass = element.ancestors().filterIsInstance<KtClassOrObject>().firstOrNull()
        if (klass != null) {
            val name = klass.name.orEmpty()
            if (conventions.reducerNameHints.any { name.contains(it, ignoreCase = true) }) return true
            if (klass.superTypeListEntries.any { superType -> conventions.reducerNameHints.any { superType.text.contains(it, ignoreCase = true) } }) return true
        }

        return false
    }
}

private fun PsiElement.ancestors(): Sequence<PsiElement> = generateSequence(this) { it.parent }
