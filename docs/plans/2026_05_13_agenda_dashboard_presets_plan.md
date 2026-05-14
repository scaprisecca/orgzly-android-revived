# agenda dashboard presets implementation plan

created: 2026-05-13
status: active
source_doc: docs/brainstorms/2026_05_13_agenda_dashboard_presets_requirements.md

## objective
- add a guided saved-search builder so users can create agenda-style task views without memorizing Orgzly query syntax
- keep created views as normal saved searches that appear in the existing drawer/sidebar and use the existing query infrastructure
- seed a useful default set of agenda presets for Scott's personal testing workflow while avoiding destructive overwrites of user-edited searches

## scope
- included:
  - guided saved-search builder for notebooks, tags, TODO states, properties, date filters/date sources, sort preference, include/exclude sections, and saved-search name
  - optional metadata on saved searches so builder-created searches can be reopened in the guided editor
  - raw-query fallback for existing/imported/manual searches without valid builder metadata
  - query compiler layer that generates current dotted query syntax where possible
  - query-language extensions for property exists / property equals
  - query/date-source option support so agenda rendering can include scheduled, deadline, and/or event timestamps intentionally
  - built-in preset definitions and idempotent seeding for Today / overdue, Next 7 days, No Date, Waiting, Calendar, Home, Business, and Recurring chores
  - parser, SQL, compiler, seeding, and focused UI tests where practical
  - manual QA checklist for creating, editing, and using views across multiple notebooks
- excluded:
  - full Emacs Org agenda parity
  - replacing the query engine or saved-search system
  - full visual editor for every possible query expression
  - property contains/fuzzy matching or property comparisons
  - automatic creation of missing default notebooks/files such as `calendar.org`, `home.org`, `business.org`, or `routines.org`
  - dashboard result-row explanations / “why did this match?” details
  - rich dashboard grouping by notebook/priority beyond existing list/agenda rendering unless needed by a concrete bug

## current_code_state
- no `AGENTS.md` or `CLAUDE.md` was present at repo root during planning.
- `README.org` confirms this is Orgzly Revived, an Android Org-mode outliner/task app, and documents the local build helpers.
- `app/src/main/java/com/orgzly/android/db/entity/SavedSearch.kt`
  - current model has only `id`, `name`, `query`, and `position`.
- `app/src/main/java/com/orgzly/android/db/dao/SavedSearchDao.kt`
  - current DAO supports get/list/create/update/delete/reorder by position.
- `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - saved-search CRUD is centralized around lines 2187-2256.
  - import/export stores saved searches as a JSON object of `name -> query` only.
  - query selection parses a string through `InternalQueryParser`, builds SQL with `SqliteQueryBuilder`, and runs it through `NoteViewDao`.
  - agenda queries currently add a broad scheduled/deadline/event existence clause whenever `agendaDays > 0`.
- `app/src/main/java/com/orgzly/android/db/OrgzlyDatabase.kt`
  - database version is currently `159`.
  - default searches are inserted by `insertDefaultSearches(...)` on database creation.
  - `CaptureTemplateSeeder.seedMissingTemplates(...)` is already called on create/open and is a useful pattern for idempotent preset seeding.
- `app/src/main/java/com/orgzly/android/db/PreRoomMigration.kt`
  - older migration `145 -> 146` inserted the original Agenda and Next 3 days saved searches into the legacy `searches` table.
- `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
  - the FAB currently calls `listener?.onSavedSearchNewRequest()` and opens the raw saved-search editor.
- `app/src/main/java/com/orgzly/android/ui/savedsearch/SavedSearchFragment.java`
  - current editor is raw name + query fields backed by `fragment_saved_search.xml`.
- `app/src/main/java/com/orgzly/android/query/user/BasicQueryParser.kt`
- `app/src/main/java/com/orgzly/android/query/user/DottedQueryParser.kt`
- `app/src/main/java/com/orgzly/android/query/user/DottedQueryBuilder.kt`
- `app/src/main/java/com/orgzly/android/query/sql/SqliteQueryBuilder.kt`
  - query parser/builder/SQL pipeline supports books, states, state types, priority, tags, scheduled/deadline/event/closed/created intervals, text, sort orders, `agenda-days`/`ad.N`, grouping, OR, AND, and negation on many conditions.
  - no property query condition exists yet.
