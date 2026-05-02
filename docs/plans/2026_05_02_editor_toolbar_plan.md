# mobile editor toolbar implementation plan

created: 2026-05-02
status: active
source_doc: docs/brainstorms/2026_05_01_editor_toolbar_requirements.md

## objective
- add a focused mobile editing toolbar for Orgzly note editing so common org syntax can be inserted without leaving keyboard flow
- keep raw-text editing as the source of truth while making common actions faster and more predictable on Android
- deliver a v1 with a compact always-visible bottom toolbar and a secondary More/Insert surface for lower-frequency actions

## scope
- included:
  - bottom editor toolbar on the note screen during title/content edit mode
  - v1 primary actions: bold, italic, link, bullet list item, checkbox / todo item, timestamp, more/insert
  - selection-aware insertion and line transformation rules for title/content editing
  - reuse of existing timestamp dialog flows for inline, scheduled, deadline, and repeater-capable insertion
  - compact secondary insert surface for heading, todo state item, numbered list item, code/verbatim, property drawer block, property line, and timestamp variants
  - unit tests for text transformation helpers and targeted behavior verification for timestamp insertion helpers
- excluded:
  - full rich text editor or alternate document model
  - heading promote/demote
  - todo state cycling for existing lines
  - desktop-parity org commands, tables, or advanced structural refactors
  - broad metadata-screen redesign outside what is needed to host the editor toolbar

## relevant_existing_patterns
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - owns note editor lifecycle, toolbar menu handling, timestamp dialog launches, and payload/view syncing
- `app/src/main/res/layout/fragment_note.xml`
  - current note screen layout with top toolbar, `NestedScrollView`, and `RichText` title/content editors
- `app/src/main/res/menu/note_actions.xml`
  - existing note action menu; currently exposes only `insert_inline_timestamp` while editing
- `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
  - wraps view/edit mode switching and already exposes cursor replacement via `insertStringAtCursorPosition`
- `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - manages cursor placement, focus, keyboard, and contains the key focus-loss risk for toolbar interactions
- `app/src/main/java/com/orgzly/android/ui/dialogs/TimestampDialogFragment.kt`
  - existing timestamp chooser with inline vs scheduled/deadline behavior, repeater/delay support, and keyboard reopen handling
- `app/src/main/java/com/orgzly/android/util/OrgFormatter.kt`
  - current source of org formatting/parsing behavior and a likely home for some syntax-template knowledge, but not for editor-specific UI state
- `app/src/test/java/com/orgzly/android/util/OrgFormatterTest.kt`
- `app/src/test/java/com/orgzly/android/util/OrgFormatterMiscTest.kt`
- `app/src/test/java/com/orgzly/android/util/OrgFormatterLinkTest.kt`
  - existing JVM test patterns that can be extended for text transformation logic
- `docs/brainstorms/2026_05_01_editor_toolbar_requirements.md`
  - planning source of truth for v1 behavior and non-goals
- `docs/ideation/2026_05_01_org_mode_mobile_features_ideation.md`
  - confirms the toolbar is intended as the first high-value mobile org UX improvement

## implementation_units
### 1. editor command model and transformation helpers
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/` (new helper file, e.g. `EditorToolbarActions.kt` or `OrgEditCommands.kt`)
  - `app/src/test/java/com/orgzly/android/ui/note/` (new JVM tests for command helpers)
- changes:
  - introduce a small editor-command layer that operates on plain text plus selection ranges, returning updated text and updated selection/caret positions
  - support two command families:
    - inline wrappers: bold, italic, code/verbatim, link
    - line-based transforms: bullet, checkbox/todo item, numbered list item, heading, property line/block templates
  - keep command logic independent from Android views so most behavior is testable as pure JVM code
  - extend `RichTextEdit`/`RichText` with selection-safe helpers beyond simple replace-at-cursor, likely something like get selection range + apply edit result + restore selection
- dependencies:
  - current `RichTextEdit`/`RichText` APIs only support replace current selection with a string; v1 needs richer edit results
- risks:
  - line-boundary math for multi-line selections can become error-prone
  - org syntax edge cases could sprawl if helper scope is not kept deliberately template-based
- tests:
  - `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
- acceptance:
  - commands can be exercised in JVM tests without fragment/UI setup
  - tests cover no-selection, single selection, and multi-line selection cases for the v1 command set

### 2. preserve focus/selection when toolbar actions are tapped
- files:
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/res/layout/fragment_note.xml`
- changes:
  - prevent bottom-toolbar interactions from immediately forcing `RichText` back to view mode via the current `onFocusChange` behavior
  - add a controlled path for executing editor commands against the currently active editor (title vs content) while preserving selection and reopening/keeping the keyboard as needed
  - decide whether the toolbar itself should be non-focusable, whether taps should proxy back to the active editor, or whether `RichText` needs a temporary “toolbar interaction in progress” escape hatch
- dependencies:
  - unit 1 command application API
  - existing keyboard reopen pattern in `TimestampDialogFragment`
- risks:
  - this is the main technical risk; current behavior explicitly flips back to view mode on focus loss
  - a weak fix here will make the toolbar feel flaky even if command logic is correct
- tests:
  - JVM coverage for selection application logic in unit 1
  - manual verification on device/emulator for title edit and content edit focus retention
- acceptance:
  - tapping toolbar actions does not collapse the edit session or lose the selection unexpectedly
  - after command execution, the edited field remains active and the caret/selection lands in the expected place

### 3. add bottom toolbar UI and edit-mode visibility rules
- files:
  - `app/src/main/res/layout/fragment_note.xml`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/drawable-anydpi/` and/or `app/src/main/res/drawable/` (new icons if existing material assets are insufficient)
