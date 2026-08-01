# sidebar tabs v1.5 implementation plan

created: 2026-07-31
status: active
source_doc: docs/brainstorms/2026_07_31_sidebar_tabs_v1_5_requirements.md

## objective
- Convert the left drawer from a mixed static/dynamic list into a clean top-level launcher.
- Keep only fixed destinations in the drawer: **Searches**, **Notebooks**, **Tags**, and **Settings**.
- Move saved-search and notebook browsing to their existing full-screen destinations.
- Map child screens back to parent drawer selection where practical:
  - `BookFragment` -> **Notebooks**
  - `QueryFragment` -> **Searches**
  - `TagsFragment` -> **Tags**

## scope
- included:
  - remove dynamic saved-search rows from the drawer
  - remove dynamic notebook rows from the drawer
  - preserve fixed drawer actions for Searches, Notebooks, Tags, and Settings
  - preserve full-screen saved-search, notebook, and tag destinations
  - update drawer checked-state behavior so child screens do not depend on removed dynamic IDs
  - update `CHANGELOG.md`
  - build verification with `./scripts/build_fdroid.sh assembleFdroidDebug`
- excluded:
  - bottom navigation
  - Favorites / More tab
  - tag editing or tag-management actions
  - saved-search semantics or saved-search builder changes
  - notebook storage/sync behavior changes
  - result-list grouping
  - preserving **Tags** drawer highlight after opening a tag query result; tag query results can highlight **Searches** for V1.5 because they are normal query-result screens

## repo_context_checked
- repo root: `/home/agent/dev/orgzly`
- current branch: `custom-todo-per-file`
- `AGENTS.md`: missing
- `CLAUDE.md`: missing
- primary overview: `README.org`
- source requirements: `docs/brainstorms/2026_07_31_sidebar_tabs_v1_5_requirements.md`
- source ideation: `docs/ideation/2026_07_30_sidebar_navigation_ux_ideation.md`
- prior related plan: `docs/plans/2026_07_31_tag_browser_v1_plan.md`

## relevant_existing_patterns
- `app/src/main/res/menu/drawer.xml`
  - Current fixed menu items are already defined here.
  - V1 added `@+id/tags` as a fixed drawer item.
  - V1.5 should keep this simple menu-based drawer instead of introducing a custom drawer layout.
- `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - Currently assigns intents to fixed drawer items.
  - Currently observes `viewModel.books()` and `viewModel.savedSearches()` only to dynamically add drawer rows and selection mappings.
  - Dynamic rows are localized in `refreshFromSavedSearches(...)`, `refreshFromBooks(...)`, `generateRandomUniqueId()`, and `removeItemsWithOrder(...)`.
- `app/src/main/java/com/orgzly/android/ui/drawer/DrawerItem.kt`
  - Drawer checked state is based on each visible fragment's `getCurrentDrawerItemId()` string.
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - Currently returns a dynamic ID from `BookFragment.getDrawerItemId(mBookId)`.
  - After dynamic notebook rows are removed, this should instead return the parent `BooksFragment.drawerItemId` so **Notebooks** highlights.
- `app/src/main/java/com/orgzly/android/ui/notes/query/QueryFragment.kt`
  - Currently returns a dynamic ID from `QueryFragment.getDrawerItemId(currentQuery)`.
  - After dynamic saved-search rows are removed, this should instead return `SavedSearchesFragment.getDrawerItemId()` so **Searches** highlights.
- `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
  - Already returns `SavedSearchesFragment.getDrawerItemId()` and should keep highlighting **Searches**.
- `app/src/main/java/com/orgzly/android/ui/books/BooksFragment.kt`
  - Already returns `BooksFragment.drawerItemId` and should keep highlighting **Notebooks**.
- `app/src/main/java/com/orgzly/android/ui/tags/TagsFragment.kt`
  - Already returns `TagsFragment.drawerItemId` and should keep highlighting **Tags**.
