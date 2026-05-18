# capture template tag autocomplete implementation plan

created: 2026-05-18
status: active
source_doc: docs/brainstorms/2026_05_18_capture_template_tag_autocomplete_requirements.md

## objective
- Add inline tag autocomplete to the capture-template editor so users can quickly select existing tags while assigning template tags.
- Preserve the current capture-template storage/runtime model: template tags remain stored as comma-separated `tags_csv` and are split by `CaptureTemplates.kt` when building note payloads.
- Match the normal note tag experience closely, while using comma tokenization because capture templates already use comma-separated tags.

## scope
- included:
  - Replace the capture-template tags field with a tokenized autocomplete field.
  - Populate suggestions from the existing known-tag source, `DataRepository.selectAllTagsLiveData()`.
  - Support multiple comma-separated tags in one field.
  - Keep free-text entry for new tags that do not exist yet.
  - Normalize tag text on save by trimming, dropping blanks, and de-duplicating while preserving order.
  - Add lightweight fuzzy ranking for suggestions using exact / prefix / contains / subsequence matching.
  - Add focused JVM tests for matcher/tokenizer/normalization helpers where possible.
- excluded:
  - Changing `CaptureTemplateEntity.tagsCsv` storage.
  - Creating a dedicated tag-management screen.
  - Requiring selected tags to already exist.
  - Changing normal note tags from space-separated behavior.
  - Changing `CaptureTemplates.kt` runtime tag parsing beyond optional test coverage that proves compatibility.

## repo_context_checked
- repo root: `/home/agent/dev/orgzly`
- `AGENTS.md`: missing
- `CLAUDE.md`: missing
- primary overview: `README.org`
- nearby plan inspected: `docs/plans/2026_05_09_custom_capture_templates_plan.md`
- important current-branch finding: the custom capture-template infrastructure from the older plan has already landed. `TemplateEditorFragment.kt`, `CaptureTemplateEntity.tagsCsv`, persisted catalog tests, and runtime payload mapping already exist, so this feature should be a small editor-UX enhancement rather than a data-model project.

## relevant_existing_patterns
- `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - Current capture-template editor.
  - Already has `setupNotebookDropdown()` using a runtime `ArrayAdapter` for target notebooks.
  - Currently reads/writes `binding.tagsInput` as plain text and saves `tagsCsv = binding.tagsInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }`.
- `app/src/main/res/layout/fragment_capture_template_editor.xml`
  - Current tags input is a `TextInputEditText` inside `TextInputLayout` with helper text `Comma-separated tags`.
  - This should become an autocomplete-capable field while keeping the same id if practical to minimize binding churn.
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - Normal note tag autocomplete pattern: `ArrayAdapter(..., R.layout.dropdown_item, tags)` plus `binding.tagsButton.setTokenizer(SpaceTokenizer())`.
  - This is the UI precedent, but capture templates need comma tokenization, not `SpaceTokenizer`.
- `app/src/main/java/com/orgzly/android/util/SpaceTokenizer.java`
  - Existing tokenizer implementation for space-separated tags.
  - Good reference for a new comma tokenizer, but do not change this class because normal note tags and book filetags still use space separation.
- `app/src/main/java/com/orgzly/android/ui/note/NotePropertySuggestionAdapter.kt`
  - Existing lightweight fuzzy matcher pattern: exact, prefix, contains, subsequence, sorted by score then alphabetical.
  - Reuse or mirror this pattern for tag suggestions.
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `selectAllTagsLiveData()` already deduplicates and sorts known tags from note tag fields.
  - `selectAllTags()` exists for synchronous test/setup use but UI should prefer LiveData.
- `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - Runtime payload builder currently splits `template.tagsCsv` on commas, trims, filters blanks, and appends tags to the generated `NotePayload`.
  - The editor should preserve this delimiter contract.
