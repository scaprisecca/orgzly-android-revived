# org mode mobile feature ideation for orgzly

created: 2026-05-01
status: active
repo: orgzly
focus: org-mode-inspired features filtered through Scott's personal notes, house projects, repeating tasks, learning notes, and small-business workflow

## context
- inspected repo docs and product positioning in `README.org` and `metadata/en-US/full_description.txt`
- inspected note editing and metadata UI in `app/src/main/res/layout/fragment_note.xml` and `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- inspected rich text behavior in `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`, `RichTextEdit.kt`, and `RichTextView.kt`
- inspected agenda/query UX in `app/src/main/java/com/orgzly/android/ui/notes/query/agenda/AgendaFragment.kt` and related menus
- inspected evidence of existing org support for repeaters, delays, warning periods, properties, clocks, progress cookies, and archive handling in `TimestampDialogFragment.kt`, `OrgTimestampMapper.kt`, `StateChangeParentTitleUpdater.kt`, `NoteItemViewBinder.kt`, and `note_actions.xml`

## key_observations
- the app already has stronger org support than its mobile UX exposes: states, priorities, scheduled/deadline/closed timestamps, repeaters, delays, warning periods, properties, clocks, drawers, agenda queries, saved searches, and checkbox/progress-cookie updates already exist
- the main gap is not raw org-file compatibility; it is mobile usability and workflow packaging around existing power features
- note editing is still basically raw-text editing with view-mode parsing. The only edit-mode toolbar affordance currently surfaced in the note screen is `insert_inline_timestamp`
- there appears to be data-layer support for habit-related timestamp fields, but no obvious user-facing habit workflow surfaced in the inspected UI
- for Scott-style use, the highest-value direction is not “all of Emacs Org on Android”. It is a smaller set of mobile-first affordances for capture, agenda focus, recurring maintenance, and fast structured editing

## candidate_ideas
### 1. mobile org editing toolbar + insert palette
- summary: add a bottom editing toolbar and optional slash-command palette for inserting org syntax quickly: bold, italic, code/verbatim, links, list bullets, checkboxes, drawers, property blocks, headings, timestamps, and block templates
- value: highest immediate usability win. It directly fixes the pain point in the current note editor and lowers the skill threshold for using more of org syntax on a phone
- cost: medium. Most work is editor UX, selection handling, insertion helpers, and some guardrails around raw-text edits
- risk: text-editing UX on Android gets fiddly fast, especially selection/cursor behavior and interaction with the current view/edit mode switching
- why_it_might_fail: if implemented as a giant toolbar with too many buttons, it becomes cluttered and still slower than typing. It needs presets, long-press variants, or a compact insert palette
- likely_implementation_areas:
  - `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
  - `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
  - new menu/layout components near `fragment_note.xml`

### 2. capture templates and target routing
- summary: bring a mobile version of org capture: quick-add flows for inbox note, task, repeating chore, meeting note, learning note, and business idea with prefilled state/tags/properties/target notebook
- value: very high for real-world phone use. This is how Org becomes practical away from the desktop: less friction, more consistent structure, better inbox discipline
- cost: medium to high. Needs a template model, UI for template selection, target routing, and insertion rules for new notes/subtrees
- risk: too much configurability could turn into a settings swamp. Better to start with a few opinionated templates and optional advanced config later
- why_it_might_fail: if templates are too generic, users still fall back to raw notes. If too configurable, maintenance cost rises and the feature becomes intimidating
- likely_implementation_areas:
  - quick note / share entry points
  - note creation use cases under `app/src/main/java/com/orgzly/android/usecase/`
  - preferences and template persistence
  - notebook/note placement logic in repository and note creation flows

### 3. workflow dashboards built on top of agenda + saved searches
- summary: create first-class mobile dashboards for common workflows: today, this week, home, business, waiting/blockers, repeating chores, and learning/review. This should sit on top of the existing query/agenda engine, not replace it
- value: high. The repo already has agenda and saved-search infrastructure, but it still feels like a power-user feature rather than a guided workflow system
- cost: medium. Much of the data plumbing exists already; the work is in presets, dashboard presentation, and easier composition
- risk: if dashboards are just hard-coded search shortcuts, they will feel shallow. The UX should explain why an item is shown and let the user tune the view
- why_it_might_fail: too much abstraction over query syntax may annoy existing users; too little abstraction will not help mobile-first users enough
- likely_implementation_areas:
  - `app/src/main/java/com/orgzly/android/ui/notes/query/agenda/AgendaFragment.kt`
  - query/search fragments and menus
  - saved-search creation/import defaults
  - widgets backed by dashboard presets

### 4. recurring task and habit UX overhaul
- summary: keep the existing repeater engine, but add mobile-friendly setup and review for recurring chores and habits: plain-language repeat presets, overdue/catch-up behavior explanation, streak/history cues, and a dedicated recurring-items review
- value: high for house management and maintenance. This is exactly the kind of thing mobile Org should be good at, and the repo already appears to have repeater/delay support plus habit-related fields in the data model
- cost: medium. The backend support seems partly there, but the UX surface is thin
- risk: real Org habits are nuanced. A half-implemented “habit tracker” could drift from org semantics and confuse existing users
- why_it_might_fail: if the team tries to recreate every Org habit detail at once, scope will balloon. V1 should focus on repeat chores and visibility, not a full habit graph system
- likely_implementation_areas:
  - `TimestampDialogFragment.kt` and related picker dialogs
  - `OrgTimestampMapper.kt` / timestamp display logic
  - agenda item rendering and grouping
  - reminders/calendar integration if recurrence semantics are shown there

### 5. project support layer: next actions, blockers, and review views
- summary: add lightweight project-oriented behavior on top of notes/subtrees: detect project headings, surface next actions, highlight blocked/waiting items, and provide a project review view for home and business projects
- value: medium to high. This is one of the biggest practical differences between “a pile of tasks” and a system that actually helps manage projects on mobile
- cost: medium to high. Requires defining opinionated project semantics without breaking general org flexibility
- risk: project handling in Org is often personal. Overfitting one GTD-ish model could alienate users who organize differently
- why_it_might_fail: if the semantics are too rigid, it will feel like a separate task app bolted onto Org. If too loose, the feature becomes just another saved search wrapper
- likely_implementation_areas:
  - query layer and default searches
  - note list/agenda item annotations
  - state/tag/property interpretation utilities
  - possible use of existing progress-cookie and archive logic

## rejected_or_weaker_options
- full Org table editor — powerful on desktop, but probably too high-ceremony and too low-frequency for Scott’s current mobile use cases
- literate programming / source-block execution features — impressive Org parity, but not aligned with home/project/task/note management goals
- export/publishing pipeline enhancements first — valuable for some users, but not the main pain point here
- deep clocking/time-reporting suite as a first priority — clock-in/out exists already; for this workflow, capture, dashboards, and mobile editing are likely higher leverage
- full parity with every Emacs Org command — wrong target. The better target is “best mobile subset with excellent workflow fit”

## recommended_next_steps
1. mobile org editing toolbar + insert palette
2. capture templates and target routing
3. workflow dashboards built on top of agenda + saved searches

## suggested_sequencing
- phase 1: fix note editing friction
  - build the formatting/structure toolbar and insertion helpers
- phase 2: improve capture on mobile
  - add a few strong templates and destination rules
- phase 3: improve review and execution
  - ship dashboard presets for today, home, business, and recurring chores
- phase 4: refine recurrence/project support
  - add recurring review and project next-action views once the capture/editing layer is solid
