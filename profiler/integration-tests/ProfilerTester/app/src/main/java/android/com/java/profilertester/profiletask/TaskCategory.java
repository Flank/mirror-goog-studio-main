package android.com.java.profilertester.profiletask;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class TaskCategory {
    public abstract static class Task {
        @Nullable
        protected abstract String execute() throws Exception;

        @Override
        public final String toString() {
            return getTaskName();
        }

        @NonNull
        protected abstract String getTaskName();

        @Nullable
        protected SelectionListener getSelectionListener() {
            return null;
        }

        public interface SelectionListener {
            void onSelection(@NonNull Object selectedItem);
        }
    }

    @NonNull
    public abstract List<? extends Task> getTasks();

    @Override
    @NonNull
    public final String toString() {
        return getCategoryName();
    }

    public final void executeTask(@NonNull Task target, @Nullable PostExecuteRunner postExecuteRunner) {
        new AsyncTaskWrapper(postExecuteRunner).execute(target);
    }

    @NonNull
    public final List<Task.SelectionListener> getTaskSelectionListeners() {
        List<Task.SelectionListener> selectionListeners = new ArrayList<>();
        List<? extends Task> tasks = getTasks();
        for (Task task : tasks) {
            Task.SelectionListener selectionListener = task.getSelectionListener();
            if (selectionListener != null) {
                selectionListeners.add(selectionListener);
            }
        }
        return selectionListeners;
    }

    @NonNull
    protected abstract String getCategoryName();

    private static final class AsyncTaskWrapper extends AsyncTask<Task, Void, String> {
        private final PostExecuteRunner mPostExecuteRunner;

        private AsyncTaskWrapper(@Nullable PostExecuteRunner postExecuteRunner) {
            mPostExecuteRunner = postExecuteRunner;
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        protected String doInBackground(Task... tasks) {
            try {
                return tasks[0].execute();
            } catch (Exception e) {
                e.printStackTrace();
                return e.toString();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            if (mPostExecuteRunner != null) {
                mPostExecuteRunner.accept(s);
            }
        }
    }

    public interface PostExecuteRunner {
        void accept(@Nullable String s);
    }
}
