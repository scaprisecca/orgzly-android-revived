package com.orgzly.android.ui.drawer

import android.content.Intent
import android.view.Menu
import com.google.android.material.navigation.NavigationView
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.AppIntent
import com.orgzly.android.ui.books.BooksFragment
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.main.MainActivityViewModel
import com.orgzly.android.ui.savedsearches.SavedSearchesFragment
import com.orgzly.android.ui.tags.TagsFragment
import com.orgzly.android.util.LogUtils


internal class DrawerNavigationView(
        private val activity: MainActivity,
        viewModel: MainActivityViewModel,
        navView: NavigationView) {

    private val menu: Menu = navView.menu

    private val menuItemIdMap = hashMapOf<String, Int>()

    private var activeFragmentTag: String? = null

    init {
        // Add mapping for groups
        menuItemIdMap[BooksFragment.drawerItemId] = R.id.books
        menuItemIdMap[SavedSearchesFragment.getDrawerItemId()] = R.id.searches
        menuItemIdMap[TagsFragment.drawerItemId] = R.id.tags

        // Setup intents
        menu.findItem(R.id.searches).intent = Intent(AppIntent.ACTION_OPEN_SAVED_SEARCHES)
        menu.findItem(R.id.books).intent = Intent(AppIntent.ACTION_OPEN_BOOKS)
        menu.findItem(R.id.tags).intent = Intent(AppIntent.ACTION_OPEN_TAGS)
        menu.findItem(R.id.settings).intent = Intent(AppIntent.ACTION_OPEN_SETTINGS)
    }

    fun updateActiveFragment(fragmentTag: String) {
        this.activeFragmentTag = fragmentTag

        setActiveItem(fragmentTag)
    }

    private fun setActiveItem(fragmentTag: String) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, fragmentTag)

        this.activeFragmentTag = fragmentTag

        val fragment = activity.supportFragmentManager.findFragmentByTag(activeFragmentTag)

        // Uncheck all
        for (i in 0 until menu.size()) {
            menu.getItem(i).isChecked = false
        }

        if (fragment != null && fragment is DrawerItem) {
            val fragmentMenuItemId = fragment.getCurrentDrawerItemId()

            val itemId = menuItemIdMap[fragmentMenuItemId]

            if (itemId != null) {
                menu.findItem(itemId)?.isChecked = true
            }
        }
    }

    companion object {
        private val TAG = DrawerNavigationView::class.java.name
    }
}
