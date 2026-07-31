# file-level TODO workflows

created: 2026-07-30
status: draft
source: ce-brainstorm
repo: orgzly

## problem
- Orgzly currently treats TODO/DONE workflow keywords as an app-global setting.
- Emacs Org files can declare TODO workflows in the file preface, commonly with `#+TODO: TODO NEXT | DONE CANCELED`.
- Scott wants each notebook/file to be able to define its own task-state workflow while preserving the plain-text Org file as the source of truth.
- Without file-level workflow support, headings using file-specific states can be misclassified as plain title text, shown with the wrong state options, or treated incorrectly by search/agenda/done filtering.

## goals
- Support one top-of-file `#+TODO:` workflow directive per Org notebook/file.
- Fall back to the existing app-global state workflow when a file has no supported `#+TODO:` directive.
- Parse file headings using the file's effective workflow so state/title splitting is correct during notebook load.
- Make the state picker inside a notebook use that notebook's effective workflow.
- Make search, agenda, widgets, reminders, done archive, and other done/todo classification paths respect each note's book-level workflow where practical in V1.
- Preserve the original preface line on export; do not rewrite or normalize `#+TODO:` unless another feature explicitly edits the preface.
- Keep the implementation compatible with existing app-global custom states and existing notebooks.

## non_goals
- No full UI editor for `#+TODO:` lines in V1.
- No `#+SEQ_TODO:` alias support in V1; only `#+TODO:` is recognized.
- No support for multiple workflow lines or multiple `|` separators in V1.
- No fast-selection keys such as Emacs `TODO(t)` / `DONE(d)` in V1.
- No subtree-local workflow inheritance or sophisticated Org-mode TODO inheritance in V1.
- No change to the existing Settings -> Note -> States screen beyond its role as the fallback/global workflow.
- No migration that modifies users' Org files to add `#+TODO:` automatically.
- No attempt at full Emacs Org parity for all TODO keyword directive variants in V1.

## current_code_findings
- Repo root: `/home/agent/dev/orgzly`.
- `AGENTS.md` and `CLAUDE.md` are absent.
- Primary project overview: `README.org`.
- Current branch during inspection: `master`.
- App-global state workflow storage/parsing lives in `app/src/main/java/com/orgzly/android/prefs/AppPreferences.java`:
  - `states(context)` reads the configured workflow string.
  - `states(context, value)` writes it and calls `updateStaticKeywords(context)`.
  - `todoKeywordsSet(context)` and `doneKeywordsSet(context)` return cached `LinkedHashSet`s parsed from `StateWorkflows(states(context))`.
- Workflow string parsing is wrapped by `app/src/main/java/com/orgzly/android/prefs/StateWorkflows.java`, which creates `OrgStatesWorkflow` objects from one or more newline-separated workflow lines.
- Notebook loading in `app/src/main/java/com/orgzly/android/data/DataRepository.kt` currently passes only app-global state sets into the parser:
  - `OrgParser.Builder().setTodoKeywords(AppPreferences.todoKeywordsSet(context)).setDoneKeywords(AppPreferences.doneKeywordsSet(context))`.
- `DataRepository.loadBookFromReader(...)` receives the parsed `OrgFile` in `onFile(file)` after parsing is complete. It currently stores:
  - `preface = file.preface`
  - `filetags = Tags.fromList(settings?.filetags)`
  - `isIndented = file.settings.isIndented`
  - `title = file.settings.title`
- `OrgFileSettings.fromPreface(file.preface)` is already used to derive `#+FILETAGS:` from the preface.
- The `Book` entity in `app/src/main/java/com/orgzly/android/db/entity/Book.kt` already stores `preface`, `filetags`, `title`, and parse/export metadata, but it does not store TODO/DONE workflow metadata.
- Export in `app/src/main/java/com/orgzly/android/NotesOrgExporter.kt` writes `book.preface` unchanged through `OrgParserWriter.whiteSpacedFilePreface(book.preface)`, then exports notes. This should naturally preserve a `#+TODO:` line as long as `preface` is preserved.
- The app can use a local `org-java` module if `app.properties` sets `org_java_directory`, but normal builds use Maven dependency `com.github.orgzly-revived:org-java:1.3.6`.
- Prior adjacent docs:
  - `docs/brainstorms/2026_07_23_todo_state_colors_requirements.md` assumes app-global state lists for state-color settings; file-level workflows may later require color settings to consider discovered per-file states or continue coloring by keyword globally.
  - `docs/brainstorms/2026_06_30_saved_search_builder_autocomplete_requirements.md` assumes state autocomplete uses app-global states; file-level workflows may later require union-of-known-states suggestions or notebook-contextual suggestions.
  - `docs/brainstorms/2026_05_13_agenda_dashboard_presets_requirements.md` relies on active/done filtering in saved searches and agenda views; per-book done/todo classification must not make active tasks disappear.

