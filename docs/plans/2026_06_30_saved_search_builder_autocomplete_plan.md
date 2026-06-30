# saved-search builder autocomplete implementation plan

created: 2026-06-30
status: active
source_doc: docs/brainstorms/2026_06_30_saved_search_builder_autocomplete_requirements.md

## objective
- Add comma-token autocomplete to the guided saved-search builder list fields for notebooks, tags, and TODO/DONE states.
- Reuse the existing capture-template tag autocomplete behavior where it fits, but keep the saved-search builder implementation generic enough for notebooks and states.
- Preserve current builder semantics: generated query text, saved-search metadata, comma/newline parsing, OR within groups, AND across groups, and free-text unknown values.

## scope
- included:
  - autocomplete for include/exclude notebooks
  - autocomplete for include/exclude tags
  - autocomplete for include/exclude TODO states
  - suggestion dictionaries from existing notebooks, known tags, and configured TODO/DONE workflow states
  - shared comma-token matcher/tokenizer/adapter helpers, or a small generic saved-search-specific wrapper over the existing capture-template helper
  - focused helper tests and a debug build verification path
  - changelog entry for the user-facing builder usability improvement
- excluded:
  - changing `SavedSearch` schema or `builder_metadata`
  - changing `AgendaViewQueryCompiler` output semantics
  - replacing text fields with chips or multi-select dialogs
  - blocking unknown typed values
  - property-name/value autocomplete in this pass
  - live updates when notebooks or workflow states change while the builder is already open

## repo_context_checked
- repo root: `/home/agent/dev/orgzly`
- current branch: `selected-mobile-org-features`
- `AGENTS.md`: missing
- `CLAUDE.md`: missing
- primary overview: `README.org`
- source requirements: `docs/brainstorms/2026_06_30_saved_search_builder_autocomplete_requirements.md`
- nearby plans inspected:
  - `docs/plans/2026_05_13_agenda_dashboard_presets_plan.md`
  - `docs/plans/2026_05_18_capture_template_tag_autocomplete_plan.md`
- relevant workflow notes inspected:
  - Orgzly repo workflow notes
  - agenda dashboard / guided saved-search builder notes

## relevant_existing_patterns
- `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
  - Current guided builder screen.
  - `setupDropdowns()` wires only date filter and sort dropdowns.
  - `setupPreviewUpdates()` watches all list fields with a `TextWatcher`, so autocomplete insertion should refresh the preview through normal text changes.
  - `bindState(...)` writes comma-separated values for notebook/tag/state fields.
  - `readState()` calls `splitList(...)`, which already splits on comma and newline and trims blanks.
- `app/src/main/res/layout/fragment_agenda_saved_search_builder.xml`
  - The six target fields are currently `TextInputEditText` inside `TextInputLayout`:
    - `fragment_saved_search_builder_include_notebooks`
    - `fragment_saved_search_builder_exclude_notebooks`
    - `fragment_saved_search_builder_include_tags`
    - `fragment_saved_search_builder_exclude_tags`
    - `fragment_saved_search_builder_include_states`
    - `fragment_saved_search_builder_exclude_states`
  - Property fields intentionally remain multi-line plain text for this pass.
- `app/src/main/java/com/orgzly/android/ui/capture/CaptureTemplateTagInput.kt`
  - Existing comma tokenizer, tag normalization helper, fuzzy matcher, and `ArrayAdapter` implementation.
  - Matcher ranking already matches the desired behavior: exact, prefix, contains, subsequence, alphabetical.
  - Current names are capture-template-specific; direct reuse would couple the saved-search builder to capture-template UI language.
- `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - Existing autocomplete wiring pattern:
    - create suggestion adapter with `R.layout.dropdown_item`
    - set adapter and tokenizer
    - threshold `1`
    - observe `dataRepository.selectAllTagsLiveData()`
    - optionally show dropdown on focus/click when current token is empty
- `app/src/main/res/layout/fragment_capture_template_editor.xml`
  - Uses `androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView` inside `TextInputLayout`, proving the view type is viable in this project.
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `selectAllTagsLiveData()` is the correct known-tag source.
  - `getBooks().map { it.book.name }` is already used by capture-template notebook dropdowns.
