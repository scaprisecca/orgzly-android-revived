# saved-search builder autocomplete

created: 2026-06-30
status: draft
source: ce-brainstorm
repo: orgzly

## problem
- The guided agenda/saved-search builder has comma-separated free-text fields for include/exclude notebooks, include/exclude tags, and include/exclude TODO states.
- Capture-template editing already has tag autocomplete, but the builder fields currently require the user to remember exact notebook names, tag names, and TODO/DONE workflow state names.
- Mistyped values produce valid-looking queries that may silently match nothing, which is worse than a visible validation failure.

## goals
- Reuse the existing capture-template tag autocomplete pattern where it fits.
- Add autocomplete to the guided saved-search builder for:
  - included notebooks
  - excluded notebooks
  - included tags
  - excluded tags
  - included TODO states
  - excluded TODO states
- Preserve current builder semantics:
  - comma/newline-separated values
  - OR within each include/exclude group
  - AND across groups
  - free-text remains allowed where useful
- Keep the generated query preview updating as the user types/selects suggestions.
- Keep this as a UI/helper enhancement; do not change saved-search schema, query compiler semantics, or preset seeding.

## non_goals
- Replacing the guided builder with chip widgets or a full visual query editor.
- Changing how builder state is stored in `builder_metadata`.
- Changing query syntax or query compiler output for notebooks/tags/states.
- Adding validation that blocks unknown tags, notebooks, or states in V1 unless Scott explicitly wants stricter behavior.
- Adding property-name/property-value autocomplete in this specific pass; it is a reasonable follow-up but has different parsing needs (`name` vs `name=value`).

## current_code_findings
- Repo root: `/home/agent/dev/orgzly`.
- `AGENTS.md` and `CLAUDE.md` are absent.
- Primary project overview: `README.org`.
- Current branch: `selected-mobile-org-features`.
- Prior relevant docs inspected:
  - `docs/brainstorms/2026_05_13_agenda_dashboard_presets_requirements.md`
  - `docs/plans/2026_05_13_agenda_dashboard_presets_plan.md`
  - `docs/brainstorms/2026_05_18_capture_template_tag_autocomplete_requirements.md`
  - `docs/plans/2026_05_18_capture_template_tag_autocomplete_plan.md`
