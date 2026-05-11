# custom capture templates implementation plan

created: 2026-05-09
status: active
source_doc: none

## objective
- extend Orgzly's current built-in capture-template feature so users can create their own templates and remove templates they no longer want
- preserve the current fast in-app and share-flow capture behavior while replacing the hard-coded template catalog with a user-managed one
- keep the first custom-template version bounded to fields that materially affect mobile capture speed, not a full org-capture scripting system

## scope
- included:
  - user-created capture templates
  - removal of existing templates from the active catalog
  - migration of the current five built-in templates into a persisted template catalog
  - a template-management UI for listing, creating, editing, and deleting templates
  - picker integration for both in-app capture and share flow using the persisted catalog
  - migration of current template settings (`enabled`, `share enabled`, `target notebook`) into the new storage model
  - tests for migration, catalog queries, payload building, and delete behavior
- excluded:
  - arbitrary scripting or org-capture parity
  - parent-note routing
  - launcher-shortcut redesign beyond preserving current behavior for existing template ids where practical
  - rich icon customization, template folders, or sync/export of template definitions as a separate feature
  - broad note-form wizard UI beyond a focused template editor

## current_code_state
- `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - defines templates as a hard-coded `enum class CaptureTemplate`
  - entry-point helpers already centralize the important read paths:
    - `enabledTemplates(context)`
    - `shareEnabledTemplates(context)`
    - `buildPayload(...)`
    - `resolveTargetBook(...)`
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - already shows a runtime template picker from `CaptureTemplates.enabledTemplates(...)`
  - this is a good seam: picker UI already expects a dynamic list, but the list source is still enum-backed
- `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - already supports explicit template selection via `AppIntent.EXTRA_CAPTURE_TEMPLATE_ID`
  - already branches between blank note / one template / multi-template picker using `CaptureTemplates.shareEnabledTemplates(...)`
- `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- `app/src/main/java/com/orgzly/android/ui/note/NoteViewModel.kt`
  - already accept a full `NotePayload` for new notes
  - this removes the biggest architecture risk from the earlier v1 plan: custom templates do **not** need a new note-editor initialization path
- `app/src/main/res/xml/prefs_screen_notebooks.xml`
- `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - current settings are statically declared once per built-in template
  - this design cannot scale to user-created templates because unknown templates cannot be represented in static XML