## proposed_behavior
### File syntax
- V1 supports this plain Org syntax near the top of the file/preface:

```org
#+TODO: TODO NEXT WAITING | DONE CANCELED
```

- Keywords before `|` are TODO/active states.
- Keywords after `|` are DONE/completed states.
- Whitespace around keywords and the separator is ignored.
- The directive is case-insensitive for the directive name (`#+TODO:`), but state keywords are treated as exact strings.
- If no supported directive exists, the notebook uses the current global workflow from Settings -> Note -> States.

### Parsing/import/load behavior
- When loading a notebook, Orgzly determines the file's effective workflow before heading state/title parsing.
- Headings are parsed with that effective workflow:
  - `* NEXT Call Bob` in a file with `#+TODO: TODO NEXT | DONE` stores `state = NEXT`, `title = Call Bob`.
  - The same heading in a file without `NEXT` in either file-level or global workflow should continue to be treated according to the fallback/global parser behavior.
- Existing notebooks without `#+TODO:` continue to behave exactly as they do today.
- Malformed or unsupported file-level workflow syntax should not break notebook loading; V1 falls back to the global app workflow and surfaces/logs a notebook warning so the bad directive is discoverable.

### Stored effective workflow
- Each book should expose an effective workflow to app code: file-level workflow if present, otherwise global workflow.
- Recommended V1 model: store the parsed file-level workflow on `Book` as a normalized string or separate todo/done keyword strings/sets, while preserving raw `preface` unchanged.
- The app should not rely on repeatedly reparsing raw preface in every UI/query path if that would make classification fragile or expensive.

### State picker behavior
- Inside a notebook, the note state picker should list the notebook's effective workflow states.
- Ordering should match the workflow: TODO keywords first, then DONE keywords.
- `No state` remains available as today.
- Cross-notebook/global UI without a specific notebook context can continue to use global states in V1 unless planning identifies an existing cross-book state picker that must do better.

### Search/agenda/done filtering behavior
- Done/todo classification should use the note's book-level effective workflow, not only app-global `AppPreferences.doneKeywordsSet(context)` / `todoKeywordsSet(context)`.
- A note with `state = FINISHED` in a book declaring `#+TODO: TODO | FINISHED` should count as done for:
  - search/query state-type filters such as TODO-like vs DONE-like where applicable
  - agenda done exclusion
  - reminders filtering
  - widgets checkmark/done hiding behavior
  - done archive active-descendant safeguards
  - parent progress cookie updates where feasible
- Explicit state-name filters should keep matching the stored state string. Example: searching for `i.FINISHED` should match `FINISHED` regardless of whether `FINISHED` came from global or file-level workflow.

### Export behavior
- Export preserves the original `#+TODO:` line because it remains part of `book.preface`.
- V1 does not rewrite state workflow directives when users change global settings.
- V1 does not add `#+TODO:` to files automatically.

## recommended_mvp
1. Add a small parser/helper for supported file-level workflow directives from raw preface text.
2. Decide whether to implement workflow detection as a lightweight pre-scan before `OrgParser` or by extending/configuring `org-java`.
3. Store normalized per-book file-level workflow metadata on `Book`, while preserving raw `preface` unchanged.
4. During notebook load, determine effective workflow before parsing headings and pass its todo/done sets into `OrgParser.Builder`.
5. Add a repository/helper API for `effectiveStateWorkflow(bookId)` / `todoKeywordsForBook(bookId)` / `doneKeywordsForBook(bookId)`.
6. Update notebook-scoped state picker paths to use book-level effective states.
7. Update done/todo classification paths that affect search/agenda/reminders/widgets/done archive to use note/book effective workflow.
8. Add focused tests for parsing, fallback behavior, import/load classification, export preservation, and at least one query/agenda done-filter path.
9. Add a `CHANGELOG.md` entry.
10. Verify with `./scripts/build_fdroid.sh assembleFdroidDebug`.

## implementation_options
### Option A: Orgzly pre-scan before OrgParser
- Shape:
  - Read enough of the file/preface before parser construction to find a supported `#+TODO:` line.
  - Parse it into todo/done sets.
  - Pass those sets into the existing `OrgParser.Builder`.
- Pros:
  - Avoids needing to fork/change `org-java` immediately.
  - Fits V1's limited syntax scope.
  - Easier to keep parser behavior fallback-compatible.
- Cons:
  - Duplicates a small amount of preface parsing outside `org-java`.
  - Care is needed not to consume or alter the reader before the real parser pass.
- Verdict: likely best V1 path unless code inspection shows `org-java` already exposes in-buffer TODO settings cleanly.

### Option B: Extend `org-java` parser support
- Shape:
  - Teach `org-java` to detect V1-supported `#+TODO:` file settings and classify subsequent headings accordingly.
- Pros:
  - More conceptually correct parser ownership.
  - Could benefit other users/code paths if published upstream.
