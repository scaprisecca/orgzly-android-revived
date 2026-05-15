package com.orgzly.android.link

data class OrgRoamLinkTarget(
    val type: Type,
    val id: String?,
    val noteId: Long? = null,
    val bookId: Long,
    val title: String,
    val bookName: String,
    val customId: String? = null,
    val roamAliases: String? = null,
    val requiresIdCreation: Boolean = false,
    val duplicateIdCount: Int = 0,
) {
    enum class Type {
        NOTE,
        BOOK,
    }

    val hasDuplicateId: Boolean
        get() = duplicateIdCount > 1
}
