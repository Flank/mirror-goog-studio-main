package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.SourceProvider;
import java.io.File;
import org.gradle.api.tasks.Sync;

/** Configuration Action for a process*JavaRes tasks. */
public class ProcessJavaResConfigAction extends TaskConfigAction<Sync> {
    private VariantScope scope;
    private final File destinationDir;

    public ProcessJavaResConfigAction(VariantScope scope, File destinationDir) {
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
    public void execute(@NonNull Sync processResources) {

        for (SourceProvider sourceProvider :
                scope.getVariantConfiguration().getSortedSourceProviders()) {
            processResources.from(
                    ((AndroidSourceSet) sourceProvider).getResources().getSourceFiles());
        }

        processResources.setDestinationDir(destinationDir);
    }
}
