package org.mozilla.reduxnav.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.mozilla.reduxnav.model.ActionId
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.toSmartPointer

class ActionSymbolResolver(
    private val conventions: FirefoxReduxConventions = FirefoxReduxConventions()
) {
    fun resolve(element: PsiElement): ActionInfo? {
        val referenceExpression = element.parent as? KtNameReferenceExpression ?: element as? KtNameReferenceExpression
        val resolved = referenceExpression?.mainReference?.resolve() ?: return null
        val declaration = resolved.parent as? KtClassOrObject ?: resolved as? KtClassOrObject ?: return null
        val name = declaration.name ?: return null

        if (!looksLikeAction(declaration, name)) return null

        val fqName = declaration.fqName?.asString() ?: name
        return ActionInfo(
            id = ActionId(fqName),
            displayName = name,
            declaration = declaration.toSmartPointer()
        )
    }

    private fun looksLikeAction(declaration: KtClassOrObject, name: String): Boolean {
        if (conventions.actionNameSuffixes.any { name.endsWith(it) }) return true
        return declaration.superTypeListEntries.any { entry ->
            val text = entry.text
            conventions.actionBaseTypeNames.any { base -> text.contains(base) }
        }
    }
}
