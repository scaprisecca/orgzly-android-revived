package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.capture.CaptureTemplates
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.db.entity.Note
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.util.CommaSeparatedAutocomplete
import com.orgzly.android.ui.util.CommaSeparatedSuggestionAdapter
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.databinding.FragmentCaptureTemplateEditorBinding
import java.util.UUID
import javax.inject.Inject

class TemplateEditorFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentCaptureTemplateEditorBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var dialog: AlertDialog? = null
    private var initialized = false
    private var loadedTemplate: CaptureTemplateEntity? = null
    private var notebookNames: Set<String> = emptySet()
    private var headingPaths: Set<String> = emptySet()

    @Inject
    lateinit var dataRepository: DataRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCaptureTemplateEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNotebookDropdown()
        setupHeadingDropdown()
        setupTagsAutocomplete()
        setupDefaultStateAutocomplete()
        binding.save.setOnClickListener { saveTemplate() }
        binding.delete.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        if (!initialized) {
            loadTemplate()
            initialized = true
        }
    }

    override fun onPause() {
        super.onPause()
        dialog?.dismiss()
        dialog = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        initialized = false
        _binding = null
    }

    private fun setupNotebookDropdown() {
        notebookNames = dataRepository.getBooks().map { it.book.name }.toSet()

        val notebookEntries = mutableListOf(getString(R.string.default_notebook))
        notebookEntries.addAll(notebookNames.sorted())

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            notebookEntries,
        )
        binding.targetNotebook.setAdapter(adapter)
        binding.targetNotebook.setOnItemClickListener { _, _, _, _ ->
            refreshHeadingSuggestions(clearInvalidHeading = true)
        }
    }

    private fun setupHeadingDropdown() {
        binding.targetHeading.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && headingPaths.isNotEmpty()) {
                binding.targetHeading.showDropDown()
            }
        }

        binding.targetHeading.setOnClickListener {
            if (headingPaths.isNotEmpty()) {
                binding.targetHeading.showDropDown()
            }
        }

        binding.targetHeading.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (binding.targetHeadingLayout.error != null) {
                    binding.targetHeadingLayout.error = null
                }
            }
        })
    }

    private fun setupTagsAutocomplete() {
        val adapter = CommaSeparatedSuggestionAdapter(
            requireContext(),
            R.layout.dropdown_item,
        )

        binding.tagsInput.setAdapter(adapter)
        binding.tagsInput.setTokenizer(CaptureTemplateTagInput.tokenizer)
        binding.tagsInput.threshold = 1

        dataRepository.selectAllTagsLiveData().observe(viewLifecycleOwner) { tags ->
            adapter.updateDictionary(tags)
        }

        binding.tagsInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && currentTagToken().isEmpty()) {
                binding.tagsInput.showDropDown()
            }
        }

        binding.tagsInput.setOnClickListener {
            if (currentTagToken().isEmpty()) {
                binding.tagsInput.showDropDown()
            }
        }
    }

    private fun currentTagToken(): String {
        val text = binding.tagsInput.text ?: return ""
        val cursor = binding.tagsInput.selectionStart.takeIf { it >= 0 } ?: text.length
        return CommaSeparatedAutocomplete.currentToken(text, cursor)
    }

    private fun setupDefaultStateAutocomplete() {
        val adapter = CommaSeparatedSuggestionAdapter(
            requireContext(),
            R.layout.dropdown_item,
        )
        val stateKeywords = (AppPreferences.todoKeywordsSet(requireContext()) +
            AppPreferences.doneKeywordsSet(requireContext()))
            .distinct()
            .sorted()

        adapter.updateDictionary(stateKeywords)
        binding.defaultStateInput.setAdapter(adapter)
        binding.defaultStateInput.threshold = 1

        binding.defaultStateInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.defaultStateInput.text.isNullOrBlank()) {
                binding.defaultStateInput.showDropDown()
            }
        }

        binding.defaultStateInput.setOnClickListener {
            if (binding.defaultStateInput.text.isNullOrBlank()) {
                binding.defaultStateInput.showDropDown()
            }
        }
    }

    private fun loadTemplate() {
        val templateId = arguments?.getString(ARG_TEMPLATE_ID)
        val duplicate = arguments?.getBoolean(ARG_DUPLICATE, false) ?: false

        loadedTemplate = templateId?.let { dataRepository.getCaptureTemplate(it) }?.takeUnless { it.deleted }

        if (templateId != null && loadedTemplate == null) {
            activity?.showSnackbar(R.string.capture_template_missing)
            parentFragmentManager.popBackStack()
            return
        }

        val template = loadedTemplate
        val isEditing = template != null && !duplicate

        (activity as? SettingsActivity)?.setToolbarTitle(
            getString(if (isEditing) R.string.capture_template_edit else R.string.capture_template_new),
        )

        binding.delete.visibility = if (isEditing) View.VISIBLE else View.GONE

        if (template == null) {
            clearForm()
            return
        }

        binding.nameInput.setText(
            if (duplicate) getString(R.string.capture_template_duplicate) + ": " + template.name else template.name,
        )
        binding.titleTemplateInput.setText(template.titleTemplate.orEmpty())
        binding.bodyTemplateInput.setText(template.bodyTemplate.orEmpty())
        binding.defaultStateInput.setText(template.defaultState.orEmpty())
        binding.tagsInput.setText(template.tagsCsv.orEmpty())
        binding.enabled.isChecked = template.enabled
        binding.shareEnabled.isChecked = template.shareEnabled
        binding.targetNotebook.setText(
            template.targetNotebookName?.takeIf { it.isNotBlank() } ?: getString(R.string.default_notebook),
            false,
        )
        binding.targetNotebookLayout.error = null
        refreshHeadingSuggestions(clearInvalidHeading = false)
        binding.targetHeading.setText(template.targetHeadingPath.orEmpty(), false)
        applyTargetHeadingStatus(validateForSave = false)
    }

    private fun clearForm() {
        loadedTemplate = null
        binding.nameInput.text = null
        binding.titleTemplateInput.text = null
        binding.bodyTemplateInput.text = null
        binding.defaultStateInput.text = null
        binding.tagsInput.text = null
        binding.enabled.isChecked = true
        binding.shareEnabled.isChecked = true
        binding.targetNotebook.setText(getString(R.string.default_notebook), false)
        binding.targetNotebookLayout.error = null
        refreshHeadingSuggestions(clearInvalidHeading = false)
        binding.targetHeading.setText("", false)
        binding.targetHeadingLayout.error = null
        binding.targetHeadingLayout.helperText = getString(R.string.capture_template_target_heading_summary)
    }

    private fun saveTemplate() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            binding.nameLayout.error = getString(R.string.can_not_be_empty)
            return
        }
        binding.nameLayout.error = null

        val targetNotebookName = validatedTargetNotebookName()
        if (targetNotebookName == INVALID_NOTEBOOK) {
            return
        }

        val targetHeadingPath = validatedTargetHeadingPath()
        if (targetHeadingPath == INVALID_HEADING) {
            return
        }

        val duplicate = arguments?.getBoolean(ARG_DUPLICATE, false) ?: false
        val existing = loadedTemplate
        val baseTemplate = existing?.takeUnless { duplicate }
        val sourceTemplate = existing

        if (baseTemplate != null) {
            val currentTemplate = dataRepository.getCaptureTemplate(baseTemplate.id)
            if (currentTemplate == null || currentTemplate.deleted) {
                activity?.showSnackbar(R.string.capture_template_missing)
                parentFragmentManager.popBackStack()
                return
            }
        }

        val entity = CaptureTemplateEntity(
            id = baseTemplate?.id ?: UUID.randomUUID().toString(),
            name = name,
            sourceType = baseTemplate?.sourceType ?: CaptureTemplateEntity.SOURCE_TYPE_CUSTOM,
            presetKey = baseTemplate?.presetKey,
            enabled = binding.enabled.isChecked,
            shareEnabled = binding.shareEnabled.isChecked,
            targetNotebookName = targetNotebookName,
            targetHeadingPath = targetHeadingPath,
            titleTemplate = binding.titleTemplateInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            bodyTemplate = binding.bodyTemplateInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            defaultState = binding.defaultStateInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            tagsCsv = CaptureTemplateTagInput.normalizeTagsCsv(binding.tagsInput.text?.toString()),
            templateKind = baseTemplate?.templateKind
                ?: sourceTemplate?.templateKind
                ?: CaptureTemplateEntity.TEMPLATE_KIND_NOTE,
            position = baseTemplate?.position ?: 0,
            deleted = false,
        )

        if (baseTemplate == null) {
            val customEntity = entity.copy(
                sourceType = CaptureTemplateEntity.SOURCE_TYPE_CUSTOM,
                presetKey = null,
            )
            dataRepository.createCaptureTemplate(customEntity)
        } else {
            dataRepository.updateCaptureTemplate(entity)
        }

        activity?.showSnackbar(R.string.capture_template_saved)
        parentFragmentManager.popBackStack()
    }

    private fun validatedTargetNotebookName(): String? {
        val selectedNotebook = binding.targetNotebook.text?.toString()?.trim().orEmpty()
        val defaultNotebookLabel = getString(R.string.default_notebook)

        return when {
            selectedNotebook.isEmpty() || selectedNotebook == defaultNotebookLabel -> {
                binding.targetNotebookLayout.error = null
                null
            }
            selectedNotebook !in notebookNames -> {
                binding.targetNotebookLayout.error = getString(R.string.capture_template_target_notebook_invalid)
                INVALID_NOTEBOOK
            }
            else -> {
                binding.targetNotebookLayout.error = null
                selectedNotebook
            }
        }
    }

    private fun validatedTargetHeadingPath(): String? {
        return applyTargetHeadingStatus(validateForSave = true)
    }

    private fun applyTargetHeadingStatus(validateForSave: Boolean): String? {
        val normalizedHeadingPath = CaptureTemplates.normalizeHeadingPath(
            binding.targetHeading.text?.toString(),
        )

        if (normalizedHeadingPath == null) {
            binding.targetHeadingLayout.error = null
            binding.targetHeadingLayout.helperText = getString(R.string.capture_template_target_heading_summary)
            return null
        }

        return if (normalizedHeadingPath in headingPaths) {
            binding.targetHeadingLayout.error = null
            binding.targetHeadingLayout.helperText = getString(R.string.capture_template_target_heading_summary)
            normalizedHeadingPath
        } else {
            binding.targetHeadingLayout.helperText = getString(
                R.string.capture_template_target_heading_missing_summary,
                normalizedHeadingPath,
            )
            if (validateForSave) {
                binding.targetHeadingLayout.error = getString(R.string.capture_template_target_heading_invalid)
                INVALID_HEADING
            } else {
                binding.targetHeadingLayout.error = null
                normalizedHeadingPath
            }
        }
    }

    private fun refreshHeadingSuggestions(clearInvalidHeading: Boolean) {
        headingPaths = loadHeadingPathsForSelectedNotebook()

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            headingPaths.sorted(),
        )
        binding.targetHeading.setAdapter(adapter)

        val normalizedCurrentHeading = CaptureTemplates.normalizeHeadingPath(
            binding.targetHeading.text?.toString(),
        )
        if (clearInvalidHeading && normalizedCurrentHeading != null && normalizedCurrentHeading !in headingPaths) {
            binding.targetHeading.setText("", false)
            binding.targetHeadingLayout.error = null
            binding.targetHeadingLayout.helperText = getString(R.string.capture_template_target_heading_summary)
            return
        }

        applyTargetHeadingStatus(validateForSave = false)
    }

    private fun loadHeadingPathsForSelectedNotebook(): Set<String> {
        val bookId = resolveSelectedTargetBookId() ?: return emptySet()
        val paths = linkedSetOf<String>()

        dataRepository.getTopLevelNotes(bookId).forEach { note ->
            collectHeadingPaths(note, note.title, paths)
        }

        return paths
    }

    private fun collectHeadingPaths(note: Note, path: String, paths: MutableSet<String>) {
        paths.add(path)

        dataRepository.getNoteChildren(note.id).forEach { child ->
            collectHeadingPaths(child, "$path/${child.title}", paths)
        }
    }

    private fun resolveSelectedTargetBookId(): Long? {
        val selectedNotebook = binding.targetNotebook.text?.toString()?.trim().orEmpty()
        val defaultNotebookLabel = getString(R.string.default_notebook)

        return if (selectedNotebook.isEmpty() || selectedNotebook == defaultNotebookLabel) {
            dataRepository.getTargetBook(requireContext()).book.id
        } else {
            dataRepository.getBookView(selectedNotebook)?.book?.id
        }
    }

    private fun confirmDelete() {
        val template = loadedTemplate ?: return
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.capture_template_delete_confirmation, template.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                dataRepository.deleteCaptureTemplate(template.id)
                activity?.showSnackbar(R.string.capture_template_deleted)
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ARG_TEMPLATE_ID = "template_id"
        private const val ARG_DUPLICATE = "duplicate"
        private const val INVALID_NOTEBOOK = "__invalid_notebook__"
        private const val INVALID_HEADING = "__invalid_heading__"

        fun createNewInstance(): TemplateEditorFragment {
            return TemplateEditorFragment()
        }

        fun createEditInstance(templateId: String): TemplateEditorFragment {
            return TemplateEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEMPLATE_ID, templateId)
                }
            }
        }

        fun createDuplicateInstance(templateId: String): TemplateEditorFragment {
            return TemplateEditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEMPLATE_ID, templateId)
                    putBoolean(ARG_DUPLICATE, true)
                }
            }
        }
    }
}
