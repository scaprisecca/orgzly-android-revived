# tag suggestion management implementation plan

created: 2026-06-30
status: active
source_doc: docs/brainstorms/2026_06_30_tag_suggestion_management_requirements.md

## objective
- Add a Settings > Notebooks > Manage tag suggestions screen that lets users hide stale tag names from autocomplete suggestions without deleting or editing notes.
- Preserve exact tag casing so duplicate-case tags such as `@HomeDepot` and `@homedepot` can be controlled independently.
- Apply the hidden-suggestion filter consistently to current tag autocomplete surfaces while still allowing users to manually type hidden tags.

## scope
- included:
  - persisted hidden tag suggestions set in default SharedPreferences
  - Settings > Notebooks entry named **Manage tag suggestions**
  - dedicated management screen for longer tag lists
  - list of visible and hidden exact tag strings
  - hide/unhide actions, plus a reset/unhide-all action if straightforward
  - filtering for note-editor tag autocomplete
  - filtering for capture-template tag autocomplete
  - filtering for saved-search builder include/exclude tag autocomplete
  - focused helper tests for case-sensitive filtering and preference behavior where practical
  - changelog entry for the new user-facing settings feature
- excluded:
  - deleting tags from notes
  - renaming tags across notes
  - creating a Room tag table/catalog
  - changing Org tag serialization, parsing, or search behavior
  - lowercasing or otherwise normalizing tag names
  - blocking manual entry of hidden tags
  - inline delete/hide controls inside autocomplete dropdown rows

## repo_context_checked
- repo root: `/home/agent/dev/orgzly`
- current branch: `selected-mobile-org-features`
- `AGENTS.md`: missing
- `CLAUDE.md`: missing
- primary overview: `README.org`
- source requirements: `docs/brainstorms/2026_06_30_tag_suggestion_management_requirements.md`
- nearby plans inspected:
  - `docs/plans/2026_05_18_capture_template_tag_autocomplete_plan.md`
  - `docs/plans/2026_06_30_saved_search_builder_autocomplete_plan.md`
- important current-state finding: tags are derived from `notes.tags`; there is no separate tag catalog to delete from, so V1 should hide suggestions rather than mutate note data.

## relevant_existing_patterns
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `selectAllTagsLiveData()` returns all known tags from note rows.
  - Keep this as the raw source of truth; do not bake hidden-suggestion filtering directly into the repository unless adding a separate suggestion-specific method.
- `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - `getDistinctTagsLiveData()` selects distinct non-empty note tag strings.
  - No DAO changes should be needed for V1.
- `app/src/main/java/com/orgzly/android/db/entity/Tags.kt`
  - Parses stored space-separated Org tags.
  - Preserve exact tag strings and case.
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - Current note-editor tag autocomplete uses `binding.tagsButton`, `ArrayAdapter`, and `SpaceTokenizer()`.
  - Existing `tags_remove` clears tags from the current note and should not become a suggestion-management control.
- `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - Currently exposes raw known tags as `tags` from `dataRepository.selectAllTagsLiveData()`.
  - Good place to expose filtered `tagSuggestions`, or keep filtering in the fragment if preference changes do not need live observation.
- `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - Current branch already uses `CommaSeparatedSuggestionAdapter` for capture-template tags and observes `selectAllTagsLiveData()`.
- `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
  - Current branch already wires tag adapters for include/exclude tag fields and observes `selectAllTagsLiveData()`.
- `app/src/main/java/com/orgzly/android/ui/util/CommaSeparatedAutocomplete.kt`
  - Existing generic autocomplete adapter/matcher infrastructure for comma-token fields.
