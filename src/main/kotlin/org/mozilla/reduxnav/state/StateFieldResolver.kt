package org.mozilla.reduxnav.state

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.mozilla.reduxnav.model.toSmartPointer

class StateFieldResolver {
    fun resolve(element: PsiElement): StateFieldInfo? =
        resolveNamedArgument(element) ?: resolveDeclaration(element)

    fun resolveDeclaration(element: PsiElement): StateFieldInfo? {
        val parameter = generateSequence(element) { it.parent }
            .filterIsInstance<KtParameter>()
            .firstOrNull { it.name != null && it.hasValOrVar() }
        if (parameter != null) {
            return buildFieldInfo(
                fieldName = parameter.name ?: return null,
                stateClass = parameter.enclosingClassOrObject()?.toResolvedStateClass(),
                declaration = parameter
            )
        }

        val property = generateSequence(element) { it.parent }
            .filterIsInstance<KtProperty>()
            .firstOrNull { it.name != null && it.enclosingClassOrObject() != null }
            ?: return null

        return buildFieldInfo(
            fieldName = property.name ?: return null,
            stateClass = property.enclosingClassOrObject()?.toResolvedStateClass(),
            declaration = property
        )
    }

    fun resolveNamedArgument(element: PsiElement): StateFieldInfo? {
        val argument = generateSequence(element) { it.parent }
            .filterIsInstance<KtValueArgument>()
            .firstOrNull { it.isNamed() } ?: return null
        val fieldName = argument.getArgumentName()?.asName?.identifier ?: return null
        val call = argument.parent?.parent as? KtCallExpression
        val stateClass = call?.toResolvedStateClass()
        return buildFieldInfo(fieldName, stateClass, declaration = null)
    }
}

internal data class ResolvedStateClass(
    val simpleName: String?,
    val qualifiedName: String?
)

internal fun buildFieldInfo(
    fieldName: String,
    stateClass: ResolvedStateClass?,
    declaration: PsiElement?,
    fieldPath: String = fieldName
): StateFieldInfo {
    val stateClassName = stateClass?.simpleName
    val qualifiedPath = listOfNotNull(stateClassName, fieldPath).joinToString(".").ifBlank { fieldPath }
    val id = stateClass?.qualifiedName?.let { "$it.$fieldPath" }
        ?: stateClassName?.let { "$it.$fieldPath" }
        ?: fieldPath
    return StateFieldInfo(
        id = id,
        fieldName = fieldName,
        fieldPath = fieldPath,
        stateClassName = stateClassName,
        qualifiedPath = qualifiedPath,
        declarationPointer = declaration?.toSmartPointer(),
        stateClassQualifiedName = stateClass?.qualifiedName
    )
}

internal fun KtCallExpression.toResolvedStateClass(): ResolvedStateClass? {
    val calleeName = calleeExpression?.text ?: return null
    if (calleeName == "copy") {
        val receiver = (parent as? KtDotQualifiedExpression)?.receiverExpression
        return receiver?.toResolvedStateClass()
    }

    val resolved = (calleeExpression as? KtNameReferenceExpression)?.mainReference?.resolve()
    val classOrObject = when (resolved) {
        is KtClassOrObject -> resolved
        else -> generateSequence(resolved) { it?.parent }.filterIsInstance<KtClassOrObject>().firstOrNull()
    }
    return classOrObject?.toResolvedStateClass()
        ?: ResolvedStateClass(calleeName.substringAfterLast('.').substringBefore('<'), null)
}

internal fun KtExpression.toResolvedStateClass(): ResolvedStateClass? =
    when (this) {
        is KtNameReferenceExpression -> {
            when (val resolved = mainReference.resolve()) {
                is KtParameter -> resolved.typeReference.toResolvedStateClass()
                is KtProperty -> resolved.typeReference.toResolvedStateClass()
                is KtClassOrObject -> resolved.toResolvedStateClass()
                else -> generateSequence(resolved) { it?.parent }.filterIsInstance<KtClassOrObject>().firstOrNull()?.toResolvedStateClass()
            }
        }
        is KtDotQualifiedExpression -> selectorExpression?.toResolvedStateClass()
            ?: receiverExpression.toResolvedStateClass()
        is KtCallExpression -> toResolvedStateClass()
        else -> null
    }

internal fun KtTypeReference?.toResolvedStateClass(): ResolvedStateClass? {
    val userType = this?.typeElement as? KtUserType
    val reference = userType?.referenceExpression
    val resolved = reference?.mainReference?.resolve()
    val classOrObject = when (resolved) {
        is KtClassOrObject -> resolved
        else -> generateSequence(resolved) { it?.parent }.filterIsInstance<KtClassOrObject>().firstOrNull()
    }
    return classOrObject?.toResolvedStateClass()
        ?: userType?.referencedName?.let { ResolvedStateClass(it, null) }
}

internal fun KtClassOrObject.toResolvedStateClass(): ResolvedStateClass =
    ResolvedStateClass(
        simpleName = name,
        qualifiedName = fqName?.asString()
    )

private fun PsiElement.enclosingClassOrObject(): KtClassOrObject? =
    generateSequence(parent) { it.parent }.filterIsInstance<KtClassOrObject>().firstOrNull()
