# note editor refile action implementation plan

created: 2026-05-16
status: active
source_doc: docs/brainstorms/2026_05_16_note_editor_refile_requirements.md

## objective
- add a top-toolbar refile action in the note editor so the current note/task can be filed to a notebook, heading, or subheading without backing out to the list
- support both existing-note refile and new-note target placement in v1
- preserve existing breadcrumb navigation behavior and existing checkmark/save behavior
- after the editor refile action completes, return to the previous list/view

## scope
- included:
  - new top-toolbar action immediately to the left of the existing done/checkmark action
  - reuse of the existing refile picker UI/behavior where possible
  - target selection for notebooks and headings/subheadings
  - existing-note flow: save current edits, refile the note, then return to previous list/view
  - new-note flow: select a notebook/heading/subheading target, create the note at that target, then return through the normal save completion path
  - `CHANGELOG.md` entry for the user-facing editor action
- excluded:
  - changing existing breadcrumb navigation semantics
  - replacing the current list/bulk refile feature
  - drag/drop outline movement
  - a full note-editor header redesign
  - broad refile UX redesign beyond what is needed to support editor-launched target selection

## relevant_existing_patterns
- `app/src/main/res/menu/note_actions.xml`
  - current top-toolbar menu; `done` is currently `showAsAction="always"` and should remain the final/checkmark action
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - owns editor toolbar menu inflation, `handleActionItemClick`, `userSave`, breadcrumb navigation, note-book selection for share/new-note flows, and listener callbacks for leaving the editor
- `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - owns `bookId`, `noteId`, `place`, payload updates, `saveNote(postSave)`, `createNote`, and `updateNote`
  - existing new-note placement already flows through `NotePlace(bookId, noteId, place)` when `place != Place.UNSPECIFIED`
- `app/src/main/java/com/orgzly/android/ui/refile/RefileFragment.kt`
  - existing dialog UI: toolbar, breadcrumbs, list, per-item refile button, and refile-here button
  - currently executes refile internally and shows snackbar/go-to behavior after success
- `app/src/main/java/com/orgzly/android/ui/refile/RefileViewModel.kt`
  - existing target tree traversal and target conversion to `NotePlace`
  - currently accepts `noteIds` and runs `NoteRefile` directly
- `app/src/main/java/com/orgzly/android/usecase/NoteRefile.kt`
  - existing use case for moving existing notes and validating against refiling under the source subtree
- `app/src/main/res/layout/dialog_refile.xml` and `app/src/main/res/layout/item_refile.xml`
  - existing refile picker UI resources
- `app/src/main/res/drawable-anydpi/ic_move_to_inbox.xml`
  - selected v1 icon for the editor toolbar action
- `app/src/main/res/values/strings.xml`
  - likely home for reusing or adding content descriptions/titles for the action
- `CHANGELOG.md`
  - repo convention for user-facing editor changes

## implementation_units
### 1. add reusable target-selection mode to the refile picker
- files:
  - `app/src/main/java/com/orgzly/android/ui/refile/RefileFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/refile/RefileViewModel.kt`
  - `app/src/main/java/com/orgzly/android/ui/refile/RefileViewModelFactory.kt`
  - optionally new small helper/interface under `app/src/main/java/com/orgzly/android/ui/refile/`
- changes:
  - extend the refile dialog so it can be launched as a target picker that returns a selected `NotePlace` instead of immediately running `NoteRefile`
  - keep existing list/bulk refile behavior unchanged for callers that pass `noteIds`
  - expose a callback/listener or fragment-result style event for selected targets:
    - notebook target -> `NotePlace(bookId)`
    - heading/subheading target -> `NotePlace(bookId, noteId, Place.UNDER)`
  - preserve existing breadcrumbs, drill-down, item button, and refile-here behavior
  - do not remove the existing `NoteRefile` execution path; isolate mode-specific behavior clearly
- dependencies:
  - existing `RefileViewModel.refile(item)` already maps `Book` and `Note` to the correct `NotePlace`
- risks:
  - mixing immediate-execute and select-only modes can regress existing bulk refile if the mode boundary is unclear
  - DialogFragment callback ownership must be lifecycle-safe when launched from `NoteFragment`
- tests:
  - add/extend unit tests if `RefileViewModel` currently has test coverage; otherwise prioritize build plus manual QA
- acceptance:
  - existing refile flow still moves selected notes
  - editor-launched target picker can return a notebook or heading/subheading `NotePlace` without moving anything by itself

### 2. add note-editor toolbar action and launch wiring
- files:
  - `app/src/main/res/menu/note_actions.xml`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/drawable-anydpi/ic_move_to_inbox.xml` (reuse, no new asset unless visual QA demands it)
