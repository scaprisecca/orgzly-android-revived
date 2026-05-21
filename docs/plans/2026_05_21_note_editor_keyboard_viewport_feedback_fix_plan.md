# Note Editor Keyboard Viewport Feedback Fix Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Fix Scott's manual QA feedback from the 2026-05-20 note-editor keyboard viewport implementation: first-tap content jumps to bottom, editor toolbar disappears, and title editing scrolls down to content instead of keeping the title visible.

**Architecture:** Keep the fix local to the note editor. Split cursor-reveal behavior by editor role: content body can auto-reveal while typing; title should reveal/scroll to title only; property values should not route through `RichTextEdit` cursor math. Restore toolbar visibility from editor mode/focus state without using broad layout rewrites.

**Tech Stack:** Android Kotlin, `NestedScrollView`, custom `RichText` / `RichTextEdit`, `NoteFragment`, XML layout in `fragment_note.xml`.

---

## Feedback summary

Source: Scott manual QA after implementing `docs/plans/2026_05_20_note_editor_keyboard_viewport_plan.md`.

Observed issues:

1. **Content first-tap jump:** Opening a long note and tapping the middle of content jumps to the bottom of the note. If the keyboard is already open, selecting elsewhere in content correctly stays near that viewport.
2. **Toolbar missing/covered:** The bottom editor toolbar controls are unavailable. Scott can see a small section above the keyboard with a horizontal line across the screen, like the top edge of the toolbar area. As more content is typed and the note pushes downward, that blank strip becomes partially covered by the keyboard, with roughly half of it behind the keyboard display.
3. **Title first-edit scroll:** Creating a new note and tapping the heading/title area scrolls down so the content area is focused/visible instead of keeping title visible. Text still goes into the title, but the title is covered until manually scrolling back.

Likely root causes to verify:

- `NoteFragment.updateScrollBottomPadding()` currently calls `currentEditor()?.ensureCursorVisible()` after every padding/inset update. This includes title editing and can run during initial keyboard/inset transitions when layout is not stable.
- `RichTextEdit.activate(charOffset)` currently calls `performClick()`, `setSelection(charOffset)`, and then opens the keyboard. The initial keyboard open / inset callback may trigger scroll work using stale or wrong visible bounds, especially for long notes.
- `ensureCursorVisibleNow()` computes `visibleBottom = visibleTop + scrollView.height - scrollView.paddingBottom`; if `paddingBottom` includes IME height while the window is also effectively resized, this can make visible bounds too small and force a bottom scroll.
- Toolbar missing likely means the toolbar container is logically visible but positioned too low / behind the IME. Scott can see only a blank top strip/horizontal edge above the keyboard, and that strip is partially swallowed as typing pushes the layout. Treat this first as an IME positioning/translation problem, not just a `hasEditor=false` state bug, while still logging both possibilities.

## Scope

Included:

- Fix first activation into content so it reveals the tapped line, not the bottom of the note.
- Fix title editing so title remains visible and is not scrolled down to content.
- Restore bottom editor toolbar visibility for title/content/property editing.
- Preserve body typing behavior that keeps the current line visible while typing past the viewport.
- Preserve existing toolbar actions and property timestamp behavior.

Excluded:

- New toolbar commands.
- Visual redesign of the toolbar.
- Replacing `NestedScrollView`/`RichText` architecture.
- Broad global `MainActivity` soft-input behavior changes unless verified necessary.

---

## Task 1: Add diagnostic logging around editor mode, toolbar state, and cursor reveal

**Objective:** Make the bug observable before changing behavior.

**Files:**

- Modify: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- Modify: `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`

**Steps:**

1. Add temporary `BuildConfig.LOG_DEBUG` logs in `NoteFragment.updateEditorToolbar()`:
   - `richEditorActive`
   - `propertyValueActive`
   - `contentEditorActive`
   - `binding.title.isBeingEdited()`
   - `binding.content.isBeingEdited()`
   - `editorToolbarContainer.visibility`
   - `imeBottomInset`
   - `scrollView.height`, `scrollView.paddingBottom`, `scrollView.scrollY`
