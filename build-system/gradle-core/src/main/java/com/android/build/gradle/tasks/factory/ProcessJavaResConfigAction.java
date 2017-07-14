package com.android.build.gradle.tasks.factory;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.model.SourceProvider;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.tasks.Sync;

/**
 * Configuration Action for a process*JavaRes tasks.
 */
public class ProcessJavaResConfigAction implements TaskConfigAction<Sync> {
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
        GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();

        AndroidSourceSet defaultSourceSet =
                (AndroidSourceSet) variantConfiguration.getDefaultSourceSet();

        processResources.from(defaultSourceSet.getResources().getSourceFiles());

        if (!variantConfiguration.getType().isSingleBuildType()) {
            AndroidSourceSet buildTypeSourceSet =
                    (AndroidSourceSet) variantConfiguration.getBuildTypeSourceSet();
            checkState(buildTypeSourceSet != null); // checked isSingleBuildType() above.

            processResources.from(buildTypeSourceSet.getResources().getSourceFiles());
        }

        if (variantConfiguration.hasFlavors()) {
            List<SourceProvider> flavorSourceProviders =
                    variantConfiguration.getFlavorSourceProviders();

            for (SourceProvider flavorSourceProvider : flavorSourceProviders) {
                AndroidSourceSet flavorSourceSet = (AndroidSourceSet) flavorSourceProvider;
                processResources.from(flavorSourceSet.getResources().getSourceFiles());
            }

            AndroidSourceSet multiFlavorSourceSet =
                    (AndroidSourceSet) variantConfiguration.getMultiFlavorSourceProvider();
            if (multiFlavorSourceSet != null) {
                processResources.from(multiFlavorSourceSet.getResources().getSourceFiles());
            }
        }

        AndroidSourceSet variantSourceSet =
                (AndroidSourceSet) variantConfiguration.getVariantSourceProvider();
        if (variantSourceSet != null) {
            processResources.from(variantSourceSet.getResources().getSourceFiles());
        }

        if (processResources.getInputs().getFiles().getFiles().isEmpty()) {
            try {
                FileUtils.deletePath(scope.getSourceFoldersJavaResDestinationDir());
            } catch (IOException e) {
                throw new RuntimeException("Cannot delete merged source resource folder", e);
            }
        }

        processResources.setDestinationDir(destinationDir);
    }
}
