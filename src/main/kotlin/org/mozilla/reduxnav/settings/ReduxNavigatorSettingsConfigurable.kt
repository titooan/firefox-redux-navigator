package org.mozilla.reduxnav.settings

import com.intellij.openapi.options.Configurable
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class ReduxNavigatorSettingsConfigurable : Configurable {
    private var includeTestFilesCheckBox: JCheckBox? = null

    override fun getDisplayName(): String = "Redux Navigator"

    override fun createComponent(): JComponent {
        val checkbox = JCheckBox("Include test files in action lens", settings.includeTestFilesInLens)
        includeTestFilesCheckBox = checkbox

        return JPanel(BorderLayout()).apply {
            add(checkbox, BorderLayout.NORTH)
        }
    }

    override fun isModified(): Boolean =
        includeTestFilesCheckBox?.isSelected != settings.includeTestFilesInLens

    override fun apply() {
        val checkbox = includeTestFilesCheckBox ?: return
        settings.setIncludeTestFilesInLens(checkbox.isSelected)
    }

    override fun reset() {
        includeTestFilesCheckBox?.isSelected = settings.includeTestFilesInLens
    }

    override fun disposeUIResources() {
        includeTestFilesCheckBox = null
    }

    private val settings: ReduxNavigatorSettingsService
        get() = com.intellij.openapi.components.service<ReduxNavigatorSettingsService>()
}
