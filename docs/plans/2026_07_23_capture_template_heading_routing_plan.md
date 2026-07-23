# capture template heading routing implementation plan

created: 2026-07-23
status: active
source_doc: Telegram request from Scott, 2026-07-23

## objective

- extend Orgzly capture templates so each template can optionally target a heading path inside its target notebook
- store the target as a human-readable heading path, not a database note id
- preserve existing behavior when no target heading is configured: the captured TODO/note is created at the notebook root/bottom like today
- support Scott's example: a template for Home Depot purchases routes new items to `errands.org` under the `Home Depot` heading

## product decisions

- storage model: **Option A — store heading path**
  - save a string such as `Home Depot` or `Shopping/Home Depot`
  - resolve it at capture time against the target notebook
  - do not store/couple the template to local note ids in v1
- if the target notebook is missing:
  - keep current behavior: fall back to the app's default target notebook
- if the target heading is missing:
  - fall back to the resolved target notebook root
  - surface a warning/snackbar when opening the capture editor, where practical
- if duplicate headings exist:
  - use the full path from notebook root, not just title text
  - example: `Shopping/Home Depot`, not only `Home Depot`
- if the notebook field is changed in the template editor:
  - clear the selected heading path if it is no longer valid in the newly selected notebook
  - alternatively revalidate and preserve it only if the same path exists in the new notebook
- if a heading is renamed:
  - template editor should surface that the saved heading path is missing
  - runtime capture should not block; it should fall back to notebook root
- feature is optional:
  - blank/missing heading path means current behavior: create at notebook root/bottom

## current code context

- `app/src/main/java/com/orgzly/android/db/entity/CaptureTemplateEntity.kt`
  - persisted capture templates already exist
  - current routing field is `targetNotebookName`
  - no parent/heading target field exists yet
- `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - `resolveTargetBook(...)` handles notebook-only routing
  - `buildPayload(...)` handles title/body/state/tags payload generation
  - best place to add `resolveTargetPlace(...)`
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - `openTemplatedNote(...)` currently resolves a book and passes `NotePlace(targetBook.book.id)`
- `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - `showNoteEditor(...)` currently resolves a book id and passes `new NotePlace(bookId)`
- `app/src/main/java/com/orgzly/android/ui/NotePlace.java`
  - already supports `NotePlace(bookId, noteId, Place.UNDER)`
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `createNote(...)` already supports `Place.UNDER`
  - `getNoteAtPath(fullPath)` already resolves full paths like `errands/Home Depot`
  - `getTopLevelNotes(...)` and `getNoteChildren(...)` can support heading-picker/autocomplete UI
- `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
  - already validates target notebook names
  - should be extended to load, validate, and save target heading paths
- `app/src/main/res/layout/fragment_capture_template_editor.xml`
  - already has a target notebook input
  - needs an optional target heading input below target notebook
- `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
  - existing tests cover payload and notebook routing
  - should be extended for heading-path routing

## architecture direction

Add a nullable `target_heading_path` field to capture templates. Runtime capture should resolve a full `NotePlace`, not just a `BookView`.

The key new helper should live in `CaptureTemplates.kt`:

```kotlin
@JvmStatic
fun resolveTargetPlace(
    dataRepository: DataRepository,
    context: Context,
    template: CaptureTemplateEntity?,
    explicitBookId: Long?,
): NotePlace
```

Resolution order:

1. Resolve the notebook using existing `resolveTargetBook(...)` semantics:
   - template target notebook if present and found
   - explicit book override if applicable
   - default target notebook fallback
2. If `template.targetHeadingPath` is blank/null, return `NotePlace(book.id)`.
3. If heading path is present, build a full path of `book.name + "/" + normalizedHeadingPath`.
4. Call `dataRepository.getNoteAtPath(fullPath)`.
5. If found, return `NotePlace(book.id, heading.note.id, Place.UNDER)`.
6. If missing, return `NotePlace(book.id)` and optionally report a warning to the caller/UI.

Because snackbar display is UI-layer work, do not make `CaptureTemplates.kt` directly show UI. Instead, expose enough information for the UI to warn when needed. Two acceptable designs:

- simple v1: `resolveTargetPlace(...)` returns only `NotePlace`; `TemplateEditorFragment` independently validates missing headings when editing templates
- better v1: add a small result type, for example:

