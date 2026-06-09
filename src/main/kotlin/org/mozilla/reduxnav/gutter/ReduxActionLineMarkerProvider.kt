package org.mozilla.reduxnav.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.mozilla.reduxnav.analysis.ActionSymbolResolver
import org.mozilla.reduxnav.icons.ReduxIcons
import org.mozilla.reduxnav.popup.ReduxActionPopup
import java.awt.event.MouseEvent
import javax.swing.Icon

class ReduxActionLineMarkerProvider : LineMarkerProviderDescriptor() {
    private val resolver = ActionSymbolResolver()

    override fun getName(): String = "Firefox Redux action navigation"

    override fun getIcon(): Icon = ReduxIcons.Redux

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is KtNameReferenceExpression) return null
        val action = resolver.resolve(element) ?: return null
        return LineMarkerInfo(
            element,
            element.textRange,
            ReduxIcons.Redux,
            { "Show Redux flow for ${action.displayName}" },
            { _: MouseEvent?, elt: PsiElement -> ReduxActionPopup.show(elt.project, action) },
            GutterIconRenderer.Alignment.LEFT,
            { "Redux flow" }
        )
    }
}
