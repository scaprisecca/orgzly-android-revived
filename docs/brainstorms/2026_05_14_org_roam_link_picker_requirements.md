# org-roam-compatible link picker requirements

created: 2026-05-14
status: draft
source: ce-brainstorm

## problem
- Scott wants Orgzly Revived to support the practical part of org-roam on Android: linking notes together, tapping links to open target notes, and creating/linking notes in a way that remains compatible with desktop Emacs/org-roam.
- Orgzly already opens standard Org `id:` links and `CUSTOM_ID` links, but it lacks a mobile-first workflow for finding a target note, inserting a correct `[[id:...][title]]` link, generating missing IDs, and recovering from broken or duplicate links.
- The feature should improve interoperability with org-roam without turning Orgzly into a full org-roam clone or depending on Emacs' org-roam SQLite database.

## goals
- Add an editor workflow for inserting org-roam-compatible links to existing notes/books.
- Preserve plain Org files as the source of truth.
- Use standard Org `ID` properties and `id:` links so Emacs/org-roam can read links created on mobile.
- Avoid importing or depending on the org-roam Emacs DB for V1.
- Make link failures understandable: missing target, duplicate ID, or target lacking an ID.
- Keep graph view, advanced backlinks, and full org-roam capture parity out of V1.

## non_goals
- No graph view.
- No direct import/sync of org-roam's Emacs SQLite database.
- No custom `roam:` link scheme for the core feature.
- No full org-roam buffer clone.
- No unlinked-reference scanning in V1.
- No broad rewrite of Orgzly's parser or sync model.
- No automatic silent mutation of target notes just because the user selected them in a picker.

## proposed_behavior
- Add a note-editor action named something like **Link to note**.
- When activated, show a searchable picker of linkable Orgzly targets.
- Candidate targets should default to already-linkable nodes:
  - existing headings/notes with an `ID` property
  - book/file root nodes where Orgzly can identify a root-level `ID`
- The picker should include a toggle such as **Include notes without IDs**:
  - off by default, so normal results stay clean and only stable link targets appear
  - when enabled, also show existing headings/notes without an `ID`, marked clearly as needing an ID before they can be linked by stable `id:` link
- Search should consider:
  - note title
  - book/notebook name
  - existing `ID`
  - existing `CUSTOM_ID`
  - `ROAM_ALIASES`, if present as a property
- Selecting a target that already has `ID` should insert:
  - `[[id:<ID>][<target title>]]`
- Selecting a target without `ID` should ask before mutating the target:
  - suggested wording: “Add an ID to this note so it can be linked from Emacs/org-roam and Orgzly?”
  - if confirmed, add an `ID` property and then insert `[[id:<new ID>][<target title>]]`
  - if cancelled, do not insert an unstable fallback link by default
- If the searched title does not exist, V1 should allow creating a new linked note in the current notebook:
  - create the note in the same notebook/book as the current note
  - assign a generated `ID` immediately
  - insert `[[id:<new ID>][<new title>]]` into the source note
  - stay in the source note after inserting the link
- Tapping an inserted link should use the existing `id:` link path where possible.
- Missing link target behavior should improve over current snackbar-only failure:
  - show that no note/book was found with matching `ID`
  - optionally offer to search by visible link description
- Duplicate ID behavior should remain a warning/recovery path:
  - do not treat duplicates as valid normal state
  - if multiple targets match, show a chooser or clear error rather than silently opening the first match

## org_roam_db_answer
- The org-roam Emacs DB is a local cache/index, not the canonical source of notes.
- Org-roam uses it to avoid reparsing every Org file on every operation. The manual describes org-roam as crawling files in `org-roam-directory` and maintaining a cache of links and nodes.
- The DB/cache powers fast node search/completion, backlinks, reference links, and other relationship views.
- Emacs can rebuild it with `org-roam-db-sync`; autosync keeps it current on file changes.
- Potential issue if Orgzly does not import the Emacs DB:
  - Orgzly will not automatically know org-roam's cached backlinks or node metadata beyond what exists in the synced Org files.
  - Orgzly needs its own Room/query layer for mobile search and link insertion.
  - After Orgzly creates or edits notes, Emacs/org-roam may need autosync or `org-roam-db-sync` to notice the new/changed node.
- Why not importing the DB is still the right V1 choice:
  - the DB is derived state and can be rebuilt from Org files
  - coupling Android to org-roam's internal SQLite schema would be fragile
  - many users sync Org files, not Emacs cache files
  - Orgzly already stores parsed notebooks in its own Room DB, so it should derive mobile node search from its own local data

