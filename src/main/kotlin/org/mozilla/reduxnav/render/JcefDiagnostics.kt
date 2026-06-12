package org.mozilla.reduxnav.render

data class JcefDiagnostics(
    val supported: Boolean,
    val summary: String,
    val details: String
)
