# capture templates + target routing

created: 2026-05-06
status: draft
source: ce-brainstorm

## problem
- Orgzly already supports plain note creation, shared-content capture, default target notebooks, notebook-scoped new-note shortcuts, and rich note metadata fields, but the mobile capture flow is still too generic.
- On a phone, raw “new note” capture creates friction at the exact moment when speed and structure matter most.
- Users who want inbox capture, repeating chores, meeting notes, learning notes, or business ideas still have to remember structure, tags, states, properties, and destination rules themselves.
- This makes Orgzly feel more capable than usable for real mobile workflows.

## goals
- make mobile capture fast enough that users actually use Orgzly in the moment instead of deferring capture
- provide a small set of opinionated templates that create better default structure without hiding org text
- route captured items to the right notebook or parent location with minimal user effort
- reuse as much of the existing note creation and share infrastructure as possible
- create a foundation that can later support user-defined templates without forcing that complexity into v1

## non_goals
- full emacs org-capture parity in v1
- a free-form template language or scripting system in v1
- a large template editor/settings builder in v1
- solving every capture source at once with the exact same UI
- replacing normal manual note creation or raw text editing
- introducing a separate document model outside existing `NotePayload` and `NotePlace` flows

## proposed_behavior
- v1 introduces a small set of first-class capture templates:
  - inbox task
  - repeating chore
  - meeting note
  - learning note
  - business idea
- each template defines:
  - a label and icon
  - a title prompt pattern
  - default note fields where applicable: state, priority, scheduled, deadline, tags, properties
  - optional body scaffold text
  - a target routing rule
- capture should feel like “pick template, fill 1-3 key fields, save” rather than “open a blank note and build everything manually”.

## v1_template_shapes
### inbox task
- purpose: quick task capture with minimal friction
- defaults:
  - title required
  - default state optional but likely `TODO` if configured in existing app note defaults
  - optional tags from template
  - usually no forced body scaffold
- routing:
  - default notebook or user-selected task inbox target
- v1 expectation:
  - fastest path; should be near current quick note speed

### repeating chore
- purpose: recurring household or maintenance task capture
- defaults:
  - title required
  - task state applied
  - scheduled timestamp required or strongly encouraged
  - repeater preset flow included
  - optional house/chore tags
- body scaffold:
  - usually none or a very small notes section
- routing:
  - chores notebook or chores parent heading if configured
- v1 expectation:
  - optimized for recurrence setup, not a full habit system

### meeting note
- purpose: structured meeting capture from phone
- defaults:
  - title prefilled from a pattern like meeting + date, but editable
  - active timestamp or created-at-friendly context
  - optional tags like `meeting`
- body scaffold:
  - attendees
  - agenda
  - notes
  - follow-ups
- routing:
  - meetings notebook or configured notes inbox
- v1 expectation:
  - should bias toward note capture first, action extraction later

### learning note
- purpose: save a concept, resource, or takeaway quickly
- defaults:
  - title required
  - optional source property or tag
  - optional timestamp
- body scaffold:
  - source
  - takeaway
  - follow-up / review
- routing:
  - learning notebook or configured parent heading
- v1 expectation:
  - should work well from both in-app creation and Android share flows

### business idea
- purpose: capture ideas without losing structure
- defaults:
  - title required
  - optional tags like `business` or `idea`
  - optional created-at/context property
- body scaffold:
  - problem
  - idea
  - next step
- routing:
  - business notebook or ideas parent heading
- v1 expectation:
  - capture now, evaluate later; avoid heavyweight forms

## target_routing_model
- routing should be explicit but simple in v1.
- recommended routing precedence:
  1. explicit target bundled with the launch source or shortcut
  2. template-specific configured target
  3. app-level default notebook fallback
- supported v1 targets:
  - notebook target
  - optional parent note target for insertion under a known heading
- supported placement behavior in v1:
  - notebook root using existing new-note placement rules
  - parent note insertion using existing `NotePlace(bookId, noteId, place)` capabilities where a known parent exists
- v1 should avoid overly clever rule engines. No tag-based dynamic routing or condition trees yet.

