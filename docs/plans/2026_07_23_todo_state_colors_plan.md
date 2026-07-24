# TODO state colors implementation plan

created: 2026-07-23
status: active
source_doc: docs/brainstorms/2026_07_23_todo_state_colors_requirements.md

## objective

- Add configurable per-state colors for Orgzly TODO/DONE workflow keywords.
- Render the configured state color anywhere the state keyword itself is displayed in V1:
  - notebook/search/agenda note rows through the shared title renderer
  - list widgets through the existing widget title renderer
  - note editor/details state button
- Keep existing TODO/DONE theme/widget colors as fallback defaults when no per-state override exists.
- Store colors in SharedPreferences only; do not change note parsing, workflow syntax, state storage, or Room schema.

## scope

### included

- Dynamic `Settings -> Look and feel -> TODO state colors` screen.
- One color picker row per currently configured workflow keyword, ordered by configured TODO states then DONE states.
- Stable generated SharedPreferences key per state keyword.
- Preservation of color preferences for removed workflow states.
- App-list and widget title keyword coloring via shared title-generation infrastructure.
- Note editor/details state button text coloring and reset behavior for `NOTE`/no-state.
- Widget refresh after a dynamic state-color preference changes.
- Focused JVM tests for key encoding/resolution where practical, plus build verification.
- `CHANGELOG.md` entry.

### excluded

- No database migration.
- No pruning/deleting stored colors for states removed from the workflow.
- No separate per-widget color map in V1.
- No automatic accessibility/contrast correction beyond current fallback defaults and user choice.
- No full-title coloring; only the state keyword label/button text is colored.
- No required reset-to-default UI in the first implementation unit unless it falls out cheaply from the color picker design.

## relevant_existing_patterns

- `README.org`
  - documents local helper build scripts; use `./scripts/build_fdroid.sh assembleFdroidDebug` as the reliable baseline verification path.
- `AGENTS.md` / `CLAUDE.md`
  - absent during planning inspection; no repo-local agent instructions found.
- `docs/plans/2026_07_23_capture_template_heading_routing_plan.md`
  - current planning style: file-level implementation units, explicit verification commands, and current-code-context notes.
- `docs/brainstorms/2026_07_23_todo_state_colors_requirements.md`
  - upstream requirements and current-code findings; treat as source of product behavior.