- `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - Settings screens are XML-backed, with custom fragment entry points wired through click listeners.
  - `setupCaptureTemplatePreference()` is the closest pattern for a Notebooks settings row that pushes a custom fragment.
  - `SettingsActivity.pushFragment(fragment)` is already used for settings-managed custom screens.
- `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - Correct place to add **Manage tag suggestions** under the Notebooks settings area.
- `app/src/main/java/com/orgzly/android/ui/capture/TemplateListFragment.kt` and `TemplateListAdapter.kt`
  - Useful pattern for a settings-hosted RecyclerView screen with toolbar title, empty state, divider, and adapter.

## architecture_decisions
- Store hidden suggestions as exact strings in default SharedPreferences using a `StringSet` key, e.g. `pref_key_hidden_tag_suggestions`.
- Treat hidden tags as a UI suggestion preference only. Query/search, sync/export, and note save behavior must remain unchanged.
- Keep raw tag retrieval separate from suggestion filtering:
  - raw known tags: `DataRepository.selectAllTagsLiveData()` / `selectAllTags()`
  - visible suggestions: raw tags minus `AppPreferences.hiddenTagSuggestions(context)`
- Apply the same helper to all autocomplete surfaces that present known note tags, so the settings screen has predictable app-wide meaning.
- For the management screen, show exact strings and avoid case-insensitive grouping in V1. Duplicate-case grouping can be a later UX improvement.

## implementation_units

### 1. Add hidden-tag preference accessors and pure filtering helper
- files:
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `app/src/main/res/values/prefs_keys.xml`
  - `app/src/main/java/com/orgzly/android/ui/tags/TagSuggestionFilter.kt` or similar new helper
  - `app/src/test/java/com/orgzly/android/ui/tags/TagSuggestionFilterTest.kt`
- changes:
  - Add non-translatable preference key `pref_key_hidden_tag_suggestions`.
  - Add `AppPreferences.hiddenTagSuggestions(context): Set<String>`.
  - Add `AppPreferences.hiddenTagSuggestions(context, Set<String>)` or focused hide/unhide setters.
  - Return defensive copies from preference accessors to avoid mutating SharedPreferences' internal set reference.
  - Add pure helper functions:
    - `visibleTags(allTags, hiddenTags)` filters exact matches and sorts/preserves existing sorted order.
    - `tagSuggestionRows(allTags, hiddenTags)` if the management screen needs visible/hidden grouping.
- dependencies:
  - none.
- risks:
  - SharedPreferences `StringSet` values can be mutable references; always copy before editing.
  - If filtering lowercases strings, it would incorrectly hide both `@HomeDepot` and `@homedepot`.
- tests:
  - `TagSuggestionFilterTest`:
    - hides exact tag only
    - preserves `@homedepot` when hiding `@HomeDepot`
    - allows hidden tags that no longer exist without affecting visible known tags
    - returns stable sorted/grouped rows for UI
- acceptance:
  - Hidden suggestion storage and filtering work without touching note data or database schema.