```kotlin
data class CaptureTargetResolution(
    val place: NotePlace,
    val resolvedBook: BookView,
    val missingHeadingPath: String? = null,
)
```

Then use:

```kotlin
fun resolveTarget(...): CaptureTargetResolution
```

Recommendation: use the result type so `BookFragment` and `ShareActivity` can show a warning when a configured heading is missing, while still falling back safely.

## proposed data model

Modify `CaptureTemplateEntity`:

```kotlin
@ColumnInfo(name = "target_heading_path")
val targetHeadingPath: String? = null,
```

Database migration:

- bump Room database version from `160` to `161`
- add migration:

```kotlin
private val MIGRATION_160_161 = object : Migration(160, 161) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE capture_templates ADD COLUMN target_heading_path TEXT")
    }
}
```

- add `MIGRATION_160_161` to `.addMigrations(...)`
- export/add schema JSON for version `161`

No default heading should be seeded for built-ins/custom templates. This keeps the feature optional and preserves current behavior.

## heading path rules

Use a normalized slash-separated path relative to the notebook root:

- `Home Depot`
- `Shopping/Home Depot`
- `Projects/House/Paint`

Normalization helper behavior:

```kotlin
fun normalizeHeadingPath(raw: String?): String? {
    return raw
        ?.split("/")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.joinToString("/")
        ?.takeIf { it.isNotEmpty() }
}
```

Do not include the notebook name in stored `targetHeadingPath`. The notebook remains stored separately as `targetNotebookName`.

## implementation units

### 1. Add schema/entity support for target heading path

**Objective:** Persist an optional heading path on each capture template.

**Files:**
- Modify: `app/src/main/java/com/orgzly/android/db/entity/CaptureTemplateEntity.kt`
- Modify: `app/src/main/java/com/orgzly/android/db/OrgzlyDatabase.kt`
- Generate/add: `app/schemas/com.orgzly.android.db.OrgzlyDatabase/161.json`

**Steps:**
1. Add `targetHeadingPath` to `CaptureTemplateEntity` after `targetNotebookName`.
2. Bump database version from `160` to `161`.
3. Add `MIGRATION_160_161` with `ALTER TABLE capture_templates ADD COLUMN target_heading_path TEXT`.
4. Register the migration in `.addMigrations(...)` after `MIGRATION_159_160`.
5. Run a schema-exporting build/test task that produces `161.json`.
6. Confirm `git diff --stat` includes the entity, database migration, and schema JSON.

**Verification:**

```bash
cd /home/agent/dev/orgzly
./scripts/build_fdroid.sh assembleFdroidDebug
```

Expected: build succeeds. If schema JSON is not generated automatically, inspect Gradle Room schema configuration before proceeding.

### 2. Add target-place resolution in `CaptureTemplates.kt`

**Objective:** Convert a template's notebook + heading path into a `NotePlace`.

