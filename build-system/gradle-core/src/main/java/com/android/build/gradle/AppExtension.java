package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.api.variant.Variant;
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
import com.android.build.gradle.options.ProjectOptions;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;

/**
 * The {@code android} extension for application plugins.
 *
 * <p>For the base module, see {@link com.android.build.gradle.BaseExtension}
 *
 * <p>For optional apks, this class is used directly.
 */
public class AppExtension extends TestedExtension {

    private final DefaultDomainObjectSet<ApplicationVariant> applicationVariantList
            = new DefaultDomainObjectSet<ApplicationVariant>(ApplicationVariant.class);

    private final List<Action<Variant>> variantActionList = new ArrayList<>();

    public AppExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isBaseModule) {
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
                isBaseModule);
    }

    /**
     * Returns a collection of <a
     * href="https://developer.android.com/studio/build/build-variants.html">build variants</a> that
     * the app project includes.
     *
     * <p>To process elements in this collection, you should use the <a
     * href="https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)">
     * <code>all</code></a> iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the <code>each</code> iterator, using <code>all</code>
     * processes future elements as the plugin creates them.
     *
     * <p>The following sample iterates through all <code>applicationVariants</code> elements to <a
     * href="https://developer.android.com/studio/build/manifest-build-variables.html">inject a
     * build variable into the manifest</a>:
     *
     * <pre>
     * android.applicationVariants.all { variant -&gt;
     *     def mergedFlavor = variant.getMergedFlavor()
     *     // Defines the value of a build variable you can use in the manifest.
     *     mergedFlavor.manifestPlaceholders = [hostName:"www.example.com/${variant.versionName}"]
     * }
     * </pre>
     */
    public DomainObjectSet<ApplicationVariant> getApplicationVariants() {
        return applicationVariantList;
    }

    /**
     * Registers an {@link Action} to be executed on each {@link Variant} of the project.
     *
     * @param action an {@link Action} taking a {@link Variant} as a parameter.
     */
    public void onVariants(Action<Variant> action) {
        variantActionList.add(action);
        // TODO: b/142715610 Resolve when onVariants is called with variants already existing the
        // applicationVariantList.
    }

    @Override
    public void addVariant(BaseVariant variant, VariantScope variantScope) {
        applicationVariantList.add((ApplicationVariant) variant);
        variantActionList.forEach(
                action -> action.execute(variantScope.getVariantData().getPublicVariantApi()));
    }
}