- `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
  - Already verifies custom template fields map into `NotePayload`, including `tagsCsv = "alpha, beta"` -> `listOf("alpha", "beta")`.
  - Useful regression test location if normalization changes need runtime coverage.
- `app/src/test/java/com/orgzly/android/capture/CaptureTemplateRepositoryTest.kt`
  - Confirms persisted capture-template catalog behavior; this feature should not require repository/schema changes.

## implementation_units

### 1. Add reusable comma-token tag helpers
- files:
  - `app/src/main/java/com/orgzly/android/ui/capture/CaptureTemplateTagInput.kt` or similar new file
  - `app/src/test/java/com/orgzly/android/ui/capture/CaptureTemplateTagInputTest.kt` or similar new test file
- changes:
  - Add a `MultiAutoCompleteTextView.Tokenizer` for comma-separated tags.
    - `findTokenStart`: walk backward to previous comma, then skip spaces.
    - `findTokenEnd`: walk forward until next comma or end.
    - `terminateToken`: trim trailing whitespace and add `, ` unless the token already ends with a comma.
  - Add a pure helper to normalize saved template tag text:
    - split on comma
    - trim each token
    - drop blanks
    - de-duplicate while preserving first occurrence
    - join with `, ` or return null/empty for no tags
  - Add a pure fuzzy matcher for tags using the same ranking style as `NotePropertyNameMatcher`.
    - exact = best
    - prefix
    - contains
    - subsequence
    - then alphabetical
- dependencies:
  - none beyond Android widget types for tokenizer; pure helpers should be testable without UI.
- risks:
  - Tokenizer behavior can accidentally include leading spaces in inserted tokens or collapse existing tag text incorrectly.
  - If the helper joins with spaces instead of commas, runtime `CaptureTemplates.kt` parsing will treat multiple tags as one tag.
- tests:
  - `app/src/test/java/com/orgzly/android/ui/capture/CaptureTemplateTagInputTest.kt`
    - tokenizer finds current token after comma + space
    - tokenizer terminates selected tag as `tag, `
    - normalization trims blanks and de-dupes: `" alpha, beta,, alpha "` -> `"alpha, beta"`
    - matcher ranks exact/prefix/contains/subsequence as expected
- acceptance:
  - Helpers are independent enough that most behavior can be verified without an Android UI test.

### 2. Add a fuzzy autocomplete adapter for capture-template tags
- files:
  - `app/src/main/java/com/orgzly/android/ui/capture/CaptureTemplateTagSuggestionAdapter.kt` or include in `CaptureTemplateTagInput.kt` if small
  - `app/src/test/java/com/orgzly/android/ui/capture/CaptureTemplateTagInputTest.kt`
- changes:
  - Implement an `ArrayAdapter<String>` with an internal dictionary and custom `Filter`, mirroring `NotePropertySuggestionAdapter`.
  - Expose `updateDictionary(tags: List<String>)` so the fragment can update suggestions when LiveData emits.
  - Filter only against the current token text passed by `MultiAutoCompleteTextView`; the tokenizer should ensure the adapter receives the token being typed, not the whole comma-separated field.
  - Use `R.layout.dropdown_item` for rows.
- dependencies:
  - unit 1 matcher.
- risks:
  - Duplicating matcher logic with `NotePropertySuggestionAdapter` could create two similar implementations. This is acceptable for a small feature, but if the implementation starts growing, extract a neutral matcher utility rather than over-abstracting up front.
- tests:
  - adapter filter behavior can be covered indirectly through the pure matcher; avoid brittle UI adapter tests unless straightforward.
- acceptance:
  - Given dictionary `[business, chore, meeting, learning]`, typing `bus` suggests `business`; typing fuzzy/subsequence text such as `mtg` only suggests matching tags if the matcher supports it.

### 3. Wire autocomplete into `TemplateEditorFragment`
- files:
  - `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - `app/src/main/res/layout/fragment_capture_template_editor.xml`
  - `app/src/main/res/values/strings.xml`
- changes:
  - Change `tags_input` in the layout from `TextInputEditText` to an autocomplete-capable `MultiAutoCompleteTextView` / `AppCompatMultiAutoCompleteTextView` if available with current dependencies.
  - Keep the field inside the current `TextInputLayout` and preserve `@+id/tags_input` where possible.
  - Add `setupTagsAutocomplete()` in `TemplateEditorFragment` and call it from `onViewCreated()` after `setupNotebookDropdown()`.
  - In `setupTagsAutocomplete()`:
    - create the tag suggestion adapter using `R.layout.dropdown_item`
    - set the adapter on `binding.tagsInput`
    - set the comma tokenizer
    - observe `dataRepository.selectAllTagsLiveData()` and call `adapter.updateDictionary(tags)`
    - optionally lower the threshold to `1`; consider `0` only if manual QA shows a clear benefit and it does not spam empty dropdowns
  - Add optional click/focus behavior if needed:
    - on field click/focus, show dropdown when there are known tags and the current token is empty
    - keep this as nice-to-have, not a blocker, because autocomplete on typing is the core requirement
- dependencies:
  - units 1 and 2.
- risks:
  - Some Material `TextInputLayout` configurations behave differently with `MultiAutoCompleteTextView` than `TextInputEditText`; keep the XML simple and verify build/resource binding.
  - Observing LiveData directly from a Fragment without a ViewModel is consistent with this fragment's existing simple repository use, but make sure the observer uses `viewLifecycleOwner`.
- tests:
  - compile/build is the primary signal for binding/XML correctness.
  - If Robolectric coverage is added later, verify adapter assignment and tokenizer type, but do not block V1 on a brittle UI test.
- acceptance:
  - Capture-template editor displays suggestions from known note tags while editing the tags field.
  - Selecting a suggestion appends it as a comma-separated tag without replacing existing tags.

