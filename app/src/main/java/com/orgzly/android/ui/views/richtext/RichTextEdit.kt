package com.orgzly.android.ui.views.richtext

import android.content.Context
import android.graphics.Rect
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ancestors
import androidx.core.widget.NestedScrollView
import com.orgzly.BuildConfig
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.util.LogUtils

class RichTextEdit : AppCompatEditText {
    enum class CursorRevealReason {
        INITIAL_ACTIVATION,
        FOLLOW_CURSOR,
        PADDING_CHANGED,
    }

    private data class CursorRevealRequest(
        val charOffset: Int,
        val reason: CursorRevealReason,
    )

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val userEditingTextWatcher: TextWatcher = RichTextEditWatcher()
    private var pendingCursorRevealRequest: CursorRevealRequest? = null
    private var cursorVisibilityScheduled = false
    private var suspendCursorTracking = false
    private val ensureCursorVisibleRunnable = Runnable {
        cursorVisibilityScheduled = false
        val request = pendingCursorRevealRequest
            ?: CursorRevealRequest(currentSelectionStart(), CursorRevealReason.FOLLOW_CURSOR)
        pendingCursorRevealRequest = null
        ensureCursorVisibleNow(request)
    }

    fun activate(charOffset: Int) {
        visibility = View.VISIBLE
        addTextChangedListener(userEditingTextWatcher)

        // Position the cursor and open the keyboard
        if (charOffset in 0..(text?.length ?: 0)) {
            suspendCursorTracking = true
            performClick()
            setSelection(charOffset)

            KeyboardUtils.openSoftKeyboard(this) {
                suspendCursorTracking = false
                ensureCursorVisible(charOffset, CursorRevealReason.INITIAL_ACTIVATION)
            }
            postDelayed({
                suspendCursorTracking = false
            }, 500)
        }
    }

    fun ensureCursorVisible(
        charOffset: Int = currentSelectionStart(),
        reason: CursorRevealReason = CursorRevealReason.FOLLOW_CURSOR,
    ) {
        pendingCursorRevealRequest = CursorRevealRequest(charOffset.coerceAtLeast(0), reason)

        if (cursorVisibilityScheduled) {
            return
        }

        cursorVisibilityScheduled = true
        post(ensureCursorVisibleRunnable)
    }

    // TODO: Handle closed drawers (and such)
    private fun ensureCursorVisibleNow(request: CursorRevealRequest) {
        if (!hasFocus() || !isShown || !isLaidOut) {
            return
        }

        val scrollView = ancestors.firstOrNull { view -> view is NestedScrollView } as? NestedScrollView
            ?: return
        val textLayout = layout ?: return
        val boundedOffset = request.charOffset.coerceIn(0, text?.length ?: 0)
        val line = textLayout.getLineForOffset(boundedOffset)
        val lineHeight = textLayout.getLineBottom(line) - textLayout.getLineTop(line)
        val cursorRect = Rect(
            0,
            totalPaddingTop + textLayout.getLineTop(line),
            width,
            totalPaddingTop + textLayout.getLineBottom(line),
        )

        scrollView.offsetDescendantRectToMyCoords(this, cursorRect)

        val visibleTop = scrollView.scrollY
        val bottomPadding = if (request.reason == CursorRevealReason.INITIAL_ACTIVATION) {
            0
        } else {
            scrollView.paddingBottom
        }
        val visibleBottom = visibleTop + scrollView.height - bottomPadding
        val viewportHeight = (visibleBottom - visibleTop).coerceAtLeast(0)
        if (viewportHeight == 0) {
            return
        }
        val useComfortPadding = request.reason == CursorRevealReason.FOLLOW_CURSOR
        val topComfort = if (useComfortPadding) {
            (lineHeight / 2).coerceAtLeast(0)
        } else {
            0
        }
        val bottomComfort = if (useComfortPadding) {
            lineHeight.coerceAtLeast(0)
        } else {
            0
        }

        val targetScrollY = when {
            cursorRect.top < visibleTop + topComfort -> {
                (cursorRect.top - topComfort).coerceAtLeast(0)
            }

            cursorRect.bottom > visibleBottom - bottomComfort -> {
                (cursorRect.bottom - viewportHeight + bottomComfort)
                    .coerceAtLeast(0)
            }

            else -> -1
        }

        if (targetScrollY != -1) {
            scrollView.scrollTo(0, targetScrollY)
        }

        if (BuildConfig.LOG_DEBUG) {
            LogUtils.d(TAG, request.reason, boundedOffset, targetScrollY)
        }
    }

    fun deactivate() {
        removeTextChangedListener(userEditingTextWatcher)
        removeCallbacks(ensureCursorVisibleRunnable)
        cursorVisibilityScheduled = false
        pendingCursorRevealRequest = null
        suspendCursorTracking = false

        visibility = View.GONE
    }

    fun currentSelectionStart(): Int {
        return selectionStart.coerceAtLeast(0)
    }

    fun currentSelectionEnd(): Int {
        return selectionEnd.coerceAtLeast(currentSelectionStart())
    }

    fun applyEdit(text: String, selectionStart: Int, selectionEnd: Int) {
        setText(text)
        val boundedStart = selectionStart.coerceIn(0, text.length)
        val boundedEnd = selectionEnd.coerceIn(boundedStart, text.length)
        setSelection(boundedStart, boundedEnd)
        requestFocusAndOpenKeyboard()
    }

    fun requestFocusAndOpenKeyboard() {
        requestFocus()
        KeyboardUtils.openSoftKeyboard(this) {
            ensureCursorVisible()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)

        if (hasFocus() && !suspendCursorTracking) {
            ensureCursorVisible(selEnd.coerceAtLeast(selStart))
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        if (hasFocus() && !suspendCursorTracking && lengthAfter != 0) {
            ensureCursorVisible()
        }
    }

    /* Clear the focus on back press before letting IME handle the event. */
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Clear focus before IME handling the event")
            clearFocus()
        }

        return super.onKeyPreIme(keyCode, event)
    }

    companion object {
        val TAG: String = RichTextEdit::class.java.name
    }
}
