package org.mozilla.reduxnav.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.mozilla.reduxnav.model.ActionGraph
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage
import org.mozilla.reduxnav.model.ReduxUsageActionMatch
import org.mozilla.reduxnav.model.lineText
import org.mozilla.reduxnav.model.safeLineNumber
import org.mozilla.reduxnav.model.toSmartPointer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtWhenEntry

class ReduxUsageFinder(
    private val project: Project,
    private val classifier: ReduxUsageClassifier = ReduxUsageClassifier()
) {
    fun buildGraph(action: ActionInfo): ActionGraph = ReadAction.nonBlocking<ActionGraph> {
        computeGraph(action)
    }.executeSynchronously()

    internal fun computeGraph(action: ActionInfo): ActionGraph {
        val declaration = action.declaration?.element ?: return ActionGraph(action, emptyList())
        return CachedValuesManager.getCachedValue(declaration) {
            CachedValueProvider.Result.create(
                doComputeGraph(action, declaration),
                declaration.containingFile,
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun doComputeGraph(action: ActionInfo, declaration: PsiElement): ActionGraph {
        val scope = GlobalSearchScope.projectScope(project)
        val rootClass = declaration as? KtClassOrObject
        val isSealedHierarchy = rootClass?.hasModifier(KtTokens.SEALED_KEYWORD) == true
        val targets = if (isSealedHierarchy) {
            findActionHierarchy(rootClass, scope, action.displayName)
        } else {
            listOf(ActionDeclaration(rootClass ?: declaration, action.displayName, coversDescendants = false))
        }

        val usagesByLocation = linkedMapOf<UsageLocation, MutableUsage>()
        targets.forEach { target ->
            ReferencesSearch.search(target.declaration, scope).findAll().forEach { reference ->
                val element = reference.element
                if (isSealedHierarchy && isQualifiedReferenceToDescendant(element, target, targets)) return@forEach
                if (isSealedHierarchy && isHierarchyDeclarationReference(element, target.declaration)) return@forEach
                val file = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: "<unknown>"
                val line = element.safeLineNumber()
                val displayText = element.lineText()
                val kind = classifier.classify(element)
                val context = if (isSealedHierarchy) usageContext(element, kind) else element
                val location = UsageLocation(
                    filePath = file,
                    kind = kind,
                    sourceOffset = context.textRange.startOffset,
                    sourceEndOffset = context.textRange.endOffset
                )
                val usage = usagesByLocation.getOrPut(location) {
                    MutableUsage(
                        kind = kind,
                        displayText = displayText,
                        fileName = element.containingFile?.virtualFile?.name ?: element.containingFile?.name ?: "<unknown>",
                        filePath = file,
                        line = line,
                        element = element.toSmartPointer()
                    )
                }
                if (isSealedHierarchy) {
                    usage.actionMatches += ReduxUsageActionMatch(
                        actionName = target.displayName,
                        coversDescendants = target.coversDescendants
                    )
                }
            }
        }

        val usages = usagesByLocation.values
            .map { it.toReduxUsage() }
            .sortedWith(compareBy<ReduxUsage> { it.kind.ordinal }.thenBy { it.filePath }.thenBy { it.line })

        return ActionGraph(
            action = action,
            usages = usages,
            descendantActionNames = if (isSealedHierarchy) {
                targets.drop(1).mapTo(linkedSetOf()) { it.displayName }
            } else {
                emptySet()
            }
        )
    }

    private fun findActionHierarchy(
        root: KtClassOrObject,
        scope: GlobalSearchScope,
        rootName: String
    ): List<ActionDeclaration> {
        val targets = mutableListOf(
            ActionDeclaration(root, rootName, coversDescendants = true)
        )
        val visited = hashSetOf<PsiElement>(root)
        val queue = ArrayDeque<Pair<KtClassOrObject, String>>()
        queue.add(root to rootName)

        while (queue.isNotEmpty()) {
            val (parent, parentName) = queue.removeFirst()
            ReferencesSearch.search(parent, scope).findAll().forEach { reference ->
                val element = reference.element
                val child = directSubtypeReferencing(element, parent) ?: return@forEach
                if (!visited.add(child)) return@forEach

                val childName = child.name ?: return@forEach
                val qualifiedName = "$parentName.$childName"
                targets += ActionDeclaration(
                    child,
                    qualifiedName,
                    coversDescendants = child.hasModifier(KtTokens.SEALED_KEYWORD)
                )
                queue.add(child to qualifiedName)
            }
        }
        return targets
    }

    private fun directSubtypeReferencing(element: PsiElement, parent: KtClassOrObject): KtClassOrObject? {
        val child = generateSequence(element) { it.parent }
            .takeWhile { it !is KtClassOrObject || it != parent }
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull() ?: return null
        if (child == parent) return null

        val referenceInSupertype = child.superTypeListEntries.any { entry ->
            val typeReference = entry.typeReference ?: return@any false
            if (!isAncestorOrSelf(typeReference, element)) return@any false
            val userType = typeReference.typeElement as? KtUserType ?: return@any false
            userType.referenceExpression?.mainReference?.resolve() == parent
        }
        return child.takeIf { referenceInSupertype }
    }

    private fun isAncestorOrSelf(ancestor: PsiElement, element: PsiElement): Boolean =
        generateSequence(element) { it.parent }.any { it == ancestor }

    private fun isHierarchyDeclarationReference(element: PsiElement, target: PsiElement): Boolean {
        val containingClass = generateSequence(element) { it.parent }
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull() ?: return false
        return containingClass.superTypeListEntries.any { entry ->
            val typeReference = entry.typeReference ?: return@any false
            if (!isAncestorOrSelf(typeReference, element)) return@any false
            val userType = typeReference.typeElement as? KtUserType ?: return@any false
            userType.referenceExpression?.mainReference?.resolve() == target
        }
    }

    private fun usageContext(element: PsiElement, kind: org.mozilla.reduxnav.model.ReduxUsageKind): PsiElement {
        val ancestors = generateSequence(element) { it.parent }
        return when (kind) {
            org.mozilla.reduxnav.model.ReduxUsageKind.MIDDLEWARE,
            org.mozilla.reduxnav.model.ReduxUsageKind.REDUCER -> ancestors.filterIsInstance<KtWhenEntry>().firstOrNull() ?: element
            org.mozilla.reduxnav.model.ReduxUsageKind.DISPATCH -> ancestors
                .filterIsInstance<KtCallExpression>()
                .firstOrNull { call ->
                    val callee = call.calleeExpression?.text.orEmpty()
                    callee == "dispatch" || callee.endsWith(".dispatch")
                } ?: element
            org.mozilla.reduxnav.model.ReduxUsageKind.OTHER -> element
        }
    }

    private fun isQualifiedReferenceToDescendant(
        element: PsiElement,
        target: ActionDeclaration,
        targets: List<ActionDeclaration>
    ): Boolean {
        val childNames = targets.asSequence()
            .filter { it != target && it.displayName.startsWith("${target.displayName}.") }
            .map { it.displayName.removePrefix("${target.displayName}.").substringBefore('.') }
            .toSet()
        if (childNames.isEmpty()) return false

        val containingFile = element.containingFile ?: return false
        val text = containingFile.text
        val endOffset = element.textRange.endOffset.coerceAtMost(text.length)
        val nextToken = text.substring(endOffset)
            .dropWhile(Char::isWhitespace)
            .takeIf { it.startsWith('.') }
            ?.drop(1)
            ?.dropWhile(Char::isWhitespace)
            ?.takeWhile { it == '_' || it.isLetterOrDigit() }
        return nextToken in childNames
    }

    private data class ActionDeclaration(
        val declaration: PsiElement,
        val displayName: String,
        val coversDescendants: Boolean
    )

    private data class UsageLocation(
        val filePath: String,
        val kind: org.mozilla.reduxnav.model.ReduxUsageKind,
        val sourceOffset: Int,
        val sourceEndOffset: Int
    )

    private data class MutableUsage(
        val kind: org.mozilla.reduxnav.model.ReduxUsageKind,
        val displayText: String,
        val fileName: String,
        val filePath: String,
        val line: Int,
        val element: com.intellij.psi.SmartPsiElementPointer<PsiElement>,
        val actionMatches: MutableList<ReduxUsageActionMatch> = mutableListOf()
    ) {
        fun toReduxUsage(): ReduxUsage = ReduxUsage(
            kind = kind,
            displayText = displayText,
            fileName = fileName,
            filePath = filePath,
            line = line,
            element = element,
            handledActions = actionMatches.distinct()
        )
    }
}
