# tag browser v1 implementation plan

created: 2026-07-31
status: active
source_doc: docs/brainstorms/2026_07_31_tag_browser_v1_requirements.md

## objective
- Build a focused V1 Tags browser for Orgzly's left drawer.
- Add one fixed drawer destination named **Tags** that opens a full-screen alphabetical tag list with active-task counts.
- Tapping a tag opens the existing query-result screen for active TODO-type notes matching that tag, including inherited tags.

## scope
- included:
  - fixed drawer item for Tags
  - new tags browser screen with toolbar, list, empty state, and row counts
  - active-task effective-tag counting where effective tags include direct tags, inherited parent tags, and filetags
  - query navigation for a selected tag using existing query-result flow
  - strings/layouts/resources needed for the user-facing UI
  - focused helper/repository tests where practical
  - `CHANGELOG.md` entry because this is user-facing navigation behavior
- excluded:
  - tabbed drawer redesign
  - bottom navigation
  - Favorites
  - tag rename/delete/hide/management actions
  - saved-search builder changes
  - query-result grouping by tag
  - changing Org tag parsing, tag storage, sync, or serialization

## repo_context_checked
- repo root: `/home/agent/dev/orgzly`
- current branch: `custom-todo-per-file`
- `AGENTS.md`: missing
- `CLAUDE.md`: missing
- primary overview: `README.org`
- source ideation: `docs/ideation/2026_07_30_sidebar_navigation_ux_ideation.md`
- source requirements: `docs/brainstorms/2026_07_31_tag_browser_v1_requirements.md`
- nearby docs inspected:
  - `docs/brainstorms/2026_06_30_tag_suggestion_management_requirements.md`
  - `docs/plans/2026_06_30_tag_suggestion_management_plan.md`
  - `docs/plans/2026_05_13_agenda_dashboard_presets_plan.md`

## relevant_existing_patterns
- `app/src/main/res/menu/drawer.xml`
  - Fixed drawer item definitions currently include Searches, Notebooks, and Settings.
  - Add Tags here rather than inserting dynamic tag rows.
- `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - Maps fixed destinations to intents and drawer checked-state IDs.
  - Saved searches and notebooks are dynamically inserted by order; preserve their behavior.
- `app/src/main/java/com/orgzly/android/ui/main/MainActivity.java`
  - Receives drawer item intents through the `NavigationView` listener and broadcasts them after a short drawer-close delay.
  - Existing intent handlers already open searches, books, settings, books, and query results.
- `app/src/main/java/com/orgzly/android/AppIntent.java`
  - Existing app-local action constants live here. Add an `ACTION_OPEN_TAGS` constant.
- `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - `displayQuery(query)` posts `MainNavigationAction.DisplayQuery(query)` for existing query-result navigation.
- `app/src/main/java/com/orgzly/android/db/dao/NoteViewDao.kt`
  - `QUERY` already builds `inherited_tags` by joining book filetags and ancestor note tags.
  - This is the safest source for inherited-tag semantics if the count helper uses `NoteView` rows.
- `app/src/main/java/com/orgzly/android/query/user/DottedQueryBuilder.kt`
  - `Condition.HasStateType(StateType.TODO)` compiles to `it.todo`.
  - `Condition.HasTag(tag)` compiles to `t.<tag>` and should be used over `HasOwnTag`.
- `app/src/main/java/com/orgzly/android/query/sql/SqliteQueryBuilder.kt`
  - TODO-state filtering uses `AppPreferences.todoKeywordsSet(context)`, so custom TODO workflows are already represented by `it.todo` queries.
- `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
  - Good main-screen list pattern: `CommonFragment`, drawer toolbar icon, RecyclerView, adapter, and search/settings menu handling.
- `app/src/main/java/com/orgzly/android/ui/capture/TemplateListFragment.kt`
  - Useful compact RecyclerView/list-refresh pattern for settings-hosted screens; less directly applicable than `SavedSearchesFragment` because Tags is a main drawer destination.

## implementation_units

### 1. Add tag-row domain model and effective-tag counting helper
- files:
  - `app/src/main/java/com/orgzly/android/ui/tags/TagBrowserRow.kt`
  - `app/src/main/java/com/orgzly/android/ui/tags/TagBrowserRows.kt`
  - `app/src/test/java/com/orgzly/android/ui/tags/TagBrowserRowsTest.kt`
- changes:
  - Create a small row model, e.g. `TagBrowserRow(tag: String, activeCount: Int)`.
  - Create a pure helper that accepts note-like inputs with direct tags, inherited tags, and TODO-state classification, then returns sorted rows.
  - Exact/case-sensitive behavior: `@HomeDepot` and `@homedepot` remain separate tags.
  - Include only rows with `activeCount > 0` for V1.
- dependencies:
  - none.
- risks:
  - If the helper only reads direct `note.tags`, inherited parent-tag workflows will be invisible.
  - If the helper treats DONE or plain headings as active, counts will be misleading.
- tests:
  - inherited tag contributes to child TODO count
  - direct tag contributes to count
  - DONE/plain notes are excluded
  - duplicate tag on direct + inherited paths for the same note counts once for that tag
  - case-sensitive tag names remain separate
  - output is alphabetical
- acceptance:
  - Counting semantics are locked down before UI wiring.

### 2. Expose active effective-tag rows from repository/view model
- files:
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NoteViewDao.kt` if adding a DAO-level query
  - `app/src/main/java/com/orgzly/android/ui/tags/TagsViewModel.kt`
  - possible tests under `app/src/test/java/com/orgzly/android/data/` or `app/src/test/java/com/orgzly/android/ui/tags/`
