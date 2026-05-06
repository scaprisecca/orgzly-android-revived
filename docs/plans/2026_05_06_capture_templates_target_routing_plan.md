# capture templates + notebook routing implementation plan

created: 2026-05-06
status: active
source_doc: docs/brainstorms/2026_05_06_capture_templates_target_routing_requirements.md

## objective
- add a mobile-first capture template system to Orgzly that lets users create structured notes/tasks faster than the current generic new-note flow
- keep v1 bounded to built-in templates, notebook routing only, and share-flow integration
- reuse existing `NotePayload`, `NotePlace`, `ShareActivity`, and note editor flows so the feature feels native to the current app rather than like a separate capture subsystem

## scope
- included:
  - built-in capture template definitions for:
    - inbox task
    - repeating chore
    - meeting note
    - learning note
    - business idea
  - notebook-only target routing with explicit fallback precedence
  - in-app template picker before blank-note creation from the book screen / new-note entry points
  - share-flow integration that can apply templates to incoming shared text/images
  - lightweight per-template settings:
    - enabled/disabled
    - target notebook name
    - optional quick-capture visibility if the current settings structure stays manageable
  - prebuilt payload population for title/content/state/scheduled/tags/properties/body scaffold
  - tests for template resolution, routing fallback, and share/in-app payload generation behavior
- excluded:
  - parent-note routing
  - user-defined custom templates
  - scripting/template language
  - notification quick capture changes
  - launcher shortcut overhaul beyond preserving compatibility with current notebook/new-note shortcuts
  - a heavy multi-step form wizard for each template

## planning_decisions
- v1 uses notebook routing only
- v1 includes share-flow integration
- parent-note routing is deferred until after real-world testing
- recommended v1 share behavior:
  - if exactly one capture template is enabled for sharing, apply it directly
  - if multiple templates are enabled, show a lightweight picker first
- recommended v1 repeating-chore behavior:
  - prefill task metadata/body scaffold immediately
  - land in the normal editor with scheduled field available
  - defer automatic forced timestamp-dialog launch unless implementation falls out cleanly during build

## relevant_existing_patterns
- `README.org`
  - repo-level product framing and command-line build/testing context
- `docs/brainstorms/2026_05_06_capture_templates_target_routing_requirements.md`
  - source-of-truth requirements and v1 scope boundary
- `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - current share entry point, existing parsing of shared text/image content, and notebook override support via `EXTRA_BOOK_ID`
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - current target-book resolution through `getTargetBook(context)` and note creation path
- `app/src/main/java/com/orgzly/android/ui/note/NotePayload.kt`
  - existing payload surface already supports title/content/state/priority/scheduled/deadline/tags/properties
- `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`
  - existing default new-note payload generation and a likely extension point for template-aware payload creation
- `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - note editor view-model currently initializes new notes from only title/content/place; this is the main constraint for template-driven rich payload initialization
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - currently passes only `bookId`, `noteId`, `place`, `title`, and `content` into new-note initialization
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - primary in-app new-note entry from the floating action button and narrowed-book context
- `app/src/main/res/layout/fragment_book.xml`
  - current screen structure where a template-trigger UI or updated FAB behavior will need to fit cleanly
- `app/src/main/java/com/orgzly/android/ui/TemplateChooserActivity.java`
  - misleadingly named current notebook chooser for launcher shortcut creation; likely should not become the v1 template picker directly
- `app/src/main/java/com/orgzly/android/SharingShortcutsManager.kt`
  - current direct-share/new-note shortcut integration; useful for preserving compatibility and future extension points