- changes:
  - add a new menu item, likely `R.id.refile`, before `R.id.done` so it appears to the left of the checkmark
  - set `android:icon="@drawable/ic_move_to_inbox"` and `app:showAsAction="always"` or `ifRoom` depending on layout behavior; preferred v1 is visible action next to done
  - add a content-description/title such as `@string/refile`
  - hide/remove the action in no-data states alongside `done` as appropriate
  - handle the action in `handleActionItemClick` by launching the target-selection mode
- dependencies:
  - unit 1 target picker mode
- risks:
  - app bar overcrowding on narrow screens; verify the icon appears in the intended spot and does not push important actions into overflow
- tests:
  - build verification
  - manual editor toolbar check on phone/emulator
- acceptance:
  - button appears in the note editor top toolbar immediately left of the done/checkmark action
  - tapping it opens the refile picker
  - existing done/checkmark action still saves exactly as before

### 3. implement existing-note save-then-refile flow
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - optionally `app/src/main/java/com/orgzly/android/usecase/NoteRefile.kt` only if a small helper return shape is needed
- changes:
  - when the user selects a target for an existing note:
    1. close keyboard if needed
    2. `updatePayloadFromViews()`
    3. validate/save the current note via `viewModel.saveNote(postSave)`
    4. after save succeeds, run `NoteRefile(setOf(note.id), selectedNotePlace)`
    5. return to previous list/view via the existing listener/back-stack path, not snackbar/go-to
  - if validation fails, keep the editor open and show the existing validation snackbar (`title_can_not_be_empty`, `note_book_not_set`, etc.)
  - if `NoteRefile.TargetInNotesSubtree` occurs, show the existing `cannot_refile_to_the_same_subtree` message and keep the picker/editor recoverable
- dependencies:
  - unit 1 selected `NotePlace` callback
- risks:
  - `saveNote(postSave)` executes asynchronously; chain refile only after save completion
  - returning to the previous list/view should not happen before refile completes
- tests:
  - targeted build
  - manual QA with modified existing note -> refile -> verify content changes and new outline location
- acceptance:
  - unsaved edits are preserved before the move
  - existing note lands under the selected target
  - editor exits to the previous list/view after success

### 4. implement new-note target placement flow
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
- changes:
  - when the user selects a target for a new note:
    1. close keyboard if needed
    2. `updatePayloadFromViews()`
    3. create/save the note directly at the selected `NotePlace`
    4. return through the normal `noteCreatedEvent`/listener completion path
  - add a ViewModel method or parameterized save path that can create a new note at an override `NotePlace` instead of only using the initial `bookId/noteId/place`
  - keep the existing share/new-note notebook-only `location_button` behavior untouched unless it can safely delegate to the new target picker
  - ensure selected `NotePlace(bookId, noteId, Place.UNDER)` creates the new note under the selected heading/subheading, not top-level in the notebook
- dependencies:
  - unit 1 selected `NotePlace` callback
  - existing `NoteCreate(payload, notePlace)` support
- risks:
  - current `NoteViewModel.place` is immutable from `initialData`; avoid trying to mutate it indirectly through fragment arguments after launch
  - do not regress existing `NoteFragment.forNewNote(notePlace, ...)` flows that already support `Place.UNDER`