- `app/src/main/java/com/orgzly/android/AppPreferences.kt` and current callers
  - `AppPreferences.todoKeywordsSet(context)` and `AppPreferences.doneKeywordsSet(context)` expose workflow-derived state keywords.
- `app/src/main/java/com/orgzly/android/savedsearch/builder/AgendaViewQueryCompiler.kt`
  - Already compiles include/exclude notebooks, tags, and states correctly via `Condition.InBook`, `Condition.HasTag`, and `Condition.HasState`.
  - No compiler changes should be needed for this feature.
- `CHANGELOG.md`
  - Has an `[Unreleased]` / `Added` section appropriate for this user-facing improvement.

## implementation_units

### 1. Extract or introduce generic comma-list autocomplete helpers
- files:
  - create `app/src/main/java/com/orgzly/android/ui/util/CommaSeparatedAutocomplete.kt`
  - create `app/src/test/java/com/orgzly/android/ui/util/CommaSeparatedAutocompleteTest.kt`
  - optionally update `app/src/main/java/com/orgzly/android/ui/capture/CaptureTemplateTagInput.kt`
  - optionally update `app/src/test/java/com/orgzly/android/ui/capture/CaptureTemplateTagInputTest.kt`
- changes:
  - Move the reusable pieces from `CaptureTemplateTagInput.kt` into a neutral helper:
    - comma tokenizer
    - fuzzy matcher
    - generic suggestion adapter with `updateDictionary(items: List<String>)`
  - Keep capture-template tag normalization in capture-specific code; saved-search builder should not normalize or de-duplicate field text on save beyond existing `splitList(...)` behavior.
  - Preserve matcher order:
    1. exact
    2. prefix
    3. contains
    4. subsequence
    5. alphabetical within same score
  - Keep tokenizer behavior compatible with existing capture-template tests:
    - current token starts after prior comma and spaces
    - token ends at next comma or end of text
    - selected value terminates as `value, `
  - If extraction gets noisy, acceptable fallback is to create a saved-search-specific helper with copied matcher/tokenizer logic and leave capture-template classes unchanged. Prefer extraction only if it stays small.
- dependencies:
  - none
- risks:
  - Refactoring working capture-template autocomplete can regress template editing.
  - A generic helper that also includes capture-specific normalization would be the wrong abstraction.
- tests:
  - `app/src/test/java/com/orgzly/android/ui/util/CommaSeparatedAutocompleteTest.kt`
    - token start/end after comma + spaces
    - selected token terminates as `value, `
    - matcher ranking exact/prefix/contains/subsequence
    - empty query returns dictionary in dictionary order
  - Existing `CaptureTemplateTagInputTest` should still pass if capture-template code is changed.
- acceptance:
  - Shared helper behavior is covered by pure JVM tests.
  - Capture-template tag autocomplete behavior is unchanged or intentionally untouched.

### 2. Convert builder list fields to autocomplete-capable views
- files:
  - `app/src/main/res/layout/fragment_agenda_saved_search_builder.xml`
- changes:
  - Replace only the six notebook/tag/state `TextInputEditText` fields with `androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView`.
  - Preserve existing ids so `FragmentAgendaSavedSearchBuilderBinding` call sites stay mostly unchanged.
  - Keep the fields inside their current `TextInputLayout`s and preserve current hints/helper text.
  - Keep property fields as `TextInputEditText`; property autocomplete is deferred because property entries can be `name` or `name=value`.
- dependencies:
  - unit 1 helper is not strictly required before the XML change, but the build should happen soon after this unit because view binding types will change.
- risks:
  - Binding types will change from `TextInputEditText` to `AppCompatMultiAutoCompleteTextView`; most current calls are still valid (`text`, `setText`, `addTextChangedListener`).
  - Material `TextInputLayout` can behave differently around dropdown widgets; mirror the already-working capture-template layout.
- tests:
  - resource/view-binding compile through `./scripts/build_fdroid.sh assembleFdroidDebug`
- acceptance:
  - The builder layout compiles and existing fragment references still type-check.