- `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - stores workflow state preference in default SharedPreferences.
  - `states(context)` reads `pref_key_states` with default `TODO NEXT | DONE`.
  - `states(context, value)` writes the workflow and calls `updateStaticKeywords(context)`.
  - `todoKeywordsSet(context)` / `doneKeywordsSet(context)` cache ordered `LinkedHashSet`s parsed from `StateWorkflows`.
  - good home for Java-callable static helpers if the feature needs to be consumed from Java and Kotlin.
- `app/src/main/java/com/orgzly/android/ui/NoteStates.kt`
  - `NoteStates.fromPreferences(context)` already combines TODO keywords then DONE keywords in workflow order.
  - use this for dynamic settings row generation.
- `app/src/main/java/com/orgzly/android/ui/util/TitleGenerator.kt`
  - single shared title-rendering path for note rows and widgets.
  - `generateState(note)` currently chooses only `attributes.colorDone` vs `attributes.colorTodo` using `AppPreferences.doneKeywordsSet(context)`.
  - this is the best central place to replace two-bucket coloring with state-specific resolution.
- `app/src/main/java/com/orgzly/android/ui/notes/NoteItemViewBinder.kt`
  - builds `TitleGenerator.TitleAttributes` from themed attrs `item_head_state_todo_color`, `item_head_state_done_color`, post-title size, and tertiary text color.
  - preserve these attrs as app-list fallback defaults.
  - existing done/archived alpha logic is separate in `setupAlpha(...)`; do not change it.
- `app/src/main/java/com/orgzly/android/widgets/WidgetStyle.kt`
  - builds widget-specific `TitleGenerator.TitleAttributes` using scheme-aware fallback state colors from `WidgetColors`.
  - preserve scheme-aware fallbacks for unset state colors.
- `app/src/main/java/com/orgzly/android/widgets/ListWidgetService.kt`
  - widget rows already call `TitleGenerator(context, false, WidgetStyle.getTitleAttributes(context))` and pass the spanned title to `RemoteViews.setTextViewText(...)`.
  - no separate widget title renderer should be introduced unless a compile constraint appears.
- `app/src/main/java/com/orgzly/android/widgets/ListWidgetProvider.java`
  - `notifyDataSetChanged(context)` broadcasts `ACTION_UPDATE_LIST_WIDGET`.
  - `update(context)` broadcasts `ACTION_UPDATE_LAYOUT_LIST_WIDGET`.
  - state-color changes likely need at least data-set refresh; full layout update is acceptable if the settings code mirrors other widget style preferences.
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `setStateView(state)` currently only sets `binding.stateButton.text` and remove-button visibility.
  - add color application here, and reset to the original/default text color when state is null or `NOTE`.
- `app/src/main/java/com/orgzly/android/prefs/ColorPickerPreference.kt`
  - persists selected color as a hex string and displays the hex as summary.
  - currently reads XML `android:defaultValue`; dynamic preferences need either a programmatic default path or a safe way to initialize the default value.
- `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - maps preference resource keys in `PREFS_RESOURCES`.
  - routes `ColorPickerPreference` dialogs in `onDisplayPreferenceDialog(...)`.
  - handles state workflow changes in `onSharedPreferenceChanged(...)` and always calls `ListWidgetProvider.notifyDataSetChanged(...)` after preference changes.
  - fixed widget-style keys currently broadcast `ACTION_UPDATE_LAYOUT_LIST_WIDGET`; dynamic state-color keys need prefix detection.
- `app/src/main/res/xml/prefs_screen_look_and_feel.xml`
  - existing Look and feel screen and color category; add an entry point here.
- `app/src/main/res/values/prefs_keys.xml`
  - contains `pref_key_states` and related preference keys; add stable keys/prefix constants here if needed.
- `app/src/main/res/values/strings.xml`
  - add user-facing labels/summaries for the screen.
- `app/src/test/java/com/orgzly/android/ui/util/CommaSeparatedAutocompleteTest.kt`
  - good pattern for pure Kotlin utility tests that avoid Android UI/runtime dependencies.

## architecture direction

Use a dedicated resolver rather than spreading state-color lookup across UI callers.

Recommended shape:

```kotlin
class StateColorResolver(
    private val context: Context,
    @ColorInt private val todoFallback: Int,
    @ColorInt private val doneFallback: Int,
) {
    @ColorInt
    fun colorForState(state: String?): Int
}
```

Resolution order:

1. If `state` is null/blank/`NOTE`, caller should either not color it or use the default view text color.
2. Build the stable SharedPreferences key for the exact state keyword.
3. If a valid persisted hex color exists, return it.
4. Else if `AppPreferences.doneKeywordsSet(context).contains(state)`, return the DONE fallback.
5. Else return the TODO fallback.

Keep `TitleGenerator` centralized by changing `TitleAttributes` to carry a color resolver or a state-color function instead of two prebuilt `ForegroundColorSpan` instances. Prefer storing fallback integers and creating a fresh `ForegroundColorSpan` per generated state so spans are not reused across multiple `SpannableString`s.

Suggested `TitleAttributes` direction:

```kotlin
class TitleAttributes(
    @ColorInt private val colorTodo: Int,
    @ColorInt private val colorDone: Int,
    postTitleTextSize: Int,
    postTitleTextColor: Int,
    private val stateColorResolver: StateColorResolver? = null,
) {
    fun colorForState(state: String): ForegroundColorSpan =
        ForegroundColorSpan(stateColorResolver?.colorForState(state) ?: fallbackColorForState(state))
}
```