### 4. Normalize tags on save and preserve existing load behavior
- files:
  - `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt` only if adding runtime regression coverage
- changes:
  - Replace direct save of `binding.tagsInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }` with the normalization helper from unit 1.
  - Keep `loadTemplate()` as `binding.tagsInput.setText(template.tagsCsv.orEmpty())` so existing saved rows display naturally.
  - Keep `clearForm()` as `binding.tagsInput.text = null` or equivalent for the new view type.
  - Do not reject unknown tags; this field is suggestion-assisted free text, not validation against the known-tag dictionary.
- dependencies:
  - unit 1 normalization helper.
- risks:
  - Over-normalizing could surprise users if tags include unusual characters. Keep delimiter handling limited to commas and whitespace trimming; do not lowercase or otherwise mutate tag names.
- tests:
  - `CaptureTemplateTagInputTest` for normalization behavior.
  - Optional `CaptureTemplatesTest` regression that a custom template saved as normalized `alpha, beta` still produces payload tags `[alpha, beta]`.
- acceptance:
  - Existing templates load as before.
  - New/edited templates save clean comma-separated tags.
  - Runtime capture behavior remains unchanged.

### 5. Update user-facing copy and changelog
- files:
  - `app/src/main/res/values/strings.xml`
  - `CHANGELOG.md`
- changes:
  - Consider updating `capture_template_tags_summary` from `Comma-separated tags` to something like `Comma-separated tags; suggestions come from existing note tags`.
  - Add an Unreleased changelog entry for capture-template tag autocomplete.
- dependencies:
  - unit 3 UI behavior.
- risks:
  - Translators will need updated strings if changed; keep wording short.
- tests:
  - build resource compile.
- acceptance:
  - UI help text accurately describes comma-separated tags and suggestions.

## sequencing
1. Add pure comma tokenizer / normalization / matcher helpers and tests.
2. Add tag suggestion adapter using the helper matcher.
3. Update `fragment_capture_template_editor.xml` to use an autocomplete-capable tags view.
4. Wire `setupTagsAutocomplete()` into `TemplateEditorFragment` and observe `selectAllTagsLiveData()`.
5. Replace direct tags save with normalization helper.
6. Update strings/changelog.
7. Run focused tests and a full debug build.
8. Manual QA on device/emulator.

## risks_and_unknowns
- risk: `MultiAutoCompleteTextView` inside Material `TextInputLayout` may require using a specific class/import to generate the correct view binding type.
  - mitigation: keep the XML simple, build early, and adjust to `android.widget.MultiAutoCompleteTextView` if `AppCompatMultiAutoCompleteTextView` is unavailable.
- risk: fuzzy adapter duplicates `NotePropertyNameMatcher`.
  - mitigation: start with a small capture-specific matcher; extract a shared matcher only if the implementation becomes clearly duplicated and noisy.
- risk: comma tokenization conflicts with a user typing a comma inside a tag name.
  - mitigation: accept this limitation because the existing storage/runtime contract is comma-separated tags; tags containing commas are out of scope.
- risk: LiveData suggestion source only includes tags from existing notes, not tags that exist only in other templates.
  - mitigation: V1 intentionally reuses known note tags. A future enhancement could merge in distinct tags from capture templates if that proves useful.
- risk: targeted Robolectric tests in this repo can fail for unrelated SDK/JDK issues.
  - mitigation: keep new helper tests pure where possible and use `assembleFdroidDebug` as the strongest baseline verification.

## validation_strategy
- unit tests:
  - Run the new helper test class, for example:
    - `./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.capture.CaptureTemplateTagInputTest`
  - If tests hit repo-wide Robolectric/JDK noise, inspect the specific XML result for the new test class.
- build:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- manual QA:
  - Create a few normal notes with tags such as `business`, `meeting`, `chore`, `home_project`.
  - Open Settings -> Manage capture templates -> edit/create template.
  - Tap the Tags field and type part of an existing tag.
  - Confirm suggestions filter as typing continues.
  - Select a suggestion and confirm it inserts as `tag, ` without removing existing tags.
  - Add a second suggested tag and a brand-new manually typed tag.
  - Save, reopen the template, and confirm tags persist normalized.
  - Use the template to create a note and confirm the generated note gets the expected tag list.

## handoff_notes
- This is not a schema or runtime-capture feature; avoid touching Room migrations, `CaptureTemplateEntity`, or `CaptureTemplates.kt` unless adding a narrow regression test.
- Preserve comma-separated template tags. Do not reuse `SpaceTokenizer()` for this field.
- Do not validate template tags against existing tags; suggestions should speed entry, not restrict it.
- Prefer `R.layout.dropdown_item` for visual consistency with normal note tag autocomplete.
- Keep direct implementation small. The likely file count is one helper/adapter file, one fragment edit, one layout edit, one test file, strings/changelog.
