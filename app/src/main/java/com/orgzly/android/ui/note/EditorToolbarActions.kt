package com.orgzly.android.ui.note

data class EditorSelection(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 0) { "Selection start must be non-negative" }
        require(end >= start) { "Selection end must be >= start" }
    }

    val isCollapsed: Boolean
        get() = start == end
}

data class EditorEditResult(
    val text: String,
    val selection: EditorSelection,
)

object EditorToolbarActions {
    private const val LINE_BREAK = "\n"
    private const val INDENT = "  "

    fun bold(text: String, selection: EditorSelection): EditorEditResult {
        return wrap(text, selection, "*", "*")
    }

    fun italic(text: String, selection: EditorSelection): EditorEditResult {
        return wrap(text, selection, "/", "/")
    }

    fun code(text: String, selection: EditorSelection): EditorEditResult {
        return wrap(text, selection, "~", "~")
    }

    fun link(text: String, selection: EditorSelection): EditorEditResult {
        return if (selection.isCollapsed) {
            insert(text, selection, "[[link][description]]", 2, "link".length)
        } else {
            val selectedText = text.substring(selection.start, selection.end)
            replace(
                text,
                selection,
                "[[$selectedText]]",
                EditorSelection(selection.start + 2, selection.end + 2),
            )
        }
    }

    fun bulletList(text: String, selection: EditorSelection): EditorEditResult {
        return transformLines(text, selection) { _, line ->
            "- $line"
        }
    }

    fun checkboxList(text: String, selection: EditorSelection): EditorEditResult {
        return transformLines(text, selection) { _, line ->
            "- [ ] $line"
        }
    }

    fun indent(text: String, selection: EditorSelection): EditorEditResult {
        return transformLines(text, selection) { _, line ->
            INDENT + line
        }
    }

    fun deindent(text: String, selection: EditorSelection): EditorEditResult {
        val lineStart = lineStart(text, selection.start)
        val lineEnd = lineEnd(text, selection.end)
        val selectedBlock = text.substring(lineStart, lineEnd)
        val lines = selectedBlock.split(LINE_BREAK)
        val removedIndents = lines.map { removableIndentLength(it) }
        val transformed = lines.mapIndexed { index, line ->
            line.drop(removedIndents[index])
        }.joinToString(LINE_BREAK)

        val selectionStartOffset = selection.start - lineStart
        val firstRemovedBeforeSelection = removedIndents.firstOrNull()
            ?.coerceAtMost(selectionStartOffset)
            ?: 0
        val newSelection = if (selection.isCollapsed) {
            val cursor = selection.start - firstRemovedBeforeSelection
            EditorSelection(cursor, cursor)
        } else {
            EditorSelection(
                selection.start - firstRemovedBeforeSelection,
                lineStart + transformed.length,
            )
        }

        return replace(
            text,
            EditorSelection(lineStart, lineEnd),
            transformed,
            newSelection,
        )
    }

    fun numberedList(text: String, selection: EditorSelection): EditorEditResult {
        return transformLines(text, selection) { index, line ->
            "${index + 1}. $line"
        }
    }

    fun heading(text: String, selection: EditorSelection): EditorEditResult {
        return transformLines(text, selection) { _, line ->
            "* $line"
        }
    }

    fun todoStateItem(text: String, selection: EditorSelection): EditorEditResult {
        return transformLines(text, selection) { _, line ->
            "* TODO $line"
        }
    }

    fun propertyLine(text: String, selection: EditorSelection): EditorEditResult {
        return insertAtLineStart(text, selection, ":PROPERTY: ")
    }

    fun propertyDrawer(text: String, selection: EditorSelection): EditorEditResult {
        return insertAtLineStart(text, selection, ":PROPERTIES:\n:PROPERTY: \n:END:\n", 13, "PROPERTY".length)
    }

    fun inlineTimestamp(text: String, selection: EditorSelection, timestamp: String): EditorEditResult {
        return replace(text, selection, timestamp, EditorSelection(selection.start + timestamp.length, selection.start + timestamp.length))
    }

