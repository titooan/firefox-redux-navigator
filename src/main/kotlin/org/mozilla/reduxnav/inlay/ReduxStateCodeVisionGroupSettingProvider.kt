package org.mozilla.reduxnav.inlay

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider

class ReduxStateCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
    override val groupId: String = ReduxStateInlayHintsProvider.PROVIDER_ID

    override val groupName: String = "Redux state changes"

    override val description: String = "Show Redux state change code vision entries above state property declarations."
}
