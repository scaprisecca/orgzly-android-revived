# note editor keyboard viewport plan

created: 2026-05-20
status: active
source_doc: none

## objective
- fix note body editing so the active cursor/current line remains visible while typing past the visible viewport with the soft keyboard open
- prevent note content from being obscured by either the custom bottom editor toolbar or the Android keyboard
- keep the fix narrow to note editor viewport/keyboard behavior, without redesigning the editor toolbar or RichText document model

## scope
- included:
  - content body `RichTextEdit` cursor visibility while typing normal text and new lines
  - bottom editor toolbar overlap handling in the note editor
  - keyboard/IME overlap handling for `NoteFragment` hosted by `MainActivity`
  - title/content regression checks so the existing editor toolbar remains usable
  - manual device/emulator QA because soft-keyboard behavior is hard to prove with local JVM tests
- excluded:
  - new editor toolbar commands or toolbar visual redesign
  - replacing the current `NestedScrollView` + `RichText` architecture
  - broad edge-to-edge/system-window refactor across unrelated fragments
  - full Android instrumentation test coverage unless a lightweight reliable path already exists

## relevant_existing_patterns
- `README.org`
  - documents preferred local build helpers: `./scripts/build_fdroid.sh` and `./scripts/build_fdroid_dev.sh`; no `AGENTS.md` or `CLAUDE.md` was found in the repo root during planning
- `docs/plans/2026_05_02_editor_toolbar_plan.md`
  - prior toolbar plan already called out layout overlap with keyboard/nested scroll content as a risk; the toolbar has since landed and this plan addresses the remaining viewport regression
- `docs/brainstorms/2026_05_01_editor_toolbar_requirements.md`
  - establishes the mobile editor toolbar goal: stay in keyboard flow without obscuring underlying org text
- `app/src/main/res/layout/fragment_note.xml`
  - note screen is a `CoordinatorLayout` with a `ViewFlipper`, a `NestedScrollView` containing title/metadata/content, a bottom `editor_toolbar_container`, and a top app bar
  - content body is `com.orgzly.android.ui.views.richtext.RichText` with `app:edit_id="@+id/content_edit"`, `android:layout_height="wrap_content"`, and multiline input
  - the bottom editor toolbar is overlaid via `android:layout_gravity="bottom"`, so scroll padding must explicitly account for it
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - owns editor toolbar visibility and currently adds bottom padding to `binding.scrollView` when an editor is active
  - current padding logic accounts for `fragment_note_editor_toolbar_height`, but it does not continuously keep the cursor visible while the user types downward
- `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - currently has `scrollForBetterCursorPosition(charOffset)`, but it is only called when entering edit mode/opening the keyboard or when `requestFocusAndOpenKeyboard()` runs after toolbar-driven edits
  - there is no observed `onSelectionChanged`, text-change, or layout-change path that keeps the caret visible during normal typing
- `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
  - wraps view/edit mode transitions and delegates text application to `RichTextEdit`; focus loss behavior is sensitive because toolbar taps can temporarily move focus
- `app/src/main/java/com/orgzly/android/ui/util/KeyboardUtils.kt`
  - opens the soft keyboard and accepts an `onShow` callback, but the callback is tied to initial keyboard open/global layout rather than ongoing cursor movement
- `app/src/main/AndroidManifest.xml`
  - `MainActivity` currently uses `android:windowSoftInputMode="stateAlwaysHidden"`, while several repo configuration activities use `stateAlwaysHidden|adjustResize`
  - the lack of explicit resize behavior is a plausible contributor to keyboard overlap or incorrect visible-height calculations
- `app/src/main/java/com/orgzly/android/ui/util/ActivityUtils.kt`
  - contains existing bottom-toolbar system-window handling for other screens via `setDecorFitsSystemWindowsForBottomToolbar(...)`; note editor should avoid broad reuse unless it proves correct for keyboard/editor behavior
