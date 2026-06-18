# note editor refile action

created: 2026-05-16
status: draft
source: ce-brainstorm

## problem
- moving an existing note/task to another file or subtree requires leaving the note editor flow
- the existing breadcrumb behavior is useful for navigation, but it is not a full refile control from the editor
- for new notes, the breadcrumb/location flow can choose a different notebook, but the note is appended at the top level instead of being placed under a selected heading/subheading
- users who capture or edit tasks on mobile need a quick way to file the current note into the correct project/context without backing out to a list and using the existing bulk refile action

## goals
- add a fast, discoverable refile action directly in the note editor top toolbar
- reuse the existing app refile picker behavior so users can choose notebook, heading, or subheading targets
- support both new-note and edit-note flows in v1
- preserve current breadcrumb navigation semantics: tapping an existing breadcrumb while editing an existing task should still navigate to that clicked note, not become the refile picker
- keep the current save checkmark behavior intact

## resolved_decisions
- v1 should include both existing-note refile and new-note target placement; do not split new-note support into a later follow-up unless implementation proves unexpectedly unsafe
- after refiling an existing note from the editor, save/refile and return to the previous list/view rather than staying in the editor or using the existing snackbar/go-to pattern
- use the existing `ic_move_to_inbox.xml` drawable for the toolbar action unless visual QA shows it is misleading; fallback candidates are `ic_folder_open.xml`, `ic_create_new_folder.xml`, or adding a new Material `drive_file_move`/`folder_move` vector

## non_goals
- redesigning the full note editor header
- replacing the existing list/bulk refile feature
- changing breadcrumb navigation behavior for existing notes
- adding drag/drop outline movement
- building a new tree picker if the existing `RefileFragment` can be reused safely

## proposed_behavior
- add a new top-toolbar action immediately to the left of the existing checkmark/done action in the new note/edit note screen
- tapping the action opens a refile picker that works like the existing refile feature:
  - list notebooks at the top level
  - allow drilling into headings/subheadings
  - allow choosing a notebook as a top-level target
  - allow choosing a heading/subheading as an `UNDER` target
  - show breadcrumbs inside the picker and allow stepping back through them
- after selecting a target:
  - existing note/task: save any current editor changes, then move the note to the selected target
  - new note/task: create/save the note at the selected target instead of only changing notebook/top-level location
- if the note has unsaved changes, the action should not silently discard them; the preferred v1 behavior is save-then-refile
- after a successful refile, dismiss the picker and return to the previous list/view:
  - existing note/task: save current edits, move the note, then leave the editor and return to the screen the user came from
  - new note/task: create/save the note at the selected target, then follow the normal save completion path back to the previous list/view

## constraints
- the existing `RefileFragment` currently takes `noteIds` and calls `NoteRefile`, which assumes notes already exist
- new-note support may need a separate target-selection mode that returns a `NotePlace` before creation rather than immediately running `NoteRefile`
- existing note breadcrumbs live in `NoteFragment` and currently use links for navigation; this should not be overloaded for refile
- the screenshot shows limited top-toolbar space: menu, app logo/title area, done checkmark, and overflow menu; the refile icon must be compact and accessible
- avoid adding a user-facing action that appears in no-data/error states

## open_questions
- none blocking for v1

## success_criteria
- user can open a note/task editor and refile the current note without backing out to the list
- user can select a notebook, heading, or subheading target using familiar refile UI behavior
- current breadcrumb navigation behavior remains unchanged
- save/checkmark still works exactly as before
- unsaved editor changes are preserved when using refile
- existing bulk/list refile flow still works

## notes_for_planning
- screenshot reference: `/home/agent/.hermes/image_cache/img_c7f77052a395.jpg`
- current note editor toolbar/menu: `app/src/main/res/menu/note_actions.xml`
- current note editor controller: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- current refile dialog: `app/src/main/java/com/orgzly/android/ui/refile/RefileFragment.kt`
- current refile ViewModel: `app/src/main/java/com/orgzly/android/ui/refile/RefileViewModel.kt`
- current refile layouts: `app/src/main/res/layout/dialog_refile.xml`, `app/src/main/res/layout/item_refile.xml`
- relevant existing use case: `app/src/main/java/com/orgzly/android/usecase/NoteRefile*` if present
- likely implementation direction: extract or extend the existing refile picker so it can either execute `NoteRefile` for existing note IDs or return a selected `NotePlace` to `NoteFragment` for new-note creation
- include `CHANGELOG.md` because this is user-facing editor behavior