### 3. Wire autocomplete dictionaries in `AgendaSavedSearchBuilderFragment`
- files:
  - `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
- changes:
  - Add `setupListAutocompletes()` and call it from `onViewCreated()` after `setupDropdowns()` and before or alongside `setupPreviewUpdates()`.
  - Create adapters using `R.layout.dropdown_item`, the generic comma tokenizer, and threshold `1`.
  - Wire the same dictionary to include/exclude pairs by type:
    - tags: observe `dataRepository.selectAllTagsLiveData()` and update both tag adapters
    - notebooks: `dataRepository.getBooks().map { it.book.name }.sorted()` for both notebook adapters
    - states: `(AppPreferences.todoKeywordsSet(requireContext()) + AppPreferences.doneKeywordsSet(requireContext())).distinct().sorted()` for both state adapters
  - Do not include the capture-template `Default notebook` sentinel in notebook suggestions.
  - Suggest both TODO and DONE keywords in both include and exclude state fields.
  - Add a small helper to show the dropdown on focus/click when the current token is empty, matching the capture-template UX if it does not feel noisy.
  - Keep `setupPreviewUpdates()` unchanged unless the autocomplete fields require type adjustments.
- dependencies:
  - units 1 and 2
- risks:
  - Calling `getBooks()` synchronously matches existing patterns but may not reflect notebook changes until reopening the screen; acceptable for V1.
  - State keyword loading on screen creation will not update if Settings -> Note -> States changes while the builder is open; acceptable for V1.
  - Showing dropdowns on focus for six fields may feel noisy on mobile. If manual QA is annoying, keep autocomplete-on-typing only and drop focus/click dropdown behavior for builder fields.
- tests:
  - compile/build for fragment binding and imports
  - manual QA for actual dropdown behavior
- acceptance:
  - Typing a partial notebook/tag/state value filters suggestions for the current comma token.
  - Selecting a suggestion inserts `value, ` without replacing earlier values in the field.
  - Generated query preview updates after selection.

### 4. Preserve builder state and query semantics
- files:
  - `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
  - no expected changes to `app/src/main/java/com/orgzly/android/savedsearch/builder/AgendaViewQueryCompiler.kt`
  - no expected changes to `app/src/main/java/com/orgzly/android/savedsearch/builder/AgendaViewBuilderState.kt`
- changes:
  - Keep `splitList(...)` as the source of truth for reading fields so pasted newline-separated lists still work.
  - Keep `bindState(...)` as comma-separated display for saved metadata.
  - Do not validate typed values against the suggestion dictionaries.
  - Do not change the compiler's OR/AND grouping or positive/negative condition behavior.
  - Confirm no save-time normalization removes unknown user-entered values.
- dependencies:
  - unit 3
- risks:
  - Accidentally tightening validation would make the feature more brittle and block legitimate ad-hoc queries.
  - Changing field parsing to comma-only would regress newline-pasted lists.
- tests:
  - Existing `AgendaViewQueryCompilerTest` should remain unchanged.
  - If practical, add a small pure test around any extracted list-field parsing helper only if `splitList(...)` is moved out of the fragment.
- acceptance:
  - Existing saved builder metadata loads into the new fields.
  - Free-text unknown values save and compile exactly as before.
  - Comma and newline separated values still compile to the same generated query strings.

### 5. Add changelog and optional copy polish
- files:
  - `CHANGELOG.md`
  - optionally `app/src/main/res/values/strings.xml`
- changes:
  - Add an `[Unreleased]` / `Added` entry such as: `Added autocomplete suggestions for notebooks, tags, and TODO states in the guided saved-search builder.`
  - Keep existing `saved_search_builder_list_helper` unless manual QA shows users need stronger wording. Current helper text `Separate entries with commas or new lines` remains accurate.
  - Do not add new translatable strings unless the UI copy actually changes.
- dependencies:
  - units 2 and 3
- risks:
  - Over-explaining helper text can clutter an already dense mobile form.
- tests:
  - build/resource compile if strings change
- acceptance:
  - Changelog reflects the user-facing behavior.

