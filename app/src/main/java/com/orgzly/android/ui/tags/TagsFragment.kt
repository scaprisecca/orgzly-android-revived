package com.orgzly.android.ui.tags

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.query.Condition
import com.orgzly.android.query.Query
import com.orgzly.android.query.StateType
import com.orgzly.android.query.user.DottedQueryBuilder
import com.orgzly.android.ui.CommonFragment
import com.orgzly.android.ui.OnViewHolderClickListener
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.FragmentTagsBinding
import android.content.Intent
import javax.inject.Inject

class TagsFragment : CommonFragment(), DrawerItem, OnViewHolderClickListener<TagBrowserRow> {
    private lateinit var binding: FragmentTagsBinding

    private var listener: Listener? = null

    private lateinit var viewAdapter: TagsAdapter

    @Inject
    lateinit var dataRepository: DataRepository

    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel

    private lateinit var viewModel: TagsViewModel

    override fun getCurrentDrawerItemId() = drawerItemId

    override fun onAttach(context: Context) {
        super.onAttach(context)

        App.appComponent.inject(this)

        listener = activity as Listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(requireActivity())
                .get(SharedMainActivityViewModel::class.java)

        val factory = TagsViewModelFactory.getInstance(dataRepository)
        viewModel = ViewModelProvider(this, factory).get(TagsViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        binding = FragmentTagsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewAdapter = TagsAdapter(this)
        viewAdapter.setHasStableIds(true)

        val layoutManager = LinearLayoutManager(context)
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)

        binding.fragmentTagsRecyclerView.let {
            it.layoutManager = layoutManager
            it.adapter = viewAdapter
            it.addItemDecoration(dividerItemDecoration)
        }
    }

    override fun onResume() {
        super.onResume()

        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        topToolbarToDefault()

        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            viewAdapter.submitList(rows)
            binding.fragmentTagsViewFlipper.displayedChild = if (rows.isEmpty()) 2 else 1
        }
    }

    private fun topToolbarToDefault() {
        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.tags_actions)

            setNavigationIcon(R.drawable.ic_menu)

            setNavigationOnClickListener {
                sharedMainActivityViewModel.openDrawer()
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.sync -> {
                        SyncRunner.startSync()
                    }

                    R.id.activity_action_settings -> {
                        startActivity(Intent(context, SettingsActivity::class.java))
                    }
                }

                true
            }

            setOnClickListener {
                binding.fragmentTagsRecyclerView.scrollToPosition(0)
            }

            title = getString(R.string.tags)
        }
    }

    override fun onClick(view: View, position: Int, item: TagBrowserRow) {
        listener?.onTagSelected(item.tag, buildQuery(item.tag))
    }

    override fun onLongClick(view: View, position: Int, item: TagBrowserRow) {
        onClick(view, position, item)
    }

    private fun buildQuery(tag: String): String {
        return DottedQueryBuilder().build(Query(
                Condition.And(listOf(
                        Condition.HasStateType(StateType.TODO),
                        Condition.HasTag(tag)
                ))
        ))
    }

    override fun onDetach() {
        super.onDetach()

        listener = null
    }

    interface Listener {
        fun onTagSelected(tag: String, query: String)
    }

    companion object {
        private val TAG = TagsFragment::class.java.name

        const val FRAGMENT_TAG = "TagsFragment"

        const val drawerItemId = "drawer-tags"

        @JvmStatic
        fun getInstance(): TagsFragment {
            return TagsFragment()
        }
    }
}