- changes:
  - add a compact bottom toolbar anchored to the note screen rather than expanding the existing top menu
  - show the toolbar only while title/content is actively being edited; hide it in view mode and when the note is missing
  - wire always-visible v1 actions:
    - bold
    - italic
    - link
    - bullet list item
    - checkbox / todo item
    - timestamp
    - more/insert
  - ensure scroll/content padding accounts for the toolbar so the editor’s last lines are not obscured
  - keep the existing top-toolbar `done`/note actions behavior intact; the new toolbar is an editing affordance, not a replacement app bar
- dependencies:
  - unit 2 focus-safe action dispatch
  - string and icon resource additions
- risks:
  - layout overlap with the keyboard and nested scroll content
  - overcrowding on narrow devices if button density is not carefully tuned
- tests:
  - manual checks on small and large phone widths
  - manual checks in light/dark theme if supported by current app theme
- acceptance:
  - toolbar appears only in edit mode
  - all primary actions are reachable in one tap without obscuring active text content
  - note content remains readable/editable near the bottom of the screen

### 4. implement v1 command wiring for primary toolbar actions
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/` (new helper file from unit 1)
  - `app/src/main/res/values/strings.xml`
- changes:
  - map each primary toolbar action to the command helper layer
  - behavior expectations for v1:
    - bold/italic/code/link wrap selection or insert a syntax pair/template at the caret
    - bullet/checkbox/todo transform current line when there is no selection
    - bullet/checkbox/todo transform each selected line when multiple lines are selected
    - link inserts a basic org link template with sensible cursor placement when no selection exists
    - checkbox / todo item should reflect the agreed “task line creation” behavior rather than full keyword cycling
  - keep title-field support intentionally narrow if needed; if some commands are content-only, make that explicit in the plan-to-build handoff rather than letting them fail silently
- dependencies:
  - units 1 through 3
- risks:
  - ambiguity between checkbox item and todo state item; v1 should use the requirement wording and place any alternate form in More/Insert
  - title editor may not be a sensible target for all commands
- tests:
  - `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
- acceptance:
  - primary commands behave predictably for empty selection, selected text, and multi-line selections
  - unsupported contexts are either disabled or handled deliberately, not accidentally

