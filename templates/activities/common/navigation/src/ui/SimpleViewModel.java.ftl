package ${packageName}.ui.${navFragmentPrefix};

import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.LiveData;
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.MutableLiveData;
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModel;

public class ${navViewModelClass} extends ViewModel {

    private final MutableLiveData<String> mText;

    public ${navViewModelClass}() {
        mText = new MutableLiveData<>();
        mText.setValue("This is ${navFragmentPrefix} fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