## sequencing
1. Add or extract the neutral comma-list autocomplete helper and pure tests.
2. If extraction touched capture-template code, run/inspect the capture-template helper tests early.
3. Change the six builder list fields to `AppCompatMultiAutoCompleteTextView`.
4. Add `setupListAutocompletes()` in `AgendaSavedSearchBuilderFragment` for tags, notebooks, and states.
5. Confirm `setupPreviewUpdates()`, `bindState(...)`, and `readState()` still work with the new binding types.
6. Add changelog entry.
7. Run focused helper tests.
8. Run `./scripts/build_fdroid.sh assembleFdroidDebug`.
9. Manual QA on device/emulator.

## risks_and_unknowns
- risk: extracting the capture-template helper creates avoidable churn.
  - mitigation: keep extraction mechanical and small; if it cascades, use a saved-search-specific helper and leave capture-template code intact.
- risk: autocomplete field type breaks generated view binding or Material layout behavior.
  - mitigation: mirror `fragment_capture_template_editor.xml`, build immediately after XML changes, and preserve ids.
- risk: state suggestions miss custom workflow states.
  - mitigation: use `AppPreferences.todoKeywordsSet(context)` plus `AppPreferences.doneKeywordsSet(context)`, not hard-coded `TODO`/`DONE` lists.
- risk: query preview does not refresh after selection.
  - mitigation: rely on normal text-change events first; if selection does not trigger preview in manual QA, call `refreshPreview()` from `setOnItemClickListener` for each autocomplete field.
- risk: focus-triggered dropdowns are too noisy across six dense fields.
  - mitigation: make focus/click dropdown behavior optional; threshold-based suggestions while typing are the core acceptance criterion.
- risk: helper tests using Android widget classes can hit local unit-test Android-stub limitations.
  - mitigation: keep matcher pure; tokenizer tests already exist in `CaptureTemplateTagInputTest`, so follow that pattern and verify specific XML results if the broader Gradle task is noisy.

## validation_strategy
- focused unit tests:
  - `./scripts/build_fdroid.sh app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.util.CommaSeparatedAutocompleteTest`
  - if capture-template helper extraction occurs: `./scripts/build_fdroid.sh app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.capture.CaptureTemplateTagInputTest`
  - if the overall Gradle test task reports unrelated repo-wide failures, inspect the specific XML result for the new/helper test class under `app/build/test-results/testFdroidDebugUnitTest/`.
- existing compiler safety:
  - `./scripts/build_fdroid.sh app:testFdroidDebugUnitTest --tests com.orgzly.android.savedsearch.builder.AgendaViewQueryCompilerTest`
  - This should not need new expectations; it is a regression signal that query semantics stayed unchanged.
- build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- manual QA:
  - Ensure there are notebooks such as `home.org`, `business.org`, and `routines.org`.
  - Ensure known tags such as `home`, `business`, and `waiting` exist on notes.
  - Configure or confirm states such as `TODO NEXT WAITING | DONE CANCELLED`.
  - Open Saved searches -> guided builder -> create a new view.
  - In include/exclude notebooks, type partial notebook names and select suggestions.
  - In include/exclude tags, type partial/fuzzy tag names and select suggestions.
  - In include/exclude states, type partial state names and select suggestions, including a custom state.
  - Confirm selected suggestions append as comma-separated values and preserve existing values.
  - Confirm query preview updates after selection, with expected snippets such as `b.home.org`, `t.waiting`, `i.NEXT`, and negated forms for exclude fields.
  - Save, reopen the builder-created search, and confirm metadata reloads into the autocomplete fields.
  - Type an unknown value manually and confirm it remains allowed.

## handoff_notes
- This is a UI/helper enhancement, not a query engine or saved-search metadata feature.
- Do not change Room migrations, `SavedSearch`, `AgendaViewBuilderState`, or `AgendaViewQueryCompiler` unless implementation reveals a real compile-time necessity.
- Keep property autocomplete out of scope. Property fields have different parsing requirements because entries may be `name`, `name=value`, or `name: value`.
- Prefer `R.layout.dropdown_item` for suggestion rows to match existing autocomplete surfaces.
- Preserve newline parsing in `splitList(...)`; autocomplete insertion can be comma-based while pasted lists remain newline-compatible.
- Use configured workflow helpers for states. Hard-coded `TODO`/`DONE` suggestions would miss Scott's custom `NEXT` / `WAITING` style workflows.