- `app/src/main/java/com/orgzly/android/db/entity/NoteProperty.kt`
- `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt`
  - properties are persisted in `note_properties` with indexes on `note_id`, `position`, `name`, and `value`, and there is already a distinct-name DAO query useful for builder suggestions.
- `app/src/main/java/com/orgzly/android/ui/notes/query/agenda/AgendaItems.kt`
  - agenda rendering currently expands scheduled, deadline, and event timestamps for every selected note.
  - this must change or become configurable to make “Today / overdue excludes events” and “Calendar uses events” reliable.
- `app/src/test/java/com/orgzly/android/query/QueryTest.kt`
  - current parameterized query tests are the best first place for parser/builder/SQL coverage.
  - existing tests already prove `s.no` / `s.none` can select notes without a scheduled timestamp, so No Date can likely compile to `s.none d.none .it.done`.

## architecture_direction
- use saved searches as the runtime surface, not a new dashboard table or separate navigation model
- add a small agenda-builder domain layer that owns structured builder state and query generation; do not spread query-string concatenation through fragments
- extend `SavedSearch` with optional metadata rather than replacing it:
  - raw saved searches remain valid
  - builder-created searches can reopen in the guided editor
  - imported/manual searches without metadata fall back to raw editing
- keep built-in preset definitions in code as seed definitions only; runtime behavior should still be normal saved searches
- extend the query language only for the concrete v1 gaps:
  - properties
  - agenda date-source filtering, because existing agenda behavior always includes scheduled/deadline/event timestamps once `ad.N` is present
- prefer Kotlin for new builder/domain code even though the existing raw editor fragment is Java

## proposed_data_model
### saved-search metadata columns
Add optional columns to `searches`:
- `builder_metadata` TEXT nullable
  - JSON representation of the guided builder state
  - source of truth for guided re-edit when valid
- `builder_metadata_version` INTEGER nullable
  - start with `1`
- `preset_key` TEXT nullable
  - stable id for seeded built-ins such as `today_overdue`, `next_7_days`, `no_date`, `waiting`, `calendar`, `home`, `business`, `recurring_chores`

Do not add hard-required columns that force existing saved searches into the builder model.

### builder metadata v1 shape
Persist a JSON object with fields conceptually equivalent to:
- `name`
- `includeNotebooks: List<String>`
- `excludeNotebooks: List<String>`
- `includeTags: List<String>`
- `excludeTags: List<String>`
- `includeStates: List<String>`
- `excludeStates: List<String>`
- `includeProperties: List<PropertyFilter>`
- `excludeProperties: List<PropertyFilter>`
- `excludeDone: Boolean`
- `dateFilter: NONE | TODAY_OVERDUE | NEXT_3 | NEXT_7 | NEXT_14 | NEXT_30 | NO_SCHEDULED_OR_DEADLINE | CUSTOM_ADVANCED`
- `dateSources: Set<SCHEDULED | DEADLINE | EVENT>`
- `sort: DATE | PRIORITY | NOTEBOOK`
- `advancedQuerySuffix: String?` or `customAdvancedQuery: String?`

Property filter v1:
- `name: String`
- `operator: EXISTS | EQUALS`
- `value: String?`

### query option additions
Add a date-source query option while preserving current behavior for old queries:
- default for manually-written agenda queries: scheduled + deadline + event, matching current behavior
- builder-generated agenda queries should include an explicit source option when the user chooses a subset
- suggested syntax:
  - dotted: `ads.s`, `ads.d`, `ads.sd`, `ads.e`, `ads.sde`
  - basic: `agenda-date-sources:scheduled,deadline,event`
- `Options` should carry the selected sources next to `agendaDays`.

The exact token names can change during implementation, but they must be short enough for generated queries and explicit enough for raw-query users to understand.

## built_in_presets
Seed these as normal saved searches with `preset_key` and `builder_metadata`:

1. Today / overdue
   - active tasks scheduled today/overdue OR deadline today/overdue
   - exclude done
   - date sources: scheduled + deadline only
   - agenda days: 1
   - candidate generated query: `.it.done (s.today or d.today) ad.1 ads.sd o.s o.d o.p`
