# org-roam-style note linking ideation for orgzly

created: 2026-05-14
status: active
repo: orgzly
focus: make Orgzly Revived interoperate better with org-roam-style notes, especially linking, link insertion, and opening linked notes without attempting graph view parity

## context
- confirmed repo root: `/home/agent/dev/orgzly`
- inspected project entrypoint `README.org`; no `AGENTS.md` or `CLAUDE.md` were present
- inspected current link rendering/opening path in `app/src/main/java/com/orgzly/android/util/OrgFormatter.kt`, `app/src/main/java/com/orgzly/android/ui/views/style/*LinkSpan.kt`, `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`, `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`, and `app/src/main/java/com/orgzly/android/usecase/LinkFindTarget.kt`
- inspected current ID/CUSTOM_ID link tests in `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt`
- inspected property storage/query support in `app/src/main/java/com/orgzly/android/db/entity/NoteProperty.kt`, `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt`, `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`, and `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
- researched org-roam behavior from the org-roam manual and Org manual:
  - org-roam defines a node as any top-level file or headline with an `ID`
  - org-roam links between nodes with standard Org `id:` links, e.g. `[[id:...][description]]`
  - org-roam’s creation/linking flow centers on `org-roam-node-find` and `org-roam-node-insert`
  - org-roam node discovery is powered by a cache over all files in `org-roam-directory`
  - aliases are stored in the `ROAM_ALIASES` property and help node search/completion
  - backlinks, reference links, and unlinked references are org-roam buffer features; graph view is separate and not required for Scott's requested use case

## key_observations
- The most important compatibility piece is already partly present: Orgzly already recognizes `id:` links and `#CUSTOM_ID` links, then resolves them through note/book properties.
- `OrgFormatter.kt` currently supports system links plus custom `id` and `file` plain-link schemes, bracket links, and fallback file/search spans. It does not need a new org-roam-specific link format for the core case.
- `IdLinkSpan.kt` resolves `id:` links by looking up the `ID` property. `CustomIdLinkSpan.kt` resolves `#...` links by looking up `CUSTOM_ID`.
- `InternalLinksTest.kt` already proves cross-book `ID`, `CUSTOM_ID`, and `file:book.org` navigation, including case-insensitive UUID matching and opening a book by root-level `ID`.
- The current missing piece is not “can links be clicked?” It is “can a mobile user easily create, discover, complete, validate, and maintain org-roam-style links?”
- Org-roam file nodes map reasonably to Orgzly book root nodes with root-level `:ID:` properties and `#+title:` / book name titles. Org-roam headline nodes map to Orgzly notes/headings with `:ID:` properties.
- A full org-roam clone would require indexing every link/backlink and perhaps reading all org files recursively. That is feasible but heavier than needed for a first useful version.
- The safest mobile-first path is to keep Org-compatible plain text as the source of truth and add helper UX over existing properties and `id:` links.

## candidate_ideas
### 1. org-roam-compatible link insert / node picker
- summary: add an editor action that searches existing Orgzly notes/books with `ID` properties, lets the user pick a target by title/book/path/alias, then inserts `[[id:<ID>][<title>]]` at the cursor. If the target has no `ID`, offer to generate one before inserting.
- value: highest practical value. It gives Scott the desktop org-roam behavior he actually cares about: select a note, insert a stable link, and later tap it to open the target.
- cost: medium. Existing ID lookup/opening exists, but this needs a node-search data source, note-picker UI, insertion helper, and safe ID generation/update flow.
- risk: property writes must be careful because adding an `ID` mutates the target note. The UI must be clear when it is about to add an ID.
- why_it_might_fail: if the picker is slow or only searches exact titles, it will feel worse than desktop org-roam. V1 needs fast fuzzy-ish title/book search and should not require graph/index complexity.
- likely_implementation_areas:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - new dialog/fragment for selecting link targets