- Cons:
  - This repo normally consumes `org-java` from Maven, so planning must include dependency/module management.
  - Larger blast radius and slower feedback loop.
- Verdict: better long-term if upstreaming, but likely too much for Scott's V1 unless local module setup is already desired.

### Option C: Reparse note titles after load using preface-derived workflow
- Shape:
  - Load with global states, parse preface, then reparse heading strings with book-level states.
- Pros:
  - Avoids reader/pre-scan complexity.
- Cons:
  - Riskier: notes may already have wrong title/state split, and reconstructing original heading text after a wrong parse may be lossy.
  - More likely to break priorities/tags/state/title edge cases.
- Verdict: avoid unless Option A is unexpectedly blocked.

## constraints
- `OrgParser.Builder` currently needs todo/done sets before parsing headings, but file-level directives are only available through `OrgFile` after parse in the current load flow.
- The normal parser dependency is Maven `org-java`; local parser changes require `app.properties` and likely a separate dependency/update workflow.
- `AppPreferences.todoKeywordsSet(context)` and `doneKeywordsSet(context)` are cached global sets. New book-aware helpers should avoid silently reusing global state where book context is available.
- Query/search code may not always carry `Book`/`bookId` to the point where state-type classification happens. Planning must trace the query SQL/data path before promising full per-book filtering.
- Existing requirements around state colors and saved-search autocomplete currently assume global state lists. This feature can ship without fully redesigning those surfaces, but the interaction should be documented.
- Full test runs in this repo can fail for unrelated existing issues; targeted tests plus `assembleFdroidDebug` are the practical verification baseline.

## open_questions
- None blocking yet.

## resolved_decisions
- V1 supports `#+TODO:` at the top of a file.
- V1 intentionally does not support `#+SEQ_TODO:` as an alias; keep the first pass strict to `#+TODO:`.
- Files without `#+TODO:` fall back to the global app workflow.
- Files with malformed `#+TODO:` also fall back to the global app workflow, but should show/log a notebook warning instead of failing silently.
- File headings must be parsed using the file's workflow, not corrected later by UI-only logic.
- The state picker inside a notebook uses that notebook's effective workflow.
- Search, agenda, and done/todo filtering should use per-book done/todo classification.
- Export preserves the existing preface line.
- V1 excludes a full UI editor for `#+TODO:` lines.
- V1 excludes multiple workflow lines, multiple `|` separators, Emacs fast-selection keys, and subtree-local inheritance.

## success_criteria
- Given a file with `#+TODO: TODO NEXT WAITING | DONE CANCELED`, imported/reloaded headings using `NEXT`, `WAITING`, `DONE`, and `CANCELED` are stored with the correct `state` and clean title.
- Given a file with no `#+TODO:`, existing global workflow parsing behavior is unchanged.
- Given a malformed `#+TODO:` line, notebook loading does not fail, global workflow fallback is used, and a warning is visible/logged.
- Inside a notebook with file-level workflow, the state picker offers `TODO`, `NEXT`, `WAITING`, `DONE`, and `CANCELED` in workflow order plus no-state.
- A book-level done state such as `FINISHED` is treated as done by agenda done exclusion and at least one explicit done-filter/search path.
- Explicit state-name search still matches by stored state string.
- Exported content still contains the original `#+TODO:` preface line.
- Existing notebooks without file-level workflows do not require migration and continue to build/load/export.
- `./scripts/build_fdroid.sh assembleFdroidDebug` succeeds.

## notes_for_planning
- Likely files/areas:
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/db/entity/Book.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/BookDao.kt`
  - `app/src/main/java/com/orgzly/android/db/OrgzlyDatabase.kt` and schema migration files if `Book` columns are added
  - `app/src/main/java/com/orgzly/android/prefs/StateWorkflows.java`
  - `app/src/main/java/com/orgzly/android/ui/NoteStates.kt`
  - notebook/note state picker code in `app/src/main/java/com/orgzly/android/ui/note/` and/or list action code
  - query SQL/state-type code under `app/src/main/java/com/orgzly/android/query/`
  - `app/src/main/java/com/orgzly/android/calendar/CalendarManager.kt`
  - `app/src/main/java/com/orgzly/android/reminders/NoteReminders.kt`
  - `app/src/main/java/com/orgzly/android/widgets/ListWidgetService.kt`
  - `app/src/main/java/com/orgzly/android/usecase/NoteArchiveDone.kt`
  - `app/src/test/java/com/orgzly/android/...` focused parser/repository/query tests
  - `CHANGELOG.md`
- Planning should first trace whether query state-type filtering happens in SQL using global sets or after note retrieval; that determines how invasive per-book classification will be.
- If adding columns to `Book`, remember this repo likely needs Room migration and exported schema JSON checks.
- Use targeted tests for helper parsing and a book load/export case. Then run `./scripts/build_fdroid.sh assembleFdroidDebug` as the reliable baseline.
