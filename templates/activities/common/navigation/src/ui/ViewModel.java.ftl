package ${packageName}.ui.${navFragmentPrefix};

import ${getMaterialComponentName('android.arch.lifecycle.LiveData', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.MutableLiveData', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.ViewModel', useAndroidX)};

public class ${navViewModelClass} extends ViewModel {

    private MutableLiveData<String> mText;

    public ${navViewModelClass}() {
        mText = new MutableLiveData<>();
        mText.setValue("This is ${navFragmentPrefix} fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}