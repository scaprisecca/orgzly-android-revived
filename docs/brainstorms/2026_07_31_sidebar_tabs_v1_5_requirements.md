# sidebar tabs v1.5 requirements

created: 2026-07-31
status: ready_for_plan
source: ce-brainstorm
source_docs:
  - docs/ideation/2026_07_30_sidebar_navigation_ux_ideation.md
  - docs/brainstorms/2026_07_31_tag_browser_v1_requirements.md
  - docs/plans/2026_07_31_tag_browser_v1_plan.md

## problem
- The current drawer still mixes top-level navigation with dynamic saved searches and dynamic notebooks.
- Tags V1 proved that a full-screen browsable destination works well, but the drawer remains cluttered because saved searches and notebooks are still inserted inline.
- Scott wants the drawer to become a cleaner launcher for the app's major areas rather than a long mixed list.

## current_state_findings
- Repo root inspected: `/home/agent/dev/orgzly`.
- Current branch inspected: `custom-todo-per-file`.
- `AGENTS.md` and `CLAUDE.md` are absent from the repo root.
- Primary overview previously inspected: `README.org`.
- Relevant source docs inspected:
  - `docs/ideation/2026_07_30_sidebar_navigation_ux_ideation.md`
  - `docs/brainstorms/2026_07_31_tag_browser_v1_requirements.md`
  - `docs/plans/2026_07_31_tag_browser_v1_plan.md`
