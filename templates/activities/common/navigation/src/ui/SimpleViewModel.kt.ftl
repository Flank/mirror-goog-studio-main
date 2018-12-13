package ${packageName}.ui.${navFragmentPrefix}

import ${getMaterialComponentName('android.arch.lifecycle.LiveData', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.MutableLiveData', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.ViewModel', useAndroidX)}

class ${navViewModelClass} : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is ${navFragmentPrefix} Fragment"
    }
    val text: LiveData<String> = _text
}