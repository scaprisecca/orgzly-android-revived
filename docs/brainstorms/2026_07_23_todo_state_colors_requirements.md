# TODO state colors

created: 2026-07-23
status: draft
source: ce-brainstorm
repo: orgzly

## problem
- Orgzly currently colors state keywords in only two buckets: TODO-like states and DONE-like states.
- Scott uses custom workflows and wants visual separation between individual states such as `TODO`, `NEXT`, `WAITING`, `DONE`, and future custom states.
- The feature needs to work not only in the in-app note/task lists, but also in widgets because widgets are part of Scott's regular workflow.
- State colors should be configurable in settings and should follow the current configured TODO/DONE workflow state list.

## goals
- Add a user-configurable color preference for each configured TODO/DONE state keyword.
- Automatically show newly-added states as color options after the workflow states preference changes.
- Preserve color preferences for removed states so a deleted state can recover its old color if it is added again later.
- Apply per-state colors to in-app note/task title state labels and the note editor/details state button.
- Apply the same per-state colors to widget note/task title state labels.
- Keep existing TODO/DONE theme colors as safe fallback defaults when no per-state color is configured.
- Avoid database migrations; use SharedPreferences because this is presentation/user-preference state.

## non_goals
- Do not delete/prune color preferences for removed states in V1.
- Do not require every state to be assigned a custom color before use.
- Do not change Org-mode parsing, state workflow syntax, done/todo classification, or note state storage.
- Do not introduce separate per-widget state colors in V1; widgets should use the same state-color preference map as the app unless Scott later wants independent widget styling.
- Do not color the full note title in V1 unless explicitly chosen later; the targeted V1 behavior is coloring the state keyword label itself.
- Do not try to solve color accessibility comprehensively in V1 beyond using existing fallback colors and the existing color picker palette.

## current_code_findings
- Repo root: `/home/agent/dev/orgzly`.
- `AGENTS.md` and `CLAUDE.md` are absent.
- Primary project overview: `README.org`.
- Current branch during inspection: `capture-to-subheading`.
- App code working tree was clean during inspection.
- Existing state workflow storage/parsing:
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `states(context)` reads `pref_key_states` with default `TODO NEXT | DONE`.
  - `states(context, value)` writes the workflow and calls `updateStaticKeywords(context)`.
  - `todoKeywordsSet(context)` and `doneKeywordsSet(context)` are cached `LinkedHashSet`s parsed from `StateWorkflows(states(context))`, preserving workflow order.
- Existing dynamic state list helper:
  - `app/src/main/java/com/orgzly/android/ui/NoteStates.kt`
  - `NoteStates.fromPreferences(context)` combines todo keywords then done keywords.
- Existing in-app title coloring:
  - `app/src/main/java/com/orgzly/android/ui/util/TitleGenerator.kt`
  - `generateState(note)` currently uses done membership to choose only `attributes.colorDone` or `attributes.colorTodo`.
  - `TitleGenerator.TitleAttributes` currently carries only TODO fallback color, DONE fallback color, post-title text size, and post-title text color.
- Existing in-app title generation entrypoint:
  - `app/src/main/java/com/orgzly/android/ui/notes/NoteItemViewBinder.kt`
  - `NoteItemViewBinder` obtains themed colors from attrs `item_head_state_todo_color` and `item_head_state_done_color` and constructs `TitleGenerator`.
  - This binder is used by normal note/task list surfaces and by settings import/export preview rows.
- Existing note editor state display:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `setStateView(state)` currently sets `binding.stateButton.text` with no custom color logic.
  - Coloring this state button is feasible, but it is separate from list title rendering and should be an explicit V1 scope choice.
- Existing theme fallback colors:
  - `app/src/main/res/values/styles.xml`
  - `item_head_state_todo_color` and `item_head_state_done_color` are red/green variants for light/dark themes.
  - `app/src/main/res/values/attrs.xml` defines the attrs.