A cleaner variant is to make the resolver non-null and construct it at each call site with app or widget fallback colors. Use whichever produces the smallest compile-safe change.

## preference key and storage design

Store custom state colors in default SharedPreferences, matching `ColorPickerPreference`.

Recommended key prefix:

```text
pref_key_state_color_
```

Recommended helper names:

- `stateColorPreferenceKey(state: String): String`
- `isStateColorPreferenceKey(key: String?): Boolean`
- `stateColorHex(context: Context, state: String): String?`
- `stateColor(context: Context, state: String, fallbackColor: Int): Int`

Encoding requirements:

- Deterministic for any UTF-8 state keyword.
- No collisions for punctuation or whitespace.
- Human readability is secondary to safety.

Recommended encoding: lowercase hex of UTF-8 bytes.

Reasons:

- URL-safe Base64 is fine, but hex avoids padding/symbol edge cases in preference keys.
- State keywords are usually short, so key length is not a practical concern.
- Hex encoding/decoding can be pure-JVM tested without Android runtime.

Example:

```text
TODO -> pref_key_state_color_544f444f
WAITING -> pref_key_state_color_57414954494e47
BLOCKED/EXT -> pref_key_state_color_424c4f434b45442f455854
```

Do not delete these keys when the workflow preference changes.

## implementation_units

### 1. Add state color preference/key helpers

- files:
  - modify: `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - or add: `app/src/main/java/com/orgzly/android/prefs/StateColorPreferences.kt`
  - test: `app/src/test/java/com/orgzly/android/prefs/StateColorPreferencesTest.kt`
- changes:
  - Add a stable prefix constant for generated state-color preference keys.
  - Add deterministic UTF-8 hex key encoding.
  - Add helpers for key construction, dynamic-key detection, reading stored hex values, parsing stored colors, and fallback handling.
  - Keep stored values as `#RRGGBB` strings, consistent with `ColorPickerPreference`.
  - On invalid stored hex, ignore it and use fallback instead of crashing.
- dependencies:
  - none beyond default SharedPreferences and Android color parsing.
- risks:
  - If helpers are in Kotlin, Java callers may need `@JvmStatic` access or should avoid direct Java usage.
  - Direct `android.graphics.Color` calls in pure JVM tests can hit Android-stub runtime issues; keep encoding tests pure and test color parsing through Robolectric only if necessary.
- tests:
  - `app/src/test/java/com/orgzly/android/prefs/StateColorPreferencesTest.kt`
- acceptance:
  - `stateColorPreferenceKey("TODO")` is stable.
  - states containing punctuation produce unique safe keys.
  - `isStateColorPreferenceKey(...)` recognizes the prefix and rejects unrelated keys.
  - removed states are not pruned because no workflow-change cleanup exists.

### 2. Introduce shared `StateColorResolver`

- files:
  - add: `app/src/main/java/com/orgzly/android/ui/util/StateColorResolver.kt`
  - test: `app/src/test/java/com/orgzly/android/ui/util/StateColorResolverTest.kt` if implementation can stay Robolectric-safe; otherwise cover only pure helper behavior in unit 1 and verify via build/manual QA.
- changes:
  - Add resolver constructed with context plus TODO/DONE fallback colors.
  - Resolve custom hex first, then configured DONE fallback, otherwise TODO fallback.
  - Use `AppPreferences.doneKeywordsSet(context)` or existing `AppPreferences.isDoneKeyword(context, state)` rather than hard-coding `DONE`.
- dependencies:
  - unit 1 key/storage helpers.
- risks:
  - If workflow preferences change during a process, `AppPreferences.updateStaticKeywords(context)` must already be called by settings; current settings code does this for `pref_key_states`.
  - Treat unknown active state strings as TODO fallback to match current `TitleGenerator` behavior.
- tests:
  - optional targeted resolver tests if feasible under current test setup.
