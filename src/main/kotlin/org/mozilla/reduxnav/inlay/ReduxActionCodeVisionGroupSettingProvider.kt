package org.mozilla.reduxnav.inlay

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider

class ReduxActionCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
    override val groupId: String = ReduxActionInlayHintsProvider.PROVIDER_ID

    override val groupName: String = "Redux action usages"

    override val description: String = "Show Redux action usage code vision entries above action declarations."
}