- Existing color picker setting:
  - `app/src/main/java/com/orgzly/android/prefs/ColorPickerPreference.kt`
  - `app/src/main/java/com/orgzly/android/prefs/ColorPickerDialogFragment.kt`
  - `app/src/main/res/xml/prefs_screen_look_and_feel.xml` already uses this for calendar color.
  - `SettingsFragment.onDisplayPreferenceDialog(...)` already routes `ColorPickerPreference` to `ColorPickerDialogFragment`.
- Existing settings navigation:
  - `app/src/main/res/xml/prefs.xml` has `Look and feel` as a top-level settings screen.
  - `SettingsFragment.PREFS_RESOURCES` maps string keys such as `prefs_screen_look_and_feel` to XML preference resources.
  - Dynamic preference screens can be added in `SettingsFragment` either by adding an XML entrypoint and populating when that screen is opened, or by creating an XML-free `PreferenceScreen` programmatically.
- Existing widget title coloring:
  - `app/src/main/java/com/orgzly/android/widgets/ListWidgetService.kt`
  - Widget rows build title text with `TitleGenerator(context, false, WidgetStyle.getTitleAttributes(context))`.
  - `app/src/main/java/com/orgzly/android/widgets/WidgetStyle.kt` provides widget-specific `TitleGenerator.TitleAttributes` via `getTitleAttributes(context)`.
  - `app/src/main/java/com/orgzly/android/widgets/WidgetColors.kt` defines widget fallback TODO/DONE state colors by scheme (`dynamic-day`, `dynamic-night`, `light`, `dark`, `black`).
  - Because widgets already use `TitleGenerator`, a shared per-state resolver can cover app lists and widget rows with limited duplication.
- Existing widget refresh triggers:
  - `SettingsFragment.onSharedPreferenceChanged(...)` broadcasts `ACTION_UPDATE_LAYOUT_LIST_WIDGET` for widget style preference keys and always calls `ListWidgetProvider.notifyDataSetChanged(requireContext())` after preference changes.
  - Per-state color preference keys are dynamic, so the widget update condition should detect a key prefix, not only fixed string resource keys.

## proposed_behavior
### Settings
- Add a `TODO state colors` entry under `Settings -> Look and feel`.
- Opening that screen displays one color picker preference per currently configured state keyword.
- State list source is `NoteStates.fromPreferences(context)`, so the screen follows the current workflow config.
- Sort/order follows workflow order: all TODO keywords first in configured order, then DONE keywords in configured order.
- Each row title is the exact state keyword, for example `TODO`, `NEXT`, `WAITING`, `DONE`, `CANCELLED`.
- Each row summary/swatch shows the current persisted color if set, otherwise its fallback default color.
- When a new workflow state is added, it appears automatically next time the color screen is opened.
- When a workflow state is removed, its stored color preference remains in SharedPreferences and is simply not shown while the state is absent.
- If that state is added again later with the same keyword, the old color is used again.

### Preference storage
- Store colors in default SharedPreferences as hex strings, consistent with existing `ColorPickerPreference` behavior.
- Use a stable generated key prefix, for example:
  - `pref_key_state_color_<encoded-state>`
- Do not delete generated state color keys during state workflow changes.
- Add helpers in `AppPreferences` or a small Kotlin preference helper:
  - `stateColorPreferenceKey(state: String): String`
  - `stateColor(context, state, fallbackColor): Int`
  - `stateColorHex(context, state): String?`
  - `setStateColor(context, state, colorHex)` if needed outside `ColorPickerPreference` persistence.
- Encoding should avoid invalid/ambiguous key suffixes for states containing punctuation. Options:
  1. URL-safe Base64 of UTF-8 state keyword.
  2. Percent-encoding.
  3. Hex of UTF-8 bytes.
- Recommendation: use a simple deterministic encoding helper and unit-test it. State keywords are usually uppercase ASCII, but custom workflows may include punctuation.

### In-app note/task lists
- `TitleGenerator.generateState(note)` should request a state-specific color for `note.state`.
- If a custom color exists for the state, use it.
- If not, fallback to existing DONE color for done states and TODO color for all other configured active states.
- Preserve current bold behavior for state/priority prefix.
- Preserve current alpha behavior for done/archived notes in `NoteItemViewBinder.setupAlpha(...)`.
- Primary V1 app surfaces covered by this path:
  - notebook note list
  - search results
  - agenda/saved-search task rows
  - settings import/export preview rows that use `NoteItemViewBinder`

