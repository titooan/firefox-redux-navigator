package org.mozilla.reduxnav.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class ReduxNavigatorSandboxStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        if (!java.lang.Boolean.getBoolean("idea.plugin.in.sandbox.mode")) return

        val editorSettings = EditorSettingsExternalizable.getInstance()
        val gutterIconsShown = editorSettings.areGutterIconsShown()
        logger.info("[redux-nav] sandbox editor settings: gutterIconsShown=$gutterIconsShown project=${project.name}")

        if (!gutterIconsShown) {
            editorSettings.setGutterIconsShown(true)
            logger.info("[redux-nav] sandbox editor settings: re-enabled gutter icons")
        }

        val editorFactory = EditorFactory.getInstance()
        editorFactory.allEditors.forEach { editor -> ensureEditorGutterIconsShown(editor, "startup") }
        editorFactory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                ensureEditorGutterIconsShown(event.editor, "created")
            }
        }, project)
    }

    private fun ensureEditorGutterIconsShown(editor: Editor, source: String) {
        val settings = editor.settings
        val gutterIconsShown = settings.areGutterIconsShown()
        logger.info(
            "[redux-nav] sandbox editor instance: source=$source gutterIconsShown=$gutterIconsShown " +
                "project=${editor.project?.name ?: "<none>"}"
        )

        if (!gutterIconsShown) {
            settings.setGutterIconsShown(true)
            logger.info("[redux-nav] sandbox editor instance: source=$source re-enabled gutter icons")
        }
    }

    private companion object {
        private val logger = Logger.getInstance(ReduxNavigatorSandboxStartupActivity::class.java)
    }
}
