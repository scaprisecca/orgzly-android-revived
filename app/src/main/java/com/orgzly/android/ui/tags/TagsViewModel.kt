package com.orgzly.android.ui.tags

import androidx.lifecycle.LiveData
import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.CommonViewModel

class TagsViewModel(private val dataRepository: DataRepository) : CommonViewModel() {
    val rows: LiveData<List<TagBrowserRow>> by lazy {
        dataRepository.getTagBrowserRowsLiveData()
    }
}
