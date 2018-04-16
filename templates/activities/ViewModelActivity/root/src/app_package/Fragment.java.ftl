package ${packageName}.${fragmentPackage};

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ${escapeKotlinIdentifiers(packageName)}.R;

public class ${fragmentClass} extends Fragment {

    public static ${fragmentClass} newInstance() {
        return new ${fragmentClass}();
    }

    private ${viewModelClass} mViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.${fragmentLayout}, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(${viewModelClass}.class);
        // TODO: Use the ViewModel
    }

}