2. Next 7 days
   - active tasks scheduled or deadline in the next 7 days
   - exclude done
   - date sources: scheduled + deadline
   - agenda days: 7
   - candidate generated query: `.it.done (s.7d or d.7d) ad.7 ads.sd o.s o.d o.p`
3. No Date
   - active tasks with no scheduled date and no deadline
   - exclude done
   - plain list, not agenda
   - candidate generated query: `.it.done s.none d.none o.p o.b`
4. Waiting
   - TODO state `WAITING` OR tag `waiting`
   - exclude done
   - plain list by default
   - candidate generated query: `.it.done (i.WAITING or t.waiting) o.b o.p`
5. Calendar
   - default notebook `calendar.org`
   - date source: event
   - agenda days: 7 or 30; choose 7 for v1 consistency unless Scott changes it during QA
   - candidate generated query: `.it.done b.calendar.org ad.7 ads.e o.e`
6. Home
   - tag `home` OR notebook `home.org`
   - exclude done
   - plain list by default
   - candidate generated query: `.it.done (t.home or b.home.org) o.p o.b`
7. Business
   - tag `business` OR notebook `business.org`
   - exclude done
   - plain list by default
   - candidate generated query: `.it.done (t.business or b.business.org) o.p o.b`
8. Recurring chores
   - notebook `routines.org`
   - exclude done
   - plain list by default
   - candidate generated query: `.it.done b.routines.org o.p o.b`

Seeding rules:
- fresh installs get the full set.
- existing installs get missing presets added.
- if an exact built-in name + exact old query exists, update only metadata/preset key; do not change the user's query text.
- if a built-in name exists but query differs, insert a non-destructive name such as `Today / overdue (preset)`.
- if `preset_key` already exists, skip it.
- do not silently create missing notebooks/files; show empty states or allow the user to change filters in the builder.