### 2. org-roam node index built from existing Orgzly books and properties
- summary: create a lightweight “nodes” query layer over books/headings: title, note ID/book ID, Org `ID`, `CUSTOM_ID`, `ROAM_ALIASES`, tags, and perhaps file/book name. This does not need to store graph edges first; it can support search and link insertion.
- value: high. It gives the app a clear internal model that matches org-roam enough to support node find/insert and future backlinks.
- cost: medium. The notes/properties are already in Room, and `note_properties` has indices for `name` and `value`; the work is mostly query composition and title/alias normalization.
- risk: org-roam file-node titles come from `#+title`, while Orgzly notebooks also have book names and root nodes. Mapping these cleanly matters for desktop compatibility.
- why_it_might_fail: if it becomes a separate duplicated cache too early, it risks sync bugs. V1 should derive from existing notes/properties where possible before adding a persisted index.
- likely_implementation_areas:
  - `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - possibly a small pure Kotlin `OrgRoamNode` model under data or linking package

### 3. tap-to-open improvements for `id:` links and broken-link recovery
- summary: keep existing click behavior, but improve failure and ambiguity handling: when an `id:` target is missing, offer search by visible title/description; when multiple matches exist, show a chooser instead of only an error; when the link points to a book root, open the book cleanly.
- value: medium-high. It makes existing org-roam links from Emacs more resilient on Android, especially after file moves, manual edits, sync conflicts, or duplicate IDs.
- cost: low to medium. The link-follow path already routes through `MainActivityViewModel.followLinkToNoteOrBookWithProperty(...)` and `NoteOrBookFindWithProperty`.
- risk: duplicate IDs are semantically bad in Org. A chooser should be a recovery path, not normalization of bad data.
- why_it_might_fail: too much fallback magic could hide real vault hygiene issues. The UI should say clearly when the ID is missing or duplicated.
- likely_implementation_areas:
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - `app/src/main/java/com/orgzly/android/usecase/NoteOrBookFindWithProperty.kt`
  - `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt`

### 4. backlinks panel for current note, minus graph view
- summary: add a note detail section or menu item showing notes that link to the current note's `ID`, with snippets and tap-to-open. This mirrors the useful part of the org-roam buffer without implementing graph visualization.
- value: medium-high. It helps navigation and “connected notes” workflows once users start inserting links.
- cost: medium-high if done robustly, because Orgzly currently parses links for display but does not appear to maintain a persisted link-edge index.
- risk: scanning all note bodies on demand may be slow on large notebooks; indexing every link creates migration/sync complexity.
- why_it_might_fail: backlinks are much less useful until link creation is easy. Building this before a link-insert picker is probably premature.
- likely_implementation_areas:
  - new link-edge parser/index, or DAO queries against note content if acceptable for V1
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/util/OrgFormatter.kt` or a separate non-UI org-link parser

### 5. alias-aware search using `ROAM_ALIASES`
- summary: parse and surface `ROAM_ALIASES` as alternate names in the node picker and maybe in normal app search. The inserted link still uses the target `ID`, but the user can find the node by any alias.
- value: medium. This is a real org-roam feature and useful for concepts/acronyms.
- cost: low to medium if aliases are already imported as normal properties; higher if org-roam quoting/space rules need exact compatibility.
- risk: alias semantics are easy to half-support incorrectly. Org-roam allows multiple aliases; mobile UI must display them without clutter.
- why_it_might_fail: aliases are secondary compared with basic ID linking. It should probably be included in the picker once the core picker exists, not built as a standalone feature.
- likely_implementation_areas:
  - `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt`
  - node picker search/ranking code
  - tests for `ROAM_ALIASES` property values

### 6. org-roam note creation helper
- summary: add a “New linked note” action from the editor: type a title, create a new note/book with a generated `ID`, optionally seed `#+title`/properties, and insert an `[[id:...][title]]` link back into the current note.
- value: medium. This approximates the “create if missing” part of `org-roam-node-insert` on mobile.
- cost: medium-high because note placement rules are hard: same book, default notebook, daily/inbox notebook, or a configured roam notebook?
- risk: if placement is wrong, users get scattered notes and sync friction with their desktop org-roam setup.
- why_it_might_fail: capture/template work may be a prerequisite. Without a good target-routing UI, this feature becomes either too rigid or too configurable.
- likely_implementation_areas:
  - existing note creation use cases under `app/src/main/java/com/orgzly/android/usecase/`
  - capture-template infrastructure if available
  - `NoteFragment.kt` editor insertion path

## rejected_or_weaker_options
- full org-roam graph view — explicitly not needed for Scott’s use case and high cost relative to link/navigation value
- importing org-roam's Emacs SQLite database directly — tempting but weak for mobile sync. The Android app should derive from org files it already owns/syncs, not depend on an Emacs-side cache format
- custom `roam:` link scheme — unnecessary for the core use case because org-roam uses standard `id:` links between nodes
- backlinks before link insertion — useful eventually, but weaker as a first step because the app needs an easy way to create links before a backlink panel becomes valuable
- deep org-roam capture/template parity — valuable later, but mobile placement/routing needs separate requirements; do not bundle it into the first linking feature
- unlinked references in V1 — org-roam itself notes this can be slow and does not enable it by default; too expensive for the first mobile feature

## recommended_next_steps
1. org-roam-compatible link insert / node picker
2. org-roam node index built from existing Orgzly books and properties
3. tap-to-open improvements for `id:` links and broken-link recovery

## suggested_v1_scope
- Add a “Link to note” editor action.
- Search existing notes/books by title, book name, `ID`, `CUSTOM_ID`, and `ROAM_ALIASES` where available.
- Insert `[[id:<ID>][<title>]]` into title/content at cursor.
- If target has no `ID`, prompt once to add a generated `ID` property to the target note before inserting.
- Reuse existing click-to-open path for `id:` links.
- Add failure UX for missing/duplicate IDs.
- Add tests extending `InternalLinksTest.kt` and pure tests for node search/ranking if feasible.

## compatibility_notes
- Desktop org-roam should understand the links produced by V1 because they are normal Org `id:` links.
- Orgzly already opens existing desktop-generated `id:` links if the target note/book has a matching `ID` property imported into the app.
- For best desktop/mobile interoperability, prefer generated `ID` properties over only `CUSTOM_ID`; org-roam considers nodes to be files/headlines with `ID`.
- Do not require graph metadata or org-roam database files for basic linking.
