package ${packageName}.ui.${navFragmentPrefix}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModelProviders
import ${packageName}.databinding.${navFragmentBinding}

class ${navFragmentClass} : Fragment() {

    private lateinit var ${navFragmentPrefix}ViewModel: ${navViewModelClass}
    private lateinit var binding: ${navFragmentBinding}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ${navFragmentPrefix}ViewModel =
                ViewModelProviders.of(this).get(${navViewModelClass}::class.java)
        binding = ${navFragmentBinding}.inflate(inflater, container, false).apply {
            setLifecycleOwner(this@${navFragmentClass})
            viewModel = ${navFragmentPrefix}ViewModel
        }
        return binding.root
    }
}