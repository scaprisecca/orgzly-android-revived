# org-roam-compatible link picker implementation plan

created: 2026-05-14
status: active
source_doc: docs/brainstorms/2026_05_14_org_roam_link_picker_requirements.md

## objective
- Add a mobile-first editor workflow for inserting org-roam-compatible links to existing Orgzly notes/books and newly-created notes.
- Generate standard Org `[[id:<ID>][<title>]]` links and standard `ID` properties so links remain compatible with desktop Emacs/org-roam after file sync and org-roam DB refresh.
- Reuse Orgzly's existing `id:` link opening path while improving missing/duplicate-target feedback enough for link-picker-created links to feel reliable.

## scope
- included:
  - replace the current editor-toolbar Link template action with a searchable **Link to note** picker flow
  - query linkable targets from Orgzly's existing Room data, not from org-roam's Emacs SQLite DB
  - default picker results to targets that already have an `ID` property
  - optional toggle to include notes/books without an `ID`, visibly marked as requiring ID creation before insertion
  - search by note title, book/notebook name, `ID`, `CUSTOM_ID`, and `ROAM_ALIASES` where present
  - confirm before adding an `ID` to an existing target that lacks one
  - create a new linked note in the current notebook/book when no existing target matches the user's desired title
  - insert the generated `[[id:...][title]]` text into the currently active title/content editor and keep the source note open
  - preserve existing `id:` and `CUSTOM_ID` tap-to-open behavior
  - clearer behavior for missing and duplicate `ID` targets
- excluded:
  - graph view
  - backlinks panel or org-roam buffer clone
  - unlinked-reference scanning
  - importing or syncing org-roam's Emacs SQLite DB
  - custom `roam:` links
  - broad parser, sync, or storage model rewrites
  - automatic silent mutation of existing target notes

## relevant_existing_patterns
- `README.org`
  - primary repo overview; `AGENTS.md`, `CLAUDE.md`, and `README.md` were not present, so this plan uses `README.org` as the top-level repo context.
- `docs/brainstorms/2026_05_14_org_roam_link_picker_requirements.md`
  - source of truth for V1 behavior, non-goals, compatibility decisions, and candidate files.
- `docs/plans/2026_05_02_editor_toolbar_plan.md`
  - nearby plan format and an important stale-risk reference: the editor toolbar/focus-preservation work described there has already landed in the current branch.
- `app/src/main/res/layout/fragment_note.xml`
  - the note editor already has a bottom `editor_toolbar_container` with `editor_toolbar_link`; no new primary toolbar surface is needed for V1.
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - owns editor toolbar setup, active title/content editor detection, dialogs, timestamp insertion, and payload updates.
  - current `ToolbarAction.LINK` path inserts a raw `[[link][description]]` template through `EditorToolbarActions.link`; this is the integration point to replace with the picker flow.
  - `currentEditor()`, `preserveEditModeOnNextFocusLoss()`, `applyEdit(...)`, and timestamp dialog flows already solve the focus/selection problem for toolbar-driven insertion.
- `app/src/main/java/com/orgzly/android/ui/note/EditorToolbarActions.kt`
  - pure text-edit helper layer for existing editor toolbar commands; add a small helper for inserting a fully-formed org id link instead of putting string surgery in `NoteFragment.kt`.
- `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
  - existing JVM test pattern for editor text transformations.
- `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - owns note loading/saving and can run use cases while preserving the source note. New link-picker operations should live here or a narrow collaborator rather than directly mutating repository state from the fragment.
  - current initialization path supports `NoteInitialData.payload` for new-note creation, so the codebase can represent full properties at creation time; there is no stale blocker around only title/content initialization.
- `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`
  - existing UUID-backed `ID` generation lives in `initialProperties(...)` when `AppPreferences.addIdToNewNotes(context)` is enabled. The picker should reuse the same `IdLinkSpan.PROPERTY` constant and `UUID.randomUUID().toString()` style, but must generate an ID regardless of that preference when the user explicitly creates/selects a link target requiring one.
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - has note create/update helpers, property replacement, `findNotesOrBooksHavingProperty(...)`, and `findUniqueNoteHavingProperty(...)`.
  - existing property lookup opens notes first and only checks books if no note matches; the picker should expose duplicate states rather than silently inheriting this priority rule for candidate selection.
