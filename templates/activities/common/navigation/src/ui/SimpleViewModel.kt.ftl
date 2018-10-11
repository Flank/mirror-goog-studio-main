package ${packageName}.ui.${navFragmentPrefix}

import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.LiveData
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.MutableLiveData
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModel

class ${navViewModelClass} : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is ${navFragmentPrefix} Fragment"
    }
    val text: LiveData<String> = _text
}