- `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - template configuration is currently fragmented across generated shared-preference keys:
    - `pref_key_capture_template_<id>_enabled`
    - `pref_key_capture_template_<id>_share_enabled`
    - `pref_key_capture_template_<id>_notebook`
  - this works for a fixed enum, but not for CRUD
- `app/src/test/java/com/orgzly/android/capture/CaptureTemplatesTest.kt`
  - current test coverage is focused on hard-coded template payload rules and routing precedence

## planning_decisions
- recommended product interpretation:
  - users manage a single template catalog containing both migrated built-in templates and user-created templates
  - "remove existing templates" should mean removing templates from the active catalog, including migrated built-ins
- recommended technical interpretation:
  - do **not** keep built-ins as a permanent enum-only source of truth plus a separate custom-template layer
  - instead, seed built-in presets into persisted rows and have all runtime pickers operate on the persisted catalog
- reason:
  - separate built-in/custom code paths create permanent branching in picker UI, settings, deletion semantics, migration logic, and future edits
  - a unified catalog makes add/edit/delete behavior consistent
- recommended deletion rule:
  - use soft-delete tombstones for seeded built-ins so app upgrades do not silently recreate templates the user deleted
  - custom templates may also use the same soft-delete path for simplicity and easier undo-safe future work
- recommended custom-template v1 fields:
  - template name
  - title default/pattern
  - body scaffold
  - default state
  - tags
  - target notebook
  - enabled flag
  - available-in-share flag
- defer for now:
  - arbitrary properties editor
  - deadline/scheduled/repeater rule builder for fully custom templates
  - custom icons
- rationale:
  - this still enables meaningful custom capture without dragging the feature into a large form-builder project

## relevant_existing_patterns
- `README.org`
  - repo-level product and build context
- `docs/brainstorms/2026_05_06_capture_templates_target_routing_requirements.md`
  - explicitly said v1 should create a foundation for later user-defined templates
- `docs/plans/2026_05_06_capture_templates_target_routing_plan.md`
  - useful for tracing the original capture-template architecture, but some assumptions are now outdated because full payload initialization is already implemented
- `docs/plans/2026_05_07_capture_template_manual_qa_fix_plan.md`
  - explicitly deferred custom-template CRUD and documents the current hard-coded limitation
- `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - current central integration seam; likely remains the facade, but should stop being enum-backed
- `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
- `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - both already consume template lists at runtime, so most entry-point changes should be backend/model swaps plus picker label changes, not full flow rewrites
- `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - current notebook-preference setup shows how notebook lists are populated dynamically; that pattern can be reused inside a dedicated template editor
- `app/src/main/java/com/orgzly/android/ui/note/NotePayload.kt`
  - confirms the persisted-template layer only needs to translate into an existing payload model
- `app/src/main/java/com/orgzly/android/db/OrgzlyDatabase*`, DAO/entity patterns nearby
  - best place to mirror the app's existing persisted-data conventions instead of packing template CRUD into shared preferences

## architecture_direction
- replace the enum-as-catalog approach with a persisted capture-template repository
- keep a small domain layer that separates:
  1. template storage and migrations
  2. template-to-`NotePayload` building
  3. template selection and filtering for in-app/share entry points
- use a seeded-preset model rather than shipping built-ins only in code forever
- preserve a small code-defined preset library only as a seeding/default-definition source, not as the runtime catalog itself
- move the settings surface from static preference XML blocks to a dedicated management screen because dynamic CRUD is the real requirement

## proposed_data_model
### runtime model
- add a persisted model such as `CaptureTemplateEntity` / `CaptureTemplateModel` with fields along these lines:
  - `id: String`
  - `name: String`
  - `source_type: BUILT_IN | CUSTOM`
  - `preset_key: String?` for seeded built-ins
  - `enabled: Boolean`
  - `share_enabled: Boolean`
  - `target_notebook_name: String?`
  - `title_template: String?`
  - `body_template: String?`
  - `default_state: String?`
  - `tags_csv` or normalized tag storage
  - `template_kind: TASK | NOTE` if needed to keep builder logic simple
  - `position: Int` for picker ordering
  - `deleted: Boolean`
  - timestamps if the repo commonly stores them for user-authored records
### preset definitions
- keep a small code-defined preset list for seeding defaults such as:
  - inbox task
  - repeating chore
  - meeting note
  - learning note
  - business idea
- each preset definition should include:
  - stable `preset_key`
  - default values for all persisted fields
- important constraint:
  - seeding must be idempotent and must respect soft-deleted preset rows so deleted built-ins are not recreated on every launch or upgrade

## implementation_units
### 1. introduce persisted capture-template storage and migration
- files:
  - `app/src/main/java/com/orgzly/android/db/entity/` (new capture-template entity)
  - `app/src/main/java/com/orgzly/android/db/dao/` (new DAO)
  - `app/src/main/java/com/orgzly/android/db/OrgzlyDatabase*`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/capture/` (new preset-seeding helper)
  - `app/src/test/java/com/orgzly/android/` or `.../capture/` (migration/repository tests)
- changes:
  - add a Room-backed storage model for capture templates
  - add queries for:
    - all active templates ordered for picker display
    - share-enabled active templates
    - template by id
    - create/update/delete
  - add a one-time migration path that converts existing built-in template preference values into seeded persisted rows
  - define deterministic ids for seeded templates so existing extras and tests can still refer to stable template ids
- dependencies:
  - must preserve compatibility with current `AppIntent.EXTRA_CAPTURE_TEMPLATE_ID`
- risks:
  - migration bugs could make existing users lose template settings or see duplicate templates
- tests:
  - migration from shared prefs to DB rows
  - soft-deleted built-in stays deleted after reseed pass
  - existing built-in target notebook/share-enabled values are preserved
- acceptance:
  - a fresh install gets the five default templates as persisted rows
  - an upgraded install gets equivalent rows seeded from current settings without duplication

### 2. replace enum-backed runtime APIs with repository-backed catalog access
- files:
  - `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/AppIntent.java`
  - tests under `app/src/test/java/com/orgzly/android/capture/`
- changes:
  - stop using `CaptureTemplate.values()` as the runtime catalog
  - refactor `enabledTemplates(...)`, `shareEnabledTemplates(...)`, `fromId(...)`, `buildPayload(...)`, and `resolveTargetBook(...)` to operate on a persisted model fetched from the repository
  - keep a thin compatibility layer if current callers still expect a `CaptureTemplate` type, but prefer introducing a real data model instead of stretching the enum
  - preserve explicit template-id intent handling in share flow and any future shortcut integrations