### Note editor/details state button
- There is a separate state button in `NoteFragment.setStateView(...)`.
- Recommended V1 inclusion: color the state button text using the same resolver because it is a visible task state label and will make the feature feel consistent.
- Fallback to the default button text color when no state is set.
- This requires care with recycled state/reset behavior: when state is `null`/`NOTE`, reset the text color back to the original/default color, not the last state's color.

### Widgets
- Widgets should use the same per-state color preferences as the app.
- `WidgetStyle.getTitleAttributes(context)` should continue to provide widget fallback TODO/DONE/post-title colors based on widget scheme.
- `TitleGenerator` should be able to use a state-specific color resolver while retaining widget scheme fallback colors.
- `ListWidgetService.setupRemoteViews(row, entry)` already uses `TitleGenerator`, so custom colors should appear in widget rows once the resolver is shared.
- Widget fallback colors should remain scheme-aware:
  - if no custom color for `WAITING`, use widget TODO fallback for the active scheme
  - if no custom color for `DONE`, use widget DONE fallback for the active scheme
- Custom per-state colors are absolute user-chosen hex colors and should not be transformed per widget scheme in V1.
- After a state color preference changes, existing widgets should refresh.
- Add dynamic-key handling to settings preference change logic so keys with the state-color prefix trigger:
  - `ACTION_UPDATE_LAYOUT_LIST_WIDGET` if needed for layout/style refresh
  - `ListWidgetProvider.notifyDataSetChanged(requireContext())`

## recommended_mvp
1. Add a small state color preference/resolution helper.
2. Extend `TitleGenerator.TitleAttributes` or introduce a `StateColorResolver` that can return:
   - custom per-state color if present
   - TODO/DONE fallback color otherwise
3. Update app list rendering through `NoteItemViewBinder`.
4. Update widget rendering through `WidgetStyle.getTitleAttributes(...)` and existing `ListWidgetService` path.
5. Add `TODO state colors` settings screen under Look and feel with dynamic `ColorPickerPreference` rows.
6. Include `NoteFragment.setStateView(...)` state-button text coloring if implementation stays small.
7. Add widget refresh handling for dynamic state-color keys.
8. Add a `CHANGELOG.md` entry.
9. Verify with `./scripts/build_fdroid.sh assembleFdroidDebug`.

## implementation_options
### Option A: Put resolver inside `TitleGenerator.TitleAttributes`
- Shape:
  - Add a function field or map to `TitleAttributes`, e.g. `stateColor: (String, Boolean) -> Int`.
  - `generateState(note)` calls `attributes.colorForState(note.state, isDone)`.
- Pros:
  - Minimal disruption to current call sites.
  - Keeps app and widget title generation centralized.
- Cons:
  - `TitleAttributes` becomes less of a passive value object.
- Verdict: recommended if implemented cleanly.

### Option B: Add a dedicated `StateColorResolver` class
- Shape:
  - `StateColorResolver(context, todoFallback, doneFallback)` exposes `colorForState(state)`.
  - App and widget code each construct it with their own fallback colors.
  - `TitleGenerator` receives resolver plus post-title styling.
- Pros:
  - Cleaner separation and easier unit testing.
  - Useful for note editor state button too.
- Cons:
  - Slightly larger refactor of `TitleGenerator.TitleAttributes` and call sites.
- Verdict: best long-term shape; likely still small enough for V1.

### Option C: Precompute a map of state -> color in each caller
- Pros:
  - Very explicit.
- Cons:
  - Duplicates fallback/custom lookup in app and widget paths.
  - Easier to drift.
- Verdict: avoid unless a fast spike proves resolver refactor awkward.