- changes:
  - Preferred simple V1 path: repository obtains relevant `NoteView` rows, filters active TODO states using configured TODO keywords, splits direct and inherited tags, and passes them through `TagBrowserRows`.
  - If performance is acceptable for typical mobile Orgzly datasets, keep the logic Kotlin-side for clarity and testability.
  - If the list becomes slow, move to a DAO-level aggregate query later; do not over-optimize V1 unless profiling shows a problem.
  - Expose rows as `LiveData<List<TagBrowserRow>>` from `TagsViewModel` so the browser refreshes as notes change.
- dependencies:
  - unit 1.
- risks:
  - `DataRepository.selectAllTagsLiveData()` is not sufficient because it is direct-tags-only and has no active-count data.
  - `DataRepository.selectAllTags()` currently does not sort; do not depend on its order.
  - Repository tests may hit existing Robolectric/JDK issues; prefer pure helper tests and a successful build if broader tests are noisy.
- tests:
  - pure helper tests from unit 1 are required
  - repository-level test only if it can be kept stable in this repo's current test environment
- acceptance:
  - `TagsViewModel` can supply alphabetical active-count rows that include inherited tags.

### 3. Build the Tags browser UI
- files:
  - `app/src/main/java/com/orgzly/android/ui/tags/TagsFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/tags/TagsAdapter.kt`
  - `app/src/main/res/layout/fragment_tags.xml`
  - `app/src/main/res/layout/item_tag_browser.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/di/AppComponent.kt` if injection is needed
- changes:
  - Implement `TagsFragment` as a main-screen drawer destination, preferably extending `CommonFragment` and implementing `DrawerItem`.
  - Use a top toolbar with hamburger navigation like `SavedSearchesFragment`.
  - Use `RecyclerView` with stable row IDs if straightforward.
  - Row content: tag name and active-count text.
  - Empty state: `No active task tags found` or similar.
  - On row tap, request query navigation through a listener/shared activity view model.
  - Do not include edit/delete/hide controls in V1.
- dependencies:
  - units 1 and 2.
- risks:
  - Overloading this into tag management will bloat V1 and overlap with separate tag-suggestion management work.
  - If the toolbar does not open the drawer, Tags will feel inconsistent with Searches/Notebooks.
- tests:
  - compile/build resource binding
  - optional adapter helper tests for formatting/count display if logic is non-trivial
- acceptance:
  - Tags screen opens, renders list/empty state, and taps are wired to query navigation.