- acceptance:
  - custom color overrides fallback for TODO and DONE states.
  - unset done states use DONE fallback.
  - unset todo/unknown active states use TODO fallback.

### 3. Update title rendering for app rows and widgets

- files:
  - modify: `app/src/main/java/com/orgzly/android/ui/util/TitleGenerator.kt`
  - modify: `app/src/main/java/com/orgzly/android/ui/notes/NoteItemViewBinder.kt`
  - modify: `app/src/main/java/com/orgzly/android/widgets/WidgetStyle.kt`
  - verify only if needed: `app/src/main/java/com/orgzly/android/widgets/ListWidgetService.kt`
- changes:
  - Replace `generateState(note)` two-bucket color selection with `attributes.colorForState(note.state)` or a resolver call.
  - Preserve bolding of state/priority prefix.
  - Preserve post-title span behavior.
  - Preserve `NoteItemViewBinder.setupAlpha(...)` done/archived alpha behavior unchanged.
  - Construct app-list resolver with `Attrs.todoColor` and `Attrs.doneColor` from theme attrs.
  - Construct widget resolver with scheme-aware fallback colors from `WidgetStyle.getTitleAttributes(context)` / `WidgetColors`.
- dependencies:
  - unit 2 resolver.
- risks:
  - Reusing one `ForegroundColorSpan` instance across many generated titles can cause span bugs; create new spans per state string.
  - Changing `TitleAttributes` constructor affects both app-list and widget call sites; update together.
  - Widget `RemoteViews` supports `CharSequence` spans in the current path; do not replace with plain strings.
- tests:
  - compile via `./scripts/build_fdroid.sh assembleFdroidDebug`.
  - if a Robolectric test is practical, add `TitleGeneratorTest` for TODO/DONE/custom state span color extraction.
- acceptance:
  - Existing users with no custom colors see the same app-list and widget TODO/DONE colors as before.
  - `WAITING` with a custom color renders that color in notebook/search/agenda rows and widget rows.

### 4. Add dynamic settings screen under Look and feel

- files:
  - modify: `app/src/main/res/xml/prefs_screen_look_and_feel.xml`
  - optionally add: `app/src/main/res/xml/prefs_screen_state_colors.xml` if using an XML-backed sub-screen placeholder
  - modify: `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - modify: `app/src/main/res/values/prefs_keys.xml`
  - modify: `app/src/main/res/values/strings.xml`
  - maybe modify: `app/src/main/java/com/orgzly/android/prefs/ColorPickerPreference.kt`
- changes:
  - Add `TODO state colors` entry under the Look and feel color category.
  - Add a sub-screen key such as `prefs_screen_state_colors` and map it in `SettingsFragment.PREFS_RESOURCES`, or detect that key and build a programmatic `PreferenceScreen`.
  - When the state-color screen opens, clear/populate one `ColorPickerPreference` per `NoteStates.fromPreferences(context)` entry.
  - Row title is the exact state keyword.
  - Row key is `stateColorPreferenceKey(state)`.
  - Row default/fallback is TODO or DONE fallback for the current app theme, not a hard-coded red.
  - Row summary/swatch should show the persisted color if present, otherwise fallback default color.
  - If `ColorPickerPreference` cannot accept programmatic defaults today, extend it with a safe API such as `setDefaultColorHexForDynamicPreference(hex)` or constructor-time fallback handling before row binding.
- dependencies:
  - unit 1 key helpers.
  - unit 2 resolver or a settings-only fallback helper.
- risks:
  - `ColorPickerPreference` currently reads XML `android:defaultValue`; dynamic rows created without attrs may default to red unless enhanced.
  - Settings screen title/navigation should continue to work with `SettingsActivity.pushFragment(...)` / `listener?.onPreferenceScreen(key)`.
  - State workflow changes while already on the color screen may leave stale rows until reopen; acceptable for V1 if documented, but better to repopulate when `pref_key_states` changes and the active screen is the state-color screen.
- tests:
  - build verification.
  - manual QA for workflow `TODO NEXT WAITING | DONE CANCELLED`.
- acceptance:
  - Opening the screen shows one row each for `TODO`, `NEXT`, `WAITING`, `DONE`, `CANCELLED` in that order.
  - Adding `DELEGATED` to the workflow makes it appear next time the screen is opened.
  - Removing `WAITING` hides the row but does not delete its stored key.
  - Re-adding `WAITING` restores its previous selected color.

### 5. Refresh widgets on state-color preference changes

- files:
  - modify: `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - optionally use: `app/src/main/java/com/orgzly/android/widgets/ListWidgetProvider.java`
