package ${packageName}.ui.${navFragmentPrefix};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ${getMaterialComponentName('android.support.annotation.Nullable', useAndroidX)};
import ${getMaterialComponentName('android.support.annotation.NonNull', useAndroidX)};
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.Observer', useAndroidX)};
import ${getMaterialComponentName('android.arch.lifecycle.ViewModelProviders', useAndroidX)};
import androidx.navigation.fragment.NavHostFragment;
import ${escapeKotlinIdentifiers(packageName)}.R;

public class ${firstFragmentClass} extends Fragment {

    private ${navViewModelClass} ${navFragmentPrefix}ViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        ${navFragmentPrefix}ViewModel =
                ViewModelProviders.of(this).get(${navViewModelClass}.class);
        View root = inflater.inflate(R.layout.fragment_${navFragmentPrefix}, container, false);
        final TextView textView = root.findViewById(R.id.text_${navFragmentPrefix});
        ${navFragmentPrefix}ViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_${navFragmentPrefix}).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ${firstFragmentClass}Directions.Action${firstFragmentClass}To${secondFragmentClass} action =
                        ${firstFragmentClass}Directions.action${firstFragmentClass}To${secondFragmentClass}
                                ("From ${firstFragmentClass}");
                NavHostFragment.findNavController(${firstFragmentClass}.this)
                        .navigate(action);
            }
        });
    }
}