- `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
- `app/src/main/res/xml/prefs_screen_notebooks.xml`
- `app/src/main/res/values/prefs_keys.xml`
  - existing preference patterns for default notebook and share behavior; likely home for lightweight capture-template settings
- `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt`
  - current Robolectric/JVM-style data-layer test pattern that can be extended for target-resolution tests

## architecture_direction
- avoid threading template logic through ad hoc extras and scattered string switches.
- introduce a small capture-template layer with three responsibilities:
  1. define built-in templates and default scaffolds
  2. resolve notebook targets using explicit override -> template setting -> global default notebook fallback
  3. produce a full `NotePayload` plus target book selection before the editor is shown
- keep the note editor as the confirmation and save surface; do not create a separate persistence path for templates.
- expand new-note initialization to support a full initial payload, not only initial title/content. Without this change, the implementation will end up duplicating note-creation behavior or silently dropping template metadata.

## implementation_units
### 1. define the capture-template model and payload builder
- files:
  - `app/src/main/java/com/orgzly/android/` or `app/src/main/java/com/orgzly/android/ui/share/` (new model/helper file, e.g. `CaptureTemplate.kt` or `CaptureTemplates.kt`)
  - `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`
  - `app/src/main/java/com/orgzly/android/AppIntent.java`
  - `app/src/test/java/com/orgzly/android/` or `app/src/test/java/com/orgzly/android/ui/share/` (new tests)
- changes:
  - define a closed v1 enum/sealed model for the five built-in templates
  - define template metadata:
    - stable id
    - label/icon resource
    - default title behavior
    - default tags/properties/state/scheduled rules
    - optional body scaffold
  - add a payload-construction helper that takes:
    - template id
    - incoming shared data or blank capture context
    - current settings/preferences
    - and returns a complete `NotePayload`
  - add new `AppIntent` extra keys for template selection and any explicit notebook override needed by share/in-app flows
- dependencies:
  - must align with current `NotePayload` fields and avoid inventing unsupported metadata
- risks:
  - if title/body merge rules for shared content are not explicit, share behavior will become inconsistent across templates
- tests:
  - new JVM tests for template-to-payload mapping
  - cases for blank capture vs shared text vs shared subject/text combination
- acceptance:
  - each built-in template can deterministically generate a `NotePayload`
  - incoming shared content is merged predictably instead of overwritten accidentally

### 2. add template settings and notebook target resolution
- files:
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `app/src/main/res/values/prefs_keys.xml`
  - `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt` or new helper near the template layer
  - `app/src/test/java/com/orgzly/android/data/DataRepositoryTest.kt` or a new dedicated target-resolution test
- changes:
  - add lightweight stored preferences per built-in template:
    - enabled flag
    - target notebook name
    - optional share-enabled / quick-capture-visible flag if kept in v1
  - implement a target-resolution helper that follows:
    1. explicit notebook override from launch/share intent
    2. template-configured notebook name if present and found
    3. current app default notebook via `getTargetBook(context)` fallback behavior
  - decide one safe failure mode when a configured notebook name no longer exists:
    - fall back to the global default/first book
    - optionally surface a snackbar/log hint later, but do not block capture
- dependencies:
  - unit 1 template identifiers
  - existing `AppPreferences.shareNotebook(context)` and `DataRepository.getTargetBook(context)` behavior
- risks:
  - storing notebook names rather than ids is more resilient across database recreation but can be ambiguous if duplicate names are possible
  - storing ids is cleaner internally but more brittle across export/import or DB reset scenarios
- recommendation:
  - use notebook name for v1 because current default-notebook preference already works that way and keeps the design aligned with existing repo patterns
- tests:
  - target-resolution cases:
    - explicit override present
    - template notebook exists
    - template notebook missing -> fallback
    - no template notebook -> fallback
- acceptance:
  - routing always resolves to a usable notebook without crashing or blocking capture
  - notebook-only routing remains the sole placement path in v1

### 3. expand note-editor initialization to accept a full initial payload
- files:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteViewModelFactory.kt`
  - `app/src/test/java/com/orgzly/android/ui/note/` (new tests if practical)
- changes:
  - extend `NoteInitialData` to optionally carry a full `NotePayload`, not just title/content
  - update `NoteFragment` argument bundling/parsing for new-note flows so a prebuilt payload can be passed in
  - update `NoteViewModel.loadData()` so new notes prefer the supplied initial payload over `NoteBuilder.newPayload(context, title, content)`
  - preserve current compatibility so existing callers that only provide title/content still work unchanged
