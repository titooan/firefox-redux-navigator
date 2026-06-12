# Next Steps Plan

## Summary

Phase 6 is now implemented and working, with follow-up fixes for:

- non-blocking State Explorer loading
- correct action-row source rendering
- compact row layout in tall panes
- visible row selection feedback

The next work should focus on improving correctness, performance, and discoverability before adding more UI surface area.

## Priority 1: Analysis Accuracy

### 1. Improve nested state-field tracking

Current limitation:

- `state.copy(tabState = state.tabState.copy(selectedTabId = ...))` is only partially modeled
- nested state paths are not consistently reported as `tabState.selectedTabId`

Implementation goals:

- detect nested `copy(...)` chains reliably
- preserve parent-to-child field path information
- report the most precise field path that can be inferred safely
- fall back to top-level field only when nested inference is ambiguous

Acceptance criteria:

- nested reducer updates produce `parent.child` paths when the structure is obvious
- top-level fallback still works when precision is not possible
- tests cover nested `copy(...)` cases

### 2. Improve action association heuristics

Current limitation:

- action association works for direct `when`, `if (action is ...)`, and concrete action parameters
- helper-local patterns and slightly indirect reducer structures may still show `Unknown action`

Implementation goals:

- support obvious local helper flows inside reducer bodies
- improve matching for `when` branches with nested expressions
- keep heuristics conservative to avoid false positives

Acceptance criteria:

- fewer obvious reducer updates appear as `Unknown action`
- existing known-good associations remain stable
- new tests cover helper-local and nested branch cases

## Priority 2: Performance

### 3. Add a reducer-focused analysis index or per-file cache

Current limitation:

- first-open still requires scanning Kotlin files, even though it now happens off the UI thread

Implementation goals:

- avoid repeated full-project reducer scans
- cache reducer candidate files and/or extracted state modifications
- invalidate safely on PSI modification count

Possible implementation path:

- add a lightweight project cache of reducer-like Kotlin files
- optionally cache extracted modifications per file
- reuse the same invalidation model as existing graph/state caches

Acceptance criteria:

- first-open latency is reduced on large projects
- repeated opens for different fields reuse cached work
- cache invalidation remains correct after file edits

## Priority 3: Editor Discoverability

### 4. Add a state-property inlay hint

Current limitation:

- the feature is only discoverable through the editor context menu

Implementation goals:

- show a lightweight hint above state properties, for example `Modified by N actions`
- clicking the hint opens the existing `State` tab
- do not introduce expensive work during editor repaint

Acceptance criteria:

- hints appear only on resolvable state properties
- clicking the hint opens State Explorer for that property
- rendering stays cache-backed and non-blocking

## Priority 4: Visualization

### 5. Add a state graph mode

Current limitation:

- State Explorer is textual only

Implementation goals:

- add a state-centric graph view using the existing graph stack
- visualize `Action -> Reducer -> State Field`
- preserve current textual `State` view as the baseline/debuggable representation

Acceptance criteria:

- graph nodes navigate correctly
- graph mode reuses the existing tool window architecture
- textual mode remains available when graph mode is incomplete or ambiguous

## Suggested Execution Order

1. Nested field precision
2. Reducer/action association improvements
3. Reducer-focused caching/indexing
4. State-property inlay hint
5. State graph mode

## Notes

- Keep the State Explorer textual path as the source of truth while expanding features.
- Prefer conservative heuristics over broader but noisy matches.
- Validate each step against large real-world files in Android Components and Fenix, not only synthetic tests.