2. Add temporary logs in `RichTextEdit.activate(...)`, `ensureCursorVisible(...)`, and `ensureCursorVisibleNow(...)`:
   - view id
   - requested char offset
   - selected line
   - cursor rect top/bottom after `offsetDescendantRectToMyCoords`
   - visibleTop/visibleBottom
   - targetScrollY
3. Build and manually reproduce the three feedback cases.
4. Keep logs only while debugging; remove or reduce before final handoff unless existing debug logging style supports retaining them.

**Verification:**

- Logs confirm whether the missing toolbar is visibility-state failure or layout/keyboard overlap.
- Logs confirm which call scrolls content/title to bottom on first keyboard open.

---

## Task 2: Separate editor role from generic `currentEditor()` behavior

**Objective:** Prevent title, content, and property fields from sharing cursor-reveal behavior that only makes sense for body editing.

**Files:**

- Modify: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- Modify if needed: `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`

**Implementation direction:**

Add explicit helpers in `NoteFragment`:

```kotlin
private enum class ActiveEditorRole {
    TITLE,
    CONTENT,
    PROPERTY_VALUE,
}

private fun activeEditorRole(): ActiveEditorRole? {
    return when {
        binding.content.isBeingEdited() -> ActiveEditorRole.CONTENT
        binding.title.isBeingEdited() -> ActiveEditorRole.TITLE
        currentPropertyValue() != null -> ActiveEditorRole.PROPERTY_VALUE
        else -> null
    }
}

private fun currentRichEditor(): RichText? {
    return when (activeEditorRole()) {
        ActiveEditorRole.CONTENT -> binding.content
        ActiveEditorRole.TITLE -> binding.title
        else -> null
    }
}
```

Then update existing call sites carefully:

- Toolbar action source can still use a rich editor, but content-only actions must require `ActiveEditorRole.CONTENT`.
- Timestamp for property values must still route to `currentPropertyValue()`.
- Cursor reveal from padding/inset updates should not blindly call title/content through the same generic path.

**Verification:**

- Code review shows title-specific behavior no longer calls body-specific reveal logic accidentally.
- Toolbar enabled-state remains:
  - title: rich text actions allowed where previously intended; body-only actions disabled
  - content: all relevant body actions available
  - property value: timestamp available; rich/body actions disabled

---

## Task 3: Fix toolbar visibility first, before tuning scroll behavior

**Objective:** Restore the editor toolbar as soon as any supported editor is active.

**Files:**

- Modify: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- Inspect: `app/src/main/res/layout/fragment_note.xml`

**Implementation direction:**

1. Make `updateEditorToolbar()` derive visibility from `activeEditorRole()`:

```kotlin
val editorRole = activeEditorRole()
val hasEditor = editorRole != null && binding.viewFlipper.displayedChild == 0
val contentEditorActive = editorRole == ActiveEditorRole.CONTENT
val propertyValueActive = editorRole == ActiveEditorRole.PROPERTY_VALUE
```

2. Keep `binding.editorToolbarContainer.goneUnless(hasEditor)`.
3. If diagnostics show the toolbar is visible but covered by the IME — consistent with Scott seeing only a blank top strip/horizontal line above the keyboard — do **not** mark this task complete. Continue with layout handling:
   - keep toolbar overlaid at bottom of root when keyboard closed
   - when keyboard is open and root is not resized, translate or constrain the toolbar above the IME instead of leaving it behind the keyboard
   - verify the toolbar's child buttons are actually within the visible part of `editorToolbarContainer`, not clipped below the keyboard
   - prefer local `NoteFragment` handling over global manifest changes
4. Ensure `updateEditorToolbar()` runs after:
   - content mode changes
   - title mode changes
   - property value focus changes
   - keyboard inset changes

**Verification:**

Manual checks:

- Tap title: toolbar appears.
- Tap content: toolbar appears.
- Tap property value: toolbar appears with timestamp available, body/rich actions disabled as intended.
- Close keyboard: toolbar hides or remains according to existing editor-mode behavior, with no ghost toolbar.

---

