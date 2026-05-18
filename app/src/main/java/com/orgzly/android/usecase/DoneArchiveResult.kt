package com.orgzly.android.usecase

data class DoneArchiveResult(
    val movedCount: Int,
    val skippedActiveDescendantCount: Int,
    val candidateCount: Int,
    val destinationBookName: String,
    val sourceScope: DoneArchiveScope,
)
