package org.mozilla.reduxnav.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.idea.references.mainReference
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.icons.ReduxIcons
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.popup.ReduxActionPopup
import java.awt.event.MouseEvent
import javax.swing.Icon

class ReduxActionLineMarkerProvider : LineMarkerProviderDescriptor() {
    private val logger = Logger.getInstance(ReduxActionLineMarkerProvider::class.java)
    private val resolver = ActionSymbolResolver()

    override fun getName(): String = "Redux flow"

    override fun getIcon(): Icon = ReduxIcons.Redux

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        logger.info("[redux-nav] collectSlowLineMarkers elements=${elements.size}")
        elements.mapNotNullTo(result) { markerForElement(it) }
    }

    internal fun markerForElement(element: PsiElement): LineMarkerInfo<*>? {
        // Case 1: element is the name identifier of an action class/object declaration
        val declaringClass = (element.parent as? KtClassOrObject)?.takeIf { it.nameIdentifier == element }
        if (declaringClass != null) {
            val action = resolver.resolveDeclaration(declaringClass) ?: return null
            logCandidate("declaration", element, "resolved=${action.displayName}")
            return createMarker(element, action)
        }

        // Case 2: element is a reference to an action at a usage site
        val referenceExpression = element.parent as? KtNameReferenceExpression ?: return null
        if (referenceExpression.firstChild != element) {
            logCandidate("usage", element, "skip=not-first-child parent=${referenceExpression.javaClass.simpleName}")
            return null
        }
        if (referenceExpression.isInImportDirective()) {
            logCandidate("usage", element, "skip=import-directive text=${referenceExpression.text}")
            return null
        }
        if (referenceExpression.isValueReference()) {
            logCandidate("usage", element, "skip=value-reference text=${referenceExpression.text}")
            return null
        }
        if (referenceExpression.isQualifiedReceiver()) {
            logCandidate("usage", element, "skip=qualified-receiver text=${referenceExpression.text}")
            return null
        }
        if (referenceExpression.isQualifiedTypeQualifier()) {
            logCandidate("usage", element, "skip=qualified-type-qualifier text=${referenceExpression.text}")
            return null
        }
        if (referenceExpression.isInSuperTypeList()) {
            logCandidate("usage", element, "skip=supertype-list text=${referenceExpression.text}")
            return null
        }
        val action = resolver.resolve(referenceExpression)
        if (action == null) {
            logCandidate("usage", element, "skip=unresolved text=${referenceExpression.text}")
            return null
        }
        logCandidate("usage", element, "resolved=${action.displayName}")
        return createMarker(element, action)
    }

    private fun createMarker(element: PsiElement, action: ActionInfo): LineMarkerInfo<PsiElement> =
        LineMarkerInfo(
            element,
            element.textRange,
            ReduxIcons.Redux,
            { "Show Redux flow for ${action.displayName}" },
            { event: MouseEvent?, elt: PsiElement ->
                logger.info(
                    "[redux-nav] gutter-click action=${action.displayName} file=${elt.containingFile?.virtualFile?.path} " +
                        "point=${event?.point} component=${event?.component?.javaClass?.name}"
                )
                ReduxActionPopup.show(elt.project, action, event)
            },
            GutterIconRenderer.Alignment.LEFT,
            { "Redux flow" }
        )

    private fun KtNameReferenceExpression.isQualifiedReceiver(): Boolean {
        val qualified = parent as? KtDotQualifiedExpression ?: return false
        return qualified.receiverExpression == this
    }

    private fun KtNameReferenceExpression.isQualifiedTypeQualifier(): Boolean {
        return generateSequence(parent) { it.parent }
            .filterIsInstance<KtUserType>()
            .any { userType ->
                userType.text.contains(".") &&
                    (userType.qualifier?.text == text || userType.text.startsWith("$text."))
            }
    }

    private fun KtNameReferenceExpression.isValueReference(): Boolean {
        return when (mainReference.resolve()) {
            is KtParameter, is KtProperty, is PsiParameter, is PsiLocalVariable -> true
            else -> false
        }
    }

    private fun KtNameReferenceExpression.isInImportDirective(): Boolean =
        generateSequence(this as PsiElement?) { it.parent }.any { it is KtImportDirective }

    private fun KtNameReferenceExpression.isInSuperTypeList(): Boolean =
        generateSequence(parent) { it.parent }.any { it is KtSuperTypeListEntry }

    private fun logCandidate(stage: String, element: PsiElement, outcome: String) {
        val file = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: "<unknown>"
        logger.info("[redux-nav] $stage file=$file text=${element.text} outcome=$outcome")
    }
}