- changes:
  - In `onSharedPreferenceChanged(...)`, detect `StateColorPreferences.isStateColorPreferenceKey(key)` before or after the existing `when` block.
  - Trigger widget refresh for dynamic state-color keys:
    - at minimum `ListWidgetProvider.notifyDataSetChanged(requireContext())`
    - optionally `ListWidgetProvider.update(requireContext())` / `ACTION_UPDATE_LAYOUT_LIST_WIDGET` to mirror fixed widget style preferences.
  - Avoid adding individual dynamic keys to XML/string resources.
- dependencies:
  - unit 1 key-prefix helper.
- risks:
  - Current settings code already calls `ListWidgetProvider.notifyDataSetChanged(requireContext())` after any preference change, so data refresh is mostly covered; explicit prefix handling is still useful for layout/style parity and future-proofing.
  - Excessive full layout updates are acceptable for a settings color change but should not run in a tight loop.
- tests:
  - compile verification.
  - manual widget QA.
- acceptance:
  - After changing `WAITING` color from the settings screen, existing list widgets update without recreating the widget.

### 6. Color the note editor/details state button

- files:
  - modify: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- changes:
  - Capture the state button's default text color once, for example as a fragment property initialized after binding.
  - In `setStateView(state)`, when state is null or `NoteStates.NO_STATE_KEYWORD`, clear text and reset the state button text color to the default.
  - When state is a real keyword, set text and apply `StateColorResolver` using app/theme TODO/DONE fallback colors.
  - Prefer a tiny helper to obtain the same theme fallback attrs as `NoteItemViewBinder` if feasible; otherwise duplicate the same styled-attribute lookup locally with clear naming.
- dependencies:
  - unit 2 resolver.
- risks:
  - Fragment view binding is recreated; do not hold a view color reference across destroyed views unless it is reset in `onDestroyView`.
  - State button may be hidden/empty for `NOTE`; stale text color must not leak to later notes.
- tests:
  - manual QA in note editor.
  - build verification.
- acceptance:
  - A `WAITING` note shows the configured `WAITING` color on the state button.
  - Opening a note with no state/`NOTE` resets to normal/default text color and does not retain the previous state's color.

### 7. Changelog and verification

- files:
  - modify: `CHANGELOG.md`
- changes:
  - Add an Unreleased/Added entry such as:
    - `Added configurable colors for individual TODO/DONE workflow states in note lists, widgets, and the note editor state button.`
- dependencies:
  - after behavior is implemented.
- risks:
  - Avoid claiming reset/default actions if not included.
- tests:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
  - optional targeted unit tests:
    - `./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.prefs.StateColorPreferencesTest`
    - `./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.util.TitleGeneratorTest`
  - If Robolectric failures appear with `Android SDK 36 requires Java 21`, retry targeted tests with Temurin 21 per repo workflow notes, but treat `assembleFdroidDebug` as the primary compile/build gate.
- acceptance:
  - Build succeeds.
  - New/changed tests pass or failures are clearly separated as unrelated repo-wide test-environment breakage.

## sequencing

