# capture template manual QA follow-up fix plan

created: 2026-05-07
status: active
source_doc: docs/plans/2026_05_06_capture_templates_target_routing_plan.md

## objective
- turn Scott's first-pass manual testing feedback on capture templates into a concrete fix sequence
- separate true implementation bugs from current-but-confusing v1 behavior
- define a bounded path for improving template reliability before expanding the feature surface further

## scope
- included:
  - inbox task template behavior audit and fix
  - routing precedence review for in-app capture, share flow, and launcher shortcuts
  - image-share compatibility fix for modern `content://` providers
  - settings UX improvements for template target selection and fallback visibility
  - issue-by-issue acceptance criteria and manual QA coverage
- excluded:
  - full user-defined custom template CRUD
  - parent-note routing
  - major launcher shortcut redesign beyond precedence fixes
  - broad attachment-management system beyond the immediate image-share bug

## issue_summary
### 1. built-in templates are not user-customizable
- current state:
  - templates are hard-coded in `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - settings only expose enable/share/notebook flags in `app/src/main/res/xml/prefs_screen_notebooks.xml`
- interpretation:
  - this is a known v1 scope limit, but it will read like missing functionality to users unless the UI language is explicit
- recommendation:
  - do not expand into custom-template CRUD yet
  - tighten naming and settings copy so users understand these are built-in templates
  - treat full customization as a follow-on feature after routing and share reliability are fixed

### 2. inbox task does not behave like a task template
- observed behavior:
  - inbox task has no scaffold and no template-specific TODO state
- code evidence:
  - `CaptureTemplate.INBOX_TASK` is a no-op branch in `CaptureTemplates.buildTemplatePayload()`
  - only repeating chore forces `TODO`
- likely user expectation:
  - inbox task should reliably open as a task, even if the app-wide new-note state is blank
- recommendation:
  - force inbox task to default to `TODO`
  - keep body scaffold minimal unless product wants a specific note-body stub

### 3. template notebook target loses to current notebook / shortcut target
- observed behavior:
  - repeating chore saved into the currently-open notebook instead of the template-configured notebook
  - home-screen shortcut target overrode the template-configured notebook
- code evidence:
  - `CaptureTemplates.resolveTargetBook()` currently uses:
    1. explicit book id
    2. template notebook
    3. app default target
  - `ShareActivity.applyRoutingExtras()` populates `data.bookId` from current launch context and shortcut extras before template resolution
- interpretation:
  - this matches the existing v1 routing plan, but conflicts with real user expectations for template-driven capture
- recommendation:
  - revise routing policy so template-configured target wins by default for template captures
  - keep an explicit override path only for launch sources that are intentionally template-bound and clearly indicate that override in UI/behavior

### 4. share flow applies content but not reliably the intended template target
- observed behavior:
  - shared text lands in note content correctly, but file placement still appears to fall back to inbox/default location unless a file is manually selected
- code evidence:
  - share routing still depends on notebook-name lookup from freeform settings text
  - missing or mismatched notebook names silently fall back to default target
- recommendation:
  - replace freeform notebook text entry with a picker-backed value or at minimum add validation
  - surface fallback behavior instead of silently ignoring an invalid template notebook target

### 5. image share fails for modern `content://` URIs
- observed behavior:
  - image share produced a numeric title and body text saying the app could not determine the image path
- code evidence:
  - `ShareActivity.handleSendImage()` tries to resolve `MediaStore.Images.Media.DATA`
  - when the provider does not expose a filesystem path, the code emits a failure message in note content
- interpretation:
  - this is a real compatibility bug with modern Android file providers and browser share flows
- recommendation:
  - stop relying on direct filesystem path extraction as the primary path
  - support copying the shared stream into app-managed storage or another supported attachment/linking flow

## planning_decisions_needed
### decision 1: target precedence for template capture
recommended policy:
1. explicit template id selected by user
2. template-configured notebook target
3. explicit notebook from generic launch context or legacy shortcut
4. app default notebook fallback

reasoning:
- if the user deliberately picked a template, that choice should dominate placement
- current precedence makes template targeting feel broken even when the code is behaving as designed
- launcher shortcuts that are intended to represent a notebook-specific capture flow should become a separate, explicit variant later if needed

### decision 2: launcher shortcut behavior
recommended v1.1 behavior:
- notebook shortcuts that open template capture should not silently override template notebook routing
- if a shortcut must force a notebook target, that should be represented as a different shortcut type later rather than the default behavior

### decision 3: customization scope
recommended near-term scope:
- postpone add/remove/custom template CRUD
- first stabilize built-in templates, routing, and sharing
- optionally rename settings section to "built-in capture templates" to reduce expectation mismatch

## relevant_existing_patterns
- `README.org`
  - repo-level product framing and local build/test guidance
- `docs/brainstorms/2026_05_06_capture_templates_target_routing_requirements.md`
  - original routing and v1 template scope
- `docs/plans/2026_05_06_capture_templates_target_routing_plan.md`
  - current implementation plan that established the existing precedence
- `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - built-in template definitions, payload construction, and target resolution
- `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
  - current tests, including one that explicitly expects explicit book override to win
- `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - share parsing, image import behavior, routing extras, and note editor launch
- `app/src/main/java/com/orgzly/android/ui/TemplateChooserActivity.java`
  - existing launcher shortcut creation path that bakes in `EXTRA_BOOK_ID`
- `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - per-template enabled/share/notebook settings storage
- `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - current settings UI, including freeform notebook-name entry
- `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`
  - app-level default new-note state/schedule behavior that templates currently inherit

## implementation_units
### 1. correct inbox task semantics
- files:
  - `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