- dependencies:
  - unit 1 payload builder
- risks:
  - this is the main architecture pivot; if skipped, template metadata will get lost before the editor loads
  - Android argument-passing needs care if `NotePayload` parceling is relied on directly
- tests:
  - new-note initialization with plain title/content only
  - new-note initialization with full payload preserving tags/state/scheduled/properties
- acceptance:
  - the editor opens with the exact template-generated metadata intact
  - current non-template note creation flows still behave the same

### 4. add an in-app template picker for new-note creation
- files:
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - `app/src/main/res/layout/fragment_book.xml` or a new picker layout under `app/src/main/res/layout/`
  - `app/src/main/res/values/strings.xml`
  - optional new UI class under `app/src/main/java/com/orgzly/android/ui/` or `.../ui/share/` for a dialog/bottom sheet
- changes:
  - replace the current direct FAB-to-blank-note behavior with a lightweight template picker entry path
  - picker should include:
    - blank note/manual note option to preserve current behavior
    - built-in templates enabled for in-app capture
  - after template selection:
    - resolve target notebook
    - build `NotePayload`
    - open the normal note editor using the selected book and payload
  - keep narrowed-book behavior explicit:
    - because v1 is notebook-only routing, template capture from a narrowed view should still route to the resolved notebook root rather than trying to infer a parent heading
- dependencies:
  - units 1 through 3
- risks:
  - overloading the existing FAB menu too much could make capture slower instead of faster
- recommendation:
  - prefer a compact bottom sheet or simple dialog list over a nested overflow menu tree
- tests:
  - manual QA on book screen, including narrowed-book state
- acceptance:
  - users can reach built-in templates from the standard new-note flow in one or two taps
  - blank-note creation remains available
  - v1 does not accidentally create notes under narrowed nodes or selected headings

### 5. integrate templates into `ShareActivity`
- files:
  - `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - `app/src/main/res/layout/activity_share.xml` if the picker requires a host container change
  - `app/src/main/res/values/strings.xml`
  - optional new helper/picker UI class
  - `app/src/test/java/com/orgzly/android/ui/share/` (new tests if feasible)
- changes:
  - preserve current shared-text/image parsing into an intermediate data object
  - after parsing, choose the template path:
    - if explicit template extra present, use it
    - else if exactly one share-enabled template is available, auto-apply it
    - else show a picker before constructing the editor fragment
  - resolve the target notebook using the v1 fallback order
  - open `NoteFragment` with the fully built template payload rather than only title/content
  - keep current direct-share/new-note shortcut support functioning, including `EXTRA_BOOK_ID`
- dependencies:
  - units 1 through 3
- risks:
  - `ShareActivity` currently creates the editor fragment immediately in `setupFragments`; adding a picker requires restructuring the activity lifecycle so it can defer fragment creation until after template selection
  - share intents can be resumed/reused awkwardly; keep state restoration simple and explicit
- tests:
  - shared plain text with one enabled template
  - shared plain text with multiple enabled templates
  - shared image path capture
  - explicit notebook override respected
- acceptance:
  - shared content can be captured through the new template system without losing the incoming data
  - no template path regresses the current share-to-new-note behavior for users who just want a fast default

### 6. preserve compatibility with existing shortcut/new-note integrations
- files:
  - `app/src/main/java/com/orgzly/android/ui/TemplateChooserActivity.java`
  - `app/src/main/java/com/orgzly/android/SharingShortcutsManager.kt`
  - `app/src/main/java/com/orgzly/android/NewNoteBroadcastReceiver.java`
  - `app/src/main/java/com/orgzly/android/usecase/NoteCreateFromNotification.kt`
- changes:
  - verify which existing integrations should remain notebook-only blank capture in v1 versus use templates later
  - do not repurpose `TemplateChooserActivity` into the runtime capture picker yet; its current role is launcher shortcut creation for notebook-scoped new notes
  - if naming becomes too misleading, note a follow-up rename/refactor, but avoid widening scope unless it blocks implementation clarity
  - leave notification quick capture unchanged in v1 unless the implementation naturally exposes a low-risk default-template hook
- dependencies:
  - units 1 through 5
- risks:
  - trying to upgrade every capture surface at once will sprawl scope and create inconsistent behavior
- tests:
  - smoke verification of current shortcut/new-note behavior after the new template plumbing lands
- acceptance:
  - existing shortcuts still work
  - v1 template work does not break notification capture or direct-share notebook shortcuts

### 7. add strings, resources, and validation coverage
- files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/drawable*/` for any new icons if needed
  - test files from units 1 through 5
