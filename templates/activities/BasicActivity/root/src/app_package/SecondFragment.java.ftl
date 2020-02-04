package ${packageName};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import ${getMaterialComponentName('android.support.annotation.NonNull', useAndroidX)};
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import androidx.navigation.fragment.NavHostFragment;

public class ${secondFragmentClass} extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.${secondFragmentLayoutName}, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_second).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(${secondFragmentClass}.this)
                        .navigate(R.id.action_${secondFragmentClass}_to_${firstFragmentClass});
            }
        });
    }
}