## id_compatibility_answer
- Org-roam considers a node to be a file or headline with an `ID` property.
- Org-roam links between nodes use standard Org `id:` links, e.g. `[[id:<uuid>][Title]]`.
- Org mode's `ID` property can be a UUID by default, depending on Emacs `org-id-method`.
- Orgzly already has code that can add an `ID` to new notes when `AppPreferences.addIdToNewNotes(context)` is enabled. It uses `UUID.randomUUID().toString()` in `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`.
- That UUID string format should be compatible with Emacs/org-roam because org-roam cares about the `ID` property value and standard `id:` links, not which program generated the UUID.
- Requirement for this feature:
  - when the link picker needs to generate an ID, it should use the same basic UUID approach and write it as an Org property named exactly `ID`
  - generated links should use standard `id:` syntax, not an Orgzly-specific link type
  - Emacs/org-roam should discover those mobile-created nodes after sync/autosync processes the changed Org file

## constraints
- Keep source-of-truth compatibility with plain `.org` files.
- Keep V1 scoped to link insertion/opening, not a graph or backlink system.
- Existing Orgzly link support should be reused where possible:
  - `OrgFormatter.kt`
  - `IdLinkSpan.kt`
  - `CustomIdLinkSpan.kt`
  - `MainActivityViewModel.followLinkToNoteOrBookWithProperty(...)`
  - `NoteOrBookFindWithProperty`
- Existing test coverage in `InternalLinksTest.kt` should be extended rather than replaced.
- Adding an `ID` property is a content mutation and must be user-visible/confirmed when applied to an existing target note.
- Org-roam file-node mapping needs care because desktop org-roam uses `#+title` for file nodes, while Orgzly also has notebook/book names and root nodes.

## open_questions
- None blocking for V1 requirements. Remaining details can be handled during planning/design.

## success_criteria
- A user can open a note, choose **Link to note**, search for another note, and insert a standard `[[id:...][title]]` link.
- A user can tap that link in Orgzly and navigate to the target note/book.
- A link created in Orgzly appears as a normal org-roam link in Emacs after file sync and org-roam DB refresh/autosync.
- If a selected target lacks an `ID`, Orgzly explicitly asks before adding one.
- Missing and duplicate ID states produce understandable UI instead of silent failure or arbitrary navigation.
- Existing `id:`, `CUSTOM_ID`, and `file:` link tests continue passing.

## notes_for_planning
- Existing ideation source: `docs/ideation/2026_05_14_org_roam_style_linking_ideation.md`.
- Relevant existing implementation files:
  - `app/src/main/java/com/orgzly/android/util/OrgFormatter.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/style/IdLinkSpan.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/style/CustomIdLinkSpan.kt`
  - `app/src/main/java/com/orgzly/android/ui/main/MainActivityViewModel.kt`
  - `app/src/main/java/com/orgzly/android/usecase/NoteOrBookFindWithProperty.kt`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NoteDao.kt`
  - `app/src/main/java/com/orgzly/android/db/dao/NotePropertyDao.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`
  - `app/src/androidTest/java/com/orgzly/android/espresso/InternalLinksTest.kt`
- Important existing behavior:
  - `NoteBuilder.kt` already uses UUIDs for note `ID` properties when configured to add IDs to new notes.
  - `InternalLinksTest.kt` already covers cross-book `id:` and `CUSTOM_ID` link navigation.
- Likely V1 architecture:
  - create a lightweight node-search/query layer derived from Orgzly's existing Room data
  - add an editor insertion action and selection dialog
  - reuse existing link-click path
  - add targeted tests around insertion, generated IDs, and link navigation

## resolved_decisions
- Use standard Org `ID` and `id:` links for compatibility.
- Do not import org-roam's Emacs SQLite DB in V1.
- Do not build graph view in V1.
- Treat the Emacs/org-roam DB as derived cache, not source of truth.
- Generated mobile IDs should be UUID-backed `ID` properties and should remain compatible with Emacs/org-roam after sync.
- V1 should support creating a new linked note from the picker.
- New linked notes should be created in the current notebook/book by default.
- After creating a new linked note, Orgzly should stay in the source note after inserting the link.
- Existing notes without IDs should be hidden from picker results by default, with a toggle to include them.
- When the no-ID toggle is enabled, selecting a no-ID target should ask before adding an `ID` and inserting the stable `id:` link.