**Files:**
- Modify: `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
- Test: `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`

**Add/modify imports:**

```kotlin
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
```

**Recommended new model:**

```kotlin
data class CaptureTargetResolution(
    val place: NotePlace,
    val resolvedBook: BookView,
    val missingHeadingPath: String? = null,
)
```

**Recommended helpers:**

```kotlin
internal fun normalizeHeadingPath(raw: String?): String? {
    return raw
        ?.split("/")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.joinToString("/")
        ?.takeIf { it.isNotEmpty() }
}
```

```kotlin
@JvmStatic
@Throws(IOException::class)
fun resolveTarget(
    dataRepository: DataRepository,
    context: Context,
    template: CaptureTemplateEntity?,
    explicitBookId: Long?,
): CaptureTargetResolution {
    val targetBook = resolveTargetBook(dataRepository, context, template, explicitBookId)
    val headingPath = normalizeHeadingPath(template?.targetHeadingPath)

    if (headingPath == null) {
        return CaptureTargetResolution(
            place = NotePlace(targetBook.book.id),
            resolvedBook = targetBook,
        )
    }

    val fullPath = "${targetBook.book.name}/$headingPath"
    val targetHeading = dataRepository.getNoteAtPath(fullPath)

    return if (targetHeading != null) {
        CaptureTargetResolution(
            place = NotePlace(targetBook.book.id, targetHeading.note.id, Place.UNDER),
            resolvedBook = targetBook,
        )
    } else {
        CaptureTargetResolution(
            place = NotePlace(targetBook.book.id),
            resolvedBook = targetBook,
            missingHeadingPath = headingPath,
        )
    }
}
```

Keep existing `resolveTargetBook(...)` for compatibility while migrating call sites.

**Tests to add:**

- no heading path -> returns `NotePlace(bookId)` with `Place.UNSPECIFIED`
- heading path exists -> returns `NotePlace(bookId, headingId, Place.UNDER)`
- nested heading path exists -> resolves using full path
- heading path missing -> returns `NotePlace(bookId)` and `missingHeadingPath == saved path`
- target notebook missing -> falls back to default notebook, then tries heading path in fallback notebook

**Verification command:**

Robolectric tests in this repo may require JDK 21 for SDK 36:

```bash
cd /home/agent/dev/orgzly
export JAVA_HOME="$HOME/.local/jdks/temurin-21"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.local/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.capture.CaptureTemplatesTest
```

If this command fails from known repo-wide Robolectric/WorkManager issues, inspect the XML result for this specific class before discarding the signal:

```bash
app/build/test-results/testFdroidDebugUnitTest/TEST-com.orgzly.android.capture.CaptureTemplatesTest.xml
```

### 3. Update in-app capture routing

**Objective:** Make template capture from the book screen use heading-aware `NotePlace`.

**Files:**
- Modify: `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Change:**

Replace notebook-only routing in `openTemplatedNote(...)`:

```kotlin
val targetBook = CaptureTemplates.resolveTargetBook(
    dataRepository,
    requireContext(),
    template,
    mBookId,
)
val payload = CaptureTemplates.buildPayload(requireContext(), template, CaptureInput())

listener?.onTemplatedNoteNewRequest(NotePlace(targetBook.book.id), payload)
```

with heading-aware routing:

```kotlin
val target = CaptureTemplates.resolveTarget(
    dataRepository,
    requireContext(),
    template,
    mBookId,
)
val payload = CaptureTemplates.buildPayload(requireContext(), template, CaptureInput())

if (target.missingHeadingPath != null) {
    activity?.showSnackbar(
        getString(R.string.capture_template_target_heading_missing_using_notebook, target.missingHeadingPath),
    )
}

listener?.onTemplatedNoteNewRequest(target.place, payload)
```

If `showSnackbar(String)` is not available, add/use the nearest existing string-resource variant.

**New string:**

```xml
<string name="capture_template_target_heading_missing_using_notebook">Target heading missing: %1$s. Using notebook root.</string>
```

**Verification:**

Manual QA after build:

1. Create/open `errands.org`.
2. Add top-level heading `Home Depot`.
3. Configure a capture template with target notebook `errands` and target heading `Home Depot`.
4. Start a new note from the book-screen capture-template picker.
5. Save the note.
6. Verify the saved Org structure is:

```org
* Home Depot
** TODO Buy something
```

7. Edit the template heading path to `Missing Heading`.
8. Capture again.
9. Verify snackbar warning appears and the new note is created at notebook root/bottom.

### 4. Update share-flow routing

**Objective:** Shared text/image capture should honor the same optional heading path as in-app capture.

**Files:**
- Modify: `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
- Modify: `app/src/main/res/values/strings.xml` if not already done in Task 3

**Change:**

Replace notebook-only logic in `showNoteEditor(...)`:

```java
long bookId = CaptureTemplates.resolveTargetBook(
        dataRepository,
        this,
        template,
        data.bookId).getBook().getId();

NotePayload payload = CaptureTemplates.buildPayload(
        this,
        template,
        new CaptureInput(data.title, data.content));

NoteFragment noteFragment = NoteFragment.forNewNote(new NotePlace(bookId), payload);
```

with target-place resolution:

```java
CaptureTargetResolution target = CaptureTemplates.resolveTarget(
        dataRepository,
        this,
        template,
        data.bookId);

NotePayload payload = CaptureTemplates.buildPayload(
        this,
        template,
        new CaptureInput(data.title, data.content));

if (target.getMissingHeadingPath() != null) {
    Toast.makeText(
            this,
            getString(R.string.capture_template_target_heading_missing_using_notebook,
                    target.getMissingHeadingPath()),
            Toast.LENGTH_SHORT).show();
}