- Current committed V1 state:
  - `app/src/main/res/menu/drawer.xml` contains fixed items for Searches, Notebooks, Settings, and Tags.
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt` still inserts saved searches dynamically at order `1` and notebooks dynamically at order `3`.
  - `TagsFragment` is a full-screen drawer destination and Scott likes its scrollable full-screen view.
  - `SavedSearchesFragment` already provides a full-screen list of all saved searches.
  - `BooksFragment` already provides a full-screen list of all notebooks.
  - Settings is still opened as an activity through `ACTION_OPEN_SETTINGS`.

## goals
- Make the drawer/sidebar a clean top-level launcher.
- Keep the four primary areas visible and predictable:
  - Searches
  - Notebooks
  - Tags
  - Settings
- Preserve the successful Tags V1 full-screen browsing model.
- Move saved searches and notebooks out of the drawer's inline dynamic list and into their respective full-screen tabs/destinations.
- Reduce navigation clutter without introducing bottom navigation or broader result-list redesign.

## non_goals
- Do not implement bottom navigation in V1.5.
- Do not build Favorites in V1.5.
- Do not redesign query result grouping in V1.5.
- Do not change saved-search query semantics.
- Do not change notebook storage/sync behavior.
- Do not add tag editing, tag hiding, or tag cleanup in this feature.
- Do not redesign Settings content; only keep Settings as a fixed top-level drawer item.
- Do not remove the existing full-screen Searches, Notebooks, or Tags screens.

## proposed_behavior
- The drawer becomes a clean launcher with only fixed top-level destinations:
  - Searches
  - Notebooks
  - Tags
  - Settings
- The drawer should no longer show every saved search inline.
- The drawer should no longer show every notebook inline.
- Tapping **Searches** opens the existing full-screen saved-search list.
  - That screen is where all saved searches are browsed, managed, reordered, imported/exported, and opened.
- Tapping **Notebooks** opens the existing full-screen notebook list.
  - That screen is where all notebooks are browsed, imported, created, synced, renamed, deleted, and opened.
- Tapping **Tags** opens the existing full-screen Tags browser from V1.
  - Keep the scrollable Tags view exactly as the default interaction.
  - Do not put individual tags directly in the drawer.
- Tapping **Settings** opens Settings as a fixed top-level drawer item.
  - Scott wants Settings treated as one of the top-level tabs/areas, not hidden under More.
- The drawer should retain normal checked/selected state by mapping child screens back to their parent top-level area:
  - individual notebook screens highlight **Notebooks**
  - query-result screens highlight **Searches**
  - the full-screen Tags browser highlights **Tags**
  - tag query results can use the existing query-result screen and highlight **Searches** for V1.5; preserving **Tags** during tag-result viewing is deferred because it would require carrying query origin/source state through navigation.
- Existing direct navigation to a saved search or notebook from outside the drawer should keep working if supported by intents, widgets, shortcuts, links, or internal code paths.
- The user-facing effect should be a calmer drawer with four stable choices and no growing dynamic lists.

## resolved_decisions
- V1.5 uses Option B from the ideation: a cleaner top-level launcher / tab-like drawer, not a long grouped list.
- Settings remains a fixed drawer item and should be treated as a top-level tab/area.
- Tags remains a full-screen scrollable browser; Scott likes being able to scroll through all tags.
- Searches opens the full-screen saved-search list; dynamic saved searches should not appear inline in the drawer.
- Notebooks opens the full-screen notebook list; dynamic notebooks should not appear inline in the drawer.
- V1.5 starts with the drawer only. Broader navigation redesign, bottom nav, Favorites, and result grouping are deferred.
- Child-screen drawer selection should map back to parent areas:
  - `BookFragment` -> **Notebooks**
  - `QueryFragment` -> **Searches**
  - `TagsFragment` -> **Tags**
- Tag query results should remain normal query-result screens for V1.5 and can highlight **Searches**; preserving a **Tags** highlight through tag-result navigation is a future polish item, not a blocker.

## constraints
- Preserve existing `SavedSearchesFragment`, `BooksFragment`, `TagsFragment`, and Settings entry points.
- Avoid a broad navigation framework rewrite unless implementation inspection proves the current `NavigationView` approach cannot support removing dynamic rows cleanly.
- Keep the change easy to revert: removing dynamic drawer insertion should not remove data retrieval used elsewhere.
- Be careful with drawer selection state: currently individual saved-search query results and notebook screens can map back to dynamically generated drawer items. After removing dynamic rows, map them to fixed parent items instead of leaving dangling dynamic IDs.
- Continue using repo-relative paths and existing Android resource conventions.

## open_questions
- None blocking planning.

## success_criteria
- The drawer shows only the four fixed top-level destinations plus existing sync/progress area if applicable.
- Saved searches no longer appear inline in the drawer.
- Notebooks no longer appear inline in the drawer.
- Searches opens the full-screen saved-search list and saved searches can still be opened from there.
- Notebooks opens the full-screen notebook list and notebooks can still be opened from there.
- Tags opens the existing full-screen Tags browser and preserves V1 behavior.
- Settings remains a visible fixed drawer destination.
- Existing query results and notebook screens still work when opened from their full-screen lists.
- Drawer checked state is not misleading or broken after dynamic rows are removed.
- Build succeeds with `./scripts/build_fdroid.sh assembleFdroidDebug` after implementation.

## notes_for_planning
- Likely files:
  - `app/src/main/res/menu/drawer.xml`
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - possibly `app/src/main/java/com/orgzly/android/ui/DisplayManager.java`
  - possibly `app/src/main/java/com/orgzly/android/ui/main/MainActivity.java`
  - `CHANGELOG.md`
- Current dynamic drawer insertion is localized in `DrawerNavigationView.refreshFromSavedSearches(...)` and `DrawerNavigationView.refreshFromBooks(...)`.
- Planning should inspect whether `menuItemIdMap` entries for `QueryFragment.getDrawerItemId(...)` and `BookFragment.getDrawerItemId(...)` are only for checked state, or whether any behavior depends on those dynamic IDs.
- A likely V1.5 implementation may stop observing saved searches/books from `DrawerNavigationView` entirely, remove `refreshFromSavedSearches(...)` / `refreshFromBooks(...)` dynamic insertion, and keep only fixed item mappings and intents.
- If individual book/query fragments no longer have a dynamic drawer item, planning should decide whether to:
  - leave no drawer item checked for those screens, or
  - map book screens to **Notebooks** and query screens to **Searches** as parent areas.
- Recommended next step: run `ce-plan` on this requirements document.
- 2026-07-31: Scott accepted the recommended selected-state behavior, and the implementation plan was created at `docs/plans/2026_07_31_sidebar_tabs_v1_5_plan.md`.