- `app/src/main/java/com/orgzly/android/ui/main/MainActivity.java`
  - Already handles fixed drawer intents:
    - `ACTION_OPEN_SAVED_SEARCHES` -> `DisplayManager.displaySavedSearches(...)`
    - `ACTION_OPEN_BOOKS` -> `DisplayManager.displayBooks(...)`
    - `ACTION_OPEN_TAGS` -> `DisplayManager.displayTags(...)`
    - `ACTION_OPEN_SETTINGS` -> `openSettings()`
    - `ACTION_OPEN_QUERY` / `ACTION_OPEN_BOOK` should remain available for non-drawer flows.
- `app/src/main/java/com/orgzly/android/ui/DisplayManager.java`
  - Already hosts full-screen Searches, Notebooks, Tags, book, and query screens.

## implementation_units

### 1. Remove dynamic saved-search and notebook drawer rows
- files:
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
- changes:
  - Remove the saved-search and book observers from `init` if they are used only for dynamic drawer rows.
  - Delete or stop using `refreshFromSavedSearches(...)` and `refreshFromBooks(...)`.
  - Delete unused dynamic-row helper code if no longer needed:
    - `generateRandomUniqueId()`
    - `removeItemsWithOrder(...)`
  - Remove imports that become unused:
    - `Resources`
    - `Observer`
    - `BookAction`
    - `BookView`
    - `SavedSearch`
    - `BookFragment`
    - `QueryFragment`
    - `Random`
    - any sync-error drawer row imports that become unused
  - Keep fixed item intent setup:
    - Searches -> `ACTION_OPEN_SAVED_SEARCHES`
    - Notebooks -> `ACTION_OPEN_BOOKS`
    - Tags -> `ACTION_OPEN_TAGS`
    - Settings -> `ACTION_OPEN_SETTINGS`
- dependencies:
  - none.
- risks:
  - Removing observers should not stop repository refresh elsewhere. Verify `MainActivityViewModel.refresh(...)` and the full-screen fragments still load their own data.
  - Dynamic notebook sync/error indicators in the drawer will disappear by design. Ensure sync status is still available in the full-screen notebook list / sync area.
- tests:
  - compile/build.
- acceptance:
  - Drawer no longer inserts saved-search or notebook rows.
  - Fixed top-level items still navigate correctly.

### 2. Map child fragments to parent top-level drawer items
- files:
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/QueryFragment.kt`
  - possibly `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
- changes:
  - Change `BookFragment.getCurrentDrawerItemId()` to return `BooksFragment.drawerItemId` instead of `BookFragment.getDrawerItemId(mBookId)`.
  - Add/import `BooksFragment` in `BookFragment.kt` if needed.
  - Change `QueryFragment.getCurrentDrawerItemId()` to return `SavedSearchesFragment.getDrawerItemId()` instead of `QueryFragment.getDrawerItemId(currentQuery)`.
  - Add/import `SavedSearchesFragment` in `QueryFragment.kt` if needed.
  - After these changes, `BookFragment.getDrawerItemId(bookId)` and `QueryFragment.getDrawerItemId(query)` may become unused. Do not delete them until search confirms no external code/tests still call them; if unused, they may be safely removed in this feature.
  - Do not introduce query-origin tracking for tag results in V1.5.
- dependencies:
  - unit 1 should remove the dynamic mapping that made those old IDs useful.
- risks:
  - Some tests or code may assert old dynamic drawer IDs. If so, update them to parent area behavior.
  - Tag query results will highlight **Searches** because they are normal query screens; this is accepted V1.5 behavior.
- tests:
  - compile/build.
  - optional focused JVM tests only if existing tests already cover drawer item IDs.
- acceptance:
  - Individual notebook screens highlight **Notebooks**.
  - Query result screens highlight **Searches**.
  - No fragment emits a drawer item ID that only existed as a removed dynamic row.

### 3. Keep drawer menu order clean and stable
- files:
  - `app/src/main/res/menu/drawer.xml`
- changes:
  - Verify fixed order is sensible:
    - Searches
    - Notebooks
    - Tags
    - Settings
    - spacer/sync-progress placeholder if still needed
  - If the current order attributes already render that order, avoid churn.
  - If Settings currently appears before Tags because of XML order/resource behavior, set explicit `android:orderInCategory` values to match the expected order.
  - Keep icons and titles unchanged unless build/resource inspection shows a problem.