- dependencies:
  - unit 1 storage/repository layer
- risks:
  - trying to keep both enum and entity as first-class runtime models will create fragile duplication
- recommendation:
  - retire the enum from runtime selection entirely; if needed, replace it with `CaptureTemplatePreset` used only for seeding defaults
- tests:
  - template lookup by id
  - enabled/share filtering
  - payload generation for seeded built-ins after migration
- acceptance:
  - in-app and share pickers read the active persisted catalog rather than hard-coded enum values

### 3. split built-in payload rules from the catalog and define bounded custom-template semantics
- files:
  - `app/src/main/java/com/orgzly/android/capture/CaptureTemplates.kt`
  - possible new files such as:
    - `CaptureTemplatePreset.kt`
    - `CaptureTemplatePayloadBuilder.kt`
    - `CaptureTemplateDefaults.kt`
  - tests under `app/src/test/java/com/orgzly/android/capture/`
- changes:
  - extract the hard-coded preset-specific logic currently embedded in the enum switch statements
  - define two payload-building paths:
    1. seeded preset templates that may still apply special defaults like meeting title date or repeating-chore scheduled behavior
    2. generic custom templates using the bounded editable fields from persisted storage
  - define a simple token approach for title/body defaults if needed, e.g. date token support only where clearly valuable
- dependencies:
  - unit 2 runtime model refactor
- risks:
  - if custom templates are allowed to edit every built-in behavior immediately, the payload builder becomes a mini DSL project
- recommendation:
  - keep special behaviors attached to preset-derived templates only in this phase
  - let user-created templates cover the high-value common case: custom title/body/state/tags/notebook/share visibility
- tests:
  - preset-derived meeting note still creates dated title when title is blank
  - custom template with explicit title/body/state/tags maps cleanly into `NotePayload`
  - deleted template id fails safely rather than crashing
- acceptance:
  - built-in-derived templates retain their current useful defaults
  - user-created templates can generate valid notes without requiring scripting

### 4. add a template-management UI instead of static XML-only settings
- files:
  - `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - new UI under something like:
    - `app/src/main/java/com/orgzly/android/ui/capture/TemplateListFragment.kt`
    - `app/src/main/java/com/orgzly/android/ui/capture/TemplateEditorFragment.kt`
    - adapter/view-model classes as needed
  - new layouts under `app/src/main/res/layout/`
  - `app/src/main/res/values/strings.xml`
  - likely a small nav/launch hook from the settings screen
- changes:
  - replace the static per-template `PreferenceCategory` blocks with a single entry such as "Manage capture templates"
  - add a list screen showing active templates in display order with quick status visibility
  - add create flow:
    - start from blank template, or
    - duplicate a preset / duplicate an existing template
  - add edit flow for name, title/body/state/tags/notebook/enabled/share-enabled
  - add delete flow from the editor or list item overflow
- dependencies:
  - units 1 through 3
- risks:
  - trying to force fully dynamic CRUD into `PreferenceScreen` XML will be awkward and brittle
- recommendation:
  - use a normal fragment + RecyclerView/form UI for template management, even if it is launched from settings
- tests:
  - focused view-model tests if editor state is extracted cleanly
  - manual QA for create/edit/delete flows
- acceptance:
  - users can create a custom template without editing shared-preference keys indirectly
  - users can remove a template from the catalog in a discoverable way

### 5. update in-app capture picker to use the managed catalog
- files:
  - `app/src/main/java/com/orgzly/android/ui/notes/book/BookFragment.kt`
  - possible shared picker helper under `app/src/main/java/com/orgzly/android/ui/capture/`
  - tests if picker-building logic is extracted
- changes:
  - load template rows from repository-backed APIs
  - show custom template names instead of only string-resource labels
  - keep the blank-note option first
  - preserve current narrowed-book blank-note behavior while letting template routing continue through `resolveTargetBook(...)`
  - optionally add ordering support so the user can decide which templates appear first
- dependencies:
  - units 1 through 4
- risks:
  - if template ordering is undefined, the picker may feel unstable after CRUD
- tests:
  - ordering and filtering tests if list assembly is extracted
  - manual QA for create template -> picker visibility -> note creation
- acceptance:
  - newly created templates appear in the in-app capture picker without app restart
  - deleted templates disappear from the picker and cannot be selected

### 6. update share flow to use the managed catalog and safe delete semantics
- files:
  - `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - possible shared picker helper under `app/src/main/java/com/orgzly/android/ui/capture/`
  - tests under `app/src/test/java/com/orgzly/android/ui/share/` and/or capture tests