- `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - existing `allNotesHavingPropertyLowerCase(...)` powers link following. New picker search needs broader candidate queries joining notes/books and properties.
- `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt`
  - property storage and upsert behavior for notes; likely useful for explicit `ID` mutation on existing targets.
- `app/src/main/java/com/orgzly/android/db/dao/BookDao.kt`
  - book rows include `name`, `title`, and book-property lookup for file/root targets.
- `app/src/main/java/com/orgzly/android/util/OrgFormatter.kt`
  - parses `id:` links into `IdLinkSpan`; this should not need broad parser changes.
- `app/src/main/java/com/orgzly/android/ui/views/style/IdLinkSpan.kt`
  - `PROPERTY = "ID"`, `PREFIX = "id:"`, and click path into `followLinkToNoteOrBookWithProperty(...)`.
- `app/src/main/java/com/orgzly/android/ui/views/style/CustomIdLinkSpan.kt`
  - existing `CUSTOM_ID` path; useful for search matching and regression coverage, but picker-created V1 links should still use `ID`/`id:`.
- `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - current missing/duplicate target behavior lives in `followLinkToNoteOrBookWithProperty(...)`; it already warns on duplicates but still opens the first match, which should be tightened.
- `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt`
  - existing cross-book `id:`, `CUSTOM_ID`, book-ID, missing target, and duplicate-property tests; extend rather than replace.
- `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt`
  - existing Robolectric in-memory database setup for repository-level tests.
- `app/src/androidTest/java/com/orgzly/android/espresso/NoteFragmentTest.kt`
  - existing note editor UI test patterns for editing, saving, and dialogs.

## implementation_units
### 1. link target model and org link formatting helper
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/EditorToolbarActions.kt`
  - `app/src/main/java/com/orgzly/android/link/` or `app/src/main/java/com/orgzly/android/ui/note/link/` (new small model/helper package)
  - `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
  - `app/src/test/java/com/orgzly/android/link/` or matching package tests
- changes:
  - introduce a small `LinkTarget`/`OrgRoamLinkTarget` model with enough data for the picker and mutation decisions:
    - target type: note or book/root
    - note ID/book ID where applicable
    - display title
    - notebook/book name
    - existing `ID`, if present
    - `CUSTOM_ID`, if present
    - `ROAM_ALIASES`, if present
    - marker for “requires ID before link insertion”
  - add a pure formatter for `[[id:<id>][<description>]]` with minimal escaping/normalization decisions documented.
  - add an editor action helper that inserts a fully-formed org id link at the active selection/caret. It should replace the existing selected text with the completed link, not prompt for raw URL text.
  - keep the old generic `EditorToolbarActions.link(...)` only if still used elsewhere; otherwise replace the toolbar link path with the new completed-link insertion helper.
- dependencies:
  - none beyond current editor toolbar helper layer.
- risks:
  - org bracket escaping for titles containing `]` or newline can sprawl; V1 should normalize link descriptions conservatively and test the chosen behavior.
