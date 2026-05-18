# done task auto-archive implementation plan

created: 2026-05-17
status: active
source_doc: docs/brainstorms/2026_05_17_done_task_auto_archive_requirements.md

## objective
- add an explicit bulk action that moves completed tasks from the current working outline into one configured global done notebook
- keep v1 conservative: user-triggered only, notebook destination only, immediate children only for the selected scope, and no deletion/flattening/rewrite of moved subtrees
- use the app's configured done/todo state workflows instead of hard-coding `DONE`

## scope
- included:
  - global done destination preference backed by existing notebooks
  - notebook/list overflow action: `Move done tasks to done list`
  - single-heading/subheading context action: `Move done tasks in this heading`
  - selection/filtering of immediate done children only
  - skipping done parent tasks that contain active TODO/NEXT descendants
  - moving matched subtrees with existing refile/move infrastructure
  - concise result feedback with moved/skipped/no-op counts
  - `CHANGELOG.md` entry because this is user-facing task/list behavior
- excluded:
  - automatic/background archiving
  - deleting done tasks
  - per-project/per-heading archive destinations
  - full Emacs Org archive semantics
  - recursive sweeping of every nested done task by default
  - adding this to configurable swipe/popup quick buttons by default

## relevant_existing_patterns
- repo guidance:
  - no `AGENTS.md` or `CLAUDE.md` exists at repo root during this planning pass
  - `README.org` confirms this is Orgzly Revived, an Android Org-mode outliner/task app, and documents `./scripts/build_fdroid.sh assembleFdroidDebug` as the local build baseline
- `docs/plans/2026_05_16_note_editor_refile_plan.md`
  - nearby plan format and a useful warning: do not repeat stale refile-picker work without checking the current branch
- `app/src/main/java/com/orgzly/android/ui/refile/RefileFragment.kt`
  - current branch already includes target-selection mode and fragment-result conversion to `NotePlace`; this confirms editor-refile infrastructure has landed and should be treated as existing pattern, not future work
- `app/src/main/java/com/orgzly/android/ui/refile/RefileViewModel.kt`
  - maps notebook targets to `NotePlace(book.id)` and heading targets to `NotePlace(bookId, noteId, Place.UNDER)`; v1 done archive only needs notebook targets
- `app/src/main/java/com/orgzly/android/usecase/NoteRefile.kt`
  - existing safety check prevents refiling under a selected note's own subtree and runs `dataRepository.refileNotes(...)`
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `refileNotes(noteIds, target)` already moves complete subtrees into a destination notebook/root or under a target note
  - `getTopLevelNotes(bookId)` returns immediate top-level notes for notebook-level sweep
  - `getNoteChildren(noteId)` returns immediate children for scoped heading sweep
  - `getNotesAndSubtrees(ids)` can inspect descendants before moving
  - `getBook(id)` / `getBooks()` support destination validation and settings population
- `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `doneKeywordsSet(context)` and `todoKeywordsSet(context)` expose configurable state workflows
  - string-backed preference accessors are the existing pattern for lightweight settings
- `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - notebook settings screen is the right surface for the global done destination
  - existing default share notebook is free-text; this feature should avoid that pattern and use a dynamically populated `ListPreference`
- `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - `setupCalendarSyncSearchPreference()` shows the repo's existing pattern for populating a `ListPreference` dynamically from repository data
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - owns notebook/list top overflow handling and selected-note action handling
  - current narrowed-view support (`viewModel.narrowedNoteId`) matters for scope: when narrowed to a heading, the notebook/list action should not silently sweep the entire book
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookViewModel.kt`
  - owns list data, narrowed state, and existing `refileRequestEvent`; add archive request/result events here rather than routing through `MainActivity`
- `app/src/main/res/menu/book_actions.xml`
  - current notebook/list overflow menu surface for the whole-scope action
- `app/src/main/res/menu/book_cab_top.xml` / `app/src/main/res/menu/book_cab_bottom.xml`
  - current selected-heading context action surfaces; use only when exactly one note is selected for scoped heading archive
