package org.mozilla.reduxnav.render

import com.intellij.ui.jcef.JBCefApp

object JcefAvailability {
    fun isSupported(): Boolean = JBCefApp.isSupported()

    fun diagnostics(): JcefDiagnostics {
        val supported = isSupported()
        val lines = buildList {
            add("JCEF supported: $supported")
            appendProperty("Java runtime", "java.runtime.name")
            appendProperty("Java version", "java.runtime.version")
            appendProperty("Java vendor", "java.vendor")
            appendProperty("Java home", "java.home")
            appendProperty("IDE vendor", "idea.vendor.name")
            appendProperty("IDE platform", "idea.platform.prefix")
            appendProperty("OS", "os.name")
            appendProperty("OS arch", "os.arch")
            appendEnv("STUDIO_JDK")
            appendEnv("IDEA_JDK")
            appendEnv("JDK_HOME")
            appendEnv("JAVA_HOME")
            appendPropertyIfPresent("ide.browser.jcef.enabled")
            appendPropertyIfPresent("ide.browser.jcef.headless.enabled")
            appendReflectionValue("JBCefApp version details", "getVersionDetails")
            appendReflectionValue("JBCefApp version", "getVersion")
        }

        val summary = if (supported) {
            "JCEF is available."
        } else {
            "Rendered Mermaid preview requires JCEF. Use the Mermaid Source tab instead."
        }
        return JcefDiagnostics(
            supported = supported,
            summary = summary,
            details = lines.joinToString(separator = "\n")
        )
    }

    private fun MutableList<String>.appendProperty(label: String, key: String) {
        add("$label: ${System.getProperty(key).orEmpty().ifBlank { "<unset>" }}")
    }

    private fun MutableList<String>.appendPropertyIfPresent(key: String) {
        System.getProperty(key)?.takeIf { it.isNotBlank() }?.let { add("$key: $it") }
    }

    private fun MutableList<String>.appendEnv(key: String) {
        val value = System.getenv(key)
        add("$key: ${value.orEmpty().ifBlank { "<unset>" }}")
    }

    private fun MutableList<String>.appendReflectionValue(label: String, methodName: String) {
        val value = runCatching {
            val method = JBCefApp::class.java.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            } ?: return
            method.invoke(null)?.toString()
        }.getOrNull()
        if (!value.isNullOrBlank()) {
            add("$label: $value")
        }
    }
}
