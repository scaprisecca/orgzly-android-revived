package com.orgzly.android.ui.savedsearch

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState
import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState.DateFilter
import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState.PropertyFilter
import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState.SortPreference
import com.orgzly.android.savedsearch.builder.AgendaViewMetadataJson
import com.orgzly.android.savedsearch.builder.AgendaViewQueryCompiler
import com.orgzly.android.ui.CommonFragment
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.savedsearches.SavedSearchesFragment
import com.orgzly.android.ui.util.CommaSeparatedAutocomplete
import com.orgzly.android.ui.util.CommaSeparatedSuggestionAdapter
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.FragmentAgendaSavedSearchBuilderBinding
import javax.inject.Inject

class AgendaSavedSearchBuilderFragment : CommonFragment(), DrawerItem {
    @Inject
    lateinit var dataRepository: DataRepository

    private lateinit var binding: FragmentAgendaSavedSearchBuilderBinding
    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel
    private var listener: Listener? = null
    private var savedSearch: SavedSearch? = null
    private val compiler = AgendaViewQueryCompiler()

    override fun getCurrentDrawerItemId(): String = SavedSearchesFragment.getDrawerItemId()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
        listener = activity as? Listener
            ?: throw ClassCastException("${requireActivity()} must implement ${Listener::class.java}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedMainActivityViewModel = ViewModelProvider(requireActivity())
            .get(SharedMainActivityViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAgendaSavedSearchBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns()
        setupListAutocompletes()
        setupToolbar()
        setupPreviewUpdates()

        if (isEditingExistingFilter()) {
            savedSearch = dataRepository.getSavedSearch(requireArguments().getLong(ARG_ID))
            val state = AgendaViewMetadataJson.deserialize(savedSearch)
            if (savedSearch == null || state == null) {
                binding.fragmentSavedSearchBuilderMissing.isVisible = true
                binding.fragmentSavedSearchBuilderContent.isVisible = false
            } else {
                bindState(state)
                binding.fragmentSavedSearchBuilderMissing.isVisible = false
                binding.fragmentSavedSearchBuilderContent.isVisible = true
            }
        } else {
            bindState(AgendaViewBuilderState())
            binding.fragmentSavedSearchBuilderMissing.isVisible = false
            binding.fragmentSavedSearchBuilderContent.isVisible = true
            KeyboardUtils.openSoftKeyboard(binding.fragmentSavedSearchBuilderName)
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
        sharedMainActivityViewModel.lockDrawer()
    }

    override fun onPause() {
        super.onPause()
        sharedMainActivityViewModel.unlockDrawer()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun setupDropdowns() {
        bindDropdown(
            binding.fragmentSavedSearchBuilderDateFilterValue,
            DateFilter.values().map(::dateFilterLabel),
        )
        bindDropdown(
            binding.fragmentSavedSearchBuilderSortValue,
            SortPreference.values().map(::sortLabel),
        )
    }

    private fun setupToolbar() {
        binding.topToolbar.setNavigationOnClickListener { close() }
        binding.topToolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.done) {
                save()
                true
            } else {
                false
            }
        }
        binding.topToolbar.setOnClickListener { binding.scrollView.scrollTo(0, 0) }
    }

    private fun setupListAutocompletes() {
        val notebookAdapter = newListSuggestionAdapter()
        val excludeNotebookAdapter = newListSuggestionAdapter()
        setupListAutocomplete(binding.fragmentSavedSearchBuilderIncludeNotebooks, notebookAdapter)
        setupListAutocomplete(binding.fragmentSavedSearchBuilderExcludeNotebooks, excludeNotebookAdapter)

        val notebookNames = dataRepository.getBooks()
            .map { it.book.name }
            .distinct()
            .sorted()
        notebookAdapter.updateDictionary(notebookNames)
        excludeNotebookAdapter.updateDictionary(notebookNames)

        val tagAdapter = newListSuggestionAdapter()
        val excludeTagAdapter = newListSuggestionAdapter()
        setupListAutocomplete(binding.fragmentSavedSearchBuilderIncludeTags, tagAdapter)
        setupListAutocomplete(binding.fragmentSavedSearchBuilderExcludeTags, excludeTagAdapter)

        dataRepository.selectAllTagsLiveData().observe(viewLifecycleOwner) { tags ->
            tagAdapter.updateDictionary(tags)
            excludeTagAdapter.updateDictionary(tags)
        }

        val stateAdapter = newListSuggestionAdapter()
        val excludeStateAdapter = newListSuggestionAdapter()
        setupListAutocomplete(binding.fragmentSavedSearchBuilderIncludeStates, stateAdapter)
        setupListAutocomplete(binding.fragmentSavedSearchBuilderExcludeStates, excludeStateAdapter)

        val states = (AppPreferences.todoKeywordsSet(requireContext()) + AppPreferences.doneKeywordsSet(requireContext()))
            .distinct()
            .sorted()
        stateAdapter.updateDictionary(states)
        excludeStateAdapter.updateDictionary(states)
    }

    private fun setupPreviewUpdates() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshPreview()
            override fun afterTextChanged(s: Editable?) = Unit
        }