- `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt`
  - existing Robolectric/in-memory DB setup can be extended for archive selection/move behavior tests

## implementation_units
### 1. add global done destination preference
- files:
  - `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - `app/src/main/res/values/prefs_keys.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
- changes:
  - add a notebook/list preference near the existing default notebook setting, e.g. `pref_key_done_archive_book_id`
  - store the selected destination as a book id string, not a free-text book name
  - populate `entries`/`entryValues` from `dataRepository.getBooks()` in a helper modeled after `setupCalendarSyncSearchPreference()`
  - include an explicit disabled/unconfigured value such as `""` / `-1` with label `Not set`
  - if the stored id no longer maps to an existing book, show a stale/missing summary and leave archive actions blocked until the user fixes it
  - add `AppPreferences.doneArchiveBookId(context): Long?` and setter/helper as needed
- dependencies:
  - none beyond current settings/repository access
- risks:
  - dynamic preferences can show stale summaries if entries are populated after the preference summary provider runs
  - book ids are stable inside the database but not portable through settings export/import; v1 should prefer safety over guessing by name
- tests:
  - focused manual settings check
  - optional Robolectric/fragment test if settings fragment test patterns are easy to extend
- acceptance:
  - user can choose an existing notebook as the done archive destination
  - missing/unconfigured destination is visible and does not allow moves

### 2. create archive scope/result model and selection logic
- files:
  - create `app/src/main/java/com/orgzly/android/usecase/NoteArchiveDone.kt`
  - optionally create `app/src/main/java/com/orgzly/android/usecase/DoneArchiveScope.kt`
  - optionally create `app/src/main/java/com/orgzly/android/usecase/DoneArchiveResult.kt`
  - `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt` or new `app/src/test/java/com/orgzly/android/usecase/NoteArchiveDoneTest.kt`
- changes:
  - define two explicit scopes:
    - notebook scope: immediate top-level notes from `DataRepository.getTopLevelNotes(bookId)`
    - heading scope: immediate children from `DataRepository.getNoteChildren(parentNoteId)`
  - select candidates whose `note.state` is in `AppPreferences.doneKeywordsSet(context)`
  - for each done candidate, inspect descendants via `getNotesAndSubtrees(setOf(candidate.id))`
  - skip the candidate if any descendant other than the candidate has a state in `AppPreferences.todoKeywordsSet(context)`
  - return a result with at least `movedCount`, `skippedActiveDescendantCount`, `candidateCount`, `destinationBookName`, and optional `sourceScope`
  - do not include descendants in the selected id set; pass only selected parent ids to refile so existing subtree movement preserves structure exactly
- dependencies:
  - unit 1 destination preference for target id, or pass target id directly into the use case for testability
- risks:
  - selecting all descendant done tasks would duplicate/overlap move requests; v1 must only move immediate candidate parents
  - active descendants need to use configured TODO keywords, not non-null state or title text
  - parent title cookie updates may be triggered by move/refile; preserve existing behavior and do not add archive-specific title rewrites
- tests:
  - top-level `DONE` note with child content is selected and moved
  - top-level `DONE` note with nested `TODO` or `NEXT` descendant is skipped
  - nested `DONE` child under an active top-level parent is not moved by notebook scope
  - heading scope moves immediate done children only, not deeper done grandchildren directly
  - custom done keyword from preferences is respected
  - no candidates returns no-op result and does not modify data
- acceptance:
  - archive selection is deterministic and independently testable before UI wiring

### 3. execute safe move to configured destination
- files:
  - `app/src/main/java/com/orgzly/android/usecase/NoteArchiveDone.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt` only if small helper methods are needed
  - tests from unit 2
