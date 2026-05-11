package com.orgzly.android.ui.capture

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.databinding.ItemCaptureTemplateBinding

class TemplateListAdapter(
    private val listener: Listener,
) : ListAdapter<CaptureTemplateEntity, TemplateListAdapter.ViewHolder>(DIFF_CALLBACK) {

    interface Listener {
        fun onTemplateSelected(template: CaptureTemplateEntity)
        fun onTemplateActionsRequested(anchor: View, template: CaptureTemplateEntity)
    }

    inner class ViewHolder(
        val binding: ItemCaptureTemplateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onTemplateSelected(getItem(position))
                }
            }

            binding.actions.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onTemplateActionsRequested(view, getItem(position))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCaptureTemplateBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = getItem(position)
        val context = holder.binding.root.context

        holder.binding.name.text = template.name

        val statusParts = buildList {
            add(
                if (template.sourceType == CaptureTemplateEntity.SOURCE_TYPE_BUILT_IN) {
                    context.getString(com.orgzly.R.string.capture_template_source_built_in)
                } else {
                    context.getString(com.orgzly.R.string.capture_template_source_custom)
                },
            )

            add(
                if (template.enabled) {
                    context.getString(com.orgzly.R.string.capture_template_enabled)
                } else {
                    context.getString(com.orgzly.R.string.capture_template_disabled)
                },
            )

            add(
                if (template.shareEnabled) {
                    context.getString(com.orgzly.R.string.capture_template_share_enabled)
                } else {
                    context.getString(com.orgzly.R.string.capture_template_not_shared)
                },
            )

            add(
                template.targetNotebookName?.takeIf { it.isNotBlank() }
                    ?: context.getString(com.orgzly.R.string.default_notebook),
            )
        }

        holder.binding.summary.text = statusParts.joinToString(" • ")
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CaptureTemplateEntity>() {
            override fun areItemsTheSame(
                oldItem: CaptureTemplateEntity,
                newItem: CaptureTemplateEntity,
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: CaptureTemplateEntity,
                newItem: CaptureTemplateEntity,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
