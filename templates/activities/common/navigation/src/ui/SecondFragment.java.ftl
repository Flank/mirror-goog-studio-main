package ${packageName}.ui.${navFragmentPrefix};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ${getMaterialComponentName('android.support.annotation.NonNull', useAndroidX)};
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import androidx.navigation.fragment.NavHostFragment;

import ${packageName}.R;

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

        String myArg = ${secondFragmentClass}Args.fromBundle(getArguments()).getMyArg();
        TextView textView = view.findViewById(R.id.textview_${navFragmentPrefix}_second);
        textView.setText(getString(R.string.hello_${navFragmentPrefix}_second, myArg));

        view.findViewById(R.id.button_${navFragmentPrefix}_second).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(${secondFragmentClass}.this)
                        .navigate(R.id.action_${secondFragmentClass}_to_${firstFragmentClass});
            }
        });
    }
}
