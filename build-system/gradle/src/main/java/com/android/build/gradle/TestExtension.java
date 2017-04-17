package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.internal.reflect.Instantiator;

/**
 * {@code android} extension for {@code com.android.test} projects.
 */
public class TestExtension extends BaseExtension implements TestAndroidConfig {

    private final DefaultDomainObjectSet<ApplicationVariant> applicationVariantList
            = new DefaultDomainObjectSet<ApplicationVariant>(ApplicationVariant.class);

    private String targetProjectPath = null;

    public TestExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo) {
        super(
                project,
                projectOptions,
                instantiator,
                androidBuilder,
                sdkHandler,
                buildTypes,
                productFlavors,
                signingConfigs,
                buildOutputs,
                extraModelInfo,
                false);
    }

    /**
     * Returns the list of Application variants. Since the collections is built after evaluation, it
     * should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DefaultDomainObjectSet<ApplicationVariant> getApplicationVariants() {
        return applicationVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant) {
        applicationVariantList.add((ApplicationVariant) variant);
    }

    /**
     * Returns the Gradle path of the project that this test project tests.
     */
    @Override
    public String getTargetProjectPath() {
        return targetProjectPath;
    }

    public void setTargetProjectPath(String targetProjectPath) {
        checkWritability();
        this.targetProjectPath = targetProjectPath;
    }

    public void targetProjectPath(String targetProjectPath) {
        setTargetProjectPath(targetProjectPath);
    }

    /**
     * Returns the variant of the tested project.
     *
     * Default is 'debug'
     */
    @Override
    @Deprecated
    public String getTargetVariant() {
        return "";
    }

    @Deprecated
    public void setTargetVariant(String targetVariant) {
        checkWritability();
        System.err.println("android.targetVariant is deprecated, all variants are now tested.");
    }

    public void targetVariant(String targetVariant) {
        setTargetVariant(targetVariant);
    }
}