    fun scheduledTimestamp(text: String, selection: EditorSelection, timestamp: String): EditorEditResult {
        return insertAtLineStart(text, selection, "SCHEDULED: $timestamp\n", "SCHEDULED: $timestamp".length)
    }

    fun deadlineTimestamp(text: String, selection: EditorSelection, timestamp: String): EditorEditResult {
        return insertAtLineStart(text, selection, "DEADLINE: $timestamp\n", "DEADLINE: $timestamp".length)
    }

    private fun wrap(text: String, selection: EditorSelection, prefix: String, suffix: String): EditorEditResult {
        return if (selection.isCollapsed) {
            val insertion = prefix + suffix
            insert(text, selection, insertion, prefix.length, 0)
        } else {
            val selectedText = text.substring(selection.start, selection.end)
            replace(
                text,
                selection,
                prefix + selectedText + suffix,
                EditorSelection(selection.start + prefix.length, selection.end + prefix.length),
            )
        }
    }

    private fun transformLines(
        text: String,
        selection: EditorSelection,
        transform: (index: Int, line: String) -> String,
    ): EditorEditResult {
        val lineStart = lineStart(text, selection.start)
        val lineEnd = lineEnd(text, selection.end)
        val selectedBlock = text.substring(lineStart, lineEnd)
        val lines = selectedBlock.split(LINE_BREAK)
        val transformed = lines.mapIndexed(transform).joinToString(LINE_BREAK)
        val prefixDelta = transform(0, lines.first()).length - lines.first().length
        val newSelection = if (selection.isCollapsed) {
            EditorSelection(selection.start + prefixDelta, selection.end + prefixDelta)
        } else {
            EditorSelection(selection.start + prefixDelta, selection.start + transformed.length)
        }

        return replace(
            text,
            EditorSelection(lineStart, lineEnd),
            transformed,
            newSelection,
        )
    }

    private fun insertAtLineStart(
        text: String,
        selection: EditorSelection,
        insertion: String,
        selectionOffset: Int = insertion.length,
        selectionLength: Int = 0,
    ): EditorEditResult {
        val lineStart = lineStart(text, selection.start)
        val newSelectionStart = lineStart + selectionOffset
        return replace(
            text,
            EditorSelection(lineStart, lineStart),
            insertion,
            EditorSelection(newSelectionStart, newSelectionStart + selectionLength),
        )
    }

    private fun insert(
        text: String,
        selection: EditorSelection,
        insertion: String,
        selectionOffset: Int,
        selectionLength: Int,
    ): EditorEditResult {
        val selectionStart = selection.start + selectionOffset
        return replace(
            text,
            selection,
            insertion,
            EditorSelection(selectionStart, selectionStart + selectionLength),
        )
    }

    private fun replace(
        text: String,
        selection: EditorSelection,
        replacement: String,
        newSelection: EditorSelection,
    ): EditorEditResult {
        return EditorEditResult(
            text = text.replaceRange(selection.start, selection.end, replacement),
            selection = newSelection,
        )
    }

    private fun lineStart(text: String, index: Int): Int {
        if (text.isEmpty()) {
            return 0
        }

        val boundedIndex = index.coerceAtMost(text.length)
        val previousBreak = text.lastIndexOf('\n', (boundedIndex - 1).coerceAtLeast(0))
        return if (previousBreak == -1) 0 else previousBreak + 1
    }

    private fun lineEnd(text: String, index: Int): Int {
        if (text.isEmpty()) {
            return 0
        }

        val boundedIndex = index.coerceAtMost(text.length)
        if (boundedIndex == text.length) {
            return text.length
        }

        val nextBreak = text.indexOf('\n', boundedIndex)
        return if (nextBreak == -1) text.length else nextBreak
    }

    private fun removableIndentLength(line: String): Int {
        return when {
            line.startsWith(INDENT) -> INDENT.length
            line.startsWith('\t') -> 1
            line.startsWith(' ') -> 1
            else -> 0
        }
    }
}