- tests:
  - `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
  - new pure helper tests under the chosen link package
- acceptance:
  - helper formats standard `[[id:uuid][Title]]` links.
  - helper inserts the completed link with deterministic selection/caret behavior.
  - title normalization behavior is explicit and covered.

### 2. Room/repository search for linkable targets
- files:
  - `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/BookDao.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt` if property-specific helper queries are cleaner there
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - new model file from unit 1
  - `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt` or a new focused Robolectric test class
- changes:
  - add a repository API such as `searchOrgRoamLinkTargets(query: String, includeWithoutIds: Boolean): List<LinkTarget>`.
  - query existing headings/notes from `notes` joined to `note_properties` for `ID`, `CUSTOM_ID`, and `ROAM_ALIASES`.
  - query book/root targets from `books` and `book_properties`, using book `title` when present and book `name` as fallback display text.
  - default results to targets with `ID`; when `includeWithoutIds` is true, include notes/books lacking `ID` but mark them as requiring confirmation and ID creation.
  - search across note title, book name, `ID`, `CUSTOM_ID`, and `ROAM_ALIASES`. Keep matching simple for V1: case-insensitive `LIKE`/contains is enough unless performance proves weak.
  - return enough duplicate metadata to identify multiple existing targets with the same `ID` and present those as a warning state in the UI.
  - avoid depending on org-roam DB, backlinks, or Emacs-only metadata.
- dependencies:
  - unit 1 model.
- risks:
  - joining properties can duplicate rows when a target has multiple matching properties; aggregate or de-duplicate by target identity.
  - large notebooks may make naive `%query%` searches slow; V1 can defer FTS but should keep query limits and ordering deterministic.
  - file/root node semantics are subtle: org-roam uses `#+title` for file nodes, while Orgzly has book `name`, book `title`, and book properties.
- tests:
  - `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt` or `app/src/test/java/com/orgzly/android/data/OrgRoamLinkTargetRepositoryTest.kt`
- acceptance:
  - already-ID notes are returned by default.
  - no-ID notes are hidden by default and appear only when requested, marked as needing ID creation.
  - book/root targets with `ID` are searchable.
  - `ROAM_ALIASES` and `CUSTOM_ID` can match a target even though the inserted link uses `ID`.
  - duplicate `ID` candidates are detectable.

### 3. safe ID creation and new linked-note use cases
- files:
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/usecase/` (new narrow use cases, e.g. `NoteEnsureId.kt`, `BookEnsureId.kt` if book support is included, `LinkedNoteCreate.kt`)
  - `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt` if extracting reusable ID-generation helper is cleaner
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - tests under `app/src/test/java/com/orgzly/android/data/` and/or `app/src/androidTest/java/com/orgzly/android/usecase/`
- changes:
  - extract or add a tiny reusable ID generator helper using `IdLinkSpan.PROPERTY` and `UUID.randomUUID().toString()`.
  - add an explicit operation to ensure an existing note has an `ID`:
    - if an `ID` already exists, return it unchanged
    - if no `ID` exists, add one and mark the owning book modified/sync-needed through the normal update path
    - do not run this operation until the user confirms in the UI
  - decide whether book/root ID creation is in V1. If implemented, update book properties/preface through existing book-property/preface mechanisms, not by hacking note properties. If this is risky, allow existing-ID book roots only in V1 and treat no-ID books as not selectable.
  - add a create-linked-note operation that creates a new note in the current `bookId` with generated `ID`, using `NoteCreate`/`DataRepository.createNote(...)` with `NotePlace(bookId)`.
  - keep the source note open after new target creation; the new note creation path must not overwrite `NoteViewModel.noteId` for the source note.
  - return the new target's ID/title to the fragment/view-model for immediate insertion.
- dependencies:
  - unit 1 model and unit 2 target search.
- risks:
  - mutating an existing target from inside the source editor can create two pending modifications: source editor text and target properties. The target mutation should be a committed use case with sync triggers before source text insertion.
  - creating new linked notes while the source note itself is unsaved is valid if `bookId` is known, but UX should make clear the new target was created even if the source note later gets discarded.
  - book/root ID mutation may require careful preface/property export handling; do not add it casually if existing infrastructure is not clear.
- tests:
  - repository/use-case tests for ensuring ID on note with and without existing ID
  - repository/use-case tests for new linked note creation with generated ID in the current book
- acceptance:
  - existing target without `ID` gets exactly one new `ID` only after confirmation.
  - existing target with `ID` is not changed.
  - newly-created linked note has an `ID` regardless of the user's “add ID to new notes” preference.
  - source `NoteViewModel.noteId` remains the source note after creating a linked target.

### 4. picker UI and editor toolbar integration
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - new UI class/layout, e.g. `app/src/main/java/com/orgzly/android/ui/note/link/OrgRoamLinkPickerDialogFragment.kt`
  - new layout resources under `app/src/main/res/layout/`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/layout/fragment_note.xml` only if the toolbar label/icon needs adjustment
