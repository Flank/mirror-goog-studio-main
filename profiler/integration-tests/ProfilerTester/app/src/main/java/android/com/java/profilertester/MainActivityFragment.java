package android.com.java.profilertester;

import android.app.Activity;
import android.com.java.profilertester.cpu.CpuAsyncTask;
import android.com.java.profilertester.memory.MemoryAsyncTask;
import android.com.java.profilertester.network.NetworkAsyncTask;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;

/**
 *  This is a placeholder view which contains two Spinners to decide the scenario
 *  mCategorySpinner specifies the category of scenario
 *  mActionSpinner specifies descriptions of scenario
 */

public class MainActivityFragment extends Fragment {

    final static String TAG = MainActivityFragment.class.getName();
    final static int CPU_CATEGORY_NUMBER = 0;
    final static int MEMORY_CATEGORY_NUMBER = 1;
    final static int NETWORK_CATEGORY_NUMBER = 2;

    private View myFragmentView;
    private Spinner mActionSpinner, mCategorySpinner;
    private ArrayList<ArrayAdapter<CharSequence> > mAdaptorList;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        myFragmentView =  inflater.inflate(R.layout.fragment_main, container, false);

        mCategorySpinner = (Spinner)myFragmentView.findViewById(R.id.category_spinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(myFragmentView.getContext(),
                R.array.category_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(adapter);

        mActionSpinner = (Spinner)myFragmentView.findViewById(R.id.action_spinner);

        // create an adaptor for each category
        mAdaptorList = new ArrayList<>();
        mAdaptorList.add(ArrayAdapter.createFromResource(myFragmentView.getContext(),
                R.array.cpu_array, android.R.layout.simple_spinner_item));
        mAdaptorList.add(ArrayAdapter.createFromResource(myFragmentView.getContext(),
                R.array.memory_array, android.R.layout.simple_spinner_item));
        mAdaptorList.add(ArrayAdapter.createFromResource(myFragmentView.getContext(),
                R.array.network_array, android.R.layout.simple_spinner_item));
        mAdaptorList.add(ArrayAdapter.createFromResource(myFragmentView.getContext(),
                R.array.event_array, android.R.layout.simple_spinner_item));
        for (ArrayAdapter<CharSequence> actionAdapter : mAdaptorList) {
            actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        mCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                mActionSpinner.setVisibility(View.VISIBLE);
                mActionSpinner.setAdapter(mAdaptorList.get(position));
                mAdaptorList.get(position).notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.i(TAG, "nothing selected");
            }

        });

        return myFragmentView;
    }

    public void testScenario() {
        int categoryNumber = mCategorySpinner.getSelectedItemPosition();
        int actionNumber = mActionSpinner.getSelectedItemPosition();
        if (categoryNumber == -1) {
            return;
        }

        // cpu scenario
        if (categoryNumber == CPU_CATEGORY_NUMBER) {
            new CpuAsyncTask(getActivity()).execute(actionNumber);
        }

        // memory scenario
        if (categoryNumber == MEMORY_CATEGORY_NUMBER) {
            new MemoryAsyncTask().execute(actionNumber);
        }

        // network scenarios
        if (categoryNumber == NETWORK_CATEGORY_NUMBER) {
            new NetworkAsyncTask().execute(actionNumber);
        }
    }
}