NoteFragment noteFragment = NoteFragment.forNewNote(target.getPlace(), payload);
```

Import `CaptureTargetResolution` and `Toast` if needed.

**Verification:**

Manual QA:

1. Share text into Orgzly.
2. Pick a template configured with `targetNotebookName = errands` and `targetHeadingPath = Home Depot`.
3. Save the editor.
4. Verify the note is created under `Home Depot`.
5. Repeat with a missing heading path and verify fallback to notebook root plus warning.

### 5. Add target heading field to the template editor

**Objective:** Let users configure the optional heading path per capture template.

**Files:**
- Modify: `app/src/main/res/layout/fragment_capture_template_editor.xml`
- Modify: `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Layout addition:**

Add this below the target notebook input:

```xml
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/target_heading_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:errorEnabled="true"
    app:helperText="@string/capture_template_target_heading_summary">

    <androidx.appcompat.widget.AppCompatAutoCompleteTextView
        android:id="@+id/target_heading"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/capture_template_target_heading"
        android:inputType="none" />
</com.google.android.material.textfield.TextInputLayout>
```

**Strings:**

```xml
<string name="capture_template_target_heading">Target heading</string>
<string name="capture_template_target_heading_summary">Optional. New notes are created under this heading in the target notebook.</string>
<string name="capture_template_target_heading_invalid">Choose an existing heading or leave empty</string>
<string name="capture_template_target_heading_missing_summary">Missing heading: %1$s. Captures will use the notebook root.</string>
```

**Editor behavior:**

- Load saved value with:

```kotlin
binding.targetHeading.setText(template.targetHeadingPath.orEmpty(), false)
```

- Save normalized value:

```kotlin
targetHeadingPath = CaptureTemplates.normalizeHeadingPath(binding.targetHeading.text?.toString())
```

If `normalizeHeadingPath` remains `internal`, either keep editor normalization local or make a small public helper with tests.

- When `targetNotebook` changes:
  - rebuild heading suggestions for the selected notebook
  - if current heading path is non-blank and does not exist in the new notebook, clear it or set an inline error
  - preferred behavior: clear on explicit notebook selection change to avoid stale hidden routing

**Heading suggestions:**

Create a flattened list from the selected notebook's note tree. Suggested implementation:

1. Resolve selected notebook name to `BookView`.
2. Traverse notes using:
   - `dataRepository.getTopLevelNotes(bookId)`
   - `dataRepository.getNoteChildren(noteId)`
3. Produce slash paths:
   - `Home Depot`
   - `Shopping/Home Depot`
4. Set the adapter on `binding.targetHeading`.

Keep this as a simple editor-only helper first. Do not build a reusable tree picker unless autocomplete proves inadequate.

**Validation:**

- Blank heading is valid.
- Non-blank heading must exist in the selected/resolved target notebook.
- If target notebook is Default notebook, validate against the current default target book returned by `dataRepository.getTargetBook(requireContext())`.
- If the heading path is missing when loading an existing template, show inline helper/error text so it is not silently presented as valid.

### 6. Persist heading path in create/edit/duplicate flows

**Objective:** Ensure template CRUD preserves the new target heading field.

**Files:**
- Modify: `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
- Modify: `app/src/main/java/com/orgzly/android/capture/CaptureTemplateSeeder.kt` if constructor calls require the new property explicitly

**Changes:**

In `saveTemplate()`, include:

```kotlin
targetHeadingPath = validatedTargetHeadingPath(),
```

For duplication, preserve the heading path from the source template unless the user changes it.

For new templates, default to null.

For built-in seeded templates, leave null.

**Acceptance:**

- Creating a new custom template with a heading path saves it.
- Editing an existing template preserves its heading path.
- Duplicating a template copies its heading path.
- Deleting templates remains unchanged.

### 7. Add tests for routing and normalization

**Objective:** Lock down edge cases and prevent regressions.

**Files:**
- Modify: `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
- Optional: add a small pure JVM test file if normalization is extracted away from Android/Room dependencies

**Test cases:**

