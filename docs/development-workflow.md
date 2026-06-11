# Development Workflow

The fastest way to test this plugin is to run a separate sandbox IDE instance and keep your main Android Studio window open for editing.

## Quick Loop

1. Start the plugin sandbox:

```bash
./gradlew runIde
```

2. Open the plugin in the sandbox IDE.
3. Make code changes in your main IDE.
4. Rebuild or let Gradle refresh the changed classes.
5. Use the sandbox IDE to click through the plugin behavior.

If you want the rebuild step to happen automatically, run this in a separate terminal:

```bash
./scripts/watch-plugin.sh
```

## What Usually Reloads Well

- Kotlin and Java implementation changes
- Popup and gutter logic
- Small UI behavior changes

## What Usually Needs a Sandbox Restart

- `plugin.xml` changes
- New extension registrations
- Resource or wiring changes that affect plugin startup

## Notes On Reloading

- `buildPlugin --continuous` is the quickest way to keep the sandbox fed with fresh plugin output.
- For code-only changes, that is often enough.
- For extension registration changes, restart the sandbox IDE after rebuilding.

## Notes

- The sandbox IDE is separate from your main Android Studio window, so you do not need to restart your main editor every time.
- For this project, the sandbox is the best place to verify gutter behavior, popup placement, and navigation changes.