- changes:
  - load share-enabled templates from the persisted catalog
  - use template names from storage instead of resource-only labels where appropriate
  - keep explicit template-id handling, but if the template was deleted or disabled, fail safely into either blank note or the regular picker
  - preserve current share-content parsing and payload handoff
- dependencies:
  - units 1 through 4
- risks:
  - deleted template ids lingering in shortcuts/intents can cause confusing behavior if not handled explicitly
- recommendation:
  - if a requested template id is missing/deleted, log it and fall back to the normal picker path instead of crashing or silently creating a broken note
- tests:
  - one share-enabled template
  - multiple share-enabled templates
  - explicit deleted template id falls back safely
- acceptance:
  - share flow keeps working with custom templates and after template deletion

### 7. retire or narrow old shared-preference code paths
- files:
  - `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`
  - `app/src/main/res/xml/prefs_screen_notebooks.xml`
  - `app/src/main/java/com/orgzly/android/ui/settings/SettingsFragment.kt`
  - tests touching legacy preference migration if needed
- changes:
  - remove the static per-template preference UI blocks after migration is stable
  - keep only migration helpers for old keys, or delete them once the migration path is confirmed and versioned appropriately
  - update any strings/help text that still describe templates as fixed built-ins only
- dependencies:
  - unit 4 management UI
- risks:
  - deleting migration helpers too early would make upgrade testing harder
- recommendation:
  - keep legacy preference readers only as migration support for one release cycle, then remove them later
- tests:
  - upgrade smoke test from old prefs-backed state
- acceptance:
  - there is one clear source of truth for template configuration

## sequencing
1. add persisted template entity/DAO/repository support and seed definitions
2. implement migration from existing shared-pref template settings into persisted rows
3. refactor `CaptureTemplates` runtime APIs to use persisted rows instead of enum catalog data
4. extract payload-building rules so preset-derived templates and custom templates are both supported cleanly
5. add management UI for list/create/edit/delete
6. switch book-screen picker to the new catalog
7. switch share flow to the new catalog and add missing-template fallback behavior
8. remove or narrow legacy prefs UI/code after migration is proven

## risks_and_unknowns
- risk: treating custom templates as fully arbitrary from day one will sprawl into a template language project
  - mitigation: bound v1 custom fields tightly and preserve special repeater/date behavior only for preset-derived templates
- risk: deleted built-ins could reappear after upgrade if seeding logic is naive
  - mitigation: use tombstones/soft delete keyed by stable preset ids
- risk: migration from shared preferences to DB can duplicate or overwrite user choices
  - mitigation: make migration idempotent and test upgrade paths explicitly
- risk: stale template ids from shortcuts or explicit intents may reference deleted templates
  - mitigation: resolve by id defensively and fall back to picker/blank-note flow
- risk: static settings architecture is the wrong place for CRUD and could slow implementation if forced
  - mitigation: move template management to a dedicated fragment launched from settings
- risk: branch already has unrelated uncommitted `ShareActivity.java` changes
  - mitigation: isolate this plan's future implementation work carefully so share-flow refactors do not accidentally trample in-progress image-share fixes

## validation_strategy
- unit tests:
  - template migration from legacy prefs
  - repository filtering/order/delete behavior
  - payload generation for seeded presets and simple custom templates
  - missing/deleted template id fallback behavior
- build verification:
  - `./scripts/build_fdroid.sh assembleFdroidDebug`
- manual QA:
  - create custom template -> verify it appears in in-app picker
  - create custom template with share enabled -> verify it appears in share picker
  - delete a migrated built-in template -> verify it disappears and stays gone after app restart
  - edit target notebook -> verify capture routes correctly
  - share explicit template id for a deleted template -> verify safe fallback

## handoff_notes
- the most important architectural choice is unifying built-ins and customs into one persisted catalog; avoid a parallel "enum built-ins + DB customs" model unless forced by an external constraint
- `NoteFragment`/`NoteViewModel` already support full payload initialization, so do not spend time re-solving that problem
- the current static settings XML is a dead end for CRUD; move quickly to a dedicated management UI instead of fighting `PreferenceScreen`
- preserve stable template ids for seeded templates because share flow already supports explicit template-id intents
- implementation should inspect Room versioning and existing migration patterns before touching the database schema
- implementation should treat the uncommitted `ShareActivity.java` diff as pre-existing local work and avoid overwriting it blindly
