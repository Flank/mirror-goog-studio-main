package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.LazyTaskCreationAction;
import com.android.builder.model.SourceProvider;
import java.io.File;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

/** Configuration Action for a process*JavaRes tasks. */
public class ProcessJavaResCreationAction extends LazyTaskCreationAction<Sync> {
    private VariantScope scope;
    private final File destinationDir;

    public ProcessJavaResCreationAction(VariantScope scope, File destinationDir) {
        this.scope = scope;
        this.destinationDir = destinationDir;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName("process", "JavaRes");
    }

    @NonNull
    @Override
    public Class<Sync> getType() {
        return Sync.class;
    }

    @Override
    public void handleProvider(@NonNull TaskProvider<? extends Sync> taskProvider) {
        super.handleProvider(taskProvider);
        scope.getTaskContainer().setProcessJavaResourcesTask(taskProvider);
    }

    @Override
    public void configure(@NonNull Sync task) {

        for (SourceProvider sourceProvider :
                scope.getVariantConfiguration().getSortedSourceProviders()) {
            task.from(((AndroidSourceSet) sourceProvider).getResources().getSourceFiles());
        }

        task.setDestinationDir(destinationDir);

        task.dependsOn(scope.getTaskContainer().getPreBuildTask());
    }
}
