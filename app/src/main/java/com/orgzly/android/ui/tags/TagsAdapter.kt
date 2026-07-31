package com.orgzly.android.ui.tags

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orgzly.android.ui.OnViewHolderClickListener
import com.orgzly.databinding.ItemTagBrowserBinding

class TagsAdapter(
        private val clickListener: OnViewHolderClickListener<TagBrowserRow>
) : ListAdapter<TagBrowserRow, TagsAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(val binding: ItemTagBrowserBinding) :
            RecyclerView.ViewHolder(binding.root),
            View.OnClickListener,
            View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun onClick(view: View) {
            bindingAdapterPosition.let { position ->
                if (position != RecyclerView.NO_POSITION) {
                    clickListener.onClick(view, position, getItem(position))
                } else {
                    Log.e(TAG, "Adapter position for $view not available")
                }
            }
        }

        override fun onLongClick(view: View): Boolean {
            bindingAdapterPosition.let { position ->
                return if (position != RecyclerView.NO_POSITION) {
                    clickListener.onLongClick(view, position, getItem(position))
                    true
                } else {
                    Log.e(TAG, "Adapter position for $view not available")
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemTagBrowserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)

        with(holder.binding) {
            tagName.text = row.tag
            tagCount.text = root.context.resources.getQuantityString(
                    com.orgzly.R.plurals.tag_browser_active_task_count,
                    row.activeCount,
                    row.activeCount)
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).tag.hashCode().toLong()
    }

    companion object {
        private val TAG = TagsAdapter::class.java.name

        private val DIFF_CALLBACK: DiffUtil.ItemCallback<TagBrowserRow> =
                object : DiffUtil.ItemCallback<TagBrowserRow>() {
                    override fun areItemsTheSame(oldItem: TagBrowserRow, newItem: TagBrowserRow): Boolean {
                        return oldItem.tag == newItem.tag
                    }

                    override fun areContentsTheSame(oldItem: TagBrowserRow, newItem: TagBrowserRow): Boolean {
                        return oldItem == newItem
                    }
                }
    }
}