1. `targetHeadingPathBlankUsesNotebookRoot`
2. `targetHeadingPathRoutesUnderMatchingTopLevelHeading`
3. `nestedTargetHeadingPathRoutesUnderMatchingDescendant`
4. `missingTargetHeadingFallsBackToNotebookRootAndReportsMissingPath`
5. `missingTargetNotebookFallsBackToDefaultNotebookBeforeResolvingHeading`
6. `headingPathNormalizationTrimsEmptySegments`

**Example assertions:**

```kotlin
assertThat(resolution.place.bookId, `is`(errands.book.id))
assertThat(resolution.place.noteId, `is`(homeDepot.id))
assertThat(resolution.place.place, `is`(Place.UNDER))
assertThat(resolution.missingHeadingPath, `is`(nullValue()))
```

For missing heading:

```kotlin
assertThat(resolution.place.bookId, `is`(errands.book.id))
assertThat(resolution.place.noteId, `is`(0L))
assertThat(resolution.place.place, `is`(Place.UNSPECIFIED))
assertThat(resolution.missingHeadingPath, `is`("Missing Heading"))
```

### 8. Update changelog and manual QA checklist

**Objective:** Document the user-facing behavior and verify it on-device.

**Files:**
- Modify: `CHANGELOG.md`
- Optional modify/add: `docs/plans/2026_07_23_capture_template_heading_routing_plan.md` with implementation notes if deviations occur

**Changelog entry:**

Add an unreleased bullet similar to:

```markdown
- Added optional target headings for capture templates, allowing templates to create new notes/tasks under a specific heading within the target notebook.
```

**Manual QA checklist:**

- Existing template with no target heading still creates notes at notebook root/bottom.
- Template with target notebook + top-level target heading creates notes under that heading.
- Template with nested target heading path creates notes under the nested heading.
- Missing target notebook falls back to default notebook.
- Missing target heading falls back to notebook root and warns the user.
- Changing the target notebook in the template editor clears or invalidates the heading path.
- Renaming a heading causes the template editor to surface the saved heading path as missing.
- Share flow and in-app new-note flow behave consistently.
- Blank note option still behaves exactly as before.

## verification plan

Primary build verification:

```bash
cd /home/agent/dev/orgzly
./scripts/build_fdroid.sh assembleFdroidDebug
```

Optional premium compile check:

```bash
cd /home/agent/dev/orgzly
./scripts/build_fdroid.sh assemblePremiumDebug
```

Targeted test verification, using JDK 21 for Robolectric SDK 36:

```bash
cd /home/agent/dev/orgzly
export JAVA_HOME="$HOME/.local/jdks/temurin-21"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.local/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.capture.CaptureTemplatesTest
```

Known caveat: this repo has noisy Robolectric/unit-test failures unrelated to feature code. If the broad test command fails, inspect the specific XML result for `CaptureTemplatesTest` and treat a clean APK build as the stronger baseline.

## implementation risks

- `DataRepository.getNoteAtPath(...)` resolves by title/ancestor path. It is good enough for Option A, but duplicate identical full paths remain inherently ambiguous. The UI should show full paths to reduce accidental ambiguity.
- Heading rename naturally breaks path-based routing. This is accepted for V1, but the template editor must show the missing path instead of silently pretending the target is valid.
- Share flow is Java and capture logic is Kotlin; any Kotlin result type exposed to Java should use Java-friendly property getters.
- Room schema export must be checked explicitly. A migration without `161.json` may compile locally but fail schema checks or future review.
- Do not use body templates to fake child headings; the new note itself must be placed under the configured heading via `NotePlace(..., Place.UNDER)`.

## out of scope

- creating missing target headings automatically
- storing target heading note ids
- arbitrary org-capture scripting
- selecting above/below placement
- custom launcher shortcuts per capture template/heading
- bulk migration of existing templates to heading targets

## acceptance criteria

- A capture template can optionally store `targetHeadingPath`.
- When no heading path is stored, all current capture behavior is preserved.
- When a valid heading path is stored, in-app capture creates the new note/task under that heading.
- Share capture uses the same heading-routing behavior.
- Missing notebooks fall back to default notebook.
- Missing headings fall back to notebook root and warn the user where practical.
- Template editor validates/surfaces stale heading paths.
- Full heading paths are used for duplicate heading names.
- `assembleFdroidDebug` succeeds after implementation.