## entry_points
- in-app new note flow should gain a template picker path instead of only blank note creation
- existing share flow should support choosing a capture template before opening the editor when appropriate
- notebook-scoped new-note shortcuts should be extensible into template-scoped shortcuts later, but v1 does not need full launcher shortcut management for every template
- notification quick capture is a good follow-on integration point, but not required for initial v1 unless scope stays very tight

## editing_and_confirmation_behavior
- after choosing a template, the user should land in a normal editable note screen populated with the template defaults
- the feature should not auto-save silently into a hidden destination in v1
- users should be able to adjust title, content, tags, and timestamps before saving
- for the fastest templates, it is acceptable to keep the form minimal and let the existing note editor handle final edits

## configuration_model
- v1 should prefer fixed built-in templates with light configuration over a full template editor
- lightweight configuration that is reasonable for v1:
  - enable/disable individual built-in templates
  - assign target notebook per template
  - optionally assign target parent note per template when a stable heading target exists
  - optionally choose whether a template appears in quick capture surfaces
- configuration that should wait until later:
  - custom arbitrary templates
  - arbitrary field scripting
  - user-authored body template language
  - advanced routing conditions

## constraints
- the repo already has useful building blocks, but they are spread across several paths:
  - `ShareActivity` already collects shared content and can receive `EXTRA_BOOK_ID`
  - `DataRepository.getTargetBook()` already implements default notebook fallback
  - `NotePayload` already supports state, priority, scheduled, deadline, tags, and properties
  - `NotePlace` already supports notebook and parent-note placement
  - external intent parsing already supports parent book/note targeting and placement concepts
- this means the main challenge is product packaging and UI flow, not raw storage capability
- too much configurability too early will create a settings swamp
- parent-note routing is higher value than notebook-only routing, but it is also more fragile because parent references must remain valid
- shared content capture adds title/body import concerns that differ from blank template capture
- launcher shortcuts and notification capture have different UX constraints than the full-screen in-app flow

## open_questions
- should share-to-orgzly always show a template picker, or only when multiple capture templates are enabled?
- for repeating chore, should the template open the existing timestamp flow immediately as part of setup, or just prefill scaffold text and let the user add recurrence manually?
- should template shortcuts be part of v1 or treated as a phase-2 extension of the current notebook shortcut path?

## decisions
- v1 scope is notebook routing only
- v1 includes share-flow integration
- parent-note routing is explicitly deferred until after real-world testing
- parent-note routing should be evaluated as a follow-on enhancement rather than designed into the first implementation

## success_criteria
- a user can capture the five v1 note/task types with less setup friction than today
- captured notes land in the expected target location without requiring manual refile in common cases
- the feature improves consistency of captured notes without forcing users through a heavy form
- the app reuses existing note editing after template application, so the result still feels like Orgzly rather than a separate capture subsystem
- the settings surface remains understandable and does not turn into a template-programming UI

## notes_for_planning
- repo evidence suggests a strong reuse path:
  - `app/src/main/java/com/orgzly/android/ui/share/ShareActivity.java`
  - `app/src/main/java/com/orgzly/android/data/DataRepository.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NotePayload.kt`
  - `app/src/main/java/com/orgzly/android/ui/note/NoteBuilder.kt`
  - `app/src/main/java/com/orgzly/android/ui/NotePlace.java`
  - `app/src/main/java/com/orgzly/android/external/actionhandlers/ExternalIntentParser.kt`
  - `app/src/main/java/com/orgzly/android/ui/TemplateChooserActivity.java`
  - `app/src/main/java/com/orgzly/android/SharingShortcutsManager.kt`
- one notable quirk: `TemplateChooserActivity` is currently really a notebook chooser for new-note shortcuts, not a true capture-template chooser. Planning should decide whether to repurpose, rename, or leave it alone and add a new template flow.
- practical implementation path is likely:
  - define a small built-in capture template model
  - add target-resolution logic with clear fallback precedence
  - insert a template selection step into one or more entry points
  - prebuild `NotePayload` + `NotePlace` from the selected template
  - hand off to the normal note editor for confirmation and save
- likely safest v1 sequencing:
  1. built-in templates + notebook routing only
  2. share-flow integration
  3. shortcut / notification extensions
  4. optional parent-note routing after validation
- this feature is ready for `ce-plan` with v1 bounded to notebook routing plus share-flow integration.