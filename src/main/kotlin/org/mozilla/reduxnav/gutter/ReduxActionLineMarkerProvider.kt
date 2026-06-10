package org.mozilla.reduxnav.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.icons.ReduxIcons
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.popup.ReduxActionPopup
import java.awt.event.MouseEvent
import javax.swing.Icon

class ReduxActionLineMarkerProvider : LineMarkerProviderDescriptor() {
    private val resolver = ActionSymbolResolver()

    override fun getName(): String = "Firefox Redux action navigation"

    override fun getIcon(): Icon = ReduxIcons.Redux

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Case 1: element is the name identifier of an action class/object declaration
        val declaringClass = (element.parent as? KtClassOrObject)?.takeIf { it.nameIdentifier == element }
        if (declaringClass != null) {
            val action = resolver.resolveDeclaration(declaringClass) ?: return null
            return createMarker(element, action)
        }

        // Case 2: element is a reference to an action at a usage site
        val referenceExpression = element.parent as? KtNameReferenceExpression ?: return null
        if (referenceExpression.firstChild != element) return null
        if (referenceExpression.isInImportDirective()) return null
        if (referenceExpression.isQualifiedReceiver()) return null
        if (referenceExpression.isInSuperTypeList()) return null
        val action = resolver.resolve(referenceExpression) ?: return null
        return createMarker(element, action)
    }

    private fun createMarker(element: PsiElement, action: ActionInfo): LineMarkerInfo<PsiElement> =
        LineMarkerInfo(
            element,
            element.textRange,
            ReduxIcons.Redux,
            { "Show Redux flow for ${action.displayName}" },
            { event: MouseEvent?, elt: PsiElement -> ReduxActionPopup.show(elt.project, action, event) },
            GutterIconRenderer.Alignment.LEFT,
            { "Redux flow" }
        )

    private fun KtNameReferenceExpression.isQualifiedReceiver(): Boolean {
        val qualified = parent as? KtDotQualifiedExpression ?: return false
        return qualified.receiverExpression == this
    }

    private fun KtNameReferenceExpression.isInImportDirective(): Boolean =
        generateSequence(parent) { it.parent }.any { it is com.intellij.psi.PsiImportStatementBase }

    private fun KtNameReferenceExpression.isInSuperTypeList(): Boolean =
        generateSequence(parent) { it.parent }.any { it is KtSuperTypeListEntry }
}
