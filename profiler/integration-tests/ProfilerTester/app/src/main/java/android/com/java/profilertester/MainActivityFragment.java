package android.com.java.profilertester;

import android.com.java.profilertester.cpu.CpuAsyncTask;
import android.com.java.profilertester.event.EmptyActivity;
import android.com.java.profilertester.event.EventConfigurations;
import android.com.java.profilertester.memory.MemoryAsyncTask;
import android.com.java.profilertester.network.NetworkAsyncTask;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
    final static int EVENT_CATEGORY_NUMBER = 3;

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
                if (mActionSpinner.getAdapter() != mAdaptorList.get(position)) {
                    mActionSpinner.setAdapter(mAdaptorList.get(position));
                    mAdaptorList.get(position).notifyDataSetChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.i(TAG, "mCategorySpinner: nothing selected");
            }
        });

        mActionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                resetScenario();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.i(TAG, "mActionSpinner: nothing selected");
            }
        });

        return myFragmentView;
    }

    private void setScenario(int category, int action) {
        if (category != mCategorySpinner.getSelectedItemPosition()) {
            mCategorySpinner.setSelection(category);
            mActionSpinner.setAdapter(mAdaptorList.get(category));
        }
        mActionSpinner.setSelection(action);
    }

    public boolean scenarioMoveBack() {
        int category = mCategorySpinner.getSelectedItemPosition();
        int action = mActionSpinner.getSelectedItemPosition();
        --action;
        if (action == -1) {
            --category;
            if (category == -1) {
                return false;
            }
            action = mAdaptorList.get(category).getCount() - 1;
        }
        setScenario(category, action);
        return true;
    }

    public boolean scenarioMoveForward() {
        int category = mCategorySpinner.getSelectedItemPosition();
        int action = mActionSpinner.getSelectedItemPosition();
        ++action;
        if (action == mAdaptorList.get(category).getCount()) {
            ++category;
            if (category > EVENT_CATEGORY_NUMBER) {
                return false;
            }
            action = 0;
        }
        setScenario(category, action);
        return true;
    }

    private void resetScenario() {
        int categoryNumber = mCategorySpinner.getSelectedItemPosition();
        int actionNumber = mActionSpinner.getSelectedItemPosition();

        // enable editor field for 'type words' scenario
        EditText editorText = (EditText) myFragmentView.findViewById(R.id.section_editor);
        if (categoryNumber == EVENT_CATEGORY_NUMBER && actionNumber == EventConfigurations.ActionNumber.TYPE_WORDS.ordinal()) {
            editorText.setVisibility(View.VISIBLE);
        } else {
            editorText.setVisibility(View.INVISIBLE);
        }
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

        // event scenarios
        if (categoryNumber == EVENT_CATEGORY_NUMBER) {
            if (actionNumber == EventConfigurations.ActionNumber.SWITCH_ACTIVITY.ordinal()) {
                Intent intent = new Intent(getActivity(), EmptyActivity.class);
                startActivity(intent);
            }
        }
    }
}
