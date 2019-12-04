package ${packageName};

import android.content.Context;
import android.os.Bundle;
import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)};
import ${getMaterialComponentName('android.support.v7.widget.GridLayoutManager', useAndroidX)};
import ${getMaterialComponentName('android.support.v7.widget.LinearLayoutManager', useAndroidX)};
import ${getMaterialComponentName('android.support.v7.widget.RecyclerView', useAndroidX)};
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
<#if applicationPackage??>
import ${applicationPackage}.R;
</#if>
import ${packageName}.dummy.DummyContent;

/**
 * A fragment representing a list of Items.
 */
public class ${fragmentClass} extends Fragment {

    // TODO: Customize parameters
    private int mColumnCount = ${columnCount};

    // TODO: Customize parameter argument names
    private static final String ARG_COLUMN_COUNT = "column-count";

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static ${fragmentClass} newInstance(int columnCount) {
        ${fragmentClass} fragment = new ${fragmentClass}();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ${fragmentClass}() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.${fragment_layout_list}, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            recyclerView.setAdapter(new ${adapterClassName}(DummyContent.ITEMS));
        }
        return view;
    }
}
