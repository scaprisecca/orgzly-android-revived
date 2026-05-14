# agenda dashboard presets

created: 2026-05-13
status: draft
source: ce-brainstorm
repo: orgzly

## problem
- Orgzly already has saved searches and an agenda renderer, but users have to understand and remember query syntax such as `.it.done ad.7`, `s.ge.today`, `t.home`, and sort options to create useful task-management views.
- This makes high-value Org-style review workflows feel like a power-user feature instead of a daily mobile workflow.
- Tasks can be scattered across notebooks/files, and the current saved-search UI does not guide users toward reliable cross-file views for life areas like home, business, errands, learning, waiting, and recurring chores.

## goals
- Let a user create useful agenda-style views without memorizing query syntax.
- Reuse the existing query engine and agenda renderer where they are sufficient.
- Make cross-file task review safer so active tasks are less likely to disappear inside individual notebooks.
- Support life-area views that can be created quickly from common filters: time window, TODO state, done exclusion, tags, notebooks, priorities, and sort/grouping.
- Keep advanced query editing available for existing power users.
- Create a path for default built-in presets plus user-created presets.

## non_goals
- Full Emacs Org agenda parity in v1.
- Replacing the existing query language or saved-search system in v1.
- Building a full visual query-language editor for every possible expression in v1.
- Solving project/next-action semantics completely in this feature; those can build on this later.
- Changing Org file syntax.

## current_code_findings
- Existing data model is very small: `SavedSearch(id, name, query, position)` in `app/src/main/java/com/orgzly/android/db/entity/SavedSearch.kt`.
- Existing default onboarding seeds two saved searches in `PreRoomMigration.insertAgendaSavedSearch(...)`:
  - `Agenda` -> `.it.done ad.7`
  - `Next 3 days` -> `.it.done s.ge.today ad.3`
- The drawer is already driven by saved searches in `DrawerNavigationView.refreshFromSavedSearches(...)`, so presets can appear in the same main navigation without inventing a new top-level navigation system.
- Query display already routes through `MainNavigationAction.DisplayQuery`, `QueryFragment`, `QueryViewModel`, and `AgendaFragment`.
- Agenda rendering is already separate from SQL querying: `QueryViewModel` selects notes from the query, then `AgendaItems` expands scheduled/deadline/event timestamps into agenda sections.
- Query options already support `agendaDays` through both basic syntax (`agenda-days:7`) and dotted syntax (`ad.7`).
- Query syntax supports enough primitives for a useful v1: book, state/state-type, priority, tag/own-tag, scheduled/deadline/closed/created intervals, text search, sort orders, AND/OR grouping, and negation.
- Existing saved-search creation/edit UI is raw name + query fields in `SavedSearchFragment` and currently exposes no guided builder.
- Existing export/import only includes name/query, so any richer preset metadata either needs to be optional/derived or requires a schema/migration decision.
- Properties exist in the database (`NoteProperty`, `NotePropertyDao`, property autocomplete in the note editor), but the inspected query parser has no property condition yet. Property filtering is therefore a real feature gap, not just a builder UI task.

## proposed_behavior
### v1 product shape
- Decision: use a guided saved-search builder for v1.
- Add a guided “New agenda view” / “Build saved search” flow from the saved-searches screen.
- The user fills friendly fields, then the builder creates the query behind the scenes.
- The final result is saved with a normal saved-search name and appears in the existing left sidebar/drawer exactly like current saved searches.
- The flow creates a normal saved search under the hood, so it immediately works with:
  - the existing drawer
  - widgets backed by saved searches
  - existing query execution
  - export/import, at least as a plain saved search
- Builder fields Scott wants included:
  - notebooks
  - tags
  - TODO states
  - properties
    - v1 supports property exists
    - v1 supports property equals value
    - property contains/fuzzy matching and comparisons are deferred
  - saved-search name
- Builder boolean behavior:
  - multiple values within the same field type use OR by default
    - example: `home` notebook OR `business` notebook
    - example: `errand` tag OR `phone` tag
  - different field groups combine with AND by default
    - example: `(notebook is home OR business) AND (tag is errand OR phone) AND (state is TODO OR NEXT)`
  - v1 should expose NOT/exclusion controls where practical, because the underlying query language already supports negation for many conditions
  - decision: use separate Include and Exclude sections for v1 rather than per-chip toggles or advanced-only NOT
    - example: Included tags and Excluded tags are separate controls
    - reason: clearer on mobile, easier to test, and harder to misunderstand
