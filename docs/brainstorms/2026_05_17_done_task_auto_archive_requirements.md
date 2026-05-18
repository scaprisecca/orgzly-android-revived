# done task auto-archive

created: 2026-05-17
status: draft
source: ce-brainstorm

## problem
- users can mark tasks as done, but completed tasks remain mixed into active working files until the user manually moves or hides them
- Orgzly already supports refile-style movement and recognizes configurable done keywords, but there is no obvious one-tap workflow for sweeping completed tasks into a dedicated archive/done location
- Scott's proposed workflow is to pick a global "done list" and use an archive action to move DONE tasks out of their current file

## goals
- add a low-friction action that moves completed tasks out of the current working notebook/list into a configured global done destination
- let the user choose the global done destination from existing notebooks/targets rather than typing a fragile free-text notebook name
- use the app's existing done keyword settings, not a hard-coded `DONE` state only
- preserve subtree content when a completed task is archived
- make the action safe enough that users do not accidentally move more than intended

## resolved_decisions
- v1 should default to archiving only top-level done tasks in the current notebook/file, not every nested done task.
- scoped heading/subheading archive is worth including as a natural extension if it can reuse the current outline context: when the user is viewing or has selected a specific heading/subheading, `Move done tasks` should sweep only the immediate done child tasks under that heading.
- v1 should skip done parent tasks that have active TODO/NEXT descendants, so active work is not hidden inside the done destination.
- the global done destination should be notebook/list only in v1, not a heading/subheading target.
- the action should live in both the current notebook/list overflow menu and the heading/subheading context menu.
- moved done tasks should preserve their full subtree/content exactly in v1; do not flatten archived tasks.
- v1 should not perform background/automatic archiving without an explicit user action.

## non_goals
- automatic background archiving without user action in v1
- deleting completed tasks
- changing the user's done/todo keyword workflow
- implementing full Emacs Org archive semantics in v1
- adding per-project archive destinations in v1 unless the global-only model proves too limiting during brainstorming
- rewriting the existing refile picker or note move system
- recursively archiving all nested done tasks from an entire notebook by default

## proposed_behavior
- add a user-configurable global done destination setting in Notebooks/settings
  - preferred UX is a dynamic notebook `ListPreference` populated from existing books, not free-text entry
  - destination is notebook/list only for v1; heading/subheading destinations are out of scope
  - a missing/deleted destination should be shown clearly as stale and should disable/archive-block until fixed
- add visible archive/sweep actions in two places
  - notebook/list overflow menu: `Move done tasks to done list` sweeps immediate top-level done tasks in the current notebook/file
  - heading/subheading context menu: `Move done tasks in this heading` sweeps immediate done child tasks under that heading/subheading only
  - avoid placing this as a swipe/popup quick button in v1 because it bulk-moves tasks and should not be too easy to trigger accidentally
- when triggered, the action finds tasks whose state is one of `AppPreferences.doneKeywordsSet(...)`
- skip done parent tasks that contain active todo descendants, and include skipped-count feedback so users know why not everything moved
- matching done task subtrees are moved to the configured global done destination via the existing refile/move infrastructure where possible
- moved tasks preserve their full subtree/content exactly; v1 should not flatten or rewrite archived task structure
- after success, the user should get a concise result such as `Moved 12 done tasks to Done`
- if there are no done tasks, show a no-op message and do not modify files
- if the done destination is not configured, missing, or is the same invalid target, prompt the user to configure/fix it rather than moving anything

## constraints
- Orgzly stores notes as outlines; moving a completed parent moves its subtree. This is desirable for many completed projects, but can surprise users if active child tasks exist under a done parent.
- Existing `NoteRefile` prevents refiling a note under its own subtree, which should be reused for safety.
- Existing `DataRepository.refileNotes(noteIds, target)` can move selected note subtrees to a `NotePlace`; the feature mainly needs reliable selection/filtering plus settings/UI.
- Existing done detection is configurable through note states; do not hard-code only `DONE`.
- Existing `ARCHIVE` handling appears mostly visual/tag-based, not a full archive command.
- Sync conflicts matter: moving many notes between files creates a larger sync-modifying operation than simply hiding done tasks.
- The current branch already contains editor-refile work, so this feature should avoid colliding with note-editor toolbar scope unless intentionally combined later.

## open_questions
- none blocking for v1

## success_criteria
- user can configure a global done destination from existing notebooks/targets
- from a current notebook/list, user can trigger a done-task archive sweep
- only completed tasks, as defined by configured done keywords, are moved
- no action occurs when destination is missing or invalid
- no completed tasks are deleted, flattened, or silently changed beyond movement
- user gets clear feedback on how many tasks moved and where
- active tasks are not accidentally hidden because they were nested under a completed parent without an explicit rule

## notes_for_planning
- project readme: `README.org`
- current docs inspected:
  - `docs/ideation/2026_05_01_org_mode_mobile_features_ideation.md`
  - `docs/brainstorms/2026_05_16_note_editor_refile_requirements.md`
  - `docs/plans/2026_05_16_note_editor_refile_plan.md`
- settings surface likely starts in `app/src/main/res/xml/prefs_screen_notebooks.xml` and `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
- done keyword behavior lives around `AppPreferences.doneKeywordsSet(...)` / configurable states
- existing movement/refile path:
  - `app/src/main/java/com/orgzly/android/usecase/NoteRefile.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt` `refileNotes(...)`
  - `app/src/main/java/com/orgzly/android/ui/refile/RefileFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/refile/RefileViewModel.kt`
- existing notebook settings already include a free-text default share notebook; this feature should avoid repeating that fragility if possible
- likely needs `CHANGELOG.md` because this is user-facing task/list behavior