### 2. Add Settings > Notebooks entry and navigation
- files:
  - `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - `app/src/main/java/com/orgzly/android/di/AppComponent.kt` if the new fragment needs injection
- changes:
  - Add a `Preference` under the existing Notebooks settings area:
    - key: `pref_key_manage_tag_suggestions`
    - title: `Manage tag suggestions`
    - summary: `Hide old or duplicate tags from autocomplete suggestions`
  - Add `setupManageTagSuggestionsPreference()` near `setupCaptureTemplatePreference()`.
  - On click, push the new management fragment via `(activity as? SettingsActivity)?.pushFragment(TagSuggestionManagementFragment())`.
  - Add required string resources.
- dependencies:
  - unit 1 for preference keys and final screen target.
- risks:
  - Placing the entry inside the wrong category could make it hard to find. Put it near note/tag/capture-template settings, not under App developer options.
- tests:
  - resource compile through debug build.
- acceptance:
  - Settings > Notebooks shows **Manage tag suggestions** and opens the management screen.

### 3. Build the management screen for long tag lists
- files:
  - `app/src/main/java/com/orgzly/android/ui/tags/TagSuggestionManagementFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/tags/TagSuggestionManagementAdapter.kt`
  - `app/src/main/res/layout/fragment_tag_suggestion_management.xml`
  - `app/src/main/res/layout/item_tag_suggestion.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/di/AppComponent.kt`
- changes:
  - Create a settings-hosted fragment similar to `TemplateListFragment`:
    - inject `DataRepository`
    - set toolbar title to `Manage tag suggestions` on resume
    - load raw tags from `dataRepository.selectAllTags()` or observe `selectAllTagsLiveData()`
    - read hidden set from `AppPreferences.hiddenTagSuggestions(requireContext())`
    - render list rows with exact tag string and visible/hidden state
  - Use `RecyclerView` so long lists are manageable.
  - Add a simple filter/search `EditText` at the top if implementation stays small; for Scott's longer list, this is high-value and should be included if not too much UI churn.
  - Row behavior:
    - visible tag row action: `Hide`
    - hidden tag row action: `Unhide`
    - optionally show hidden rows in a separate section or with clear `Hidden` status text
  - Empty states:
    - no known tags: `No tags found`
    - search filter returns no rows: `No matching tags`
  - Add overflow/menu or button for `Unhide all` if there are hidden tags.
- dependencies:
  - units 1 and 2.
- risks:
  - A management screen that only lists currently-known tags can hide old hidden entries after the underlying notes no longer contain them. Include hidden-only entries too so users can unhide/reset stale hidden preferences.
  - If search/filter is omitted, long lists may be frustrating; include it unless it significantly complicates binding.
- tests:
  - helper tests from unit 1 cover row grouping/filtering if extracted.
  - compile/build for binding and resources.
- acceptance:
  - User can browse/search a longer tag list and hide/unhide exact tag suggestions.
  - Hidden tags remain visible in the management screen as hidden entries so the action is reversible.

### 4. Filter note-editor tag autocomplete
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - optionally `app/src/main/java/com/orgzly/android/ui/tags/TagSuggestionFilter.kt`
- changes:
  - Apply hidden-tag filtering before constructing the `ArrayAdapter` for `binding.tagsButton`.
  - Keep `SpaceTokenizer()` unchanged because normal note tags are space-separated.
  - Do not change `tags_remove`; it continues to clear tags from the current note.
  - On returning from Settings, filtered suggestions should refresh when the note editor is reopened. Live refresh while the editor is open is nice-to-have but not required for V1 unless cheap.
- dependencies:
  - unit 1.
- risks:
  - If the raw `viewModel.tags` field is replaced, other call sites may unintentionally receive filtered tags. Prefer an explicitly named `tagSuggestions` if changing the ViewModel.
- tests:
  - helper test coverage is sufficient for filtering logic.
  - manual QA needed for actual autocomplete dropdown behavior.
- acceptance:
  - `@HomeDepot` no longer appears in note-editor autocomplete after being hidden.
  - Manually typing `@HomeDepot` still saves normally.

### 5. Filter capture-template and saved-search tag autocomplete
- files:
  - `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
  - optionally `app/src/main/java/com/orgzly/android/ui/tags/TagSuggestionFilter.kt`
- changes:
  - When observing `dataRepository.selectAllTagsLiveData()`, call `adapter.updateDictionary(visibleTags(tags, hiddenTags))` for each tag adapter.
  - Leave notebook/state autocomplete dictionaries untouched.
  - Keep free-text entry allowed.
- dependencies:
  - units 1 and existing current-branch autocomplete infrastructure.
- risks:
  - If hidden suggestions are only filtered in the note editor, users will still see stale tags in capture-template or saved-search builder tag fields. Applying the filter to all tag suggestion surfaces avoids that inconsistency.
- tests:
  - compile/build for affected fragments.
  - existing `CommaSeparatedAutocompleteTest` should remain valid because tokenization/matching is unchanged.