- The generated query should be visible before save, with an “Advanced query” escape hatch for users who know the syntax.
- Guided re-edit decision:
  - use a hybrid fallback model
  - builder-created saved searches should store enough metadata to reopen in the guided builder
  - existing saved searches, imported saved searches, or manually edited queries without valid metadata fall back to the raw-query editor
  - if a builder-created query is manually changed in a way the builder cannot safely parse, keep the raw query as source of truth and degrade gracefully rather than guessing

### recommended v1 built-in presets
- Decision: ship/seed this starting set:
  1. Today / overdue
     - active tasks scheduled today or overdue OR due today or overdue
     - excludes event/calendar timestamps; those belong in the Calendar preset
  2. Next 7 days
  3. No Date
     - active tasks with no scheduled date AND no deadline
  4. Waiting
     - defaults to tasks matching either TODO state `WAITING` or tag `waiting`
  5. Calendar
     - defaults to the `calendar.org` notebook/file
  6. Home
     - defaults to tasks matching either a `home` tag or a `home.org` notebook/file
  7. Business
     - defaults to tasks matching either a `business` tag or a `business.org` notebook/file
  8. Recurring chores
     - pulls from the `routines.org` notebook/file
- Presets should still be editable through the guided saved-search builder after creation.
- Built-in preset seeding decision:
  - seed built-ins for new installs and add missing built-ins on upgrade/migration
  - this app is currently for Scott's personal testing use, so optimize for usefulness over broad-user migration conservatism
  - do not overwrite user-edited saved searches when adding missing built-ins
  - if a built-in name already exists but lacks builder metadata, prefer adding the missing preset with a clear non-destructive name or updating only if it is clearly an older built-in
- Presets that reference a default notebook/file should handle missing files gracefully:
  - show a clear “notebook not found” or empty-state explanation
  - allow the user to choose a different notebook
  - do not silently create files in v1 unless the user explicitly confirms that behavior

### guided fields
- View name.
- Area source: all notebooks, selected notebooks, selected tags, or both.
- Task state: active TODOs by default; optional explicit state(s) later.
- Time window:
  - no date filter
  - today / overdue
  - next 3 days
  - next 7 days
  - next 14 days
  - next 30 days
  - no scheduled/deadline date
  - custom advanced query
- Date sources:
  - scheduled
  - deadline
  - scheduled OR deadline
  - events/calendar timestamps
- Include/exclude done: default exclude done.
- Sort preference: date first, priority first, notebook first.
- Output style: agenda grouped by day when a time window is selected; plain list for no-date/inbox review.

## query_reuse_assessment
- Existing syntax appears sufficient for most v1 views:
  - exclude done: `.it.done`
  - TODO items: `it.todo`
  - agenda days: `ad.N`
  - scheduled/deadline intervals: `s.*` / `d.*`
  - tags: `t.<tag>`
  - notebooks: `b.<book>`
  - priority: `p.A`
  - sort: `o.s`, `o.d`, `o.p`, `o.b`
  - NOT/exclusion for many existing conditions through dotted syntax prefix dot / basic syntax prefix hyphen
- Builder query composition rules:
  - OR within the same field type
  - AND across field groups
  - NOT should be represented as explicit exclusion groups rather than forcing users to understand boolean syntax
- Known query-language gaps for Scott's requested builder fields:
  - property filtering is not currently supported by the query parser/builder and would need a new condition, SQL join/selection support, tests, and UI controls
  - v1 property support should cover property exists and property equals value
  - property contains/fuzzy matching and property comparisons are deferred
  - “undated active tasks” may need clearer syntax if existing null-date conditions are not enough for a friendly builder
  - “recurring tasks only” may not be expressible cleanly unless repeater information is exposed in queryable fields
- Best v1 direction: keep the query engine, add a builder layer that compiles to existing query strings where possible, and extend the query language only for builder fields that are clearly needed. Property filters are now in scope for v1, limited to exists and equals.