### 5. reuse and extend timestamp flows for toolbar-driven insertion
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/dialogs/TimestampDialogFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/TimeType.kt` (only if a new origin/type distinction is actually needed)
  - `app/src/main/res/values/strings.xml`
- changes:
  - keep `TimestampDialogFragment` as the source for actual timestamp picking and repeater/delay options
  - add a toolbar timestamp entry path that lets the user choose inline vs scheduled vs deadline vs repeater-oriented insertion flow from the editor context
  - for inline insertion, preserve current selection-replace behavior but route through the richer selection helper from unit 1 if needed
  - for scheduled/deadline insertion from the More/Insert surface, decide whether the target is inline text insertion only or whether content helpers should emit literal `SCHEDULED:` / `DEADLINE:` line templates in the editor
  - keep existing metadata timestamp buttons unchanged unless a later cleanup intentionally unifies both code paths
- dependencies:
  - existing timestamp dialog behavior and note callback handling
- risks:
  - overloading `TimeType.EVENT` may be enough for inline insertion but not for distinguishing sheet entry points cleanly
  - timestamp dialog currently assumes origin is a view id; new sheet flows may need a clearer command origin enum or request key pattern
- tests:
  - extend/add JVM tests around any new text-template helper logic
  - manual verification for inline timestamp insertion into both title and content fields
  - manual verification that time-bearing timestamps still trigger `ensureAlarmPermissions`
- acceptance:
  - timestamp action launches a predictable chooser and inserts the requested syntax without losing edit context
  - existing inline timestamp behavior is preserved or improved, not regressed

### 6. add More/Insert surface for lower-frequency commands
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/res/layout/` (new sheet/dialog layout)
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/ui/note/` (optional new small fragment/dialog class if extraction keeps `NoteFragment` manageable)
- changes:
  - implement a compact secondary insert surface; preferred shape is a Material bottom sheet if available cleanly in the current stack, otherwise a dialog/list surface that preserves editor focus semantics
  - include v1 items from the requirements doc:
    - heading
    - todo state item
    - numbered list item
    - code / verbatim
    - property drawer block
    - single property line
    - scheduled timestamp
    - deadline timestamp
    - inline timestamp
    - repeater / recurring timestamp helper
  - keep the sheet action-only; no heavy form builder in v1
  - use plain org templates for property/drawer insertion
- dependencies:
  - units 1, 2, and 5
  - choice of UI container that works with current Material dependency and focus behavior
- risks:
  - there is no existing bottom-sheet pattern in the inspected codebase, so introducing one adds UI integration risk
  - if the sheet is too deep or modal, it will break the “stay in keyboard flow” goal
- tests:
  - manual verification of action list contents and insertion results
- acceptance:
  - More/Insert exposes all lower-frequency v1 actions without bloating the main toolbar
  - choosing an item applies the expected text template and returns the user to editing smoothly

### 7. strings, icons, accessibility, and regression coverage
- files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/strings_untranslatable.xml` (only if needed for samples; likely not)
  - `app/src/main/res/drawable-anydpi/` and/or `app/src/main/res/drawable/`
  - `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
  - possibly `app/src/test/java/com/orgzly/android/util/OrgFormatterMiscTest.kt` if helper coverage fits better there
- changes:
  - add user-facing labels/content descriptions for every toolbar and sheet action
  - add or reuse icons with consistent Material styling
  - document any intentional content-only actions and disable states for title editing
  - add regression cases around link template insertion, checkbox/todo transforms, and multi-line edits
- dependencies:
  - final set of actions from units 3 through 6
- risks:
  - translation backlog is expected because new strings are likely numerous
  - poor content descriptions will hurt usability despite the feature being mobile-first
- tests:
  - JVM tests for text transforms
  - manual accessibility sanity check with touch targets and content descriptions
- acceptance:
  - no unlabeled toolbar actions
  - new strings are centralized and understandable
  - regression coverage exists for the highest-risk text transformations

## sequencing
1. confirm the command surface and define a minimal editor-command API around text + selection, before touching layout
2. implement and test pure text transformation helpers for wrapper and line-based commands
3. harden `RichText`/`RichTextEdit` so toolbar taps do not force a mode exit or lose selection
4. add the bottom toolbar UI in `fragment_note.xml` and wire edit-mode visibility in `NoteFragment.kt`
5. connect primary toolbar actions to the command layer and verify title/content behavior
6. integrate timestamp chooser flow for toolbar usage, reusing `TimestampDialogFragment` as much as possible
7. add the More/Insert surface and wire the lower-frequency v1 actions
8. finish strings/icons/accessibility cleanup and run full validation

## risks_and_unknowns
- risk: current `RichTextEdit` focus-loss behavior is likely incompatible with tapping an external toolbar
  - mitigation: solve focus/selection preservation before investing in polished UI wiring; treat this as the first implementation spike
- risk: multi-line selection transforms can create off-by-one bugs and broken cursor restoration
  - mitigation: keep edit commands pure and heavily unit tested with explicit selection assertions
- risk: title and content fields may not support the same action set cleanly
  - mitigation: explicitly define which actions are content-only in v1 and disable them when title editing is active
- risk: no established bottom-sheet pattern was found in the inspected codebase
  - mitigation: prefer the simplest action surface that preserves focus correctly; use a dialog/list if bottom sheet integration becomes noisy
- risk: reusing timestamp dialog callbacks via raw view ids may get awkward for new insert-surface entry points
  - mitigation: if needed, introduce a small command-origin abstraction instead of overloading view ids further
- risk: Android soft keyboard + bottom toolbar + `NestedScrollView` can hide content near the caret
  - mitigation: budget time for layout inset/content padding adjustments and manual small-device verification

## validation_strategy
- JVM tests:
  - run targeted tests for new editor command helpers under `app/src/test/java/com/orgzly/android/ui/note/`
  - run existing formatter-related suites to catch parsing regressions:
    - `app/src/test/java/com/orgzly/android/util/OrgFormatterTest.kt`
    - `app/src/test/java/com/orgzly/android/util/OrgFormatterMiscTest.kt`
    - `app/src/test/java/com/orgzly/android/util/OrgFormatterLinkTest.kt`
- project test/build checks:
  - `./gradlew test`
  - `./gradlew assembleDebug`
- manual checks:
  - edit note title and content, confirm toolbar visibility toggles correctly
  - apply each primary toolbar action with no selection, single selection, and multi-line selection where relevant
  - verify timestamp insertion flows, including inline insertion and time-bearing permission path
  - verify keyboard remains usable and final lines of content are not hidden behind the toolbar/IME
  - verify note save/view-mode round-trip still renders expected org syntax correctly

## handoff_notes
- keep the implementation centered on plain-text transformations; do not drift into a secondary editor model
- prioritize the focus/selection problem first; if that is not stable, the rest of the toolbar will feel broken
- prefer a small new helper in the note/editor area over stuffing more ad hoc string edits directly into `NoteFragment.kt`
- preserve existing top-toolbar behavior and existing metadata timestamp buttons unless there is a clear simplification win
- be conservative about command scope in title editing; disabling some actions there is better than shipping surprising behavior
- if the insert surface needs a new fragment/dialog class, keep it narrow and action-oriented rather than introducing a generic command framework prematurely
- if implementation proceeds with a coding subagent, this file should be the handoff artifact