- changes:
  - validate destination before moving:
    - configured id exists
    - destination is not the same notebook as the source book for v1, because notebook-target refile would be a confusing no-op/top-level reshuffle
    - destination root note exists
  - if no selected candidates remain after filtering, return no-op result without calling `refileNotes`
  - move selected candidate ids with existing `NoteRefile(candidateIds, NotePlace(destinationBookId))` or equivalent direct repository call plus the same safety checks
  - set `UseCaseResult(modifiesLocalData = movedCount > 0, triggersSync = SYNC_DATA_MODIFIED when movedCount > 0, userData = DoneArchiveResult(...))`
  - surface typed failure for missing destination / same-book destination so UI can show an actionable message
- dependencies:
  - unit 2 selection logic
- risks:
  - `NoteRefile` returns only the first refiled note as `userData`; the archive use case needs its own aggregate result instead of relying on that
  - moving many subtrees between notebooks can create larger sync deltas; keep action explicit and report counts
- tests:
  - moved notes land in destination notebook as top-level entries with children/content intact
  - source notebook no longer contains moved candidate subtrees
  - destination same as source is blocked
  - missing destination is blocked
- acceptance:
  - no done tasks are deleted, flattened, or rewritten beyond the existing refile/move behavior

### 4. wire notebook/list overflow action
- files:
  - `app/src/main/res/menu/book_actions.xml`
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookViewModel.kt`
  - `app/src/main/res/values/strings.xml`
- changes:
  - add an overflow menu item such as `R.id.book_actions_move_done_tasks` with title `Move done tasks to done list`
  - in default book toolbar handling, call a `BookViewModel.archiveDoneInCurrentScope()` method
  - respect narrowed view:
    - if `viewModel.isNarrowed()` is true, use heading scope for `viewModel.narrowedNoteId.value`
    - otherwise use notebook scope for `mBookId`
  - run the use case from `BookViewModel` on disk IO and expose result/error single-live events
  - show concise feedback from `BookFragment`, for example:
    - `Moved 12 done tasks to Done`
    - `Moved 12 done tasks to Done; skipped 2 with active subtasks`
    - `No done tasks to move`
    - `Choose a done list in Settings > Notebooks first`
- dependencies:
  - units 1 through 3
- risks:
  - default overflow action could surprise users if narrowed view semantics are unclear; feedback should say whether the scope was current notebook or current heading if practical
  - avoid using selected ids for the whole-notebook action; it should derive scope from current view state
- tests:
  - build verification
  - manual QA in normal notebook view and narrowed heading view
- acceptance:
  - notebook/list overflow action sweeps only the intended immediate scope
  - no action occurs without a valid destination

### 5. wire single-heading/subheading context action
- files:
  - `app/src/main/res/menu/book_cab_top.xml`
  - `app/src/main/res/menu/book_cab_bottom.xml`
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookViewModel.kt`
  - `app/src/main/res/values/strings.xml`
- changes:
  - add a selected-note context action such as `R.id.archive_done_children` with title `Move done tasks in this heading`
  - show/enable it only when exactly one note is selected; hide for multi-selection using `hideMenuItemsBasedOnSelection(menu)`
  - when triggered, use heading scope with the selected note id and clear selection / return app bar to default after the operation is requested
  - do not add this action to the default configurable swipe popup button lists in `prefs_keys.xml`; bulk-moving should not become a casual swipe action in v1
- dependencies:
  - units 2 through 4
- risks:
  - current selected-note toolbar already has many always-show actions; prefer overflow placement if toolbar crowding occurs
  - users may interpret the selected parent itself as archivable; title and help text should make clear it moves done children under the heading, not the selected heading itself
- tests:
  - manual QA: select heading with immediate done children -> action moves those children
  - manual QA: select a completed heading with active descendants -> scoped child action does not move the selected heading itself
- acceptance:
  - heading/subheading context action behaves as a scoped sweep of immediate children

