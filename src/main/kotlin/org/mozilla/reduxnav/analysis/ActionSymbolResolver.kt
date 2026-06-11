package org.mozilla.reduxnav.analysis

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.mozilla.reduxnav.model.ActionId
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.toSmartPointer

class ActionSymbolResolver(
    private val conventions: FirefoxReduxConventions = FirefoxReduxConventions()
) {
    fun resolveDeclaration(element: PsiElement): ActionInfo? {
        val classOrObject = when (element) {
            is KtClassOrObject -> element
            else -> {
                val parent = element.parent as? KtClassOrObject ?: return null
                if (parent.nameIdentifier != element) return null
                parent
            }
        }

        val name = classOrObject.name ?: return null
        if (!looksLikeAction(classOrObject, name)) return null
        val fqName = classOrObject.fqName?.asString() ?: name
        return ActionInfo(
            id = ActionId(fqName),
            displayName = name,
            declaration = classOrObject.toSmartPointer()
        )
    }

    fun resolveDeclaration(classOrObject: KtClassOrObject): ActionInfo? = resolveDeclaration(classOrObject as PsiElement)

    fun resolve(element: PsiElement): ActionInfo? {
        val referenceExpression = element.parent as? KtNameReferenceExpression ?: element as? KtNameReferenceExpression ?: return null
        val referenceName = referenceExpression.getReferencedName()
        val resolved = referenceExpression.mainReference.resolve() ?: return null
        val declaration = findActionDeclaration(resolved, referenceName) ?: return null
        val name = declaration.name ?: return null

        if (!looksLikeAction(declaration, name)) return null

        val fqName = declaration.fqName?.asString() ?: name
        return ActionInfo(
            id = ActionId(fqName),
            displayName = name,
            declaration = declaration.toSmartPointer()
        )
    }

    private fun findActionDeclaration(resolved: PsiElement, referenceName: String): KtClassOrObject? {
        val declarations = generateSequence(resolved) { it.parent }.filterIsInstance<KtClassOrObject>().toList()

        return declarations.firstOrNull { declaration ->
            declaration.name == referenceName && looksLikeAction(declaration, referenceName)
        } ?: declarations.firstOrNull { declaration ->
            declaration.name == referenceName
        } ?: declarations.firstOrNull { declaration ->
            declaration == resolved && looksLikeAction(declaration, declaration.name.orEmpty())
        } ?: declarations.firstOrNull { declaration ->
            looksLikeAction(declaration, declaration.name.orEmpty())
        }
    }

    private fun looksLikeAction(declaration: KtClassOrObject, name: String): Boolean {
        if (conventions.actionNameSuffixes.any { name.endsWith(it) }) return true
        if (declaration.superTypeListEntries.any { entry ->
            val text = entry.text
            conventions.actionBaseTypeNames.any { base -> text.contains(base) }
        }) return true

        val parent = declaration.parent as? KtClassOrObject ?: return false
        val parentName = parent.name ?: return false
        return looksLikeAction(parent, parentName)
    }
}
