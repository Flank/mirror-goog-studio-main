package ${escapeKotlinIdentifiers(packageName)}.${escapeKotlinIdentifiers(fragmentPackage)}

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ${escapeKotlinIdentifiers(packageName)}.R

class ${fragmentClass} : Fragment() {

    companion object {
        fun newInstance() = ${fragmentClass}()
    }

    private lateinit var viewModel: ${viewModelClass}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.${fragmentLayout}, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(${viewModelClass}::class.java)
        // TODO: Use the ViewModel
    }

}
