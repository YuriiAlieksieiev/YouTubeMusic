package com.yurii.youtubemusic.screens.saved

import android.content.Intent
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.tabs.TabLayoutMediator
import com.yurii.youtubemusic.CategoriesEditorActivity
import com.yurii.youtubemusic.EqualizerActivity
import com.yurii.youtubemusic.R
import com.yurii.youtubemusic.databinding.FragmentSavedMusicBinding
import com.yurii.youtubemusic.models.Category
import com.yurii.youtubemusic.utilities.Injector
import com.yurii.youtubemusic.utilities.TabFragment
import com.yurii.youtubemusic.utilities.CategoriesTabAdapter
import com.yurii.youtubemusic.screens.main.MainActivityViewModel


class SavedMusicFragment : TabFragment<FragmentSavedMusicBinding>(
    layoutId = R.layout.fragment_saved_music,
    titleStringId = R.string.label_fragment_title_saved_music,
    optionMenuId = R.menu.saved_musics_fragment_menu
) {
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val savedMusicViewModel: SavedMusicViewModel by viewModels { Injector.provideSavedMusicViewModel(requireContext()) }

    override fun onClickOption(id: Int) {
        when (id) {
            R.id.item_add_edit_categories -> openCategoriesEditor()
            R.id.item_open_equalizer -> openEqualizerActivity()
        }
    }

    override fun onInflatedView(viewDataBinding: FragmentSavedMusicBinding) {
        savedMusicViewModel.categoryItems.observe(viewLifecycleOwner, Observer { categoryItems ->
            initCategoriesLayout(categoryItems)
        })

        mainActivityViewModel.onMediaItemIsDeleted.observe(viewLifecycleOwner, Observer {
            savedMusicViewModel.deleteMediaItem(it)
        })

        mainActivityViewModel.onVideoItemHasBeenDownloaded.observe(viewLifecycleOwner, Observer {
            savedMusicViewModel.notifyVideoItemHasBeenDownloaded(it.videoId)
        })

        mainActivityViewModel.onUpdateMediaItem.observe(viewLifecycleOwner, Observer {
            savedMusicViewModel.updateMediaItem(it)
        })
    }

    private fun openEqualizerActivity() {
        startActivity(Intent(requireContext(), EqualizerActivity::class.java))
    }

    private fun openCategoriesEditor() {
        val activity = CategoriesEditorActivity.create(requireContext())
        startActivityForResult(activity, CategoriesEditorActivity.REQUEST_CODE)
    }

    private fun initCategoriesLayout(categories: List<Category>) {
        binding.viewpager.adapter = CategoriesTabAdapter(this, categories)
        TabLayoutMediator(binding.categories, binding.viewpager) { tab, position ->
            tab.text = categories[position].name
        }.attach()
        binding.categories.visibility = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CategoriesEditorActivity.REQUEST_CODE && resultCode == CategoriesEditorActivity.CATEGORIES_ARE_CHANGE_RESULT_CODE) {
            savedMusicViewModel.refreshCategories()
        }
    }

    companion object {
        fun createInstance(): SavedMusicFragment = SavedMusicFragment()
    }
}