## Task 4: Stop first content activation from scrolling to note bottom

**Objective:** Tapping into the middle of a long content body should keep the tapped area visible when the keyboard opens.

**Files:**

- Modify: `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`
- Modify if needed: `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
- Modify if needed: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`

**Implementation direction:**

1. In `RichTextEdit.activate(charOffset)`, avoid scroll work until after the edit view has replaced the view-mode text and the keyboard/insets have settled.
2. Replace immediate first-activation reveal with a delayed/coalesced reveal that is anchored to the tapped `charOffset`, not `currentSelectionStart()` after unrelated changes.
3. Add a mode to reveal only if the cursor is outside the visible viewport, without trying to force bottom alignment:

```kotlin
enum class CursorRevealReason {
    INITIAL_ACTIVATION,
    TEXT_CHANGED,
    SELECTION_CHANGED,
    PADDING_CHANGED,
}
```

or keep it simpler with booleans if preferred:

```kotlin
fun ensureCursorVisible(
    charOffset: Int = currentSelectionStart(),
    allowUpwardScroll: Boolean = true,
    allowDownwardScroll: Boolean = true,
    forceComfortPadding: Boolean = false,
)
```

4. For initial activation, use conservative reveal:
   - if tapped line is already visible after keyboard opens, do nothing
   - if it is below the visible area, scroll only enough to show the tapped line above the toolbar/keyboard
   - never scroll to bottom simply because the note is long
5. Keep aggressive enough reveal for actual typing/newline events so the original bug remains fixed.

**Verification:**

Manual checks:

- Open a long note fresh.
- Tap content in the middle.
- Keyboard opens.
- View stays around tapped middle line; it does not jump to the bottom.
- Continue typing/newlines near bottom: cursor still follows typing.

---

## Task 5: Make title reveal title, not content/body

**Objective:** Creating/editing a note title should keep the title field visible and not scroll down to the content area.

**Files:**

- Modify: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- Modify: `app/src/main/java/com/orgzly/android/ui/views/richtext/RichText.kt`
- Modify if needed: `app/src/main/java/com/orgzly/android/ui/views/richtext/RichTextEdit.kt`

**Implementation direction:**

1. Add a title-specific reveal path in `NoteFragment`, e.g.:

```kotlin
private fun revealTitleEditor() {
    binding.scrollView.post {
        binding.scrollView.smoothScrollTo(0, binding.title.top.coerceAtLeast(0))
    }
}
```

Tune this to preserve breadcrumbs/top toolbar spacing if needed.

2. On title edit mode, do not call generic content cursor reveal from `updateScrollBottomPadding()`.
3. After keyboard opens while title is active:
   - ensure the title remains visible near the top/middle of viewport
   - do not scroll to content just because content is lower and has a larger text layout
4. If `RichText.ensureCursorVisible()` is retained for title, ensure its coordinate math is correct for a short single-line title and does not use content editor state.

**Verification:**

Manual checks:

- Create a new note.
- Tap title.
- Keyboard opens.
- Title remains visible while typing.
- Text goes into title.
- Content area is not auto-focused or centered.
- Existing content editing still works after switching from title to content.

---

## Task 6: Revisit IME bottom inset and padding math to avoid double-counting

**Objective:** Keep the content above keyboard/toolbar without shrinking the visible bounds so much that scroll jumps happen.

**Files:**

- Modify: `app/src/main/java/com/orgzly/android/ui/note/NoteFragment.kt`
- Inspect: `app/src/main/AndroidManifest.xml`
- Inspect: `app/src/main/res/layout/fragment_note.xml`

**Implementation direction:**

1. Confirm whether `MainActivity` remains `stateAlwaysHidden` or was changed locally during implementation. Current repo shows `stateAlwaysHidden`; keep it unless testing proves otherwise.
2. In `updateScrollBottomPadding()`, separate:
   - padding needed so content can scroll above the custom toolbar
   - keyboard/IME inset if the window is not already resized
