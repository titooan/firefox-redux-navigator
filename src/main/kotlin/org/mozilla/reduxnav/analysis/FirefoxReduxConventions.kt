package org.mozilla.reduxnav.analysis

/**
 * First-iteration Firefox Android Redux conventions.
 * Keep this deliberately simple and easy to evolve after testing on Fenix/android-components.
 */
data class FirefoxReduxConventions(
    val dispatchMethodNames: Set<String> = setOf("dispatch"),
    val actionNameSuffixes: Set<String> = setOf("Action"),
    val actionBaseTypeNames: Set<String> = setOf("Action", "BrowserAction", "AppAction", "TabsTrayAction", "MenuAction"),
    val middlewareNameHints: Set<String> = setOf("Middleware"),
    val reducerNameHints: Set<String> = setOf("Reducer", "reduce")
)
