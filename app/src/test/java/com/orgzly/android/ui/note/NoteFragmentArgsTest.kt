package com.orgzly.android.ui.note

import com.orgzly.android.ui.NotePlace
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteFragmentArgsTest {
    @Test
    fun forNewNoteStoresInitialPayloadInArguments() {
        val payload = NotePayload(
            title = "Meeting 2026-05-06",
            content = "* Agenda",
            state = "TODO",
            scheduled = "<2026-05-06 Tue>",
            tags = listOf("meeting"),
        )

        val fragment = NoteFragment.forNewNote(NotePlace(42L), payload)
        val args = requireNotNull(fragment?.arguments)

        @Suppress("DEPRECATION")
        val storedPayload = requireNotNull(args.getParcelable("payload") as? NotePayload)

        assertThat(args.getLong("book_id"), `is`(42L))
        assertThat(storedPayload, `is`(payload))
    }
}