- changes:
  - make inbox task force a `TODO` state regardless of blank global default state
  - decide whether inbox task should preserve any global scheduled defaults or remain unscheduled by default
  - add direct test coverage for inbox-task state behavior
- dependencies:
  - none beyond current payload builder
- risks:
  - if users expect inbox task to respect their custom default keyword instead of literal `TODO`, forcing `TODO` may be too rigid
- recommendation:
  - use `TODO` for now because the template name implies task capture rather than generic note creation
- tests:
  - `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
- acceptance:
  - selecting inbox task always opens a new note with a task state

### 2. change routing precedence for template-driven capture
- files:
  - `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - `app/src/main/java/com/orgzly/android/ui/TemplateChooserActivity.java`
  - `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
- changes:
  - revise target resolution so template-configured notebook wins for user-selected templates
  - distinguish between:
    - generic launch context carrying a notebook id
    - explicit template selection
    - legacy shortcut behavior
  - update tests to reflect the new precedence rather than the current explicit-book-wins assumption
- dependencies:
  - requires a product decision on precedence and shortcut semantics
- risks:
  - changing precedence may surprise users who rely on notebook-scoped shortcuts for fixed placement
- mitigation:
  - keep fallback behavior predictable and document the new rule in settings/help text
- tests:
  - `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
  - add cases for current notebook context, share flow, and shortcut-launched template captures
- acceptance:
  - selecting repeating chore routes to its configured notebook even when launched from another notebook
  - selecting a template from a home-screen shortcut does not silently ignore the template target

### 3. harden template target settings UX
- files:
  - `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - possible new settings helper / chooser UI classes under `app/src/main/java/com/orgzly/android/ui/`
- changes:
  - replace or augment freeform notebook-name entry with a notebook picker
  - if freeform input remains temporarily, validate values and warn on missing notebooks
  - clarify that built-in templates have configurable targets but are not yet fully user-editable templates
- dependencies:
  - can be done independently of image-share work
- risks:
  - adding a proper picker may touch preferences architecture more than expected
- tests:
  - manual QA in settings
  - unit coverage if target-setting parsing logic is extracted
- acceptance:
  - a user can set a target notebook without guessing the exact notebook string
  - invalid notebook targets no longer fail silently

### 4. fix image-share import for `content://` providers
- files:
  - `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - possible new helper under `app/src/main/java/com/orgzly/android/util/` or `.../ui/share/`
  - tests under `app/src/test/java/com/orgzly/android/ui/share/` if practical
- changes:
  - stop depending on `MediaStore.Images.Media.DATA` as the main image-handling path
  - read the shared image via `ContentResolver`
  - decide a stable app-supported behavior:
    - copy to app-managed storage and insert a supported link, or
    - explicitly import into a location Orgzly can reference reliably
  - keep filename/title extraction as a best-effort enhancement rather than the core success path
- dependencies:
  - requires deciding where imported images should live
- risks:
  - attachment/location policy can sprawl if not bounded
- recommendation:
  - implement the smallest viable import path that works with modern providers and existing note-link behavior
- tests:
  - focused unit/instrumentation coverage if possible
  - manual QA with Firefox Focus or another provider-based share source
- acceptance:
  - sharing an image from a provider-backed source no longer inserts the current failure message

### 5. clean up product framing for built-in vs custom templates
- files:
  - `app/src/main/res/values/strings.xml`
  - any settings/help surfaces exposing capture templates
  - optional docs note in `docs/`
- changes:
  - rename or clarify UI labels so users do not assume full custom-template management exists today
  - optionally add a backlog note for future custom template CRUD
- dependencies:
  - none
- risks:
  - purely cosmetic if not paired with routing fixes
- tests:
  - manual QA for labels and settings comprehension
- acceptance:
  - settings no longer imply that users can create/delete/edit arbitrary templates today

## sequencing
1. lock the product decision on template target precedence
2. fix inbox task semantics and add tests
3. change routing precedence and update capture-template tests
4. improve target-setting UX so routing failures are visible and preventable
5. implement the modern image-share path
6. do a full manual QA sweep across in-app capture, share text, share image, and launcher shortcuts

## risks_and_unknowns
- risk: changing precedence may break legitimate notebook-scoped shortcut expectations
  - mitigation: define whether notebook-forcing shortcuts remain a supported concept or become a separate explicit shortcut type
- risk: image-share fix can balloon into attachment-system design work
  - mitigation: keep the first fix scoped to a reliable import/link path for single shared images only
- risk: notebook settings UX may require more plumbing than a simple preference XML tweak
  - mitigation: allow a staged approach where validation ships before a richer picker if needed
- risk: inbox task state semantics may conflict with user-specific keyword setups
  - mitigation: confirm whether literal `TODO` is acceptable or whether "first todo keyword" is the better rule

## validation_strategy
- unit tests:
  - update `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt` for new routing and inbox-task expectations
- build verification:
  - assemble the fdroid debug build after each implementation slice
- manual QA checklist:
  - from notebook A, choose repeating chore with notebook B configured and verify save target is notebook B
  - share text from another app and verify both imported content and configured template target
  - share image from a provider-based source and verify the image import path succeeds
  - create a launcher shortcut, choose a template with its own target, and verify actual precedence matches the chosen policy
  - verify inbox task opens with task state every time

## handoff_notes
- the biggest product problem is not only code correctness; it is mismatch between current routing precedence and what users expect template selection to mean
- update the old explicit-book-wins test carefully; it currently encodes behavior that appears to be the wrong product decision, not just a bug
- avoid starting custom-template CRUD before the built-in template flows are stable
- if implementation reveals that image import needs a broader attachment design decision, stop and write a short follow-up brainstorm instead of quietly expanding scope