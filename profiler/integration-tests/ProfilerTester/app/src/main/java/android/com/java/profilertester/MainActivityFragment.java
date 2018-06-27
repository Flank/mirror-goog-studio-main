package android.com.java.profilertester;

import android.app.Activity;
import android.com.java.profilertester.taskcategory.AudioTaskCategory;
import android.com.java.profilertester.taskcategory.BackgroundTaskCategory;
import android.com.java.profilertester.taskcategory.BluetoothTaskCategory;
import android.com.java.profilertester.taskcategory.CameraTaskCategory;
import android.com.java.profilertester.taskcategory.CpuTaskCategory;
import android.com.java.profilertester.taskcategory.EventTaskCategory;
import android.com.java.profilertester.taskcategory.FeedbackTaskCategory;
import android.com.java.profilertester.taskcategory.LocationTaskCategory;
import android.com.java.profilertester.taskcategory.MemoryTaskCategory;
import android.com.java.profilertester.taskcategory.NetworkTaskCategory;
import android.com.java.profilertester.taskcategory.ScreenBrightnessTaskCategory;
import android.com.java.profilertester.taskcategory.TaskCategory;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * This is a placeholder view which contains two Spinners to decide the scenario
 * mCategorySpinner specifies the category of scenario
 * mTaskSpinner specifies descriptions of scenario
 */

public class MainActivityFragment extends Fragment {
    final static String TAG = MainActivityFragment.class.getName();

    private View mFragmentView;
    private MainLooperThread mMainLooperThread;

    private Spinner mCategorySpinner, mTaskSpinner;
    private TaskCategory[] mTaskCategories;
    private List<ArrayAdapter<? extends TaskCategory.Task>> mTaskAdapters;

    private final List<TaskCategory.Task.SelectionListener> mSelectionListeners = new ArrayList<>();
    /**
     * Tasks waiting for permission request results to run, see {@link
     * #onRequestPermissionsResult(int, String[], int[])}.
     */
    private final List<PendingPermissionTask> mPendingPermissionTasks = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainLooperThread = new MainLooperThread();
        mMainLooperThread.start();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mFragmentView = inflater.inflate(R.layout.fragment_main, container, false);

        mCategorySpinner = (Spinner) mFragmentView.findViewById(R.id.category_spinner);
        final Activity host = getActivity();
        mTaskCategories =
                new TaskCategory[] {
                    new CpuTaskCategory(host.getFilesDir()),
                    new MemoryTaskCategory(),
                    new NetworkTaskCategory(host),
                    new EventTaskCategory(
                            new Callable<Activity>() {
                                @Override
                                public Activity call() {
                                    return host;
                                }
                            },
                            (EditText) mFragmentView.findViewById(R.id.section_editor)),
                    new BluetoothTaskCategory(host),
                    new LocationTaskCategory(host, mMainLooperThread.getLooper()),
                    new ScreenBrightnessTaskCategory(host),
                    new CameraTaskCategory(host),
                    new AudioTaskCategory(host),
                    new FeedbackTaskCategory(host),
                    new BackgroundTaskCategory(host)
                };
        ArrayAdapter<TaskCategory> categoryAdapters =
                new ArrayAdapter<>(
                        mFragmentView.getContext(),
                        android.R.layout.simple_spinner_item,
                        mTaskCategories);
        categoryAdapters.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(categoryAdapters);
        mTaskSpinner = (Spinner) mFragmentView.findViewById(R.id.task_spinner);
        // create an adaptor for each category
        mTaskAdapters = new ArrayList<>();
        for (TaskCategory taskCategory : mTaskCategories) {
            ArrayAdapter<? extends TaskCategory.Task> taskAdapter = new ArrayAdapter<>(
                    mFragmentView.getContext(),
                    android.R.layout.simple_spinner_item,
                    taskCategory.getTasks());
            taskAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mTaskAdapters.add(taskAdapter);
            mSelectionListeners.addAll(taskCategory.getTaskSelectionListeners());
        }

        mCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (mTaskSpinner.getAdapter() != mTaskAdapters.get(position)) {
                    mTaskSpinner.setAdapter(mTaskAdapters.get(position));

                    Object selectedTaskItem = mTaskSpinner.getSelectedItem();
                    notifySelection(selectedTaskItem);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.i(TAG, "mCategorySpinner: nothing selected");
            }
        });

        mTaskSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Object selectedTaskItem = parentView.getItemAtPosition(position);
                notifySelection(selectedTaskItem);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.i(TAG, "mTaskSpinner: nothing selected");
            }
        });

        return mFragmentView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMainLooperThread.quit();
    }

    private void notifySelection(@Nullable Object selectedItem) {
        if (selectedItem != null) {
            for (TaskCategory.Task.SelectionListener listener : mSelectionListeners) {
                listener.onSelection(selectedItem);
            }
        }
    }

    private void setScenario(int category, int task) {
        if (category != mCategorySpinner.getSelectedItemPosition()) {
            mCategorySpinner.setSelection(category);
            mTaskSpinner.setAdapter(mTaskAdapters.get(category));
        }
        mTaskSpinner.setSelection(task);
    }

    public boolean scenarioMoveBack() {
        int category = mCategorySpinner.getSelectedItemPosition();
        int task = mTaskSpinner.getSelectedItemPosition();
        --task;
        if (task == -1) {
            --category;
            if (category == -1) {
                return false;
            }
            task = mTaskAdapters.get(category).getCount() - 1;
        }
        setScenario(category, task);
        return true;
    }

    public boolean scenarioMoveForward() {
        int category = mCategorySpinner.getSelectedItemPosition();
        int task = mTaskSpinner.getSelectedItemPosition();
        ++task;
        if (task == mTaskAdapters.get(category).getCount()) {
            ++category;
            if (category >= mCategorySpinner.getAdapter().getCount()) {
                return false;
            }
            task = 0;
        }
        setScenario(category, task);
        return true;
    }

    public void testScenario() {
        Object categoryObject = mCategorySpinner.getSelectedItem();
        if (categoryObject == null) {
            return;
        }
        if (!(categoryObject instanceof TaskCategory)) {
            Log.e("ProfilerTester", "Invalid category spinner selection!");
            return;
        }
        final TaskCategory taskCategory = (TaskCategory) categoryObject;

        Object taskObject = mTaskSpinner.getSelectedItem();
        if (taskObject == null) {
            return;
        }
        if (!(taskObject instanceof TaskCategory.Task)) {
            Log.e("ProfilerTester", "Invalid task spinner selection!");
            return;
        }
        final TaskCategory.Task task = (TaskCategory.Task) taskObject;

        TaskCategory.RequestCodePermissions permissions = taskCategory.getPermissionsRequired(task);
        Runnable taskRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        taskCategory.executeTask(
                                task,
                                new TaskCategory.PostExecuteRunner() {
                                    @Override
                                    public void accept(@Nullable String s) {
                                        View view = getView();
                                        if (view != null && s != null) {
                                            Toast.makeText(getActivity(), s, Toast.LENGTH_LONG)
                                                    .show();
                                        }
                                    }
                                });
                    }
                };
        if (hasAllPermissions(permissions)) {
            taskRunnable.run();
        } else {
            mPendingPermissionTasks.add(new PendingPermissionTask(permissions, taskRunnable));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mTaskCategories == null) {
            return;
        }

        for (TaskCategory taskCategory : mTaskCategories) {
            taskCategory.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            return;
        }
        boolean isGranted = true;
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                isGranted = false;
                break;
            }
        }
        for (PendingPermissionTask task : mPendingPermissionTasks) {
            if (requestCode == task.mPermissions.getRequestCode().ordinal()) {
                if (isGranted) {
                    task.mTaskRunnable.run();
                }
                mPendingPermissionTasks.remove(task);
                break;
            }
        }
    }

    private boolean hasAllPermissions(TaskCategory.RequestCodePermissions codePermissions) {
        boolean hasAllPermissions = true;
        String[] permissions = codePermissions.getPermissions();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String p : permissions) {
                if (ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), p)
                        != PackageManager.PERMISSION_GRANTED) {
                    hasAllPermissions = false;
                    break;
                }
            }
        }
        if (!hasAllPermissions) {
            ActivityCompat.requestPermissions(
                    getActivity(), permissions, codePermissions.getRequestCode().ordinal());
        }
        return hasAllPermissions;
    }

    private static final class PendingPermissionTask {
        @NonNull private final TaskCategory.RequestCodePermissions mPermissions;
        @NonNull private final Runnable mTaskRunnable;

        PendingPermissionTask(
                @NonNull TaskCategory.RequestCodePermissions permissions,
                @NonNull Runnable runnable) {
            mPermissions = permissions;
            mTaskRunnable = runnable;
        }
    }
}