### 4. Add drawer and intent navigation wiring
- files:
  - `app/src/main/res/menu/drawer.xml`
  - `app/src/main/java/com/orgzly/android/AppIntent.java`
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivity.java`
  - any display/navigation helper used by `MainActivity`
  - `app/src/main/res/values/strings.xml`
- changes:
  - Add string `tags` if not already available in base `values/strings.xml`; reuse existing translated `tags` key only if present in the default resource set.
  - Add a fixed drawer item `@+id/tags` between Notebooks and Settings, with an available icon or a simple new vector drawable if needed.
  - Add `AppIntent.ACTION_OPEN_TAGS`.
  - Map Tags drawer item to that intent in `DrawerNavigationView`.
  - Add drawer checked-state mapping for `TagsFragment.drawerItemId`.
  - Add a `MainActivity` handler that displays `TagsFragment` when `ACTION_OPEN_TAGS` is broadcast.
  - Keep saved-search order `1`, notebooks order `3`, and settings below Tags; adjust only the fixed item order values needed to preserve visual sequence.
- dependencies:
  - unit 3 can exist before final drawer wiring.
- risks:
  - Drawer dynamic insertion uses menu order; a careless order change can interleave saved searches/notebooks incorrectly.
  - If Tags is checkable but not mapped through `DrawerItem`, checked state will not update consistently.
- tests:
  - compile/build
  - manual QA of drawer ordering and checked state
- acceptance:
  - Drawer order is Searches, saved searches, Notebooks, notebooks, Tags, Settings.

### 5. Open active-tag query on row tap
- files:
  - `app/src/main/java/com/orgzly/android/ui/tags/TagsFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt` if adding a named-query navigation helper
  - `app/src/main/java/com/orgzly/android/query/user/DottedQueryBuilder.kt` only if existing builder cannot be reused from UI code
- changes:
  - Build a query equivalent to `it.todo t.<tag>` using existing query model/builder where practical.
  - Prefer `Condition.HasStateType(StateType.TODO)` plus `Condition.HasTag(tag)` to avoid accidentally using direct-tag-only semantics.
  - Navigate via the existing query-result path (`displayQuery` / `DisplayManager.displayQuery`).
  - If the current path supports a search name/title, pass the tag name as a readable title; otherwise accept the query-string title in V1.
- dependencies:
  - units 2 and 3.
- risks:
  - Hand-concatenating `t.$tag` can break if tags need quoting/escaping; use `DottedQueryBuilder` if available.
  - Using `HasOwnTag` would contradict the inherited-tag requirement.
- tests:
  - helper test that selected tag produces a TODO + inherited-tag query string, if query construction is extracted
  - manual QA with a parent-tagged child TODO
- acceptance:
  - Tapping a tag opens only active TODO-type matches and includes inherited matches.

### 6. Add copy, changelog, and QA notes
- files:
  - `app/src/main/res/values/strings.xml`
  - `CHANGELOG.md`
  - optionally `docs/` manual QA note if useful
- changes:
  - Add clear strings for Tags title, count formatting, and empty state.
  - Add a concise changelog bullet such as `Add a Tags browser in the navigation drawer for active tasks grouped by tag.`
  - Keep copy generic and app-wide; do not mention Scott-specific workflows in product strings.
- dependencies:
  - UI units.
- risks:
  - Count pluralization may need Android quantity strings if the UI says `1 task` vs `2 tasks`.
- tests:
  - resource compile through debug build.
- acceptance:
  - User-facing text is clear and build resources compile.

## sequencing
1. Add pure row model/counting helper with tests.
2. Expose tag rows from repository/view model without touching drawer/UI yet.
3. Build TagsFragment/adapter/layout against mocked or view-model data.
4. Wire drawer item, app intent, checked state, and MainActivity display handling.
5. Wire row taps to query navigation through the existing query-result flow.
6. Add changelog/copy and run verification.

## risks_and_unknowns
- risk: inherited-tag active counts are harder than direct-tag discovery.
  - mitigation: base counting on `NoteView` effective tags or equivalent SQL that includes ancestor tags/filetags; verify with a parent-tagged child TODO fixture/manual note.
- risk: Kotlin-side counting over all notes could be slow on very large notebooks.
  - mitigation: keep V1 simple unless actual performance is poor; a DAO aggregate can be a follow-up optimization.
- risk: drawer item ordering regresses the existing saved-search/notebook insertion behavior.
  - mitigation: preserve dynamic order buckets and manually QA drawer order.
- risk: unit tests may be noisy in this repo due to existing test-environment issues.
  - mitigation: prioritize pure JVM helper tests and `assembleFdroidDebug`; separate repo-wide test failures from feature failures.
- risk: tag-browser work overlaps with tag-suggestion management naming.
  - mitigation: keep this feature read-only and call actions **Browse tags** / **Tags**, not manage/cleanup.

## validation_strategy
- Run focused helper tests if added, e.g. explicit `app:testFdroidDebugUnitTest --tests ...TagBrowserRowsTest` and inspect the XML result if the broader task is noisy.
- Run the repo's reliable baseline build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- Manual QA checklist:
  - Drawer shows Tags in the intended position.
  - Searches, saved searches, Notebooks, notebooks, and Settings still open normally.
  - Tags screen opens from drawer and hamburger reopens drawer.
  - Tag list is alphabetical and shows counts.
  - Parent-tagged child TODO contributes to the inherited tag count.
  - DONE/plain notes do not contribute to default count.
  - Tapping a tag opens the query-result screen with active TODO matches.
  - Empty state appears when there are no active task tags.

## handoff_notes
- Do not implement the larger tabbed-drawer redesign in this branch.
- Do not add tag editing/cleanup controls; that belongs to separate tag-suggestion/tag-management work.
- Use configured TODO keywords through `StateType.TODO` / `it.todo`, not hard-coded `TODO`.
- Use inherited tag query semantics (`Condition.HasTag`), not direct-tag-only (`HasOwnTag`).
- Keep the feature compatible with a later drawer redesign by isolating the browser screen and row/counting helpers.
