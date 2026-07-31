# tag browser v1 requirements

created: 2026-07-31
status: ready_for_plan
source: ce-brainstorm
source_doc: docs/ideation/2026_07_30_sidebar_navigation_ux_ideation.md

## problem
- The current left drawer mixes fixed destinations, dynamic saved searches, dynamic notebooks, and settings into one long menu.
- Tags are an important workflow layer for Scott, especially inherited context tags such as Amazon, Walmart, Home Depot, errands, and similar shopping/action groupings.
- Orgzly can already query by tag, including inherited tags, but there is no first-class place to browse all tags without manually creating saved searches.
- Putting every tag directly into the drawer would make the clutter problem worse.

## current_state_findings
- Repo root inspected: `/home/agent/dev/orgzly`.
- Current branch inspected: `custom-todo-per-file`.
- `AGENTS.md` and `CLAUDE.md` are absent.
- Primary overview inspected: `README.org`.
- Source ideation inspected: `docs/ideation/2026_07_30_sidebar_navigation_ux_ideation.md`.
- Nearby planning docs inspected:
  - `docs/brainstorms/2026_06_30_tag_suggestion_management_requirements.md`
  - `docs/plans/2026_06_30_tag_suggestion_management_plan.md`
  - `docs/plans/2026_05_13_agenda_dashboard_presets_plan.md`
- Current drawer structure:
  - `app/src/main/res/menu/drawer.xml` defines fixed drawer items for Searches, Notebooks, and Settings.
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt` dynamically inserts saved searches at order `1` and notebooks at order `3`.
  - Drawer menu item intents are broadcast through `MainActivity.setupDrawer()` after the drawer closes.
- Current tag discovery:
  - `NoteDao.getDistinctTagsLiveData()` returns distinct direct `notes.tags` strings.
  - `DataRepository.selectAllTagsLiveData()` splits, deduplicates, and sorts direct tag strings.
  - `DataRepository.selectAllTags()` splits and deduplicates but currently does not sort.
  - `NoteViewDao.QUERY` already joins ancestors and exposes `inherited_tags` on `NoteView`.
- Current query support:
  - `Condition.HasTag` maps to dotted query `t.<tag>` and includes inherited tags.
  - `Condition.HasOwnTag` maps to own/direct-tag-only query behavior and should not be the default for this feature.
  - `Condition.HasStateType(StateType.TODO)` maps to dotted query `it.todo`, using configured TODO keywords through `AppPreferences.todoKeywordsSet(...)`.
  - `MainActivityViewModel.displayQuery(query)` already navigates to a normal query-result screen.

## goals
- Add a dedicated Tags browser as the focused V1 slice from the sidebar navigation ideation.
- Let the user open the drawer, tap Tags, see all useful tags, and tap one tag to open active tasks matching that tag.
- Treat inherited tags as first-class so parent-heading context tags work the way Scott expects.
- Show active-task counts beside each tag so the browser is useful for triage, not just discovery.
- Keep the implementation compatible with later drawer grouping/tabbing work.

## non_goals
- Do not redesign the entire drawer into tabs in V1.
- Do not add bottom navigation in V1.
- Do not place every dynamic tag directly inside the drawer.
- Do not build Favorites in V1.
- Do not build custom grouping for query results in V1.
- Do not create, rename, hide, or delete tags from notes.
- Do not replace saved searches or the saved-search builder.
- Do not change Org tag parsing or serialization.

## proposed_behavior
- The drawer gains a fixed top-level **Tags** item between Notebooks and Settings.
- Tapping **Tags** opens a full-screen Tags browser, not an expanded drawer list.
- The Tags browser lists tag names alphabetically.
- Each row shows:
  - exact tag name
  - count of active TODO-type headings matching that tag
- Counts use the same default semantics as tapping a tag:
  - include inherited tag matches
  - include direct tag matches
  - include only active TODO-type notes, based on the user's configured TODO keywords
  - exclude DONE-type headings
  - exclude headings with no TODO state
  - exclude cut/deleted/non-visible note rows consistently with normal query result expectations
- Tapping a tag opens the existing query result flow using a query equivalent to `it.todo t.<tag>`.
- The opened query should have a readable title such as the tag name where existing query navigation allows it.
- The Tags browser is read-only in V1.
- If no tags have active-task matches, show an empty state explaining that no active task tags were found.
- If a tag exists only on completed/plain notes, it should not appear in the default V1 list unless the implementation chooses to show it disabled with count `0`; the preferred V1 is to hide zero-count tags to keep the browser task-focused.

## constraints
- The V1 slice should be smaller than a full information-architecture redesign.
- The feature should reuse existing query/navigation plumbing instead of inventing a parallel task retrieval screen.
- `DataRepository.selectAllTagsLiveData()` is direct-tags-only and not sufficient for inherited-tag counts.
- Counting inherited active tags likely needs a new repository/DAO/helper path based on `NoteView` data or equivalent SQL over `notes`, `note_ancestors`, and `books.filetags`.
- Tags should remain exact/case-sensitive strings. Do not normalize tag names.
- The feature should preserve current saved-search and notebook drawer behavior.
- Use existing RecyclerView/list-fragment patterns such as `SavedSearchesFragment` or `TemplateListFragment` rather than introducing a broad navigation framework.

## resolved_decisions
- V1 feature: dedicated Tags browser screen launched from a top-level drawer item.
- V1 does not attempt full tabbed drawer or bottom-navigation redesign.
- Default tag results include inherited tags.
- Default tag results and counts include only active TODO-type headings.
- The Tags browser shows counts beside each tag.
- Settings remains the fourth major navigation area for now; Favorites is deferred.
- Errands-style grouping by tag remains a future result-list feature, not part of the Tags browser V1 build.

## open_questions
- None blocking planning.

## success_criteria
- The drawer shows a **Tags** destination without expanding every tag into the drawer menu.
- Opening **Tags** displays a stable alphabetical list of tags with active-task counts.
- A child TODO under a tagged parent appears/counts when tapping the inherited parent tag.
- DONE headings and plain non-task headings do not appear/count in the default tag result flow.
- Tapping a tag opens the normal query result screen for active tasks matching that tag.
- Existing Searches, Notebooks, Settings, and dynamic drawer item behavior still work.
- Build succeeds with `./scripts/build_fdroid.sh assembleFdroidDebug` after implementation.

## notes_for_planning
- Likely files:
  - `app/src/main/res/menu/drawer.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/AppIntent.java`
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivity.java`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - new `app/src/main/java/com/orgzly/android/ui/tags/TagsFragment.kt`
  - new `app/src/main/java/com/orgzly/android/ui/tags/TagsViewModel.kt`
  - new `app/src/main/java/com/orgzly/android/ui/tags/TagsAdapter.kt`
  - new layouts under `app/src/main/res/layout/`
  - possible new DAO/repository methods for active effective-tag counts
- Prefer generating query strings through the existing query builder/model if practical rather than hand-concatenating strings.
- If hand-building dotted queries is used, escape/quote tags consistently with existing `DottedQueryBuilder` behavior.
- Implementation plan: `docs/plans/2026_07_31_tag_browser_v1_plan.md`.