## implementation_constraints_for_later_planning
- Saved search metadata is currently only name/query/position, so a guided preset can either:
  1. create plain saved searches only, losing builder-edit metadata but keeping implementation simple, or
  2. extend the schema with optional preset metadata, enabling future guided re-editing but requiring migration/import/export decisions.
- The app already has capture-template persistence patterns in this branch; if those landed cleanly, agenda preset persistence could follow a similar approach, but this should not be assumed until planning.
- Agenda item rendering currently only groups by date/overdue. More dashboard-specific sections like “Overdue”, “Today”, “No date”, “Waiting”, “By notebook”, or “By priority” may need adapter/model changes beyond simple query generation.
- The saved-search screen is the most obvious low-risk entry point, but a separate “Dashboards” concept may be clearer if presets eventually become richer than saved searches.

## open_questions
- Optional/future enhancement: result rows explaining why they matched a dashboard can be deferred unless planning finds a cheap way to reuse existing note metadata display.

## resolved_decisions
- V1 product shape is a guided saved-search builder, not a separate dashboard system.
- Created views appear in the existing left sidebar/drawer as normal saved searches.
- Guided re-edit uses a hybrid fallback model: builder-created searches store metadata for guided editing, while existing/imported/manual queries fall back to raw-query editing.
- Builder supports notebooks, tags, TODO states, properties, date filters/date sources, sort options, and saved-search name.
- Property filters are in scope for v1, limited to property exists and property equals value.
- Boolean behavior: OR within the same field group, AND across field groups.
- NOT/exclusion uses separate Include and Exclude sections.
- Full date-filter set is in scope: no date filter, today/overdue, next 3/7/14/30 days, no scheduled/deadline date, and custom advanced query.
- Date sources are in scope: scheduled, deadline, scheduled OR deadline, and events/calendar timestamps.
- Built-in presets: Today / overdue, Next 7 days, No Date, Waiting, Calendar, Home, Business, Recurring chores.
- Built-in presets are seeded for new installs and missing built-ins are added on upgrade/migration, optimized for Scott's personal testing use while still avoiding overwriting user-edited saved searches.
- Today / overdue includes scheduled today/overdue OR deadline today/overdue, and excludes event/calendar timestamps.
- No Date means active tasks with no scheduled date AND no deadline.
- Waiting uses TODO state `WAITING` OR tag `waiting`.
- Calendar defaults to `calendar.org`.
- Home uses `home` tag OR `home.org` notebook/file.
- Business uses `business` tag OR `business.org` notebook/file.
- Recurring chores pulls from `routines.org`.

## success_criteria
- A user can create a new useful agenda-style view in under one minute without typing query syntax.
- The created view reliably surfaces active tasks across notebooks/files.
- Existing saved-search users can still view and edit raw queries.
- Built-in presets give new users a useful starting set immediately after install or upgrade.
- The feature does not fork task retrieval away from the existing query/agenda infrastructure unless a specific gap requires it.

## notes_for_planning
- Relevant files inspected:
  - `README.org`
  - `docs/ideation/2026_05_01_org_mode_mobile_features_ideation.md`
  - `app/src/main/java/com/orgzly/android/db/entity/SavedSearch.kt`
  - `app/src/main/java/com/orgzly/android/db/PreRoomMigration.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/ui/savedsearch/SavedSearchFragment.java`
  - `app/src/main/java/com/orgzly/android/ui/savedsearches/SavedSearchesFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/drawer/DrawerNavigationView.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/QueryFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/QueryViewModel.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/agenda/AgendaFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/notes/query/agenda/AgendaItems.kt`
  - `app/src/main/java/com/orgzly/android/query/user/BasicQueryParser.kt`
  - `app/src/main/java/com/orgzly/android/query/user/DottedQueryParser.kt`
  - `app/src/main/java/com/orgzly/android/query/sql/SqliteQueryBuilder.kt`
- Likely planning areas:
  - saved-search builder UI
  - preset definition model
  - query compiler/generator helpers
  - optional saved-search metadata migration
  - agenda/list rendering gaps
  - tests for generated queries and parser compatibility