## implementation_units
### 1. add saved-search metadata and seeding foundation
- files:
  - `app/src/main/java/com/orgzly/android/db/entity/SavedSearch.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/SavedSearchDao.kt`
  - `app/src/main/java/com/orgzly/android/db/OrgzlyDatabase.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - create `app/src/main/java/com/orgzly/android/savedsearch/AgendaPresetSeed.kt`
  - create `app/src/main/java/com/orgzly/android/savedsearch/AgendaPresetSeeder.kt`
  - tests under `app/src/test/java/com/orgzly/android/savedsearch/`
- changes:
  - bump Room database version from `159` to `160`
  - add migration `159 -> 160` for nullable `builder_metadata`, nullable `builder_metadata_version`, and nullable `preset_key`
  - update `SavedSearch.areContentsTheSame(...)` to include metadata fields where UI list behavior requires refresh
  - add DAO helpers for lookup by `preset_key`, exact name, and exact name/query if needed for safe seeding
  - add a preset seeder modeled after `CaptureTemplateSeeder.seedMissingTemplates(...)`
  - call the preset seeder from database `onCreate`/`onOpen` after the `searches` table exists
  - keep import/export backward compatible by continuing to export name/query only in v1 unless a separate compatibility decision is made
- dependencies:
  - must happen before guided re-edit or built-in presets can work cleanly
- risks:
  - seeding on every open can duplicate rows if idempotency is wrong
  - adding metadata to `areContentsTheSame` may trigger unnecessary list updates if not handled carefully
- tests:
  - fresh DB seeds all built-in presets once
  - reopening DB does not duplicate presets
  - existing same-name different-query saved search is not overwritten
  - exact old built-in can receive metadata without changing query
- acceptance:
  - saved searches still load and reorder as before
  - built-ins appear in the saved-search list/drawer on fresh install and are not duplicated on reopen

### 2. create builder state model and query compiler
- files:
  - create `app/src/main/java/com/orgzly/android/savedsearch/builder/AgendaViewBuilderState.kt`
  - create `app/src/main/java/com/orgzly/android/savedsearch/builder/AgendaViewQueryCompiler.kt`
  - create `app/src/main/java/com/orgzly/android/savedsearch/builder/AgendaViewMetadataJson.kt`
  - tests under `app/src/test/java/com/orgzly/android/savedsearch/builder/`
- changes:
  - define strongly-typed builder state matching the metadata v1 shape
  - implement metadata JSON serialization/deserialization with graceful invalid-metadata failure
  - compile builder state to a `Query` object first, then to dotted query text through `DottedQueryBuilder` where possible
  - build OR groups for multiple values within one field group
  - build AND groups across different field groups
  - build NOT/exclusion using existing condition `not` flags where supported
  - append custom advanced query only for the explicit advanced-query path; avoid silently mixing arbitrary text into guided fields
  - centralize query examples/preset defaults here, not in UI fragments
- dependencies:
  - property conditions and agenda date-source options may initially be represented in state but fail compilation until units 3 and 4 land
- risks:
  - incorrect grouping can hide tasks; this is the highest functional risk
  - raw text concatenation would be brittle; prefer AST/`Condition` construction
- tests:
  - `(tag A OR tag B) AND (notebook X OR notebook Y)` compiles with correct parentheses
  - include/exclude tags compile to positive and negative tag conditions
  - No Date compiles to scheduled none + deadline none + exclude done
  - Today / overdue and Next 7 generate scheduled OR deadline groups
  - invalid metadata returns a raw-editor fallback result instead of crashing
- acceptance:
  - compiler can generate every built-in preset query from structured state
  - generated query text is visible and parseable by `InternalQueryParser`

### 3. add property query support
- files:
  - `app/src/main/java/com/orgzly/android/query/Condition.kt`
  - `app/src/main/java/com/orgzly/android/query/user/BasicQueryParser.kt`
  - `app/src/main/java/com/orgzly/android/query/user/DottedQueryParser.kt`
  - `app/src/main/java/com/orgzly/android/query/user/DottedQueryBuilder.kt`
  - `app/src/main/java/com/orgzly/android/query/sql/SqliteQueryBuilder.kt`
  - `app/src/test/java/com/orgzly/android/query/QueryTest.kt`
  - potentially `app/src/test/java/com/orgzly/android/query/QueryResultTest.kt`
- changes:
  - add `Condition.HasProperty(name, value?, not)` or equivalent variants for property exists and property equals
  - add basic syntax, suggested:
    - `property:name`
    - `property:name=value`
    - `-property:name`
    - `-property:name=value`
  - add dotted syntax, suggested:
    - `prop.name`
    - `prop.name=value`
    - `.prop.name`
    - `.prop.name=value`
  - generate SQL using `EXISTS` against `note_properties` correlated by note id
  - quote/escape property names and values through selection args, not string interpolation
  - use case-insensitive name matching if consistent with `NotePropertyDao.allDistinctNames()` grouping by `LOWER(name)`; document the choice in tests
- dependencies:
  - compiler/UI property filters depend on this
- risks:
  - SQL aliases from `NoteViewDao.QUERY_WITH_NOTE_EVENTS` must be verified; if `id` is ambiguous, the correlated subquery should reference the selected note id column explicitly
  - property names/values with spaces or punctuation need query quoting tests
- tests:
  - parser recognizes property exists and equals in basic and dotted syntax
  - builder round-trips property conditions through `DottedQueryBuilder`
  - SQL selection uses `EXISTS` with expected args
  - query result test proves property filters return the right notes and do not duplicate notes with multiple properties
- acceptance:
  - builder can include/exclude property exists and property equals filters
  - raw-query users can type the new property syntax and get correct results

### 4. add agenda date-source query options and rendering support
- files:
  - `app/src/main/java/com/orgzly/android/query/Options.kt`
  - `app/src/main/java/com/orgzly/android/query/user/BasicQueryParser.kt`
  - `app/src/main/java/com/orgzly/android/query/user/DottedQueryParser.kt`
  - `app/src/main/java/com/orgzly/android/query/user/DottedQueryBuilder.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/agenda/AgendaItems.kt`
  - `app/src/test/java/com/orgzly/android/query/QueryTest.kt`
  - agenda tests if existing coverage supports them
- changes:
  - extend `Options` with selected agenda date sources, defaulting to scheduled + deadline + event for backward compatibility
  - parse and build the new source option syntax
  - update `DataRepository.buildSqlQuery(...)` agenda existence clause so it includes only selected source types
  - update `AgendaItems` so it expands only selected source types
  - keep old raw agenda queries without source option behaving exactly as before
- dependencies:
  - built-ins that exclude events or show calendar events depend on this
- risks:
  - changing agenda selection/rendering can regress existing saved searches if defaults are not backward compatible
  - grouping by `event_timestamp` may need to remain only when event source is included
- tests:
  - old `ad.7` still includes scheduled/deadline/event
  - `ads.sd ad.7` includes scheduled/deadline and excludes event-only notes
  - `ads.e ad.7` includes event-only notes and does not expand scheduled/deadline rows
  - `DottedQueryBuilder` emits stable option text
- acceptance:
  - Today / overdue preset does not show calendar/event rows
  - Calendar preset can show event rows without also showing scheduled/deadline rows from the same selected notes

### 5. build guided saved-search editor UI
- files:
  - either replace/extend `app/src/main/java/com/orgzly/android/ui/savedsearch/SavedSearchFragment.java` or create a Kotlin sibling such as `AgendaSavedSearchBuilderFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
  - `app/src/main/res/layout/fragment_saved_search.xml` or new builder layouts under `app/src/main/res/layout/`
  - `app/src/main/res/menu/saved_searches_actions.xml` if adding a menu choice
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt` for suggestions/read helpers if not already present
- changes:
  - make the saved-search FAB offer a clear path to “New agenda view” while preserving access to raw query creation
  - recommended low-risk UX: FAB opens a small choice dialog: `Build agenda view` / `Write raw query`
  - on edit:
    - if saved search has valid builder metadata, open guided builder
    - otherwise open existing raw editor
  - guided builder sections:
    - view name
    - Include: notebooks, tags, TODO states, properties
    - Exclude: notebooks, tags, TODO states, properties
    - Dates: filter and source checkboxes
    - Sort
    - generated query preview
    - Advanced/raw escape hatch
  - populate notebook choices from `DataRepository.getBooks()` or equivalent existing book APIs
  - populate property-name choices from existing `DataRepository.getNotePropertyNames()` / `NotePropertyDao.allDistinctNames()` path if available; otherwise add a repository method to expose it
  - validate empty name and invalid generated query before save
  - save both query text and metadata for guided-builder searches
  - if the user manually edits raw query in advanced mode beyond what metadata represents, mark/degrade metadata so future edits use raw mode
- dependencies:
  - units 1 and 2 for metadata and query generation
  - unit 3 for property filters
  - unit 4 for date-source behavior
- risks:
  - too much form complexity on mobile; keep v1 controls simple and collapsible
  - Java/Kotlin interop friction if extending the existing Java fragment heavily
- tests:
  - focused ViewModel/compiler tests should carry most behavior
  - fragment UI tests only if the repo already has stable patterns; otherwise rely on build + manual QA
- acceptance:
  - user can create a Home/Business-style agenda view in under one minute without typing query syntax
  - generated query is visible before save
  - raw saved-search editing still works

### 6. wire built-ins into the builder metadata and drawer flow
- files:
  - `app/src/main/java/com/orgzly/android/savedsearch/AgendaPresetSeed.kt`
  - `app/src/main/java/com/orgzly/android/savedsearch/AgendaPresetSeeder.kt`
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt` only if metadata display or ordering needs adjustment
  - `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
- changes:
  - ensure each built-in seed uses the same builder-state model the UI edits
  - order seeded presets so high-frequency views are near the top
  - keep drawer behavior unchanged: saved searches appear via existing saved-search list
  - handle missing default notebooks through empty results plus editability, not auto-creation
- dependencies:
  - units 1, 2, and 4
- risks:
  - old `Agenda`, `Next 3 days`, `Scheduled`, and `To Do` defaults may coexist with new presets; that may be acceptable for Scott testing but should be visible in QA
- tests:
  - seeded preset metadata can be parsed into builder state
  - seeded preset query matches compiler output from metadata
- acceptance:
  - seeded built-ins show up as normal saved searches and can be guided-edited

### 7. polish import/export, empty states, and documentation/changelog
- files:
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/res/values/strings.xml`
  - `CHANGELOG.md`
  - possibly documentation under `docs/` if the repo has a local feature-doc convention
