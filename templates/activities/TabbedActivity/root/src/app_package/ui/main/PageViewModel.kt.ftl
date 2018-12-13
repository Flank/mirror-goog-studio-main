package ${packageName}.ui.main

import ${getMaterialComponentName('android.arch.lifecycle.LiveData', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.MutableLiveData', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.Transformations', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.ViewModel', useAndroidX)}
import ${getMaterialComponentName('android.arch.lifecycle.ViewModelProvider', useAndroidX)}

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val text: LiveData<String> = Transformations.map(_index) {
        "Hello world from section: $it"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}