### 6. strings, icons, changelog, and polish
- files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/prefs_keys.xml`
  - optionally `app/src/main/res/drawable-anydpi/ic_archive.xml` if no suitable existing icon is available
  - `CHANGELOG.md`
- changes:
  - add preference labels/summaries, action titles, snackbar/plural messages, and stale destination error strings
  - use an existing archive/move icon if present; otherwise add a simple material-style archive icon only if needed for toolbar/menu clarity
  - add a changelog bullet for the new done-task archive action and setting
- dependencies:
  - units 1 through 5
- risks:
  - strings should avoid promising deletion or permanent archival; the action moves tasks to another notebook
- tests:
  - build resource compilation
  - quick visual/manual check for menu labels and snackbars
- acceptance:
  - user-facing language is clear and does not imply destructive deletion

## sequencing
1. Add preference keys/strings and dynamic done-destination `ListPreference` population.
2. Implement archive scope/result model plus candidate filtering as a testable use case.
3. Add move execution and destination validation using existing refile infrastructure.
4. Wire notebook/list overflow action and result/error feedback.
5. Wire single-heading/subheading context action and selection-mode visibility.
6. Add changelog entry and polish strings/icons.
7. Run unit tests for archive filtering/move behavior, then baseline Android build.
8. Perform focused manual QA on a dev build.

## risks_and_unknowns
- risk: storing destination as book id may not survive settings export/import cleanly.
  - mitigation: v1 should prefer safe live selection over fragile free-text; if import portability becomes important, add a future fallback by book name with stale warning.
- risk: moving a done parent with hidden active descendants would hide active work.
  - mitigation: explicitly inspect the full subtree for configured todo keywords and skip those parents with skipped-count feedback.
- risk: narrowed-view notebook action could be ambiguous.
  - mitigation: when narrowed, treat the current narrowed heading as the scope and use feedback/title copy that references current heading scope where possible.
- risk: adding another selected-note action crowds existing CAB menus.
  - mitigation: hide for multi-select and put in overflow if needed; do not add to swipe popup defaults.
- risk: bulk refile can create sync conflicts if many notes move between files.
  - mitigation: no background automation in v1; keep action explicit and report counts.
- unknown: whether existing tests cover `DataRepository.refileNotes` deeply enough.
  - mitigation: add focused Robolectric use-case tests with in-memory DB and run at least targeted tests plus `assembleFdroidDebug`.

## validation_strategy
- unit/Robolectric tests:
  - `./scripts/build_fdroid.sh testFdroidDebugUnitTest --tests "com.orgzly.android.usecase.NoteArchiveDoneTest"`
  - or, if tests are added to `DataRepositoryTest`, run the matching class filter for that test
- build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- manual QA on dev/debug APK:
  - create `work.org` and `done.org`; configure `done.org` as done destination
  - notebook scope: top-level `DONE` with content/children moves to `done.org`
  - notebook scope: top-level `DONE` with nested active `TODO`/`NEXT` is skipped and counted
  - notebook scope: nested `DONE` under active top-level parent is not moved by whole-notebook action
  - heading scope: select or narrow to a heading; immediate done children move, deeper done grandchildren are not independently swept
  - unconfigured/missing destination: action blocks with settings guidance and no data changes
  - destination same as current source notebook: action blocks with clear message
  - custom done keyword configured in Settings > Notebooks > States is respected
  - sync/manual file inspection confirms moved subtrees preserve title/content/properties/dates/tags

## handoff_notes
- Treat `docs/brainstorms/2026_05_17_done_task_auto_archive_requirements.md` as the behavior source of truth, but this plan is the implementation handoff artifact.
- The editor-refile plan's infrastructure has already landed in the current branch; do not re-plan target-selection mode or editor refile as part of this feature.
- Keep v1 conservative: explicit user action, notebook destination only, immediate children only, and skip done parents with active todo descendants.
- Use configured state workflows: `AppPreferences.doneKeywordsSet(context)` and `AppPreferences.todoKeywordsSet(context)`.
- Reuse `NoteRefile` / `DataRepository.refileNotes(...)` for subtree-preserving movement instead of creating a second move implementation.
- Do not put the archive action into default swipe popup button preferences in v1.
- Include `CHANGELOG.md`.