- changes:
  - decide whether import/export remains name/query-only for v1 or includes optional metadata in a backward-compatible shape
  - recommended v1: keep export/import name/query-only and treat imported searches as raw unless a later compatibility task expands the format
  - add user-facing empty-state/help text for missing notebooks and zero-result presets
  - update changelog with user-facing feature summary
- dependencies:
  - should happen after core behavior is stable
- risks:
  - if import/export drops metadata, exported/reimported builder-created searches become raw-edit only; acceptable if documented but not ideal
- tests:
  - existing import/export tests still pass
  - imported name/query-only search opens raw editor
- acceptance:
  - no regression to existing settings/saved-search import/export
  - user-facing strings explain builder behavior clearly enough for manual testing

## sequencing
1. Implement saved-search metadata migration and idempotent preset seeding foundation.
2. Add builder state serialization/deserialization.
3. Add query compiler for existing syntax only, enough to generate notebooks/tags/states/date/no-date/sort built-ins except properties/date-source filtering.
4. Add property query parser/builder/SQL support and tests.
5. Add agenda date-source options and tests.
6. Connect built-in presets to compiler-generated metadata/query strings.
7. Build the guided editor UI and save/edit flow.
8. Add empty-state/help strings and changelog.
9. Run targeted parser/compiler/seeding tests.
10. Run `./scripts/build_fdroid.sh` as the primary compile/build verification.
11. Manually QA on a device or emulator with multiple notebooks and scattered tasks.

