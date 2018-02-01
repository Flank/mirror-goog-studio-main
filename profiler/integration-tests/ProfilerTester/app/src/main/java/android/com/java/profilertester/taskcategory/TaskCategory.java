package android.com.java.profilertester.taskcategory;

import android.annotation.TargetApi;
import android.content.Intent;
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

    public final void executeTask(
            @NonNull TaskCategory taskCategory,
            @NonNull Task target,
            @Nullable PostExecuteRunner postExecuteRunner) {
        new AsyncTaskWrapper(taskCategory, postExecuteRunner).execute(target);
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

    private static final class AsyncTaskWrapper extends AsyncTask<Task, Void, String> {
        private TaskCategory mTaskCategory;
        private final PostExecuteRunner mPostExecuteRunner;

        private AsyncTaskWrapper(
                @NonNull TaskCategory taskCategory, @Nullable PostExecuteRunner postExecuteRunner) {
            mTaskCategory = taskCategory;
            mPostExecuteRunner = postExecuteRunner;
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        protected String doInBackground(Task... tasks) {
            try {
                if (!mTaskCategory.shouldRunTask(tasks[0])) {
                    return "TaskCategory prevented task to run!";
                }
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
