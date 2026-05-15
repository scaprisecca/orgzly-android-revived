package com.orgzly.android.ui.note.link

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orgzly.R
import com.orgzly.android.link.OrgRoamLinkTarget
import com.orgzly.databinding.ItemOrgRoamLinkTargetBinding

class OrgRoamLinkTargetsAdapter(
    private val listener: Listener,
) : ListAdapter<OrgRoamLinkTargetsAdapter.Item, OrgRoamLinkTargetsAdapter.ViewHolder>(DIFF_CALLBACK) {

    sealed class Item {
        data class Target(val target: OrgRoamLinkTarget) : Item()
        data class Create(val title: String, val bookName: String) : Item()
    }

    interface Listener {
        fun onTargetSelected(target: OrgRoamLinkTarget)
        fun onCreateRequested(title: String)
    }

    inner class ViewHolder(
        private val binding: ItemOrgRoamLinkTargetBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            when (item) {
                is Item.Target -> bindTarget(item.target)
                is Item.Create -> bindCreate(item)
            }
        }

        private fun bindTarget(target: OrgRoamLinkTarget) {
            val context = binding.root.context
            binding.title.text = target.title
            binding.subtitle.text = context.getString(
                R.string.org_roam_link_picker_target_subtitle,
                when (target.type) {
                    OrgRoamLinkTarget.Type.NOTE -> context.getString(R.string.note)
                    OrgRoamLinkTarget.Type.BOOK -> context.getString(R.string.notebook)
                },
                target.bookName,
            )

            binding.status.text = when {
                target.hasDuplicateId -> context.getString(
                    R.string.org_roam_link_picker_duplicate_id_status,
                    requireNotNull(target.id),
                )
                target.requiresIdCreation -> context.getString(R.string.org_roam_link_picker_requires_id_status)
                else -> context.getString(R.string.org_roam_link_picker_id_status, requireNotNull(target.id))
            }

            binding.root.alpha = if (target.hasDuplicateId) 0.6f else 1f
            binding.root.setOnClickListener {
                listener.onTargetSelected(target)
            }
        }

        private fun bindCreate(item: Item.Create) {
            val context = binding.root.context
            binding.title.text = context.getString(R.string.org_roam_link_picker_create_new, item.title)
            binding.subtitle.text = context.getString(
                R.string.org_roam_link_picker_create_in_notebook,
                item.bookName,
            )
            binding.status.text = context.getString(R.string.org_roam_link_picker_create_status)
            binding.root.alpha = 1f
            binding.root.setOnClickListener {
                listener.onCreateRequested(item.title)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemOrgRoamLinkTargetBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                return when {
                    oldItem is Item.Target && newItem is Item.Target ->
                        oldItem.target.type == newItem.target.type &&
                            oldItem.target.noteId == newItem.target.noteId &&
                            oldItem.target.bookId == newItem.target.bookId &&
                            oldItem.target.id == newItem.target.id
                    oldItem is Item.Create && newItem is Item.Create ->
                        oldItem.title == newItem.title && oldItem.bookName == newItem.bookName
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem == newItem
            }
        }
    }
}
