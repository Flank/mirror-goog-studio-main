package ${packageName}.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModelProviders
import ${packageName}.databinding.FragmentMainBinding

/**
 * A placeholder fragment containing a simple view.
 */
class PlaceholderFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProviders.of(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentMainBinding.inflate(inflater, container, false).apply {
            setLifecycleOwner(this@PlaceholderFragment)
            viewModel = pageViewModel
        }
        return binding.root
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}