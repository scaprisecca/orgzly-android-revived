package com.orgzly.android.ui.capture

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.ui.capture.TemplateEditorFragment.Companion.createDuplicateInstance
import com.orgzly.android.ui.capture.TemplateEditorFragment.Companion.createEditInstance
import com.orgzly.android.ui.capture.TemplateEditorFragment.Companion.createNewInstance
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.databinding.FragmentCaptureTemplateListBinding
import javax.inject.Inject

class TemplateListFragment : androidx.fragment.app.Fragment(), TemplateListAdapter.Listener {
    private var _binding: FragmentCaptureTemplateListBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var dialog: AlertDialog? = null
    private lateinit var adapter: TemplateListAdapter

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
        _binding = FragmentCaptureTemplateListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TemplateListAdapter(this)

        val layoutManager = LinearLayoutManager(context)
        binding.list.layoutManager = layoutManager
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            DividerItemDecoration(context, layoutManager.orientation),
        )

        binding.fab.setOnClickListener {
            openEditor(createNewInstance())
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setToolbarTitle(getString(R.string.manage_capture_templates))
        refreshTemplates()
    }

    override fun onPause() {
        super.onPause()
        dialog?.dismiss()
        dialog = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onTemplateSelected(template: CaptureTemplateEntity) {
        openEditor(createEditInstance(template.id))
    }

    override fun onTemplateActionsRequested(anchor: View, template: CaptureTemplateEntity) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, ACTION_EDIT, 0, R.string.capture_template_edit)
            menu.add(0, ACTION_DUPLICATE, 1, R.string.capture_template_duplicate)
            menu.add(0, ACTION_DELETE, 2, R.string.delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_EDIT -> openEditor(createEditInstance(template.id))
                    ACTION_DUPLICATE -> openEditor(createDuplicateInstance(template.id))
                    ACTION_DELETE -> confirmDelete(template)
                }
                true
            }
            show()
        }
    }

    private fun refreshTemplates() {
        val templates = dataRepository.getCaptureTemplates()
        adapter.submitList(templates)
        binding.empty.visibility = if (templates.isEmpty()) View.VISIBLE else View.GONE
        binding.list.visibility = if (templates.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmDelete(template: CaptureTemplateEntity) {
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.capture_template_delete_confirmation, template.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                dataRepository.deleteCaptureTemplate(template.id)
                activity?.showSnackbar(R.string.capture_template_deleted)
                refreshTemplates()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openEditor(fragment: TemplateEditorFragment) {
        (activity as? SettingsActivity)?.pushFragment(fragment)
    }

    companion object {
        private const val ACTION_EDIT = 1
        private const val ACTION_DUPLICATE = 2
        private const val ACTION_DELETE = 3
    }
}
