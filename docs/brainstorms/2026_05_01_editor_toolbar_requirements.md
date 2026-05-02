# orgzly mobile editor toolbar

created: 2026-05-01
status: draft
source: ce-brainstorm

## problem
- editing org content on mobile is still too raw-text-heavy for common note and task workflows
- the current note editor exposes very little structured insertion help, which makes org features feel harder to use on a phone than they need to be
- this especially hurts workflows built around projects, house tasks, repeating tasks, business notes, and learning notes

## goals
- make common org editing actions fast on mobile
- reduce syntax friction without hiding the underlying plain-text org model
- support Scott-style note and task workflows with a focused hybrid toolset

## non_goals
- full emacs org parity
- a giant always-visible toolbar with every org command
- replacing raw text editing with a fully abstracted rich text editor
- heading promotion and demotion in v1
- todo state cycling in v1

## proposed_behavior
- v1 direction: hybrid essential set
- primary interaction: bottom toolbar plus a More/Insert sheet for less-common actions
- common actions should stay one tap away while less-common actions stay discoverable without cluttering the main editor surface
- light context awareness for v1: actions adapt to selection vs cursor and simple current-line context, but behavior should remain predictable rather than heavily mode-driven
- always-visible toolbar actions for v1:
  - bold
  - italic
  - link
  - bullet list item
  - checkbox / todo item
  - timestamp
  - more/insert
- selection and insertion behavior for v1:
  - bold, italic, code, and link wrap selected text; if nothing is selected they insert a syntax pair at the cursor
  - bullet, checkbox, and todo actions transform the current line when there is no selection and transform each selected line when multiple lines are selected
  - timestamp is a main-toolbar action that opens a chooser for inline, scheduled, deadline, and repeater / recurring insertion flows
  - inline timestamp insertion places text at the cursor or replaces the current selection
  - more/insert opens the sheet without modifying text

## constraints
- android text selection and cursor behavior are easy to make worse
- orgzly should preserve plain-text org editing rather than inventing a separate document model
- the feature should stay focused on real mobile note/task workflows rather than desktop-only org power features

## open_questions
- none blocking for v1

## v1 more_insert_sheet
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

## insertion_rules
- property drawer block and single property line use plain org templates in v1 rather than guided forms
- heading insertion adds a simple heading marker and leaves further structure edits to the user
- todo state item insertion focuses on creating a new task line, not cycling existing states

## success_criteria
- common org edits can be performed faster and with less syntax recall
- users can add structure to notes and tasks without leaving the keyboard flow
- the editor becomes meaningfully more usable on mobile without obscuring the underlying org text

## notes_for_planning
- relevant repo areas likely include `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`, `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`, `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`, and note editor layouts
- existing ideation artifact: `docs/ideation/2026_05_01_org_mode_mobile_features_ideation.md`
