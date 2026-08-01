# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Added a Tags browser in the navigation drawer for active tasks grouped by effective tag counts, including inherited parent tags and filetags.
- Added configurable colors for individual TODO and DONE workflow states in note lists, widgets, and the note editor state button.
- Added optional target headings for capture templates so new notes can be created under a specific heading inside the target notebook.
- Added autocomplete suggestions for TODO and DONE states in capture-template default state fields.
- Added autocomplete suggestions for notebooks, tags, and TODO states in the guided saved-search builder.
- Added comma-separated tag autocomplete to the capture-template editor, reusing existing note tags as fuzzy-ranked suggestions while still allowing new free-text tags.
- Added property name autocomplete in the note editor, using existing property names from other notes with fuzzy matching.
- Added a mobile-first capture template system for faster note creation, with built-in templates, notebook routing, in-app picker entry points, and share-flow integration.
- Added a mobile note editor toolbar for faster Org editing on Android, including one-tap bold, italic, link, bullet, checkbox, timestamp, and More actions.
- Added content-only indent and decrease-indent buttons to the mobile note editor toolbar for nesting bullet points while editing note body text.
- Added secondary insert actions for headings, TODO state items, numbered lists, code/verbatim text, property drawers, property lines, and scheduled, deadline, inline, and recurring timestamps.
- Added a note editor refile action so notes can be moved to another notebook or under a heading without leaving the editor, including support for placing brand-new notes before first save.
- Added a side-by-side `Orgzly Revived Dev` app variant and helper build scripts so local test builds can coexist with the installed store or F-Droid app.
- Added a guided saved-search builder for agenda-style views, plus built-in preset searches for Today / overdue, Next 7 days, No Date, Waiting, Calendar, Home, Business, and Recurring chores.
- Added saved-search metadata, property query filters, and agenda date-source options so guided views reopen for editing while staying compatible with the existing search engine.
- Added a configurable done-list destination and bulk actions to move completed tasks into it from the current notebook or a selected heading.

### Fixed
- Cleaned up the navigation drawer so saved searches and notebooks live in their full-screen sections instead of appearing inline.
- Clarified guided saved-search property filter help text so users know exact matches require both the property name and value, for example `client=acme` or `client: acme`.
- Fixed a crash after using the mobile timestamp toolbar while editing property values.
- Fixed the note editor so the mobile timestamp toolbar can be used while editing property values, allowing inline Org timestamps to be inserted directly into metadata fields.
- Fixed the note editor so when the formatting toolbar appears while you are already at the bottom of a note, the last lines stay visible instead of slipping under the toolbar.
