package ${packageName}.ui.main;

import ${getMaterialComponentName('android.arch.core', useAndroidX)}.util.Function;
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.LiveData;
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.MutableLiveData;
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.Transformations;
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModel;

public class PageViewModel extends ViewModel {

    private final MutableLiveData<Integer> mIndex = new MutableLiveData<>();
    private final LiveData<String> mText =
            Transformations.map(mIndex, new Function<Integer, String>() {

        @Override
        public String apply(Integer input) {
            return "Hello world from section: " + input;
        }
    });

    public LiveData<String> getText() {
        return mText;
    }

    void setIndex(int index) {
        mIndex.setValue(index);
    }
}