- changes:
  - add human-readable labels for the built-in templates and any picker/settings text
  - keep resource naming stable and template ids unambiguous
  - add focused regression coverage around:
    - template payload creation
    - routing fallback
    - full payload initialization into editor
    - share-flow branching
- dependencies:
  - final action set and settings structure
- risks:
  - if too many template-specific strings are added without clear naming, translation and maintenance become noisy
- acceptance:
  - no unlabeled template actions
  - the highest-risk logic has JVM/Robolectric coverage even if UI picker interactions remain mostly manual QA

## sequencing
1. define the built-in template model and payload-generation rules
2. add per-template notebook settings and target-resolution fallback logic
3. extend note initialization so a full `NotePayload` can survive into the editor
4. wire the in-app new-note flow to a template picker plus payload-based editor launch
5. refactor `ShareActivity` to delay fragment creation until template selection is resolved
6. integrate share-flow template application and notebook routing
7. run compatibility checks against existing notebook shortcuts and notification quick capture
8. finish strings/resources and validate with tests plus manual device/emulator QA

## risks_and_unknowns
- risk: current new-note initialization only passes title/content, which is too narrow for template-driven capture
  - mitigation: treat full-payload initialization as an early architectural unit, not a late patch
- risk: `ShareActivity` currently assumes immediate fragment creation and will need control-flow restructuring
  - mitigation: introduce an intermediate parsed-share-data model and resolve template choice before building `NoteFragment`
- risk: settings bloat could arrive quickly if each template gets too many toggles
  - mitigation: keep v1 to enable/disable + notebook target, with optional quick-share visibility only if it stays clean
- risk: repeating-chore UX could invite scope creep into recurrence/habit tooling
  - mitigation: keep v1 focused on template defaults and normal editor/timestamp flows, not a new recurrence wizard
- risk: current `TemplateChooserActivity` name is misleading and could tempt the implementation into the wrong reuse path
  - mitigation: treat it as existing shortcut infrastructure; add a separate runtime picker surface for capture templates
- risk: notebook names as routing keys can be ambiguous if duplicate notebook names are allowed
  - mitigation: confirm current notebook uniqueness assumptions during implementation; if duplicates are possible, plan a follow-up migration to ids or disambiguation UI

## validation_strategy
- unit / Robolectric:
  - template-to-payload generation for all five templates
  - share-data merge behavior for subject/text/image cases
  - target notebook fallback resolution
  - note editor initialization with full payload
- manual validation:
  - create each template from the in-app book screen
  - create from narrowed and normal book views
  - share plain text into Orgzly
  - share a URL with subject text
  - share an image and confirm content survives template application
  - verify save, cancel, and back behavior still feel normal in the note editor
  - verify legacy new-note shortcut and notification capture still work
- non-goal validation for v1:
  - do not test parent-note routing because it is intentionally out of scope

## handoff_notes
- do not implement parent-note routing opportunistically just because `NotePlace` supports it; that would widen the product contract beyond the approved scope
- prefer adapting current `AppPreferences` + `DataRepository.getTargetBook()` patterns over introducing a heavier config subsystem
- keep the template system closed and explicit in v1; a built-in enum/sealed model is better than a premature dynamic registry
- the most important correctness point is this: template-generated metadata must survive all the way into the normal editor and then save correctly. If that path is weak, the whole feature will look present but behave inconsistently.
- after this plan, the next execution choice is either:
  - implement directly in-session, or
  - hand off this plan through `ce-handoff` for guarded coding execution.