- acceptance:
  - Hidden tags do not appear in capture-template tag suggestions or saved-search builder include/exclude tag suggestions.
  - Hidden tags can still be manually typed in those fields.

### 6. Add copy, changelog, and manual QA checklist
- files:
  - `app/src/main/res/values/strings.xml`
  - `CHANGELOG.md`
  - optionally a short manual QA note under `docs/` only if useful
- changes:
  - Add concise user-facing strings:
    - `Manage tag suggestions`
    - `Hide old or duplicate tags from autocomplete suggestions`
    - `Hide from suggestions`
    - `Unhide`
    - `Hidden from suggestions`
    - `Unhide all`
  - Add changelog entry under Unreleased.
  - Keep language explicit that notes are not modified.
- dependencies:
  - units 2 and 3.
- risks:
  - Ambiguous wording such as `Delete tag` would imply note mutation. Avoid it.
- tests:
  - resource compile.
- acceptance:
  - UI clearly communicates hiding suggestions, not deleting tags from notes.

## sequencing
1. Add preference key/accessors and pure filtering helper with tests.
2. Add Settings > Notebooks preference row and navigation hook.
3. Add management fragment/layout/adapter and wire hide/unhide persistence.
4. Apply hidden-tag filtering to note editor suggestions.
5. Apply hidden-tag filtering to capture-template and saved-search builder tag suggestions.
6. Add strings/changelog.
7. Run focused helper tests if practical.
8. Run `./scripts/build_fdroid.sh assembleFdroidDebug`.
9. Manual QA on device/emulator.

## risks_and_unknowns
- risk: Users may expect `hide` to remove tags from existing notes.
  - mitigation: use explicit copy: `Hide from suggestions`; include summary/help text saying notes are not changed.
- risk: Long tag list is cumbersome without search.
  - mitigation: include a simple text filter at the top of the management screen if feasible in V1.
- risk: Hidden tags no longer present in notes become impossible to unhide if the screen only displays current known tags.
  - mitigation: merge `knownTags + hiddenTags` for management rows.
- risk: SharedPreferences `StringSet` mutation bugs.
  - mitigation: always copy to `LinkedHashSet`/`HashSet` before add/remove and save a new set.
- risk: Filtering in `DataRepository.selectAllTagsLiveData()` could accidentally affect non-suggestion uses.
  - mitigation: keep repository raw; filter at UI/view-model suggestion boundaries.
- risk: Current branch already contains several unrelated feature changes.
  - mitigation: keep implementation changes isolated to tag-suggestion files and verify `git diff --stat` before handoff/commit.

## validation_strategy
- unit tests:
  - `./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.tags.TagSuggestionFilterTest`
  - If the repo-wide test task reports unrelated failures, inspect the specific XML result for this new test class.
- build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- manual QA:
  1. Create/import notes containing both `@HomeDepot` and `@homedepot`.
  2. Confirm both appear in note-editor tag autocomplete before hiding.
  3. Open Settings > Notebooks > Manage tag suggestions.
  4. Search for `home`.
  5. Hide `@HomeDepot` only.
  6. Return to note editor and confirm `@HomeDepot` is absent while `@homedepot` remains suggested.
  7. Manually type `@HomeDepot` into a note and save; confirm save still works.
  8. Confirm the hidden tag remains hidden from suggestions after reopening the editor.
  9. Reopen Manage tag suggestions, unhide `@HomeDepot`, and confirm it returns to suggestions.
  10. Check capture-template and saved-search builder tag autocomplete if those current-branch features are active in the test build.

## handoff_notes
- Do not implement bulk tag deletion or rename under this plan.
- Do not repurpose the note editor `tags_remove` button; it has existing current-note semantics.
- Keep hidden-suggestion matching exact and case-sensitive.
- Prefer small pure helpers and adapter-level filtering over changing the raw tag repository method.
- If the management screen UI starts getting large, ship the basic searchable RecyclerView first and defer duplicate-case grouping/warnings.
