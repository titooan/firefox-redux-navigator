package org.mozilla.reduxnav.ui

import java.awt.KeyboardFocusManager
import javax.swing.AbstractButton
import javax.swing.SwingUtilities

internal fun <T : AbstractButton> T.withTransientFocusRing(): T =
    apply {
        isFocusable = true
        isFocusPainted = true
        addActionListener {
            SwingUtilities.invokeLater {
                if (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner === this) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner()
                }
            }
        }
    }
