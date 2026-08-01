# sidebar navigation ux ideation

created: 2026-07-30
status: active
repo: orgzly
focus: redesign the left drawer/sidebar so searches, notebooks, tags, and settings are easier to navigate without clutter

## context
- Scott's main problem: the current left drawer is too cluttered; he wants clearer navigation for searches/notebooks/tags/settings.
- Scott is not yet sure whether the solution should be a tabbed drawer, bottom navigation, or another structure, so this document compares the main options before choosing a feature to take into `ce-brainstorm`.
- Vault source inspected: `/home/agent/the_atelier/tasks/projects.org`, especially:
  - `Layout redesign for the left sidebar`
  - `Can the tags button for the new left navigation show a list of all the tags that are currently in my org files?`
  - related custom-search notes about errands grouping and hiding inherited-tag parent headings.
- Repo source inspected:
  - `README.org`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/menu/drawer.xml`
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivity.java`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - `app/src/main/java/com/orgzly/android/ui/books/BooksFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/QueryFragment.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - existing ideation and plan docs for mobile Org features and agenda/saved-search builder work.

## key_findings
- The current drawer is one long `NavigationView` menu with three fixed items plus dynamic saved searches and dynamic notebooks.
- Saved searches are inserted at order `1`; notebooks are inserted at order `3`; settings is fixed at order `4`.
- There is no separate top-level Tags surface yet, but the data layer already has useful tag discovery:
  - `NoteDao.getDistinctTagsLiveData()` returns stored tag strings.
  - `DataRepository.selectAllTagsLiveData()` splits, deduplicates, and sorts tags.
  - `DataRepository.selectAllTags()` exists but currently does not sort its output.
- Query support already understands tags and inherited tags through `Condition.HasTag`, so a tag browser can open a normal query rather than inventing a separate task retrieval path.
- The existing guided saved-search / agenda-builder planning overlaps with this redesign. A better navigation surface should not create a second competing way to define custom views; it should reuse saved searches and query strings under the hood.

## candidate_ideas

### 1. tabbed drawer with Searches / Notebooks / Tags / Settings
- summary: keep the existing left drawer gesture and hamburger pattern, but make the drawer a real navigation panel with bottom tabs or segmented controls. Each tab shows one list: saved searches, notebooks, all tags, and settings/favorites.
- value: high. It directly addresses Scott's complaint: the drawer stops being one mixed long list and becomes four predictable sections.
- cost: medium. The current drawer is a simple Material `NavigationView` menu; tabbed content likely means replacing or wrapping it with a custom drawer layout containing a tab row plus RecyclerViews.
- risk: moderate. It changes a central navigation component but preserves the mental model of “open drawer, choose destination.”
- why_it_might_fail: if implemented as tiny bottom tabs inside a narrow drawer, it may feel cramped or non-standard. It also may hide sync/status affordances if the bottom of the drawer is overloaded.
- pros:
  - lowest conceptual disruption from the current app.
  - gives tags a first-class home without forcing users to create saved searches for every tag.
  - keeps notebooks and saved searches separate, which matches Scott's intended categories.
  - can reuse existing query/navigation plumbing.
- cons:
  - custom drawer UI is more implementation work than adding another menu group.
  - bottom tabs inside a drawer are less familiar than bottom app navigation.
  - settings may not deserve a whole tab unless it becomes “More / Settings / Favorites.”
- likely_implementation_areas:
  - `activity_main.xml`
  - `DrawerNavigationView.kt`
  - new drawer row adapters/fragments/view models for section lists
  - `MainActivityViewModel` for tag LiveData and tag-query navigation

### 2. main-screen bottom navigation: Searches / Notebooks / Tags / Settings
- summary: replace the drawer-first model with persistent bottom navigation across the main app. Tapping a bottom tab switches the main content to saved searches, notebooks, a tag browser, or settings/more.
- value: medium to high. It makes the major areas visible and thumb-friendly.
- cost: high. It changes app navigation more broadly than the drawer redesign and may require rethinking nested navigation, toolbar behavior, back behavior, and note editing context.
- risk: high. Orgzly is an outliner/task app where the current screen can be a book, query result, note detail, or editor. Persistent bottom navigation can fight with editor toolbars and task-list selection modes.
- why_it_might_fail: if bottom navigation remains visible during note editing or selection mode, it may crowd the screen and conflict with the editor toolbar work Scott already wants.
- pros:
  - more modern and discoverable than a drawer.
  - fast switching between major areas.
  - good for one-handed phone use.
- cons:
  - likely too much redesign for a first pass.
  - uses vertical space on every screen, including the editor.
  - Settings is usually not a core bottom-nav destination; Android UX convention would usually put it under More.
- likely_implementation_areas:
  - `activity_main.xml`
  - `MainActivity.java`
  - current fragments' toolbar/drawer interactions
  - possible navigation graph or explicit navigation-state refactor

### 3. hybrid: bottom app tabs for daily workflow, drawer/more menu for management
- summary: use bottom navigation for high-frequency daily destinations such as Today, Search/Views, Notebooks, and Capture/Inbox, while moving settings/import/export/sync/admin functions to a More or overflow surface.
- value: high if the goal expands from “cleaner sidebar” to “better daily workflow.”
- cost: high. This is no longer just a sidebar cleanup; it becomes an app information architecture redesign.
- risk: high. It may conflict with the app's existing Orgzly identity where notebooks and saved searches are equally central, and it may overfit Scott's workflow unless made configurable.
- why_it_might_fail: it can become a redesign rabbit hole before the simpler drawer clutter problem is solved.
- pros:
  - strongest long-term UX if Orgzly becomes more dashboard/workflow-oriented.
  - can surface Today/Next/Waiting/Inbox without making Scott open the drawer constantly.
  - aligns with the agenda-dashboard preset work.
- cons:
  - too broad for a clean V1 unless split into phases.
  - requires hard choices about the default tabs.
  - could make existing users feel the app became less file/notebook-oriented.
- likely_implementation_areas:
  - same as candidate 2, plus agenda preset seeding and saved-search builder UX.

### 4. low-risk drawer cleanup: grouped sections plus collapsible Tags
- summary: keep the simple `NavigationView` drawer, but add clear section headers, collapse/expand saved searches and notebooks, and add a Tags group with a “View all tags” destination.
- value: medium. It reduces clutter without a major layout rewrite.
- cost: low to medium. This can likely reuse more of the current menu-based drawer.
- risk: low. It is incremental and easy to back out.
- why_it_might_fail: it may not go far enough. If Scott already dislikes the long mixed drawer, grouping alone may still feel like clutter with nicer labels.
- pros:
  - smallest implementation step.
  - preserves current drawer behavior and back-stack expectations.
  - can prove tag-browsing value before committing to a larger navigation redesign.
- cons:
  - still fundamentally a long list.
  - tags could become very long unless shown through a separate screen rather than directly in the drawer.
  - does not answer the broader “better UI/UX based on how I use the app” request.
- likely_implementation_areas:
  - `drawer.xml`
  - `DrawerNavigationView.kt`
  - simple Tag list screen or query destination

### 5. dedicated Tags browser screen opened from drawer
- summary: add a new “Tags” top-level drawer item that opens a full screen listing all known tags alphabetically. Tapping a tag opens a normal query for notes/tasks with that tag.
- value: high as a focused feature. It solves Scott's “without a million saved searches” problem and can be implemented independently of the full drawer redesign.
- cost: medium. Needs a new fragment/adapter/view model, but can reuse existing tag discovery and query display.
- risk: low to medium. The main ambiguity is exact query semantics: include inherited tags or only explicitly assigned tags.
- why_it_might_fail: if the tag list is just alphabetical with no counts, search, favorites, or recent tags, it may become another long list once Scott has many tags.
- pros:
  - excellent V1 slice.
  - uses existing `selectAllTagsLiveData()` and query navigation.
  - avoids turning every tag into a saved search.
  - can later become the Tags tab in a tabbed drawer.
- cons:
  - does not fully declutter searches and notebooks by itself.
  - needs design decisions about tag counts, inherited tags, and whether done tasks should be included.
- likely_implementation_areas:
  - new `TagsFragment`, `TagsViewModel`, `TagsAdapter`
  - `MainActivityViewModel.displayQuery(...)`
  - `DrawerNavigationView.kt`
  - string/menu resources

## rejected_or_weaker_options
- Put every tag directly into the existing drawer — rejected for now. It solves discoverability but makes clutter worse, especially if tags grow.
- Replace saved searches with tags — rejected. Saved searches are still needed for cross-cutting views like Today, Waiting, No Date, Home + date filters, etc.
- Make Settings one of the four most prominent daily tabs — weak. Settings is important, but low-frequency. A better fourth slot may be Favorites, Inbox/Capture, or More.
- Build a completely new navigation architecture before testing tag browsing — weak. It is more risk than needed to validate Scott's core UX need.

## recommended_next_steps
1. Best V1: build a dedicated Tags browser screen and add it as a top-level drawer item.
   - This proves the key behavior Scott wants: all tags listed alphabetically, tap tag, see matching notes/tasks.
   - It can later move into a tabbed drawer without wasting work.
2. Best V1.5: redesign the drawer into grouped or tabbed sections once the Tags screen is useful.
   - Suggested sections: Searches, Notebooks, Tags, More.
   - Treat Settings as part of More unless Scott strongly wants it as a primary tab.
3. Best broader redesign direction: connect saved-search builder presets, tag browsing, and notebooks into a unified “Views” model.
   - Searches remain power views.
   - Tags are lightweight automatic views.
   - Notebooks remain file/source views.

## resolved_decisions
- Tag browser results should include inherited tags, not only tags directly assigned to the matching heading.
  - Rationale: Scott uses parent headings such as Amazon/Walmart/Home Depot to assign tags through inheritance, and child tasks under those headings should appear when tapping the inherited tag.
  - Existing query support is aligned with this: `Condition.HasTag` checks both `tags` and `inherited_tags`. Avoid `Condition.HasOwnTag` for the default tag-browser query.
- Tag browser results should show only active task headings by default, not every note with the tag.
  - Active means TODO-type states such as `TODO`, `NEXT`, `WAITING`, and Scott's custom active states.
  - Completed/DONE headings and plain non-task notes should be excluded from the default tap behavior.
  - Implementation direction: combine the selected tag condition with an active-task/state-type condition, e.g. tag match plus `it.todo` / equivalent TODO-state filtering.
  - Consider an optional overflow/filter toggle later for “include done” or “show all tagged notes,” but do not make that the default.
- The Tags list should show a count next to each tag.
  - Counts should include only active task headings, matching the default tag-result behavior.
  - Counts should include inherited-tag matches too, so parent-heading tag workflows are represented accurately.
  - Completed/DONE headings and plain non-task notes should not contribute to the default count.
  - Implementation note: this likely needs a query/helper that counts active TODO-type notes grouped by effective tag, where effective tag includes both direct and inherited tags. Do not simply count raw `notes.tags`, because that would miss inherited tags.
- The fourth major navigation area should link to Settings for V1.
  - Scott likes the idea of Favorites, but that requires a separate feature for favoriting notes/tasks.
  - Keep Favorites as a future option after Scott confirms the new layout works in daily use.
  - For now, Settings is low-risk because the settings screen already exists and the navigation button can simply route to the current settings view.

## future_options
- Favorites tab / section.
  - Requires defining what can be favorited: saved searches, notebooks, tags, notes, tasks, or all of those.
  - Should wait until the new navigation layout is validated.

- For errands-style views, grouping by tag is more important than grouping by notebook or priority.
  - Rationale: Scott uses tags such as Home Depot, Amazon, Walmart, errands, and similar contexts as the practical shopping/action grouping layer.
  - This should influence both tag-browser design and later custom-view/saved-search result grouping.
  - Implementation implication: a future grouped result list should support tag sections first, with notebook/priority grouping as secondary or optional.

## design_questions_for_scott
- None blocking for `tag_browser_v1` ideation.

## v1_planning_handoff
- 2026-07-31: `tag_browser_v1` was promoted through `ce-brainstorm` into:
  - requirements: `docs/brainstorms/2026_07_31_tag_browser_v1_requirements.md`
  - implementation plan: `docs/plans/2026_07_31_tag_browser_v1_plan.md`
- V1 scope is a dedicated Tags browser launched from the drawer, with inherited-tag active-task counts and existing query-result navigation.
- Full tabbed drawer redesign, Favorites, and grouped query results remain deferred follow-ups.

## suggested_ce_brainstorm_options
- `tag_browser_v1`: promoted to requirements and implementation plan on 2026-07-31.
- `sidebar_tabs_v1_5`: promoted to requirements on 2026-07-31 after Scott confirmed Tags V1 works well.
  - Scope: clean drawer launcher with only top-level Searches, Notebooks, Tags, and Settings.
  - Dynamic saved searches and notebooks move out of the drawer and remain browsable from their full-screen tabs.
  - Requirements: `docs/brainstorms/2026_07_31_sidebar_tabs_v1_5_requirements.md`.
- `custom_view_grouping_v1`: saved-search result grouping by tag/notebook/priority; overlaps with errands workflow and should prefer tag grouping first.