3. Avoid subtracting both resized height and IME padding in `RichTextEdit.ensureCursorVisibleNow()`.
4. If toolbar needs to sit above the keyboard, prefer applying translation/margin to `editorToolbarContainer` rather than adding huge padding to the scroll view that changes visible-bottom calculations.
5. After changing padding/insets, call role-specific reveal:
   - content: conservative `ensureCursorVisible` only when active and necessary
   - title: `revealTitleEditor()`
   - property: no RichText reveal

**Verification:**

Manual checks:

- Keyboard open: content can scroll above toolbar/keyboard.
- Keyboard closed: no large stale bottom blank space.
- Toolbar visible and usable.
- First-tap content/title no longer jumps.

---

## Task 7: Regression test pure editor action behavior where practical

**Objective:** Protect toolbar action text transformations while the layout bug is fixed manually.

**Files:**

- Existing test: `app/src/test/java/com/orgzly/android/ui/note/EditorToolbarActionsTest.kt`
- Modify only if editor action changes are needed: `app/src/main/java/com/orgzly/android/ui/note/EditorToolbarActions.kt`

**Steps:**

1. Do not add JVM tests for IME/viewport behavior unless a pure helper is extracted.
2. If any toolbar action code changes, add focused tests for that action.
3. If cursor-reveal math is extracted to a pure helper, add JVM tests for:
   - cursor already visible -> no scroll
   - cursor below bottom comfort area -> minimal downward scroll
   - cursor above top comfort area -> minimal upward scroll
   - bottom padding does not force scroll when cursor is visible

**Verification command:**

Use explicit flavor tasks if running tests:

```bash
cd /home/agent/dev/orgzly
export JAVA_HOME="$HOME/.local/jdks/temurin-21"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.local/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
./gradlew app:testFdroidDebugUnitTest --tests com.orgzly.android.ui.note.EditorToolbarActionsTest
```

Caveat: this repo has known broader test-environment issues. A successful debug build plus Scott's manual device QA is the decisive signal for the viewport fixes.

---

## Task 8: Build and manual QA handoff

**Objective:** Produce a build that Scott can retest against the exact reported failures.

**Files:**

- No source changes unless build errors appear.

**Build command:**

```bash
cd /home/agent/dev/orgzly
./scripts/build_fdroid.sh
```

Expected APK:

```text
/home/agent/dev/orgzly/app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

**Focused manual QA checklist:**

1. **Fresh long-note content tap**
   - Open long note.
   - Tap middle of content.
   - Expected: keyboard opens and viewport stays around tapped middle area; no jump to bottom.

2. **Content typing near bottom**
   - Tap near bottom of visible content.
   - Type lines/newlines.
   - Expected: active line remains visible above keyboard/toolbar.

3. **Toolbar restored**
   - Tap content.
   - Expected: toolbar visible with bold, italic, link, bullet, checkbox, indent/de-indent, timestamp, More.
   - Tap title.
   - Expected: toolbar visible with title-appropriate actions; body-only buttons disabled if applicable.

4. **New note title edit**
   - Create new note.
   - Tap title.
   - Expected: title remains visible while typing; viewport does not scroll down to content.

5. **Property timestamp regression**
   - Edit property value.
   - Use timestamp.
   - Expected: timestamp inserts into property field; no crash; no body scroll jump.

6. **Keyboard close/reopen**
   - Close keyboard, reopen on title/content.
   - Expected: no stale bottom blank area; toolbar visibility still correct.

---

## Acceptance criteria

The plan is complete when Scott can verify all of these on-device:

- Tapping the middle of a fresh long note's content does not jump to note bottom.
- Typing in body content still keeps the cursor/current line visible while progressing downward.
- Editor toolbar is visible and usable during supported editor states.
- New-note title editing keeps title visible while typing.
- Toolbar action focus/cursor restoration still works.
- Property value timestamp editing still works.
- Keyboard close/open does not leave stale padding or hide content.

## Questions for Scott, only if still needed after implementation inspection

No blocker questions right now. The feedback is specific enough to plan and start fixing.

If the toolbar remains difficult to diagnose, ask one targeted question:

- When you say the toolbar is completely gone, is there empty space where it used to be, or is the keyboard occupying that entire area with no toolbar visible above it?