## constraints
- `ColorPickerPreference` currently reads XML `android:defaultValue` during init. Dynamically-created preferences must be initialized with a default value or be enhanced to support programmatic default/fallback color cleanly.
- Dynamic preference keys require `SettingsFragment.onSharedPreferenceChanged(...)` to identify a prefix, because there will not be one fixed `R.string.pref_key_*` per state.
- `TitleGenerator` is used by both app list UI and widgets. Any constructor/API change must update both `NoteItemViewBinder` and `WidgetStyle`/`ListWidgetService` paths.
- Widget `RemoteViews.setTextViewText(...)` accepts a `CharSequence`; current spanned title coloring is already used, so per-state `ForegroundColorSpan` should continue to work.
- Custom colors may look poor on some themes or widget schemes. V1 should trust the user-selected color and rely on fallback defaults when unset.
- Full test runs in this repo can fail for unrelated existing issues; use build verification as the primary confidence signal and targeted helper tests where possible.

## open_questions
- Should there be a `Reset to default` action per state or for all state colors? This is useful but not required for initial implementation.

## resolved_decisions
- Preserve color preferences for deleted/removed states so re-adding the same state keyword restores the prior chosen color.
- Include widget support in the feature scope because Scott uses widgets often.
- Include the note editor/details `State` button in V1 so state color is consistent between list rows, widgets, and note details.
- Custom colors apply equally to TODO and DONE states; existing done-item alpha behavior remains separate and unchanged.
- Use settings-configurable colors, not hard-coded state color rules.
- Newly-added states should automatically become color options.

## success_criteria
- Given workflow `TODO NEXT WAITING | DONE CANCELLED`, the settings screen shows color pickers for all five states.
- Choosing a color for `WAITING` changes the color of the `WAITING` keyword in notebook lists, search/agenda task rows, the note editor/details state button, and widgets.
- Choosing a color for `DONE` changes the `DONE` keyword color while preserving existing done-item alpha behavior.
- Adding a new state such as `DELEGATED` makes `DELEGATED` appear on the color settings screen with a fallback color.
- Removing `WAITING` hides it from the color settings screen but does not delete its stored color preference.
- Re-adding `WAITING` restores the previously selected color.
- Widgets refresh after a state color is changed.
- Existing users with no custom colors see the same TODO/DONE colors as before.
- `./scripts/build_fdroid.sh assembleFdroidDebug` succeeds.

## notes_for_planning
- Likely files:
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - new helper under `app/src/main/java/com/orgzly/android/ui/util/`, e.g. `StateColorResolver.kt`
  - `app/src/main/java/com/orgzly/android/ui/util/TitleGenerator.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/NoteItemViewBinder.kt`
  - `app/src/main/java/com/orgzly/android/widgets/WidgetStyle.kt`
  - `app/src/main/java/com/orgzly/android/widgets/ListWidgetService.kt` only if needed after resolver changes
  - `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - possibly new settings fragment/screen helper for dynamic state color preferences
  - `app/src/main/res/xml/prefs_screen_look_and_feel.xml`
  - `app/src/main/res/values/strings.xml`
  - `CHANGELOG.md`
- Suggested implementation direction:
  1. Add state color key encoding and lookup helpers with focused unit tests if feasible.
  2. Introduce a shared resolver that accepts TODO/DONE fallback colors and checks SharedPreferences for state-specific overrides.
  3. Update `TitleGenerator.generateState(...)` to use resolver; verify app and widget call sites compile.
  4. Add the dynamic settings screen and row generation.
  5. Add widget refresh prefix handling.
  6. Optionally color `NoteFragment`'s state button using the same resolver and reset default color when no state is set.
- Manual QA:
  - Configure states: `TODO NEXT WAITING | DONE CANCELLED`.
  - Set distinct colors for `NEXT`, `WAITING`, and `CANCELLED`.
  - Create notes/tasks in a notebook with each state.
  - Confirm notebook list, search results, agenda view, and widget show the selected colors.
  - Remove `WAITING` from workflow, reopen color screen, confirm it is hidden.
  - Re-add `WAITING`, reopen color screen, confirm old color returns.
  - Change widget color scheme and confirm unset states still use scheme fallback while custom states keep chosen hex colors.
