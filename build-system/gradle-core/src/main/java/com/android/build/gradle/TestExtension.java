package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.Variant;
import com.android.build.api.variant.VariantProperties;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.options.ProjectOptions;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;

/** {@code android} extension for {@code com.android.test} projects. */
public class TestExtension extends BaseExtension
        implements TestAndroidConfig, com.android.build.api.dsl.TestExtension {

    private final DomainObjectSet<ApplicationVariant> applicationVariantList;

    private String targetProjectPath = null;

    private List<Action<Variant<VariantProperties>>> variantActionList = new ArrayList<>();
    private List<Action<VariantProperties>> variantPropertiesActionList = new ArrayList<>();

    public TestExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo) {
        super(
                project,
                projectOptions,
                globalScope,
                buildTypes,
                productFlavors,
                signingConfigs,
                buildOutputs,
                sourceSetManager,
                extraModelInfo,
                false); // FIXME figure this out.
        applicationVariantList = project.getObjects().domainObjectSet(ApplicationVariant.class);
    }

    /**
     * Returns the list of Application variants. Since the collections is built after evaluation, it
     * should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DefaultDomainObjectSet<ApplicationVariant> getApplicationVariants() {
        return (DefaultDomainObjectSet<ApplicationVariant>) applicationVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant, VariantScope variantScope) {
        applicationVariantList.add((ApplicationVariant) variant);
        BaseVariantData variantData = variantScope.getVariantData();
        for (Action<Variant<VariantProperties>> action : variantActionList) {
            //noinspection unchecked // TODO: Have a subtype for Android Test too.
            action.execute((Variant<VariantProperties>) variantData.getPublicVariantApi());
        }
        for (Action<VariantProperties> action : variantPropertiesActionList) {
            action.execute(variantData.getPublicVariantPropertiesApi());
        }
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
     * <p>Default is 'debug'
     *
     * @deprecated This is deprecated, test module can now test all flavors.
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

    @Nullable
    @Override
    public String getTestBuildType() {
        return null;
    }

    @Override
    public void onVariants(Action<Variant<VariantProperties>> action) {
        variantActionList.add(action);
    }

    @Override
    public void onVariantsProperties(Action<VariantProperties> action) {
        variantPropertiesActionList.add(action);
    }
}
