package ${packageName}.ui.${navFragmentPrefix}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.Observer', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.ViewModelProviders', useAndroidX)}
import ${escapeKotlinIdentifiers(packageName)}.R

class ${firstFragmentClass} : Fragment() {

    private lateinit var ${navFragmentPrefix}ViewModel: ${navViewModelClass}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ${navFragmentPrefix}ViewModel =
                ViewModelProviders.of(this).get(${navViewModelClass}::class.java)
        val root = inflater.inflate(R.layout.fragment_${navFragmentPrefix}, container, false)
        val textView: TextView = root.findViewById(R.id.text_${navFragmentPrefix})
        ${navFragmentPrefix}ViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })
        return root
    }
}