- changes:
  - change `binding.editorToolbarLink.setOnClickListener` from direct `applyEditorAction(ToolbarAction.LINK)` to launching the picker.
  - before opening the picker, capture the current editor identity and selection. Use existing `preserveEditModeOnNextFocusLoss()` to avoid losing edit mode while the dialog is open.
  - implement a searchable dialog/list surface with:
    - search text input
    - toggle/checkbox: include notes without IDs
    - result rows showing target title, notebook/book, and ID/no-ID status
    - clear duplicate-ID warning display when applicable
    - create-new action when query text has no suitable exact target, e.g. “Create linked note ‘<query>’ in this notebook”
  - selecting a target with an `ID` inserts the completed link.
  - selecting a target without an `ID` opens a confirmation dialog before unit 3 mutation, then inserts after mutation succeeds.
  - creating a new linked note runs the unit 3 create operation, then inserts the link and stays on the source note.
  - restore focus/caret to the original editor before applying insertion; if the editor is gone or source note was closed, abort gracefully with a snackbar.
- dependencies:
  - units 1 through 3.
- risks:
  - complex custom dialog UI can grow quickly. Prefer a focused dialog/list implementation over a broad reusable picker framework.
  - preserving editor selection through asynchronous search and confirmation is the main UI risk; capture and validate selection explicitly.
  - new note creation from picker can surprise users if the source note is later discarded; use clear copy.
- tests:
  - `app/src/androidTest/java/com/orgzly/android/espresso/NoteFragmentTest.kt` for editor insertion flow if stable enough
  - JVM tests for view-model event decisions if picker logic is separated from Android widgets
- acceptance:
  - toolbar Link opens the picker instead of inserting `[[link][description]]`.
  - selecting an existing ID target inserts `[[id:<ID>][<title>]]` at the original caret/selection.
  - no-ID selection requires confirmation before mutation.
  - create-new creates a target note in the current notebook and inserts the link without navigating away.
  - cancellation leaves editor text unchanged.

### 5. improve missing and duplicate target navigation behavior
- files:
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - `app/src/main/java/com/orgzly/android/usecase/NoteOrBookFindWithProperty.kt` if result typing should be clearer
  - `app/src/main/res/values/strings.xml`
  - `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt`
- changes:
  - keep using `IdLinkSpan` and `followLinkToNoteOrBookWithProperty(...)` for tapped links.
  - adjust duplicate handling so multiple matches do not silently open the first target after warning. Preferred V1 behavior: show a clear error and do not navigate; optional chooser can be deferred unless easy.
  - keep missing-target snackbar, but improve copy to clearly say no note/book was found with matching `ID` and optionally include the visible link description if available in a later UI layer.
  - if a chooser is implemented for duplicates, make it explicit and testable; otherwise avoid partial navigation.
- dependencies:
  - none for missing/duplicate behavior; can be implemented independently.
- risks:
  - changing duplicate behavior may alter existing test expectations. This is desirable per requirements, but the test should make the new safer behavior explicit.
- tests:
  - `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt`
- acceptance:
  - missing ID target shows understandable error.
  - duplicate ID does not silently navigate to the first match.
  - existing successful `id:` and `CUSTOM_ID` navigation tests still pass.

