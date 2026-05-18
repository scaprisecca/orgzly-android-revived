package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.CaptureTemplateEntity
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
        setupTagsAutocomplete()
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
    }

    private fun setupTagsAutocomplete() {
        val adapter = CaptureTemplateTagSuggestionAdapter(
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
        val start = CaptureTemplateTagInput.tokenizer.findTokenStart(text, cursor)
        val end = CaptureTemplateTagInput.tokenizer.findTokenEnd(text, cursor)
        return text.subSequence(start, end).toString().trim()
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