- dependencies:
  - unit 1.
- risks:
  - The existing placeholder item for sync/progress may be layout-sensitive; do not remove it unless manual/code inspection proves it is unnecessary.
- tests:
  - build resource compilation.
  - manual drawer visual check.
- acceptance:
  - Drawer has only the intended fixed destinations in the intended order.

### 4. Changelog and documentation cleanup
- files:
  - `CHANGELOG.md`
  - `docs/brainstorms/2026_07_31_sidebar_tabs_v1_5_requirements.md` if implementation discovers a scope correction
  - this plan file if implementation discovers a required sequencing correction before handoff
- changes:
  - Add a concise user-facing changelog entry such as: `Cleaned up the drawer so saved searches and notebooks live in their full-screen sections instead of appearing inline.`
  - Keep docs aligned with any final selected-state behavior.
- dependencies:
  - units 1-3.
- risks:
  - Avoid overstating as a full navigation redesign; this is drawer cleanup only.
- tests:
  - none beyond review.
- acceptance:
  - User-facing change is documented.

## sequencing
1. Update `DrawerNavigationView.kt` to stop adding dynamic rows and remove unused dynamic-row plumbing.
2. Update `BookFragment` and `QueryFragment` drawer item IDs to return fixed parent area IDs.
3. Check `drawer.xml` order and adjust only if necessary.
4. Run `git diff --check`.
5. Build with `./scripts/build_fdroid.sh assembleFdroidDebug`.
6. Fix compile/import/resource issues.
7. Update `CHANGELOG.md` and docs if final behavior differs from the plan.
8. Prepare manual QA checklist.

## risks_and_unknowns
- risk: Removing dynamic drawer rows could accidentally remove the only visible indication of out-of-sync/error notebooks in the drawer.
  - mitigation: This is acceptable for the clean-launcher scope if the full-screen Notebooks list still shows sync/error state. Include manual QA for sync/error indicator visibility in Notebooks.
- risk: Query screens from tag taps highlight **Searches**, which may feel less semantically perfect.
  - mitigation: Accepted for V1.5 to avoid origin-state plumbing. Future polish can add query-origin metadata if Scott wants Tags to stay highlighted after tag-row taps.
- risk: Existing `getDrawerItemId(...)` helper methods may be used by tests or dynamic mappings.
  - mitigation: Search for callers after code changes before deleting helpers.
- risk: Drawer item selection may briefly clear when opening Settings because Settings is an activity, not a fragment implementing `DrawerItem`.
  - mitigation: Do not redesign Settings hosting in V1.5. Manual QA should verify the user-facing result is not confusing.

## validation_strategy
- Static checks:
  - `git diff --check`
  - search for remaining `refreshFromSavedSearches`, `refreshFromBooks`, dynamic `menu.add(...)`, `BookFragment.getDrawerItemId(`, and `QueryFragment.getDrawerItemId(` references.
- Build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- Optional targeted tests:
  - Run existing drawer/navigation tests if discoverable; otherwise do not block on absent tests.
  - Avoid broad test runs as the repo has known unrelated Robolectric/JDK failures.
- Manual QA:
  - Open drawer and verify only Searches, Notebooks, Tags, Settings plus sync/progress area are visible.
  - Tap Searches and open a saved search from the full-screen list.
  - Confirm query result opens and drawer highlights Searches.
  - Tap Notebooks and open a notebook from the full-screen list.
  - Confirm notebook opens and drawer highlights Notebooks.
  - Tap Tags and confirm the V1 tag browser still works.
  - Tap a tag and confirm the query result opens; drawer may highlight Searches for V1.5.
  - Tap Settings and confirm Settings opens.
  - Confirm app restart/back-stack behavior still feels normal.

## handoff_notes
- This should be a small implementation. Do not replace `NavigationView` with a custom tabbed drawer for V1.5.
- The main work is deleting dynamic drawer insertion and replacing dynamic checked-state IDs with parent area IDs.
- Preserve all non-drawer ways to open individual notebooks and saved searches.
- Keep tag-browser behavior unchanged from V1 except for the cleaner drawer context around it.
- Build success is the key automated verification; manual QA is important for drawer visual behavior.