- The guided builder is already implemented at:
  - `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
  - `app/src/main/res/layout/fragment_agenda_saved_search_builder.xml`
- The builder currently binds plain `TextInputEditText` fields for include/exclude notebooks, tags, and states.
- The builder parses those fields with `splitList(...)`, splitting on comma and newline, trimming blanks, and allowing arbitrary text.
- The query compiler already handles include/exclude values correctly:
  - notebooks -> `Condition.InBook(...)`
  - tags -> `Condition.HasTag(...)`
  - states -> `Condition.HasState(...)`
  - multiple values in a field -> `Condition.Or(...)`
  - different populated fields -> `Condition.And(...)`
- Capture-template tag autocomplete already exists and is directly relevant:
  - `app/src/main/java/com/orgzly/android/ui/capture/CaptureTemplateTagInput.kt`
  - `CaptureTemplateTagSuggestionAdapter`
  - `CaptureTemplateTagInput.tokenizer`
  - `CaptureTemplateTagMatcher`
  - `CaptureTemplateTagInput.normalizeTagsCsv(...)`
  - tests in `app/src/test/java/com/orgzly/android/ui/capture/CaptureTemplateTagInputTest.kt`
- Capture-template autocomplete uses a comma tokenizer, fuzzy matcher, and `R.layout.dropdown_item` rows. This is the right base pattern for saved-search builder list fields.
- Known data sources:
  - tags: `DataRepository.selectAllTagsLiveData()` already returns sorted distinct known note tags.
  - notebooks: `DataRepository.getBooks().map { it.book.name }` is already used by capture-template notebook dropdown setup.
  - TODO/DONE states: `AppPreferences.todoKeywordsSet(context)` and `AppPreferences.doneKeywordsSet(context)` parse the configured workflow through `StateWorkflows`.
- Current capture-template autocomplete only covers tags. It should not be copied blindly as a capture-specific class into saved-search code; extract or generalize the reusable parts.

## proposed_behavior
### Field behavior
- Convert the six builder list fields from plain `TextInputEditText` to autocomplete-capable multi-token text fields.
- Use comma tokenization for all six fields because builder state display already joins values with `", "` and `splitList(...)` already accepts comma/newline.
- Selecting a suggestion inserts the value followed by `, ` without replacing existing values.
- Typing should filter suggestions for the current token only.
- The dropdown should use fuzzy matching consistent with the capture-template tag autocomplete:
  1. exact
  2. prefix
  3. contains
  4. subsequence
  5. alphabetical within same score
- Unknown typed values should remain allowed in V1.

### Suggestions by field type
- Include/exclude tags:
  - use `DataRepository.selectAllTagsLiveData()`.
  - suggested values are plain tag names, matching what `AgendaViewQueryCompiler` passes to `Condition.HasTag(...)`.
- Include/exclude notebooks:
  - use `DataRepository.getBooks().map { it.book.name }.sorted()`.
  - suggested values are exact notebook names as the query compiler expects for `Condition.InBook(...)`.
  - Do not include the capture-template `Default notebook` sentinel here; builder notebook filters are real notebook names only.
- Include/exclude TODO states:
  - use configured state keywords from app preferences.
  - Suggest both TODO and DONE keywords in both include and exclude state fields because the compiler accepts arbitrary `Condition.HasState(...)`, and users may intentionally include or exclude either active or completed states.
  - The suggestion list must reflect custom workflows configured in Settings -> Note -> States.
  - Examples: if the user configures states such as `WAITING` or `IN-PROGRESS`, those states should appear in autocomplete alongside built-in/default states.
  - Implementation source should be `AppPreferences.todoKeywordsSet(context)` plus `AppPreferences.doneKeywordsSet(context)`, because those sets are parsed from the configured states workflow by `StateWorkflows`.

### Query preview behavior
- The current `TextWatcher`-based preview refresh should keep working if the autocomplete fields are still `TextView`/`EditText` subclasses.
- Selecting a dropdown item should trigger normal text changes and refresh the preview.
- Existing saved builder metadata should still bind as comma-separated text through `bindState(...)`.

## recommended_mvp
- Extract the capture-template comma tokenizer and matcher into a neutral saved-search/list-autocomplete helper, or create a generic helper beside the saved-search builder if extraction is too noisy.
- Add a generic suggestion adapter that can be reused for tags, notebooks, and states.
- Convert the six relevant XML inputs to `android.widget.MultiAutoCompleteTextView` or an appcompat equivalent that works inside `TextInputLayout`.
- Wire `setupListAutocompletes()` in `AgendaSavedSearchBuilderFragment` after `setupDropdowns()` and before/with `setupPreviewUpdates()`.
- Keep `splitList(...)` unchanged for compatibility.
- Add focused helper tests for tokenizer/matcher behavior if the capture-template tests cannot be reused directly after extraction.
- Add a changelog entry because this is a user-facing builder usability improvement.

## alternatives_considered
### A. Reuse capture-template classes directly
- Pros: fastest and already tested for tags.
- Cons: names and normalization are capture-template-specific; notebooks/states would read oddly and increase coupling between unrelated UI surfaces.
- Verdict: acceptable for a quick spike, but not recommended for final implementation.

### B. Extract neutral multi-token autocomplete helpers
- Pros: one implementation for capture-template tags and saved-search builder lists; keeps ranking/tokenization consistent; easier to test.
- Cons: small refactor touches existing working capture-template autocomplete.
- Verdict: recommended if the implementation stays small.

### C. Replace fields with chips/multi-select dialogs
- Pros: clearer selected-token UI and easier to prevent invalid values.
- Cons: larger UX change, more layout/state complexity, worse for fast typing, not necessary to solve the current pain.
- Verdict: defer. Good future improvement if text fields remain too error-prone.

## constraints
- `TextInputLayout` compatibility matters; build early after XML view-class changes.
- Autocomplete must not break `FragmentAgendaSavedSearchBuilderBinding` types used in `AgendaSavedSearchBuilderFragment.kt`.
- The builder currently uses comma/newline splitting. Autocomplete tokenization can insert commas, but manual newline-separated pasted lists should still parse.
- Notebook suggestions are currently likely synchronous through `getBooks()`. That matches existing capture-template code, but if notebook list changes live while the screen is open, suggestions may not update until reopening. Acceptable for V1.
- State suggestions should reflect current preferences from Settings -> Note -> States. They can be loaded on screen creation; preference changes while the screen is open are not a V1 requirement.

## open_questions
- None blocking for V1.

## resolved_decisions
- Use the same inline, comma-token autocomplete style as capture-template tags.
- Suggestions should assist entry, not restrict it; unknown free-text values remain allowed in V1.
- Notebooks, tags, and states all use the same multi-value text-field behavior for V1.
- State autocomplete suggests both TODO and DONE keywords in both include and exclude fields.
- State suggestions must include custom workflow states configured in Settings -> Note -> States, such as `WAITING` or `IN-PROGRESS`.
- Property autocomplete is explicitly deferred to V2; Scott will evaluate whether it is needed after real use of the V1 autocomplete feature.

## property_autocomplete_assessment
- Property autocomplete is possible, but it is moderately more complex than notebooks/tags/states.
- Existing reusable pieces:
  - `DataRepository.getNotePropertyNames()` returns default properties plus distinct property names from notes.
  - `NotePropertyDao.allDistinctNames()` provides known property names.
  - `NotePropertySuggestionAdapter` / `NotePropertyNameMatcher` already implement fuzzy property-name suggestions for the note editor.
  - Query support already exists for property exists / equals through `Condition.HasProperty`, dotted syntax like `prop.name` / `prop.name=value`, and SQL generation against `note_properties`.
- Complexity comes from the builder property field syntax, not from data lookup:
  - builder properties allow both `name` and `name=value` entries.
  - entries can be comma-separated or newline-separated.
  - autocomplete should suggest only the property-name part before `=` or `:`.
  - after selecting a property name, the ideal insertion should probably leave the cursor ready for either `, ` or `=` depending on whether the user wants exists vs equals.
  - value autocomplete is a separate problem and would require a distinct property-value source; no current DAO helper for distinct values by property name was found during this pass.
- Recommendation: defer property-name autocomplete to V2. Scott will decide whether it is worth adding after using the V1 notebook/tag/state autocomplete in real searches.
- If added in V1, limit it to property-name autocomplete only; do not attempt property-value autocomplete yet.
- V2 success shape:
  - typing `cli` in a property field suggests `client`.
  - selecting `client` inserts `client` without destroying `=value` if the user is editing an existing property filter.
  - `client=acme` remains valid and preview compilation still produces `prop.client=acme`.

## success_criteria
- Creating or editing an agenda/saved-search builder view shows suggestions while typing include/exclude tags.
- Creating or editing a builder view shows suggestions while typing include/exclude notebooks.
- Creating or editing a builder view shows suggestions while typing include/exclude TODO states.
- Selecting a suggestion appends it as a comma-separated value and preserves existing values in the field.
- The generated query preview refreshes after selecting suggestions.
- Existing saved builder metadata still loads and saves correctly.
- Free-text unknown values remain possible.
- `./scripts/build_fdroid.sh assembleFdroidDebug` succeeds.

## notes_for_planning
- Likely files:
  - `app/src/main/java/com/orgzly/android/ui/capture/CaptureTemplateTagInput.kt`
  - new shared helper under `app/src/main/java/com/orgzly/android/ui/util/` or `app/src/main/java/com/orgzly/android/ui/savedsearch/`
  - `app/src/main/java/com/orgzly/android/ui/savedsearch/AgendaSavedSearchBuilderFragment.kt`
  - `app/src/main/res/layout/fragment_agenda_saved_search_builder.xml`
  - `app/src/test/java/com/orgzly/android/ui/...` helper tests
  - `CHANGELOG.md`
- Suggested implementation shape:
  1. Extract/introduce `CommaSeparatedAutocomplete` helper with tokenizer, normalizer if needed, matcher, and adapter.
  2. Keep capture-template tag autocomplete behavior passing its current tests after any extraction.
  3. Replace builder list fields with multi-autocomplete views in XML.
  4. Add `setupListAutocompletes()` to bind dictionaries for tag/notebook/state fields.
  5. Run `CaptureTemplateTagInputTest` and any new helper tests.
  6. Run `./scripts/build_fdroid.sh assembleFdroidDebug`.
- Manual QA:
  - Add notes with tags `home`, `business`, `waiting`.
  - Ensure notebooks include examples like `home.org`, `business.org`, `routines.org`.
  - Configure or confirm TODO states such as `TODO NEXT WAITING | DONE CANCELLED`.
  - Open Saved searches -> guided builder -> create a new view.
  - Type partial values in all six fields and confirm suggestions/filtering/selection.
  - Confirm selected values generate expected query snippets like `b.home.org`, `t.waiting`, `i.NEXT`, and negated equivalents for exclude fields.
