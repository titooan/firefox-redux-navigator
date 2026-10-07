package org.mozilla.reduxnav.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.mozilla.reduxnav.model.ActionInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.mozilla.reduxnav.nativeflow.DiagramNodeTarget
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ReduxCodePreviewPanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {
    private val metadataForeground = JBColor(
        java.awt.Color(0x777777),
        java.awt.Color(0xA0A0A0)
    )
    private var editor: EditorEx? = null
    private var currentDocument: Document? = null
    private var currentFilePath: String = ""
    private var currentPreviewIdentity: PreviewIdentity? = null
    private var previewRequestId: Long = 0
    private val messageLabel = JBLabel("Select a Redux flow node to preview its source.").apply {
        border = JBUI.Borders.empty(12)
    }
    private val fileLabel = JBLabel().apply {
        border = JBUI.Borders.empty(8, 12, 8, 0)
    }
    private val pathLabel = JBLabel().apply {
        foreground = metadataForeground
        border = JBUI.Borders.empty(8, 6, 8, 12)
    }
    private val headerPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createMatteBorder(
            0,
            0,
            1,
            0,
            JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        )
        add(fileLabel)
        add(pathLabel)
        add(Box.createHorizontalGlue())
    }

    init {
        border = JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
        showMessage("Select a Redux flow node to preview its source.")
    }

    fun showTarget(target: DiagramNodeTarget?) {
        val requestId = ++previewRequestId
        if (target == null) {
            showMessage("Select a Redux flow node to preview its source.")
            return
        }

        submitModelRead(project, {
            when (target) {
                is DiagramNodeTarget.ActionTarget -> previewDataFor(target.action.declaration?.element)
                is DiagramNodeTarget.ModificationTarget -> previewDataFor(target.modification.modificationPointer.element)
                is DiagramNodeTarget.StateFieldTarget -> previewDataFor(target.field.declarationPointer?.element)
                is DiagramNodeTarget.UsageTarget -> previewDataFor(target.usage.element.element)
            }
        }) { preview ->
            if (requestId != previewRequestId || project.isDisposed) return@submitModelRead
            if (preview == null) {
                showMessage("Preview is unavailable for the selected node.")
            } else if (preview.identity != currentPreviewIdentity) {
                showPreview(preview)
            }
        }
    }

    fun showAction(action: ActionInfo?) {
        if (action == null) showTarget(null) else showTarget(DiagramNodeTarget.ActionTarget(action))
    }

    fun showElement(element: PsiElement?) {
        val requestId = ++previewRequestId
        submitModelRead(project, {
            previewDataFor(element)
        }) { preview ->
            if (requestId != previewRequestId || project.isDisposed) return@submitModelRead
            if (preview == null) {
                showMessage("Preview is unavailable for the selected node.")
            } else if (preview.identity != currentPreviewIdentity) {
                showPreview(preview)
            }
        }
    }

    private fun previewDataFor(element: PsiElement?): PreviewData? {
        val validElement = element?.takeIf { it.isValid } ?: return null
        val file = validElement.containingFile?.virtualFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val line = document.getLineNumber(validElement.textOffset)
        return PreviewData(
            file = file,
            document = document,
            offset = validElement.textOffset,
            lineStartOffset = document.getLineStartOffset(line),
            lineEndOffset = document.getLineEndOffset(line),
            displayPath = displayPathFor(file),
            identity = PreviewIdentity(file.url, document.getLineStartOffset(line), document.getLineEndOffset(line))
        )
    }

    private fun showPreview(preview: PreviewData) {
        currentPreviewIdentity = preview.identity
        val viewer = ensureViewer(preview.document, preview.file)
        if (componentCount == 0 || getComponent(0) !== headerPanel) {
            removeAll()
            add(headerPanel, BorderLayout.NORTH)
            add(viewer.component, BorderLayout.CENTER)
            revalidate()
            repaint()
        } else if (componentCount < 2 || getComponent(1) !== viewer.component) {
            removeAll()
            add(headerPanel, BorderLayout.NORTH)
            add(viewer.component, BorderLayout.CENTER)
            revalidate()
            repaint()
        }

        fileLabel.text = preview.file.name
        pathLabel.text = preview.displayPath
        currentFilePath = preview.displayPath
        viewer.caretModel.moveToOffset(preview.lineStartOffset)
        viewer.selectionModel.setSelection(preview.lineStartOffset, preview.lineEndOffset)
        viewer.component.validate()
        viewer.component.doLayout()
        scrollToPreviewLine(viewer, preview)
    }

    private fun ensureViewer(document: Document, file: VirtualFile): EditorEx {
        val existing = editor
        if (existing != null && currentDocument === document) {
            existing.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
            return existing
        }

        releaseEditor()
        val created = EditorFactory.getInstance().createViewer(document, project) as EditorEx
        created.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
        created.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = true
            isCaretRowShown = true
            isLineMarkerAreaShown = true
            isVirtualSpace = false
        }
        created.setHorizontalScrollbarVisible(true)
        created.setVerticalScrollbarVisible(true)
        editor = created
        currentDocument = document
        return created
    }

    private fun showMessage(message: String) {
        releaseEditor()
        removeAll()
        fileLabel.text = ""
        pathLabel.text = ""
        currentFilePath = ""
        currentPreviewIdentity = null
        add(messageLabel.apply { text = message }, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun releaseEditor() {
        editor?.let { EditorFactory.getInstance().releaseEditor(it) }
        editor = null
        currentDocument = null
    }

    override fun dispose() {
        previewRequestId++
        releaseEditor()
    }

    internal fun currentMessageForTest(): String? =
        (getComponent(0) as? JBLabel)?.text

    internal fun isShowingEditorForTest(): Boolean = editor != null && componentCount > 1 && getComponent(1) === editor?.component

    internal fun currentFileNameForTest(): String? = fileLabel.text.takeIf { it.isNotBlank() }

    internal fun currentFilePathForTest(): String? = currentFilePath.takeIf { it.isNotBlank() }

    internal fun selectedTextForTest(): String? = editor?.selectionModel?.selectedText

    internal fun currentPreviewLineForTest(): Int? = editor?.caretModel?.logicalPosition?.line

    internal fun visibleStartLineForTest(): Int? =
        editor?.let { currentEditor ->
            currentEditor.xyToLogicalPosition(currentEditor.scrollingModel.visibleArea.location).line
        }

    private data class PreviewData(
        val file: VirtualFile,
        val document: Document,
        val offset: Int,
        val lineStartOffset: Int,
        val lineEndOffset: Int,
        val displayPath: String,
        val identity: PreviewIdentity
    )

    private data class PreviewIdentity(
        val fileUrl: String,
        val lineStartOffset: Int,
        val lineEndOffset: Int
    )

    private fun scrollToPreviewLine(viewer: EditorEx, preview: PreviewData) {
        val line = preview.document.getLineNumber(preview.lineStartOffset)
        val logicalPosition = LogicalPosition(line, 0)

        fun reveal() {
            if (editor !== viewer) return
            viewer.scrollingModel.disableAnimation()
            viewer.scrollingModel.scrollTo(logicalPosition, ScrollType.CENTER)
            viewer.scrollingModel.scrollHorizontally(0)
        }

        reveal()
        SwingUtilities.invokeLater { reveal() }
    }

    private fun displayPathFor(file: VirtualFile): String {
        val basePath = project.basePath?.trimEnd('/') ?: ""
        val filePath = file.path
        return when {
            basePath.isNotBlank() && filePath.startsWith(basePath) ->
                filePath.removePrefix(basePath).trimStart('/')
            else -> filePath
        }
    }
}
