# Firefox Redux Navigator

First iteration of an Android Studio / IntelliJ plugin to navigate Firefox Android Redux actions.

## What works in this first iteration

- Adds a gutter icon next to Kotlin references that look like Redux action references.
- Resolves the referenced Kotlin class/object.
- Finds references to the same action across the project.
- Classifies references into:
  - Dispatches
  - Middlewares
  - Reducers
  - Other references
- Opens a grouped popup when clicking the gutter icon.
- Double-click a usage in the popup to navigate to it.

## Current heuristics

The first iteration is intentionally Firefox-oriented and heuristic-based.

An action is recognized when:

- its name ends with `Action`, or
- it extends/implements a type containing one of:
  - `Action`
  - `BrowserAction`
  - `AppAction`
  - `TabsTrayAction`
  - `MenuAction`

A usage is classified as:

- **Dispatch** when it appears in a `dispatch(...)` call.
- **Middleware** when it appears in a `when` branch inside a class/function with `Middleware` in the name or supertype.
- **Reducer** when it appears in a `when` branch inside a class/function with `Reducer` or `reduce` in the name.
- **Other references** otherwise.

## Build

This project uses the IntelliJ Platform Gradle Plugin 2.x. JetBrains documents 2.x as the current supported plugin line, replacing the older 1.x Gradle IntelliJ Plugin.

```bash
./gradlew buildPlugin
```

For a faster edit/build loop while the sandbox IDE stays open:

```bash
./scripts/watch-plugin.sh
```

This keeps `buildPlugin` running in continuous mode, so saved code changes rebuild automatically.

The built plugin zip will be under:

```text
build/distributions/
```

## Try it in Android Studio

1. Build the plugin zip.
2. Open Android Studio.
3. Go to **Settings / Preferences > Plugins > Gear icon > Install Plugin from Disk...**.
4. Select the zip from `build/distributions/`.
5. Restart Android Studio.
6. Open a Firefox Android checkout.
7. Look for the purple Redux icon next to Kotlin action references.

## Faster sandbox loop

If you are already running `./gradlew runIde`, you usually do not need to kill it for ordinary code changes.

- Keep the sandbox IDE open.
- Run `./scripts/watch-plugin.sh` in another terminal.
- Save your Kotlin or Java changes.
- For pure code changes, the rebuilt classes are picked up without restarting the sandbox in many cases.
- For `plugin.xml`, new extensions, or startup wiring changes, restart the sandbox IDE.

## Important limitations

This is a prototype-quality first iteration, not a production-ready plugin yet.

Known limitations:

- Kotlin only.
- No persistent index yet; references are searched on click.
- No caching yet.
- Middleware/reducer detection is name/supertype based.
- Dispatch wrapper functions are not modeled yet.
- State-centric visualization is not implemented yet.
- Mermaid export is not implemented yet.

## Suggested next iterations

1. Add a persistent per-file Redux index.
2. Add a CodeLens-style inlay above action declarations.
3. Add a dedicated Redux Flow tool window.
4. Add Mermaid export.
5. Add settings for Firefox-specific conventions.
6. Add tests against small fake Fenix/android-components Redux samples.
