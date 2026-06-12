package org.mozilla.reduxnav.render

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JcefAvailabilityTest : BasePlatformTestCase() {
    fun testDiagnosticsContainCoreRuntimeFields() {
        val diagnostics = JcefAvailability.diagnostics()

        assertTrue(diagnostics.summary.isNotBlank())
        assertTrue(diagnostics.details.contains("JCEF supported:"))
        assertTrue(diagnostics.details.contains("Java runtime:"))
        assertTrue(diagnostics.details.contains("Java version:"))
        assertTrue(diagnostics.details.contains("Java vendor:"))
        assertTrue(diagnostics.details.contains("Java home:"))
        assertTrue(diagnostics.details.contains("STUDIO_JDK:"))
        assertTrue(diagnostics.details.contains("IDEA_JDK:"))
    }
}