- `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
  - existing toolbar command tests are useful regression coverage for toolbar actions, but viewport/IME behavior will primarily need manual QA

## implementation_units

### 1. replace one-shot cursor scroll with reusable cursor visibility behavior
- files:
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
- changes:
  - rename or supplement `scrollForBetterCursorPosition(charOffset)` with a clearer method such as `ensureCursorVisible(charOffset = currentSelectionStart())`
  - make the method safe to call repeatedly while editing:
    - return early if the view is not focused, not visible, not laid out, or has no layout
    - post/coalesce scroll work so the current line layout is up to date after text insertion
    - calculate the cursor line Y using the edit view layout and convert it into the ancestor `NestedScrollView` coordinate space
    - avoid unnecessary scrolling if the cursor is already comfortably visible
  - prefer immediate or short smooth scrolling that follows typing without causing jumpy feedback
- dependencies:
  - existing `NestedScrollView` ancestor lookup
- risks:
  - the current calculation uses `richText.top + baseline/ascent`, which may be too simple if nested offsets, padding, metadata folding, or view translations are involved
  - too-aggressive smooth scrolling could feel jumpy while typing fast
- tests:
  - no reliable pure JVM test expected for actual viewport movement
  - if practical, extract coordinate/clamp math into a tiny helper with JVM coverage; otherwise document as manual QA
- acceptance:
  - repeatedly typing new lines in the content editor keeps the caret/current line above the bottom obstruction without manual scrolling

### 2. trigger cursor visibility after normal typing and cursor movement
- files:
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - possibly `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEditWatcher.kt` only if text watcher integration is cleaner than overriding selection changes
- changes:
  - call `ensureCursorVisible()` from `onSelectionChanged(selStart, selEnd)` when the edit view has focus
  - consider an additional call after text changes/newline insertion if `onSelectionChanged` does not fire consistently across keyboards
  - coalesce repeated calls using `post {}` plus a pending flag so every keystroke does not schedule multiple scroll operations
  - preserve existing list-continuation behavior in `RichTextEditWatcher`; do not fold viewport behavior into list syntax logic unless necessary
- dependencies:
  - unit 1 reusable cursor visibility method
- risks:
  - `onSelectionChanged` can fire during programmatic `setSelection(...)` in toolbar actions and timestamp insertion; the new code must not fight existing explicit cursor placement
  - Android keyboards vary in whether composition text changes move selection before/after layout updates
- tests:
  - existing editor toolbar tests should still pass if run
  - manual QA with Gboard or default emulator keyboard, including fast newline entry and mid-note cursor tapping
- acceptance:
  - cursor remains visible while the user types beyond the viewport, not only when edit mode first starts

### 3. make note editor bottom padding account for toolbar and IME without double-counting
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/res/layout/fragment_note.xml` only if layout ids/attributes need minor support
- changes:
  - centralize scroll bottom padding calculation in `NoteFragment`, e.g. `updateScrollBottomPadding()`
  - include editor toolbar height when `editorToolbarContainer` is visible
  - inspect and handle IME bottom inset using `WindowInsetsCompat.Type.ime()` if the main activity does not reliably resize around the keyboard
  - avoid double-counting keyboard space if `adjustResize` already shrinks the scroll view
  - after padding changes, request `ensureCursorVisible()` on the active editor rather than only scrolling by the padding delta when the view was already at bottom
- dependencies:
  - unit 1 should expose a public `RichText`/`RichTextEdit` path for requesting cursor visibility from `NoteFragment` if needed
- risks:
  - IME inset values and resize behavior vary by API level, navigation mode, and edge-to-edge settings
  - `CoordinatorLayoutFitsSystemWindows` and `AppBarLayoutStyle` already apply system window behavior; stacking new inset padding incorrectly can create too much blank space
- tests:
  - manual QA on portrait phone/emulator with gesture navigation and/or 3-button navigation if available
  - verify keyboard closed state does not leave persistent empty space below the note
- acceptance:
  - bottom of editable content can scroll above the editor toolbar when keyboard is closed
  - bottom of editable content can scroll above both toolbar and keyboard when keyboard is open

