package org.mozilla.reduxnav.mermaid

object MermaidEscaper {
    fun escape(text: String): String = buildString {
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("&quot;")
                '[' -> append("&#91;")
                ']' -> append("&#93;")
                '(' -> append("&#40;")
                ')' -> append("&#41;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\n' -> append(' ')
                '\r' -> Unit
                else -> append(character)
            }
        }
    }
}