1. Implement key encoding/storage helpers and pure tests first.
2. Add `StateColorResolver` with TODO/DONE fallback behavior.
3. Update `TitleGenerator.TitleAttributes` and both existing call sites (`NoteItemViewBinder`, `WidgetStyle`) in one commit-sized unit so the project stays compileable.
4. Add the dynamic settings screen and fix `ColorPickerPreference` dynamic-default behavior if needed.
5. Add state-color prefix handling for widget refresh.
6. Add note editor/details state button coloring with reset behavior.
7. Add `CHANGELOG.md` entry.
8. Run targeted tests where feasible.
9. Run `./scripts/build_fdroid.sh assembleFdroidDebug`.
10. Manual QA on device/emulator, especially widgets.

## risks_and_unknowns

- risk: Dynamic `ColorPickerPreference` rows may default to red because the class currently expects XML `android:defaultValue`.
  - mitigation: extend the preference with an explicit programmatic default setter before building the dynamic screen.
- risk: `TitleAttributes` constructor/API changes can break both app rows and widgets.
  - mitigation: update `TitleGenerator`, `NoteItemViewBinder`, and `WidgetStyle` together and use a compile build immediately after unit 3.
- risk: Widget colors are scheme-aware but custom state colors are absolute.
  - mitigation: document V1 behavior and preserve scheme-aware fallbacks only for unset states.
- risk: Invalid old/stale color strings could crash parsing.
  - mitigation: parse defensively and fallback to TODO/DONE defaults.
- risk: Full unit-test runs may fail for existing repo issues unrelated to this feature.
  - mitigation: rely on focused helper tests plus `assembleFdroidDebug`; use JDK 21 for Robolectric only when needed.
- risk: The current branch has untracked source brainstorm material.
  - mitigation: include both the source requirements doc and this plan intentionally if committing later; do not accidentally omit the source doc.

## validation_strategy

### automated

```bash
cd /home/agent/dev/orgzly
./scripts/build_fdroid.sh assembleFdroidDebug
```

Optional targeted tests after implementation:

```bash
cd /home/agent/dev/orgzly
export JAVA_HOME="$HOME/.local/jdks/temurin-17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.local/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.prefs.StateColorPreferencesTest
./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.util.TitleGeneratorTest
```

If Robolectric-related tests hit Android SDK 36 / JDK 17 failures, retry those specific tests with Temurin 21 and report whether failures are feature-specific or existing environment breakage.

### manual QA

1. Set workflow states to `TODO NEXT WAITING | DONE CANCELLED`.
2. Open `Settings -> Look and feel -> TODO state colors`.
3. Confirm rows appear in order: `TODO`, `NEXT`, `WAITING`, `DONE`, `CANCELLED`.
4. Set distinct colors for `NEXT`, `WAITING`, and `CANCELLED`.
5. Create or edit notes with each state.
6. Confirm selected state keyword colors in:
   - notebook list
   - search results
   - agenda/saved-search rows
   - note editor/details state button
   - list widget rows
7. Confirm done/archived alpha behavior is unchanged.
8. Remove `WAITING` from workflow; reopen state-color screen and confirm `WAITING` is hidden.
9. Re-add `WAITING`; confirm its previous color returns.
10. Change widget color scheme; confirm unset states use scheme-specific fallback colors, while custom states keep the chosen absolute color.
11. Change a state color and confirm existing widgets refresh without recreation.

## handoff_notes

- Keep V1 narrow: state keyword color only, not title/background/whole-row coloring.
- Do not hard-code only `DONE`; use configured done keywords through `AppPreferences.doneKeywordsSet(...)` / `isDoneKeyword(...)`.
- Do not prune old dynamic preference keys on workflow changes.
- Be careful with Android local unit tests that touch framework classes; pure helper tests are safer.
- The source requirements doc is currently untracked alongside this new plan; include both intentionally if preparing a commit.
- After implementation, update `CHANGELOG.md` and run `./scripts/build_fdroid.sh assembleFdroidDebug` before reporting the branch as testable.