### 4. evaluate `MainActivity` soft-input mode as a targeted spike, not a blind global fix
- files:
  - `app/src/main/AndroidManifest.xml`
  - possibly `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- changes:
  - locally test whether changing `MainActivity` from `stateAlwaysHidden` to `stateAlwaysHidden|adjustResize` fixes the keyboard-covered viewport by itself
  - if it works and does not regress core hosted fragments, decide whether to keep it as the simpler fix
  - if it causes broader layout side effects, revert the manifest spike and handle IME insets inside `NoteFragment`
  - document the decision in the commit/PR notes because this affects all `MainActivity` fragments if kept
- dependencies:
  - manual testing across at least note editor, book list, search/agenda list, and saved search editing if the manifest change is retained
- risks:
  - global `adjustResize` can alter layout behavior outside the note editor
  - relying only on `adjustResize` may still not account for the overlaid custom editor toolbar
- tests:
  - build and manual app smoke test
- acceptance:
  - either a safe manifest change is retained with evidence, or the plan explicitly avoids it in favor of fragment-level inset handling

### 5. expose a narrow RichText cursor-visibility API if NoteFragment needs it
- files:
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- changes:
  - add a small method such as `RichText.ensureCursorVisible()` that delegates to the active edit view only when edit mode is active
  - use it from `NoteFragment` after toolbar visibility/padding/inset updates
  - keep the API narrowly named and editor-specific; do not expose generic scroll internals through `RichText`
- dependencies:
  - units 1 and 3
- risks:
  - widening `RichText` API can become a dumping ground for fragment-specific layout behavior if not kept tight
- tests:
  - compile/build validation
- acceptance:
  - `NoteFragment` can ask the active editor to reveal its caret without duplicating coordinate math

### 6. regression guardrails for toolbar actions and metadata/property editing
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
  - `CHANGELOG.md` if user-visible behavior changes ship
- changes:
  - ensure toolbar actions that call `applyEdit(...)` still restore focus and cursor visibility correctly
  - ensure property value editing still shows the toolbar timestamp behavior introduced by prior work and does not crash or steal focus
  - add a short changelog entry if the branch is intended for user-facing release notes
- dependencies:
  - final implementation of units 1 through 5
- risks:
  - property value editing uses plain `EditText`, not `RichTextEdit`; do not accidentally route content-only cursor behavior to metadata fields
  - toolbar focus preservation was previously fragile; viewport fixes should not regress it
- tests:
  - existing `EditorToolbarActionsTest` if relevant
  - manual title/content/property-value editing checks
- acceptance:
  - toolbar buttons still work, focus stays in the editor, and cursor remains visible after command execution

## sequencing
1. reproduce and document the failure on device/emulator using a long note body with keyboard open and editor toolbar visible
2. implement unit 1: reusable `ensureCursorVisible()` in `RichTextEdit`
3. implement unit 2: call cursor visibility after selection/text changes with coalescing
4. test the behavior manually before touching manifest/insets; if this alone fixes toolbar overlap but not keyboard overlap, continue
5. spike unit 4: try `MainActivity` `adjustResize`; keep only if it fixes keyboard overlap without broader regressions
6. if `adjustResize` is not safe/sufficient, implement unit 3 with note-fragment IME inset-aware bottom padding
7. add the narrow `RichText.ensureCursorVisible()` bridge from unit 5 only if `NoteFragment` needs to request caret reveal after padding/inset changes
8. run build verification and toolbar regression checks
9. update `CHANGELOG.md` if the branch is going to be committed for user-facing release notes

## risks_and_unknowns
- risk: cursor coordinate math may be wrong when `RichTextEdit` is nested inside `RichText`, inside a `LinearLayout`, inside `NestedScrollView`
  - mitigation: prefer coordinate conversion relative to the scroll view rather than assuming `richText.top` is enough; manually test mid-note and bottom-note cursor positions
- risk: IME inset handling and `adjustResize` can double-count bottom space
  - mitigation: treat manifest `adjustResize` as a spike; choose one keyboard-space strategy and verify keyboard-open/closed states
- risk: global `MainActivity` soft-input changes may affect unrelated fragments
  - mitigation: only retain the manifest change after smoke-testing common hosted screens; otherwise keep the fix local to `NoteFragment`
- risk: repeated smooth scrolls while typing could feel jumpy
  - mitigation: coalesce posted scroll requests and only scroll when the cursor is below a comfortable bottom threshold or above the visible top
- risk: Android keyboard behavior differs across devices
  - mitigation: manual QA on Scott's phone is the decisive validation step after local emulator testing

## validation_strategy
- static/code review:
  - confirm no unrelated layout refactor was introduced
  - confirm `RichTextEditWatcher` list-continuation behavior is unchanged
  - confirm title/content/property editor paths are not conflated
- build:
  - `./scripts/build_fdroid.sh`
  - if needed for side-by-side install: `./scripts/build_fdroid_dev.sh`
- optional tests:
  - `./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.note.EditorToolbarActionsTest`
  - note: this repo has known broader test-environment caveats; a successful debug build plus manual QA is the strongest signal for this viewport bug
- manual QA:
  - open an existing long note and tap into content near the lower half
  - with keyboard open, type lines until the cursor would normally pass behind the toolbar/keyboard
  - verify the scroll view advances automatically and the active line remains visible
  - repeat with metadata folded and unfolded
  - repeat after using toolbar actions: bullet, checkbox, indent/de-indent, timestamp, More action
  - verify title editing still shows/hides toolbar correctly
  - verify property value timestamp editing remains functional
  - rotate or test landscape only if current app behavior is intended to support it for editing; portrait is the primary acceptance path

## handoff_notes
- Root-cause hypothesis: the old fix only added bottom padding and performed one-time scroll when the toolbar appeared or keyboard opened; it did not continuously keep the caret visible while typing.
- Do not start by rewriting layout architecture. First make caret visibility reliable and measure whether keyboard resize/insets are still a separate problem.
- Be careful with `MainActivity` manifest changes because they affect many fragments. Prefer a local `NoteFragment` inset fix if global `adjustResize` creates side effects.
- Keep the implementation branch focused. No new toolbar commands, no rich-text model changes, and no metadata UX cleanup unless directly required by the viewport fix.
