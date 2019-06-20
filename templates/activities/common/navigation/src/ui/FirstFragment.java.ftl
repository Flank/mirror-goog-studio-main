package ${packageName};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import androidx.navigation.fragment.NavHostFragment;

public class ${firstFragmentClass} extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.${firstFragmentLayoutName}, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_first).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ${firstFragmentClass}Directions.Action${firstFragmentClass}To${secondFragmentClass} action =
                        ${firstFragmentClass}Directions.
                                action${firstFragmentClass}To${secondFragmentClass}("From ${firstFragmentClass}");
                NavHostFragment.findNavController(${firstFragmentClass}.this)
                        .navigate(action);
            }
        });
    }
}