### 6. strings, accessibility, and documentation polish
- files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/layout/` picker row/dialog files
  - `README.org` or external docs only if the feature needs user-facing documentation in this repo
- changes:
  - add strings for **Link to note**, search hint, include-without-IDs toggle, no-ID marker, confirmation wording, create-new action, duplicate-ID warning, and failure messages.
  - ensure picker rows expose accessible labels including title, notebook, and ID status.
  - keep toolbar content description accurate if the action changes from generic link insertion to note-link picker.
- dependencies:
  - unit 4 final UI copy.
- risks:
  - translation backlog from new strings is expected; keep wording concise and central.
- tests:
  - manual accessibility sanity check on picker rows, toggle, and confirmation dialog
- acceptance:
  - no unlabeled picker controls.
  - user-visible copy makes mutation and compatibility behavior clear.

## sequencing
1. Add the pure target model and link formatting/insertion helper with JVM tests.
2. Add repository/DAO search for ID-backed link targets, then expand to optional no-ID targets and aliases.
3. Add safe ID-generation/mutation and linked-note creation operations with repository/use-case tests.
4. Replace the toolbar Link action with the picker UI, preserving editor focus/selection and inserting completed links.
5. Tighten missing/duplicate link navigation behavior and extend `InternalLinksTest.kt`.
6. Finish strings, accessibility labels, and manual validation.

## risks_and_unknowns
- risk: book/root file-node support may be more complex than note/headline support because Orgzly stores book title/preface/properties separately from normal notes.
  - mitigation: support existing-ID book/root targets in search first; defer adding IDs to books unless the preface/property update path is clearly safe.
- risk: duplicate `ID` handling currently warns but still opens the first result.
  - mitigation: change duplicate handling before relying on picker-created links as trustworthy; tests should assert no silent navigation.
- risk: asynchronous target search/confirmation can lose the original editor selection.
  - mitigation: capture editor identity plus selection before launching the picker, preserve edit mode on focus loss, and abort if the editor is no longer active/available.
- risk: creating a linked target from an unsaved source editor can leave a new target note even if the source edit is later discarded.
  - mitigation: clear create-new copy and possibly snackbar confirmation; do not auto-save the source unless product requirements change.
- risk: broad search across notes/properties can be slow on large notebooks.
  - mitigation: start with bounded result counts and simple case-insensitive matching; consider FTS only after profiling or user feedback.
- risk: title text may contain characters awkward in Org link descriptions.
  - mitigation: normalize descriptions conservatively in the pure formatter and test edge cases.

## validation_strategy
- JVM/Robolectric tests:
  - run/link helper tests under `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt` and the new link package tests
  - run repository search/ID creation tests under `app/src/test/java/com/orgzly/android/data/`
- Instrumented tests:
  - extend `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt` for duplicate/no-target behavior and successful picker-created `id:` links if practical
  - extend `app/src/androidTest/java/com/orgzly/android/espresso/NoteFragmentTest.kt` for toolbar picker insertion and create-new flow if the dialog tests are stable
- Build/test commands:
  - `./gradlew test`
  - targeted instrumented tests for `InternalLinksTest` / `NoteFragmentTest` on an emulator/device
  - `./gradlew assembleDebug` or repo helper `./scripts/build_fdroid.sh` if local SDK/JDK setup requires it
- Manual checks:
  - open existing note, tap Link, search/select an ID-backed note, verify inserted `[[id:...][title]]`
  - tap the inserted link and verify navigation to the target
  - toggle include-no-ID, select a no-ID target, cancel confirmation and verify no mutation/insertion
  - repeat and confirm, verify target receives an `ID` and source gets the link
  - search a non-existing title, create linked note in the current notebook, verify source remains open and new note exists with an `ID`
  - verify duplicate ID shows recovery/error behavior and does not silently open the first match
  - sync/export a test book and inspect plain Org output for standard `ID` properties and `id:` links

## handoff_notes
- The current branch already has the editor toolbar/focus-preservation infrastructure from the earlier toolbar plan; do not repeat that architecture work.
- The implementation should be centered on existing Room data and normal note/book mutation paths, not org-roam DB import.
- Prefer a narrow picker and target repository over a generic global search framework.
- Keep content mutation explicit: adding an `ID` to an existing target requires user confirmation.
- Be conservative with book/root ID mutation. Existing book IDs should be searchable/linkable; adding IDs to books can be deferred if preface/property persistence is not straightforward.
- Preserve the current `IdLinkSpan` click path and improve only the unsafe missing/duplicate states.
- If implementation proceeds with a coding subagent, this file should be the handoff artifact.