## risks_and_unknowns
- risk: agenda date-source filtering is broader than simple query generation.
  - mitigation: treat it as a first-class query option with backward-compatible defaults and tests around old `ad.N` behavior.
- risk: property SQL can duplicate or incorrectly filter notes because the query runs against `NoteViewDao.QUERY_WITH_NOTE_EVENTS`.
  - mitigation: use `EXISTS` subqueries and verify against `QueryResultTest` or a focused DB-backed test.
- risk: builder-generated OR/AND grouping hides tasks.
  - mitigation: generate a `Condition` tree, test generated query strings and parsed SQL, and avoid hand-concatenated query snippets.
- risk: seeding duplicates or overwrites user searches.
  - mitigation: use stable `preset_key`, exact-match checks, and non-destructive renamed inserts when name conflicts exist.
- risk: mobile UI gets too complex.
  - mitigation: use collapsible Include/Exclude sections, sensible defaults, and query preview rather than exposing every field all at once.
- risk: full unit-test suites may be noisy in this repo.
  - mitigation: use explicit flavor tasks for targeted tests where possible and rely on `assembleFdroidDebug` as the strongest baseline verification, per repo workflow notes.

## validation_strategy
- unit/parser/compiler:
  - `./scripts/build_fdroid.sh app:testFdroidDebugUnitTest --tests com.orgzly.android.query.QueryTest`
  - add focused builder compiler tests and run them with the explicit fdroid/premium unit-test tasks if stable
- database/seeding:
  - test migration from version 159 to 160 if the repo has migration test support available
  - test idempotent seed behavior against an in-memory or file DB
- build:
  - `./scripts/build_fdroid.sh`
  - optionally `./scripts/build_fdroid_dev.sh` for side-by-side manual QA
- manual QA checklist:
  - fresh install shows built-in presets in saved searches/drawer
  - upgrade/reopen does not duplicate presets
  - existing edited search with same name is not overwritten
  - create a new Home view using tag + notebook OR behavior
  - create a Business view with excluded tag/notebook
  - create No Date view and verify scheduled/deadline tasks are excluded
  - create Waiting view and verify `WAITING` state OR `waiting` tag works
  - create property exists and property equals views
  - edit a builder-created search and confirm fields reload
  - edit/import a raw query and confirm it falls back to raw editor
  - verify Today / overdue excludes event-only calendar entries
  - verify Calendar shows event entries from `calendar.org`

## handoff_notes
- The key product constraint is “do not lose tasks across files”; prioritize correctness of generated query grouping over UI polish.
- Do not fork task retrieval away from `InternalQueryParser` / `SqliteQueryBuilder` / `NoteViewDao` unless a specific test proves the query engine cannot express the needed behavior.
- Treat `SavedSearch.query` as the runtime source of truth. Metadata is for guided editing and seeding, not a second retrieval path.
- Use the capture-template seeder only as a pattern; do not copy its exact conflict behavior if it would overwrite or duplicate saved searches.
- Keep old raw saved searches valid forever.
- If property or date-source syntax names change during implementation, update this plan or add a note to the PR/commit so future work does not chase stale examples.
