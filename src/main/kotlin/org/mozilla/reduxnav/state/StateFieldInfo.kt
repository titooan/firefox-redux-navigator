package org.mozilla.reduxnav.state

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.mozilla.reduxnav.model.ActionInfo
import org.mozilla.reduxnav.model.ReduxUsage

data class StateFieldInfo(
    val id: String,
    val fieldName: String,
    val fieldPath: String = fieldName,
    val stateClassName: String?,
    val qualifiedPath: String,
    val declarationPointer: SmartPsiElementPointer<PsiElement>?,
    val stateClassQualifiedName: String? = null
) {
    fun matchesTarget(target: StateFieldInfo): Boolean {
        if (id == target.id) return true
        if (sameResolvedStateClass(target)) {
            if (fieldPath == target.fieldPath) return true
            if (fieldPath.startsWith("${target.fieldPath}.")) return true
            return fieldName == target.fieldName
        }
        if (sameStateClassName(target)) {
            if (fieldPath == target.fieldPath) return true
            if (fieldPath.startsWith("${target.fieldPath}.")) return true
            return fieldName == target.fieldName
        }
        return fieldName == target.fieldName
    }

    private fun sameResolvedStateClass(other: StateFieldInfo): Boolean =
        stateClassQualifiedName != null && stateClassQualifiedName == other.stateClassQualifiedName

    private fun sameStateClassName(other: StateFieldInfo): Boolean =
        stateClassName != null && stateClassName == other.stateClassName
}

data class StateModification(
    val stateField: StateFieldInfo,
    val action: ActionInfo?,
    val reducerUsage: ReduxUsage?,
    val modificationPointer: SmartPsiElementPointer<PsiElement>,
    val fileName: String,
    val filePath: String,
    val lineNumber: Int,
    val snippet: String
)

data class StateFieldGraph(
    val stateField: StateFieldInfo,
    val modifications: List<StateModification>
)
