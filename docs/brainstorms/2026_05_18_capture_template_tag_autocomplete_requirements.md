# capture template tag autocomplete

created: 2026-05-18
status: draft
source: ce-brainstorm

## problem
- Capture-template editing currently stores template tags as comma-separated free text.
- Normal note editing already helps users pick known tags via autocomplete, but capture-template tags do not.
- This makes template setup slower and more error-prone because users must remember exact tag names.

## goals
- make assigning tags to capture templates as easy as assigning tags to normal notes
- reuse the app's existing known-tag source from notes
- support multiple tags in one field
- preserve the current persisted `tags_csv` model and runtime comma-splitting behavior
- keep the implementation small and consistent with nearby template-editor UI patterns

## non_goals
- redesign capture-template storage
- introduce a full tag-management screen
- force users to pick only existing tags
- normalize all app tag delimiters in this feature

## recommended_behavior
- Replace the capture-template tag `TextInputEditText` with a tokenized autocomplete text field.
- Use comma-separated tokens because `CaptureTemplates.kt` already parses `tagsCsv` by comma and the current helper text says `Comma-separated tags`.
- Populate suggestions from `DataRepository.selectAllTagsLiveData()` / the same distinct known-tag source used by the normal note editor.
- Show a dropdown when the user starts typing a tag token and filter suggestions as they type.
- Selecting a suggestion should insert the tag plus the comma/token separator, then let the user continue typing the next tag.
- Keep free-text entry allowed so users can create a new tag that does not exist yet.

## recommendation
- Recommended V1: `MultiAutoCompleteTextView` with comma tokenization plus a reusable fuzzy/contains adapter.
- This is better than a separate modal dropdown because it keeps the flow inline, works naturally for multiple tags, and matches Android autocomplete patterns already used in Orgzly.
- Prefer using the same ranking style as `NotePropertyNameMatcher`: exact, prefix, contains, subsequence, then alphabetical. This gives the requested fuzzy feel without adding a heavy dependency.
- If speed matters more than fuzzy matching for V1, reuse the normal note tag field pattern first (`ArrayAdapter` + tokenizer) and add the custom fuzzy filter after. But the custom adapter is small enough that I would include it in the first implementation.

## implementation_notes
- Relevant current files:
  - `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - `app/src/main/res/layout/fragment_capture_template_editor.xml`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NotePropertySuggestionAdapter.kt`
- Current template editor only sets up the target notebook dropdown. Add a parallel `setupTagsAutocomplete()` path.
- Current normal note tags use `binding.tagsButton.setAdapter(...)` and `SpaceTokenizer()` because normal note tags are space-separated.
- Capture templates should not blindly reuse `SpaceTokenizer()` because persisted template tags are comma-separated and `CaptureTemplates.kt` splits `tagsCsv` on commas.
- Use `R.layout.dropdown_item` for suggestion rows for consistency with note-editor dropdowns.
- Consider extracting the existing property matcher into a neutral reusable matcher if code reuse is cleaner than duplicating the exact/prefix/contains/subsequence ranking.

## validation_rules
- Do not reject unknown typed tags in V1; users should be able to create new tag names in templates.
- On save, normalize tags by splitting on comma, trimming whitespace, dropping blanks, optionally de-duplicating while preserving order, and joining back to comma-separated `tagsCsv`.
- Existing saved templates with comma-separated tags should load unchanged and still save correctly.

## success_criteria
- Opening a capture-template editor shows tag suggestions from existing note tags.
- Typing filters the dropdown for the current comma-separated token.
- Selecting a suggestion appends it to the template tag list without overwriting other tags.
- User can still type a brand-new tag manually.
- Saved templates continue to produce the same runtime note tags via existing `CaptureTemplates.kt` parsing.
- `./scripts/build_fdroid.sh assembleFdroidDebug` succeeds.

## open_questions
- Should the dropdown open immediately when the tag field gains focus, or only after the user types/taps the dropdown affordance? Recommendation: open on focus/tap if there are known tags, but do not require this for V1.
