package ${packageName}.ui.main

import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.LiveData
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.MutableLiveData
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.Transformations
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModel
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModelProvider

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val text: LiveData<String> = Transformations.map(_index) {
        "Hello world from section: $it"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}