# tag suggestion management

created: 2026-06-30
status: planned
source: ce-brainstorm

## problem
- The note editor tag field suggests every distinct tag currently found in the notes database.
- Removing a tag from the note being edited only removes it from that note; it does not remove the tag from the autocomplete dictionary if the tag still exists anywhere else in the local database.
- This makes stale or duplicate-case tags such as `@HomeDepot` and `@homedepot` keep appearing while adding tags to new notes.
- Users need a low-risk way to keep tag suggestions tidy without accidentally editing or deleting note content.

## current_state_findings
- Repo root inspected: `/home/agent/dev/orgzly`.
- `AGENTS.md` and `CLAUDE.md` are absent.
- Primary overview inspected: `README.org`.
- Relevant prior docs inspected:
  - `docs/brainstorms/2026_05_18_capture_template_tag_autocomplete_requirements.md`
  - `docs/plans/2026_05_18_capture_template_tag_autocomplete_plan.md`
  - current autocomplete helper code from the saved-search/capture-template work on this branch.
- Current note-editor tag autocomplete path:
  - `app/src/main/res/layout/fragment_note.xml` defines `tags_button` as a `MultiAutoCompleteTextView`, plus `tags_menu` and `tags_remove` buttons.
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt` observes `viewModel.tags`, creates an `ArrayAdapter`, sets it on `binding.tagsButton`, and uses `SpaceTokenizer()`.
  - `tags_menu` currently just calls `binding.tagsButton.showDropDown()`.
  - `tags_remove` clears tags from the current note only.
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt` exposes `tags` from `dataRepository.selectAllTagsLiveData()`.
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt` builds known tags from `NoteDao.getDistinctTagsLiveData()`, then `Tags.fromString(...)`, `distinct()`, and `sorted()`.
  - `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt` only selects distinct non-empty `notes.tags`; there is no separate tag table/catalog.
- Current branch already has reusable comma-separated autocomplete infrastructure:
  - `app/src/main/java/com/orgzly/android/ui/util/CommaSeparatedAutocomplete.kt`
  - `CommaSeparatedSuggestionAdapter` and matcher can be reused conceptually, but the note editor uses space-separated Org tag syntax and should keep `SpaceTokenizer()`.

## key_product_distinction
- There are two different features that sound similar:
  1. **Hide/forget a tag suggestion**: remove a tag from autocomplete dropdowns only. This is low-risk and does not edit notes.
  2. **Rename/delete a tag across notes**: modify every note containing that tag. This is powerful but destructive/sync-affecting and needs preview/confirmation.
- Recommended V1 should implement **hide/forget from suggestions**, not bulk note mutation.

## goals
- Let users suppress stale tag names from tag suggestion dropdowns.
- Preserve existing notes and synced Org files unchanged.
- Make duplicate-case cleanup practical, e.g. hide `@HomeDepot` while keeping `@homedepot` visible.
- Apply consistently to tag autocomplete surfaces that use known note tags.
- Provide a way to undo hidden suggestions.

## non_goals
- Do not delete tags from notes in V1.
- Do not bulk-rename tags in V1.
- Do not introduce a full tag database/catalog unless later requirements justify it.
- Do not change Org tag serialization or delimiter behavior.
- Do not lowercase or otherwise normalize user tags automatically.

## recommended_mvp
- Add a persisted hidden-tag-suggestions set in SharedPreferences.
- Filter known tag suggestions by this hidden set before binding autocomplete adapters.
- Add a dedicated Settings > Notebooks > **Manage tag suggestions** screen for longer tag lists.
- Let users browse/search exact tag strings, hide visible suggestions, and unhide hidden suggestions.
- Label the action clearly as hiding/removing from **suggestions**, not deleting the tag from notes.
- Include a reset/unhide path so accidental hides are recoverable.

## ui_options

### option_a_note_editor_manage_dialog
- Keep the current tag row behavior mostly intact.
- Add a discoverable way from the note tag row to open **Manage tag suggestions**.
- The dialog/screen lists current known tags with hide controls and optionally a separate hidden section.
- Pros:
  - Closest to the user's pain point while adding a tag.
  - Does not require a full settings navigation path for a small cleanup task.
  - Reuses the existing note-editor context.
- Cons:
  - Need to avoid overloading `tags_menu`, which currently means "show dropdown".
  - A full list inside a dialog can be cramped if the user has many tags.

### option_b_settings_screen_recommended
- Add **Settings > Notebooks > Manage tag suggestions**.
- This is the selected V1 path because Scott has a longer tag list to sort through.
- Pros:
  - Clean, discoverable as app management rather than per-note editing.
  - Better fit for a longer searchable list and hidden-tag reset controls.
- Cons:
  - More navigation for the common case of seeing a bad suggestion while typing.
  - Requires a new preference entry and screen/dialog wiring.

### option_c_inline_delete_in_dropdown
- Add delete/hide affordances directly in autocomplete dropdown rows.
- Pros:
  - Fastest possible cleanup when seeing a bad suggestion.
- Cons:
  - Android autocomplete rows are not a great place for secondary actions.
  - Risk of accidental hides while selecting a tag.
  - More custom UI complexity for a fragile interaction.
- Not recommended for V1.

### option_d_bulk_rename_tag
- Provide a tool to rename `@HomeDepot` to `@homedepot` across all notes.
- Pros:
  - Actually cleans the underlying notes and eventually removes old suggestions naturally.
- Cons:
  - Mutates notebook content, affects sync, and needs preview/confirmation/conflict handling.
  - Bigger feature than the immediate autocomplete cleanup problem.
- Defer as a future feature.

## proposed_behavior
- Hidden tags are excluded from:
  - note editor tag autocomplete
  - capture-template tag autocomplete
  - saved-search builder include/exclude tag autocomplete
- Hidden tags are **not** excluded from:
  - search/query behavior
  - existing notes
  - exports/sync
  - any future dedicated tag cleanup/rename tool
- If a user manually types a hidden tag, the app should allow it. Hiding only affects suggestions.
- If all variants of a duplicate-case tag are hidden except one, only the visible one appears in suggestions.
- If a hidden tag is later needed, user can unhide it from the management surface.

## implementation_notes
- Prefer a pure helper such as `TagSuggestionFilter.visibleTags(allTags, hiddenTags)` so behavior is testable without Android UI.
- Add AppPreferences accessors, probably a `StringSet`, for hidden tag suggestions:
  - `hiddenTagSuggestions(context): Set<String>`
  - `hideTagSuggestion(context, tag)` / `unhideTagSuggestion(context, tag)` or a setter for the whole set.
- Do not filter `DataRepository.selectAllTagsLiveData()` directly unless intentionally applying hidden tags globally to every consumer. A safer shape is a new repository/helper method or UI-level mapping that makes the suggestion-specific nature explicit.
- Existing `selectAllTagsLiveData()` currently uses `distinct().sorted()`, which is case-sensitive; this is good for preserving exact tag strings but means duplicate-case tags remain separate suggestions.
- If a management UI is added under settings, `SettingsFragment` already has patterns for dynamic `ListPreference` population and opening custom fragments/dialogs.
- If a management UI is added from the note editor, avoid changing the meaning of the existing `tags_remove` button because that currently clears tags from the current note.

## data_model_considerations
- Current tags are derived from `notes.tags`; there is no tag table to delete from.
- A hidden-suggestions set in SharedPreferences is portable with existing settings export/import behavior because default SharedPreferences are exported by `AppPreferences.getAllValues(...)`.
- Hidden suggestions may include tags that no longer exist in notes. That is harmless, but the management UI should probably show them in a hidden section so they can be unhidden or reset.
- Avoid case-insensitive storage for hidden tags. `@HomeDepot` and `@homedepot` must be independently controllable.

## edge_cases
- A tag remains in the dropdown after hiding if another note still contains a visually similar variant; show exact strings in management UI.
- A hidden tag manually typed into a new note will be saved and still hidden from suggestions afterward.
- If a user imports/syncs a note with a previously hidden tag, it should remain hidden from suggestions.
- If hidden tags are filtered globally, ensure saved-search builder users can still manually type a hidden tag query.
- If a tag list is very large, the management UI should support search/filter eventually; V1 can start with sorted list if complexity needs to stay low.

## resolved_decisions
- V1 hides tag suggestions instead of deleting or renaming tags in notes.
- V1 management surface lives under Settings > Notebooks > **Manage tag suggestions**.
- V1 should use a dedicated screen suitable for a longer list, preferably with search/filter.
- V1 should list exact tag strings and let the user hide/unhide them; duplicate-case grouping/warnings are deferred.
- Hidden suggestions should apply consistently to tag autocomplete surfaces, while manual typing remains allowed.

## open_questions
- None blocking planning. Future question: whether a later feature should support bulk rename/delete of tags across notes with preview and confirmation.

## success_criteria
- User can hide `@HomeDepot` from tag suggestions without changing any notes.
- `@homedepot` can remain visible while `@HomeDepot` is hidden.
- Hidden tags no longer appear in note-editor tag autocomplete.
- Hidden tags can be restored.
- Manually typing a hidden tag still works.
- Existing note save/update behavior remains unchanged.
- Build succeeds with `./scripts/build_fdroid.sh assembleFdroidDebug` after implementation.

## notes_for_planning
- Likely files:
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `app/src/main/res/values/prefs_keys.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - new tag-suggestion management fragment/adapter under `app/src/main/java/com/orgzly/android/ui/tags/` or another settings-adjacent package
  - `app/src/main/res/xml/prefs_screen_notebooks.xml` for the Settings > Notebooks entry
  - tests under `app/src/test/java/...`
- Implementation plan: `docs/plans/2026_06_30_tag_suggestion_management_plan.md`.
- Consider later follow-up feature: **bulk rename tag across notes** with preview, counts, sync warning, and confirmation.
