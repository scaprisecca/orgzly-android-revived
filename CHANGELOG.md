# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Added property name autocomplete in the note editor, using existing property names from other notes with fuzzy matching.
- Added a mobile-first capture template system for faster note creation, with built-in templates, notebook routing, in-app picker entry points, and share-flow integration.
- Added a mobile note editor toolbar for faster Org editing on Android, including one-tap bold, italic, link, bullet, checkbox, timestamp, and More actions.
- Added an org-roam-style note link picker behind the note editor Link button, with searchable targets, optional ID creation for existing notes, and create-new linked note insertion using `[[id:...][title]]` links.
- Added secondary insert actions for headings, TODO state items, numbered lists, code/verbatim text, property drawers, property lines, and scheduled, deadline, inline, and recurring timestamps.
- Added a side-by-side `Orgzly Revived Dev` app variant and helper build scripts so local test builds can coexist with the installed store or F-Droid app.
- Added a guided saved-search builder for agenda-style views, plus built-in preset searches for Today / overdue, Next 7 days, No Date, Waiting, Calendar, Home, Business, and Recurring chores.
- Added saved-search metadata, property query filters, and agenda date-source options so guided views reopen for editing while staying compatible with the existing search engine.

### Fixed
- Clarified guided saved-search property filter help text so users know exact matches require both the property name and value, for example `client=acme` or `client: acme`.
- Fixed a crash after using the mobile timestamp toolbar while editing property values.
- Fixed the note editor so the mobile timestamp toolbar can be used while editing property values, allowing inline Org timestamps to be inserted directly into metadata fields.
- Fixed the note editor so when the formatting toolbar appears while you are already at the bottom of a note, the last lines stay visible instead of slipping under the toolbar.
