package android.com.java.profilertester.taskcategory;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class TaskCategory {
    public abstract static class Task {
        protected static final long DEFAULT_TASK_TIME_MS = TimeUnit.SECONDS.toMillis(10);
        protected static final long LONG_TASK_TIME_MS = TimeUnit.SECONDS.toMillis(20);

        /** Method that is called just prior to {@link Task#execute()} begins running. */
        public void preExecute() {}

        /**
         * Main execution logic for the task.
         *
         * @return A displayable string describing the results of the execution. {@code null} is OK
         *     to return, if there's no useful message to show after the task completes.
         */
        @Nullable
        protected abstract String execute() throws Exception;

        /**
         * Method that is called when {@link Task#execute()} finishes running. User is responsible
         * for determining under what conditions should the contents be run.
         */
        public void postExecute() {}

        @Override
        public final String toString() {
            return getTaskDescription();
        }

        @NonNull
        protected abstract String getTaskDescription();

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

    public final void executeTask(
            @NonNull TaskCategory taskCategory,
            @NonNull Task target,
            @Nullable PostExecuteRunner postExecuteRunner) {
        new AsyncTaskWrapper(taskCategory, target, postExecuteRunner).execute();
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

    /**
     * A predicate for whether or not to run the given {@code taskToRun}. Override this method to
     * conditionally execute the {@code taskToRun} (defaults to {@code true}). This is always called
     * before the task is executed.
     *
     * @param taskToRun the selected task to be run.
     * @return true to run the task, or false to prevent the task from being run.
     */
    protected boolean shouldRunTask(@NonNull Task taskToRun) {
        return true;
    }

    /**
     * Callback to the {@link TaskCategory} if it needs to start an {@link Intent}. The params are
     * just passed through from {@link android.app.Activity#onActivityResult(int, int, Intent)}.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {}

    private static final class AsyncTaskWrapper extends AsyncTask<Void, Void, String> {
        private final TaskCategory mTaskCategory;
        private final Task mTask;
        private final PostExecuteRunner mPostExecuteRunner;

        private AsyncTaskWrapper(
                @NonNull TaskCategory taskCategory,
                @NonNull Task task,
                @Nullable PostExecuteRunner postExecuteRunner) {
            mTaskCategory = taskCategory;
            mTask = task;
            mPostExecuteRunner = postExecuteRunner;
        }

        @Override
        protected void onPreExecute() {
            try {
                if (!mTaskCategory.shouldRunTask(mTask)) {
                    cancel(true);
                    return;
                }
                mTask.preExecute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        protected String doInBackground(Void... voids) {
            try {
                return mTask.execute();
            } catch (Exception e) {
                e.printStackTrace();
                return e.toString();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            mTask.postExecute();
            if (mPostExecuteRunner != null) {
                mPostExecuteRunner.accept(s);
            }
        }
    }

    public interface PostExecuteRunner {
        void accept(@Nullable String s);
    }
}
