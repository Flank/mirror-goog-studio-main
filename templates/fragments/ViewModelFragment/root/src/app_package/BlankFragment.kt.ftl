package ${escapeKotlinIdentifiers(packageName)}

import ${getMaterialComponentName('android.arch.lifecycle.ViewModelProviders', useAndroidX)}
import android.os.Bundle
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

<#if applicationPackage??>
import ${applicationPackage}.R
</#if>

class ${fragmentClass} : Fragment() {

    companion object {
        fun newInstance() = ${fragmentClass}()
    }

    private lateinit var viewModel: ${viewModelName}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.${layoutName}, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(${viewModelName}::class.java)
        // TODO: Use the ViewModel
    }

}
