package org.mozilla.reduxnav.state

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.analysis.ReduxUsageClassifier
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageKind
import org.mozilla.reduxnav.model.lineText
import org.mozilla.reduxnav.model.safeLineNumber
import org.mozilla.reduxnav.model.toSmartPointer

class StateModificationAnalyzer(
    private val project: Project,
    private val classifier: ReduxUsageClassifier = ReduxUsageClassifier(),
    private val actionResolver: ActionSymbolResolver = ActionSymbolResolver()
) {
    internal fun collectFromFile(file: KtFile): List<StateModification> {
        val modifications = mutableListOf<StateModification>()
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!belongsToReducerFlow(expression)) return
                collectFromCall(expression, modifications)
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                super.visitBinaryExpression(expression)
                if (!belongsToReducerFlow(expression)) return
                collectFromAssignment(expression, modifications)
            }
        })
        return modifications
    }

    private fun collectFromCall(
        call: KtCallExpression,
        modifications: MutableList<StateModification>
    ) {
        val stateClass = call.toResolvedStateClass()
        if (stateClass == null && call.calleeExpression?.text != "copy") return

        call.valueArguments.forEach { argument ->
            val fieldName = argument.getArgumentName()?.asName?.identifier ?: return@forEach
            val candidateField = candidateFieldForArgument(argument, fieldName, stateClass)
            addModification(argument, candidateField, modifications)
        }
    }

    private fun collectFromAssignment(
        expression: KtBinaryExpression,
        modifications: MutableList<StateModification>
    ) {
        val operation = expression.operationReference as? KtOperationReferenceExpression ?: return
        if (operation.text != "=") return
        val left = expression.left as? KtDotQualifiedExpression ?: return
        val fieldRef = left.selectorExpression as? KtNameReferenceExpression ?: return
        val fieldName = fieldRef.getReferencedName()
        val stateClass = left.receiverExpression.toResolvedStateClass()
        val candidateField = buildFieldInfo(fieldName, stateClass, declaration = null)
        addModification(fieldRef, candidateField, modifications)
    }

    private fun candidateFieldForArgument(
        argument: KtValueArgument,
        fieldName: String,
        stateClass: ResolvedStateClass?
    ): StateFieldInfo {
        val nestedPath = inferNestedFieldPath(argument)
        val leafFieldName = nestedPath?.substringAfterLast('.') ?: fieldName
        val fieldPath = nestedPath?.let { "$fieldName.$it" } ?: fieldName
        return buildFieldInfo(
            fieldName = leafFieldName,
            stateClass = stateClass,
            declaration = null,
            fieldPath = fieldPath
        )
    }

    private fun inferNestedFieldPath(argument: KtValueArgument): String? {
        val expression = argument.getArgumentExpression().extractStateRebuildCall() ?: return null
        if (!expression.isStateRebuildCall()) return null
        val namedArguments = expression.valueArguments.filter { it.isNamed() }
        if (namedArguments.size != 1) return null
        val childArgument = namedArguments.single()
        val childFieldName = childArgument.getArgumentName()?.asName?.identifier ?: return null
        val nestedChildPath = inferNestedFieldPath(childArgument)
        return if (nestedChildPath != null) {
            "$childFieldName.$nestedChildPath"
        } else {
            childFieldName
        }
    }

    private fun addModification(
        element: PsiElement,
        candidateField: StateFieldInfo,
        modifications: MutableList<StateModification>
    ) {
        val file = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: "<unknown>"
        val snippet = element.lineText()
        val usage = ReduxUsage(
            kind = ReduxUsageKind.REDUCER,
            displayText = snippet,
            fileName = element.containingFile?.virtualFile?.name ?: element.containingFile?.name ?: "<unknown>",
            filePath = file,
            line = element.safeLineNumber(),
            element = element.toSmartPointer()
        )
        modifications += StateModification(
            stateField = candidateField,
            action = resolveAssociatedAction(element),
            reducerUsage = usage,
            modificationPointer = element.toSmartPointer(),
            fileName = usage.fileName,
            filePath = usage.filePath,
            lineNumber = usage.line,
            snippet = snippet
        )
    }

    private fun resolveAssociatedAction(
        element: PsiElement,
        visitedFunctions: MutableSet<KtNamedFunction> = mutableSetOf(),
        visitedProperties: MutableSet<KtProperty> = mutableSetOf()
    ): ActionInfo? {
        generateSequence(element) { it.parent }.forEach { ancestor ->
            when (ancestor) {
                is KtWhenEntry -> actionFromWhenEntry(ancestor)?.let { return it }
                is KtIfExpression -> actionFromIfExpression(ancestor)?.let { return it }
                is KtProperty -> actionFromHelperProperty(ancestor, visitedFunctions, visitedProperties)?.let { return it }
                is KtNamedFunction -> {
                    actionFromFunction(ancestor)?.let { return it }
                    actionFromHelperFunction(ancestor, visitedFunctions, visitedProperties)?.let { return it }
                }
            }
        }
        return null
    }

    private fun actionFromWhenEntry(entry: KtWhenEntry): ActionInfo? {
        entry.conditions.forEach { condition ->
            when (condition) {
                is KtWhenConditionIsPattern -> resolveActionFromTypeReference(condition.typeReference)?.let { return it }
                is KtWhenConditionWithExpression -> resolveActionFromExpression(condition.expression)?.let { return it }
            }
        }
        return null
    }

    private fun actionFromIfExpression(expression: KtIfExpression): ActionInfo? {
        val condition = expression.condition as? KtIsExpression ?: return null
        return resolveActionFromTypeReference(condition.typeReference)
    }

    private fun actionFromFunction(function: KtNamedFunction): ActionInfo? =
        function.valueParameters.firstNotNullOfOrNull { parameter ->
            resolveActionFromTypeReference(parameter.typeReference, requireConcreteAction = true)
        }

    private fun actionFromHelperFunction(
        function: KtNamedFunction,
        visitedFunctions: MutableSet<KtNamedFunction>,
        visitedProperties: MutableSet<KtProperty>
    ): ActionInfo? {
        if (!function.isLocal || !visitedFunctions.add(function)) return null
        val reducerFunction = function.enclosingReducerFunction() ?: return null
        return collectCallSiteActions(
            owner = reducerFunction,
            matchesTarget = { call -> call.resolvesTo(function) },
            visitedFunctions = visitedFunctions,
            visitedProperties = visitedProperties
        )
    }

    private fun actionFromHelperProperty(
        property: KtProperty,
        visitedFunctions: MutableSet<KtNamedFunction>,
        visitedProperties: MutableSet<KtProperty>
    ): ActionInfo? {
        if (!property.isLocal || property.initializer !is KtLambdaExpression || !visitedProperties.add(property)) return null
        val reducerFunction = property.enclosingReducerFunction() ?: return null
        return collectCallSiteActions(
            owner = reducerFunction,
            matchesTarget = { call -> call.resolvesTo(property) },
            visitedFunctions = visitedFunctions,
            visitedProperties = visitedProperties
        )
    }

    private fun collectCallSiteActions(
        owner: KtNamedFunction,
        matchesTarget: (KtCallExpression) -> Boolean,
        visitedFunctions: MutableSet<KtNamedFunction>,
        visitedProperties: MutableSet<KtProperty>
    ): ActionInfo? {
        val actions = linkedMapOf<org.mozilla.reduxnav.model.ActionId, ActionInfo>()
        owner.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!matchesTarget(expression)) return
                val action = resolveAssociatedAction(expression, visitedFunctions, visitedProperties) ?: return
                actions.putIfAbsent(action.id, action)
            }
        })
        return actions.values.singleOrNull()
    }

    private fun resolveActionFromTypeReference(
        typeReference: org.jetbrains.kotlin.psi.KtTypeReference?,
        requireConcreteAction: Boolean = false
    ): ActionInfo? {
        val userType = typeReference?.typeElement as? KtUserType ?: return null
        val resolved = userType.referenceExpression?.mainReference?.resolve()
        val classOrObject = when (resolved) {
            is KtClassOrObject -> resolved
            else -> generateSequence(resolved) { it?.parent }.filterIsInstance<KtClassOrObject>().firstOrNull()
        } ?: return null
        if (requireConcreteAction && !isConcreteActionDeclaration(classOrObject)) return null
        return actionResolver.resolveDeclaration(classOrObject)
    }

    private fun resolveActionFromExpression(expression: KtExpression?): ActionInfo? =
        when (expression) {
            is KtNameReferenceExpression -> {
                val resolved = expression.mainReference.resolve()
                val classOrObject = when (resolved) {
                    is KtClassOrObject -> resolved
                    else -> generateSequence(resolved) { it?.parent }.filterIsInstance<KtClassOrObject>().firstOrNull()
                }
                classOrObject?.let { actionResolver.resolveDeclaration(it) }
            }
            is KtDotQualifiedExpression -> resolveActionFromExpression(expression.selectorExpression)
            else -> null
        }

    private fun isConcreteActionDeclaration(classOrObject: KtClassOrObject): Boolean {
        val klass = classOrObject as? KtClass ?: return true
        return !klass.isInterface() && !klass.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.SEALED_KEYWORD) &&
            !klass.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD)
    }

    private fun KtCallExpression.isStateRebuildCall(): Boolean {
        val calleeName = calleeExpression?.text ?: return false
        if (calleeName == "copy") return true
        val resolved = (calleeExpression as? KtNameReferenceExpression)?.mainReference?.resolve()
        return resolved is KtClassOrObject ||
            generateSequence(resolved) { it?.parent }.filterIsInstance<KtClassOrObject>().firstOrNull() != null
    }

    private fun KtExpression?.extractStateRebuildCall(): KtCallExpression? =
        when (this) {
            is KtCallExpression -> this
            is KtDotQualifiedExpression -> selectorExpression.extractStateRebuildCall()
            else -> null
        }

    private fun KtCallExpression.resolvesTo(target: PsiElement): Boolean {
        val reference = calleeExpression as? KtNameReferenceExpression ?: return false
        return reference.mainReference.resolve() == target
    }

    private fun PsiElement.enclosingReducerFunction(): KtNamedFunction? =
        generateSequence(parent) { it.parent }
            .filterIsInstance<KtNamedFunction>()
            .firstOrNull { classifier.isReducerContainer(it) }

    private fun belongsToReducerFlow(element: PsiElement): Boolean =
        classifier.isReducerContainer(element) || element.enclosingReducerFunction() != null
}
