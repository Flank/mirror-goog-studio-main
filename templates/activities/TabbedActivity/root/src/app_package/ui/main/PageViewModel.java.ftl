package ${packageName}.ui.main;

import ${getMaterialComponentName('android.arch.core.util.Function', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.LiveData', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.MutableLiveData', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.Transformations', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.ViewModel', useAndroidX)};

public class PageViewModel extends ViewModel {

    private MutableLiveData<Integer> mIndex = new MutableLiveData<>();
    private LiveData<String> mText = Transformations.map(mIndex, new Function<Integer, String>() {
        @Override
        public String apply(Integer input) {
            return "Hello world from section: " + input;
        }
    });

    public void setIndex(int index) {
        mIndex.setValue(index);
    }

    public LiveData<String> getText() {
        return mText;
    }
}