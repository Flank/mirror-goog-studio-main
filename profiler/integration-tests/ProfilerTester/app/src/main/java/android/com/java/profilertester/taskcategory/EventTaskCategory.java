package android.com.java.profilertester.taskcategory;

import android.app.Activity;
import android.com.java.profilertester.fragment.FragmentHostActivity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.EditText;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class EventTaskCategory extends TaskCategory {
    private final List<? extends Task> mTasks;

    // Using {@link Callable} to maintain backwards compatibility.
    public EventTaskCategory(@NonNull Callable<Activity> activitySupplier, @NonNull EditText textEditor) {
        mTasks = Arrays.asList(new TypeWordsTask(textEditor), new SwitchActivityTask(activitySupplier));
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "Event";
    }

    private static final class SwitchActivityTask extends Task {
        private final Callable<Activity> mActivitySupplier;

        private SwitchActivityTask(@NonNull Callable<Activity> activitySupplier) {
            mActivitySupplier = activitySupplier;
        }

        @Override
        @Nullable
        protected String execute() throws Exception {
            Activity activity = mActivitySupplier.call();
            Intent intent = new Intent(activity, FragmentHostActivity.class);
            activity.startActivity(intent);
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Switch Activity";
        }
    }

    private static final class TypeWordsTask extends Task {
        private final TypeWordsSelectionListener mListener;

        private TypeWordsTask(@NonNull EditText textEditor) {
            mListener = new TypeWordsSelectionListener(textEditor);
        }

        @Override
        @Nullable
        protected String execute() {
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Type Words";
        }

        @NonNull
        @Override
        protected SelectionListener getSelectionListener() {
            return mListener;
        }

        private static final class TypeWordsSelectionListener implements SelectionListener {
            private final EditText mTextEditor;

            private TypeWordsSelectionListener(@NonNull EditText textEditor) {
                mTextEditor = textEditor;
            }

            @Override
            public void onSelection(@NonNull Object selectedItem) {
                if (selectedItem instanceof TypeWordsTask) {
                    mTextEditor.setVisibility(View.VISIBLE);
                } else {
                    mTextEditor.setVisibility(View.INVISIBLE);
                }
            }
        }
    }
}