        listOf(
            binding.fragmentSavedSearchBuilderName,
            binding.fragmentSavedSearchBuilderIncludeNotebooks,
            binding.fragmentSavedSearchBuilderExcludeNotebooks,
            binding.fragmentSavedSearchBuilderIncludeTags,
            binding.fragmentSavedSearchBuilderExcludeTags,
            binding.fragmentSavedSearchBuilderIncludeStates,
            binding.fragmentSavedSearchBuilderExcludeStates,
            binding.fragmentSavedSearchBuilderIncludeProperties,
            binding.fragmentSavedSearchBuilderExcludeProperties,
            binding.fragmentSavedSearchBuilderAdvancedQuery,
        ).forEach { it.addTextChangedListener(watcher) }

        listOf(
            binding.fragmentSavedSearchBuilderExcludeDone,
            binding.fragmentSavedSearchBuilderDateSourceScheduled,
            binding.fragmentSavedSearchBuilderDateSourceDeadline,
            binding.fragmentSavedSearchBuilderDateSourceEvent,
        ).forEach { it.setOnCheckedChangeListener { _, _ -> refreshPreview() } }

        binding.fragmentSavedSearchBuilderDateFilterValue.setOnItemClickListener { _, _, _, _ -> refreshPreview() }
        binding.fragmentSavedSearchBuilderSortValue.setOnItemClickListener { _, _, _, _ -> refreshPreview() }
    }

    private fun newListSuggestionAdapter(): CommaSeparatedSuggestionAdapter {
        return CommaSeparatedSuggestionAdapter(requireContext(), R.layout.dropdown_item)
    }

    private fun setupListAutocomplete(
        view: AppCompatMultiAutoCompleteTextView,
        adapter: CommaSeparatedSuggestionAdapter,
    ) {
        view.setAdapter(adapter)
        view.setTokenizer(CommaSeparatedAutocomplete.tokenizer)
        view.threshold = 1
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && currentListToken(view).isEmpty()) {
                view.showDropDown()
            }
        }
        view.setOnClickListener {
            if (currentListToken(view).isEmpty()) {
                view.showDropDown()
            }
        }
    }

    private fun currentListToken(view: AppCompatMultiAutoCompleteTextView): String {
        val text = view.text ?: return ""
        val cursor = view.selectionStart.takeIf { it >= 0 } ?: text.length
        return CommaSeparatedAutocomplete.currentToken(text, cursor)
    }

    private fun bindState(state: AgendaViewBuilderState) {
        binding.fragmentSavedSearchBuilderName.setText(state.name)
        binding.fragmentSavedSearchBuilderIncludeNotebooks.setText(state.includeNotebooks.joinToString(", "))
        binding.fragmentSavedSearchBuilderExcludeNotebooks.setText(state.excludeNotebooks.joinToString(", "))
        binding.fragmentSavedSearchBuilderIncludeTags.setText(state.includeTags.joinToString(", "))
        binding.fragmentSavedSearchBuilderExcludeTags.setText(state.excludeTags.joinToString(", "))
        binding.fragmentSavedSearchBuilderIncludeStates.setText(state.includeStates.joinToString(", "))
        binding.fragmentSavedSearchBuilderExcludeStates.setText(state.excludeStates.joinToString(", "))
        binding.fragmentSavedSearchBuilderIncludeProperties.setText(formatProperties(state.includeProperties))
        binding.fragmentSavedSearchBuilderExcludeProperties.setText(formatProperties(state.excludeProperties))
        binding.fragmentSavedSearchBuilderExcludeDone.isChecked = state.excludeDone
        binding.fragmentSavedSearchBuilderDateFilterValue.setText(dateFilterLabel(state.dateFilter), false)
        binding.fragmentSavedSearchBuilderSortValue.setText(sortLabel(state.sort), false)
        binding.fragmentSavedSearchBuilderDateSourceScheduled.isChecked =
            state.dateSources.contains(com.orgzly.android.query.AgendaDateSource.SCHEDULED)
        binding.fragmentSavedSearchBuilderDateSourceDeadline.isChecked =
            state.dateSources.contains(com.orgzly.android.query.AgendaDateSource.DEADLINE)
        binding.fragmentSavedSearchBuilderDateSourceEvent.isChecked =
            state.dateSources.contains(com.orgzly.android.query.AgendaDateSource.EVENT)
        binding.fragmentSavedSearchBuilderAdvancedQuery.setText(state.advancedQuery.orEmpty())
        refreshPreview()
    }

    private fun refreshPreview() {
        val state = readState() ?: return
        val query = compileQueryForUi(state) ?: return
        binding.fragmentSavedSearchBuilderQueryPreview.setText(query)
    }

    private fun save() {
        val state = readState() ?: return
        val name = state.name.trim()

        if (name.isBlank()) {
            binding.fragmentSavedSearchBuilderNameInputLayout.error = getString(R.string.can_not_be_empty)
            return
        }
        if (sameNameFilterExists(name)) {
            binding.fragmentSavedSearchBuilderNameInputLayout.error = getString(R.string.filter_name_already_exists)
            return
        }

        binding.fragmentSavedSearchBuilderNameInputLayout.error = null

        val query = compileQueryForUi(state) ?: return

        val metadata = AgendaViewMetadataJson.serialize(state.copy(name = name))
        val existing = savedSearch
        val result = if (existing != null) {
            SavedSearch(
                id = existing.id,
                name = name,
                query = query,
                position = existing.position,
                builderMetadata = metadata,
                builderMetadataVersion = AgendaViewMetadataJson.VERSION,
                presetKey = existing.presetKey,
            )
        } else {
            SavedSearch(
                id = 0,
                name = name,
                query = query,
                position = 0,
                builderMetadata = metadata,
                builderMetadataVersion = AgendaViewMetadataJson.VERSION,
                presetKey = null,
            )
        }

        if (existing != null) {
            listener?.onSavedSearchUpdateRequest(result)
        } else {
            listener?.onSavedSearchCreateRequest(result)
        }
    }

    private fun compileQueryForUi(state: AgendaViewBuilderState): String? {
        return runCatching {
            compiler.compileToString(state)
        }.onSuccess {
            binding.fragmentSavedSearchBuilderAdvancedQueryInputLayout.error = null
        }.onFailure {
            binding.fragmentSavedSearchBuilderAdvancedQueryInputLayout.error = getString(R.string.invalid_query)
        }.getOrNull()
    }

    private fun close() {
        listener?.onSavedSearchCancelRequest()
    }

    private fun readState(): AgendaViewBuilderState? {
        val dateSources = linkedSetOf<com.orgzly.android.query.AgendaDateSource>().apply {
            if (binding.fragmentSavedSearchBuilderDateSourceScheduled.isChecked) add(com.orgzly.android.query.AgendaDateSource.SCHEDULED)
            if (binding.fragmentSavedSearchBuilderDateSourceDeadline.isChecked) add(com.orgzly.android.query.AgendaDateSource.DEADLINE)
            if (binding.fragmentSavedSearchBuilderDateSourceEvent.isChecked) add(com.orgzly.android.query.AgendaDateSource.EVENT)
        }

        if (dateSources.isEmpty()) {
            binding.fragmentSavedSearchBuilderDateSourcesLabel.error = getString(R.string.select_at_least_one_date_source)
            return null
        }

        binding.fragmentSavedSearchBuilderDateSourcesLabel.error = null

        return AgendaViewBuilderState(
            name = binding.fragmentSavedSearchBuilderName.text?.toString().orEmpty().trim(),
            includeNotebooks = splitList(binding.fragmentSavedSearchBuilderIncludeNotebooks.text?.toString()),
            excludeNotebooks = splitList(binding.fragmentSavedSearchBuilderExcludeNotebooks.text?.toString()),
            includeTags = splitList(binding.fragmentSavedSearchBuilderIncludeTags.text?.toString()),
            excludeTags = splitList(binding.fragmentSavedSearchBuilderExcludeTags.text?.toString()),
            includeStates = splitList(binding.fragmentSavedSearchBuilderIncludeStates.text?.toString()),
            excludeStates = splitList(binding.fragmentSavedSearchBuilderExcludeStates.text?.toString()),
            includeProperties = parseProperties(binding.fragmentSavedSearchBuilderIncludeProperties.text?.toString()),
            excludeProperties = parseProperties(binding.fragmentSavedSearchBuilderExcludeProperties.text?.toString()),
            excludeDone = binding.fragmentSavedSearchBuilderExcludeDone.isChecked,
            dateFilter = dateFilterFromLabel(binding.fragmentSavedSearchBuilderDateFilterValue.text?.toString()),
            dateSources = dateSources,
            sort = sortFromLabel(binding.fragmentSavedSearchBuilderSortValue.text?.toString()),
            advancedQuery = binding.fragmentSavedSearchBuilderAdvancedQuery.text?.toString()?.trim()?.ifBlank { null },
        )
    }

    private fun splitList(value: String?): List<String> {
        return value
            .orEmpty()
            .split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseProperties(value: String?): List<PropertyFilter> {
        return value
            .orEmpty()
            .split(",", "\n")
            .mapNotNull { item ->
                val trimmed = item.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    val parts = trimmed.split("=", ":", limit = 2)
                    if (parts.size == 2) {
                        PropertyFilter(parts[0].trim(), PropertyFilter.Operator.EQUALS, parts[1].trim())
                    } else {
                        PropertyFilter(trimmed, PropertyFilter.Operator.EXISTS)
                    }
                }
            }
    }

    private fun formatProperties(properties: List<PropertyFilter>): String {
        return properties.joinToString("\n") {
            when (it.operator) {
                PropertyFilter.Operator.EXISTS -> it.name
                PropertyFilter.Operator.EQUALS -> "${it.name}=${it.value.orEmpty()}"
            }
        }
    }

    private fun bindDropdown(view: AppCompatAutoCompleteTextView, labels: List<String>) {
        view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        if (labels.isNotEmpty() && view.text.isNullOrEmpty()) {
            view.setText(labels.first(), false)
        }
    }

    private fun dateFilterValues(): List<DateFilter> = DateFilter.values().toList()

    private fun sortValues(): List<SortPreference> = SortPreference.values().toList()

    private fun dateFilterFromLabel(value: String?): DateFilter {
        return dateFilterValues().firstOrNull { dateFilterLabel(it) == value } ?: DateFilter.NONE
    }

    private fun sortFromLabel(value: String?): SortPreference {
        return sortValues().firstOrNull { sortLabel(it) == value } ?: SortPreference.PRIORITY
    }

    private fun dateFilterLabel(value: DateFilter): String {
        return when (value) {
            DateFilter.NONE -> getString(R.string.saved_search_builder_date_none)
            DateFilter.TODAY_OVERDUE -> getString(R.string.saved_search_builder_date_today_overdue)
            DateFilter.NEXT_3 -> getString(R.string.saved_search_builder_date_next_3)
            DateFilter.NEXT_7 -> getString(R.string.saved_search_builder_date_next_7)
            DateFilter.NEXT_14 -> getString(R.string.saved_search_builder_date_next_14)
            DateFilter.NEXT_30 -> getString(R.string.saved_search_builder_date_next_30)
            DateFilter.NO_SCHEDULED_OR_DEADLINE -> getString(R.string.saved_search_builder_date_no_date)
            DateFilter.CUSTOM_ADVANCED -> getString(R.string.saved_search_builder_date_custom)
        }
    }

    private fun sortLabel(value: SortPreference): String {
        return when (value) {
            SortPreference.DATE -> getString(R.string.saved_search_builder_sort_date)
            SortPreference.PRIORITY -> getString(R.string.saved_search_builder_sort_priority)
            SortPreference.NOTEBOOK -> getString(R.string.saved_search_builder_sort_notebook)
            SortPreference.EVENT -> getString(R.string.saved_search_builder_sort_event)
        }
    }

    private fun isEditingExistingFilter(): Boolean {
        return arguments != null && requireArguments().containsKey(ARG_ID)
    }

    private fun sameNameFilterExists(name: String): Boolean {
        val matches = dataRepository.getSavedSearchesByNameIgnoreCase(name)
        val editingId = savedSearch?.id
        return matches.any { existing ->
            name.equals(existing.name, ignoreCase = true) && existing.id != editingId
        }
    }

    interface Listener {
        fun onSavedSearchCreateRequest(savedSearch: SavedSearch)
        fun onSavedSearchUpdateRequest(savedSearch: SavedSearch)
        fun onSavedSearchCancelRequest()
    }

    companion object {
        private const val ARG_ID = "id"
        const val FRAGMENT_TAG: String = "com.orgzly.android.ui.savedsearch.AgendaSavedSearchBuilderFragment"
        private val TAG = AgendaSavedSearchBuilderFragment::class.java.name

        @JvmStatic
        fun getInstance(): Fragment = AgendaSavedSearchBuilderFragment()

        @JvmStatic
        fun getInstance(id: Long): Fragment {
            return AgendaSavedSearchBuilderFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ID, id)
                }
            }
        }
    }
}