- tests:
  - manual QA: new note -> refile button -> choose notebook -> save lands top-level
  - manual QA: new note -> refile button -> choose heading/subheading -> save lands under that heading
- acceptance:
  - new notes can be filed below headings/subheadings from the editor before first save
  - normal checkmark save still uses the original/new-note location if the user does not use the refile button

### 5. error handling, lifecycle, strings, and changelog
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/refile/RefileFragment.kt`
  - `app/src/main/res/values/strings.xml`
  - `CHANGELOG.md`
- changes:
  - use existing string resources where possible: `@string/refile`, `@string/cannot_refile_to_the_same_subtree`
  - ensure selected-target callbacks are removed/handled safely on fragment recreation
  - ensure picker dismissal/cancel leaves current editor content intact
  - add changelog entry for the note editor refile action
- dependencies:
  - units 1 through 4
- risks:
  - lifecycle leaks or duplicate callbacks if using a direct listener from a DialogFragment to a Fragment
  - title validation and target errors should be distinguishable to the user
- tests:
  - build verification
  - manual cancel/dismiss test
- acceptance:
  - canceling the picker does not save, discard, or move the note
  - user-facing text is understandable and accessible
  - changelog mentions the new editor refile action

## sequencing
1. Extend/rework the existing refile picker into immediate-execute and select-target modes without changing current bulk refile behavior.
2. Add the editor toolbar menu item and wire it to the picker.
3. Implement existing-note save-then-refile chaining and return-to-previous-list behavior.
4. Implement new-note create-at-selected-target behavior.
5. Add error handling, strings/accessibility polish, and changelog entry.
6. Run build verification and focused manual QA.

## risks_and_unknowns
- risk: existing `RefileFragment` has behavior baked in around executing `NoteRefile` and showing snackbar/go-to.
  - mitigation: introduce explicit mode/callback separation and keep old code path covered by manual regression.
- risk: asynchronous save/refile chaining could return to the list before the move completes.
  - mitigation: chain operations in ViewModel/usecase callbacks and only notify the fragment/listener after the final operation succeeds.
- risk: new-note placement may be implemented by mutating fragment arguments instead of the actual ViewModel create path.
  - mitigation: pass an override `NotePlace` into the create/save path; `NoteCreate(payload, notePlace)` already accepts the needed target.
- risk: app bar crowding.
  - mitigation: use the existing compact `ic_move_to_inbox` asset and verify on a narrow screen; fall back to `ifRoom` only if necessary, but the requirement is a visible top header action.

## validation_strategy
- build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- optional broader build if time allows:
  - `./scripts/build_fdroid.sh assemblePremiumDebug`
- manual QA:
  - existing note with unsaved title/content/metadata changes -> refile under a different heading -> confirm changes persist and note moved
  - existing note -> refile to same subtree -> confirm clear error and no move
  - new note -> select notebook target -> confirm top-level creation in that notebook
  - new note -> select heading/subheading target -> confirm creation under selected heading
  - cancel picker from both existing and new note flows -> confirm editor remains open and no save/move occurs
  - breadcrumb tap behavior in existing note remains navigation, not refile
  - checkmark save remains unchanged
  - existing list/bulk refile still works

## handoff_notes
- Do not hand off directly from the brainstorm; this plan is the source of truth for Codex execution.
- Preserve the existing breadcrumb behavior exactly.
- The editor-launched refile action must return to the previous list/view after successful save/refile.
- V1 includes both existing-note and new-note support; do not defer new-note target placement unless a concrete implementation blocker is found.
- Reuse `ic_move_to_inbox.xml` for the toolbar action unless visual QA shows it is misleading.
- Include `CHANGELOG.md` because this is user-facing editor behavior.
- This repo's broad unit tests may be noisy; a successful `assembleFdroidDebug` is the required baseline verification signal.
