package ${packageName}.ui.${navFragmentPrefix};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ${getMaterialComponentName('android.support.annotation.NonNull', useAndroidX)};
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle', useAndroidX)}.ViewModelProviders;
import ${packageName}.databinding.${navFragmentBinding};

public class ${navFragmentClass} extends Fragment {

    private ${navViewModelClass} ${navFragmentPrefix}ViewModel;
    private ${navFragmentBinding} binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        ${navFragmentPrefix}ViewModel = ViewModelProviders.of(this).get(${navViewModelClass}.class);
        binding = ${navFragmentBinding}.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(${navFragmentPrefix}ViewModel);
        return binding.getRoot();
    }
}