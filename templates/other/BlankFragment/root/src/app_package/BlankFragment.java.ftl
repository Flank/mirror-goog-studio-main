package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
<#if !includeLayout>import android.widget.TextView;</#if>
<#if applicationPackage??>
import ${applicationPackage}.R;
</#if>

/**
 * A simple {@link Fragment} subclass.
<#if includeFactory>
 * Use the {@link ${className}#newInstance} factory method to
 * create an instance of this fragment.
</#if>
 *
 */
public class ${className} extends Fragment {
<#if includeFactory>
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
</#if>

<#if includeFactory>
    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ${className}.
     */
    // TODO: Rename and change types and number of parameters
    public static ${className} newInstance(String param1, String param2) {
        ${className} fragment = new ${className}();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }
</#if>
    public ${className}() {
        // Required empty public constructor
    }

<#if includeFactory>
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }
</#if>

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
<#if includeLayout>
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.${fragmentName}, container, false);
<#else>
        TextView textView = new TextView(getActivity());
        textView.setText(R.string.hello_blank_fragment);
        return textView;
</#if>
    }
}
