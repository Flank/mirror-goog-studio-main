package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.api.variant.LibraryVariantProperties;
import com.android.build.api.variant.Variant;
import com.android.build.api.variant.VariantProperties;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.ProjectOptions;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;

/**
 * The {@code android} extension for {@code com.android.library} projects.
 *
 * <p>Apply this plugin to your project to <a
 * href="https://developer.android.com/studio/projects/android-library.html">create an Android
 * library</a>.
 */
public class LibraryExtension extends TestedExtension<com.android.build.api.variant.LibraryVariant, LibraryVariantProperties>
        implements com.android.build.api.dsl.LibraryExtension {

    private final DomainObjectSet<LibraryVariant> libraryVariantList;

    private Collection<String> aidlPackageWhiteList = null;

    public LibraryExtension(
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
                false);
        libraryVariantList = project.getObjects().domainObjectSet(LibraryVariant.class);
    }

    /**
     * Returns a collection of <a
     * href="https://developer.android.com/studio/build/build-variants.html">build variants</a> that
     * the library project includes.
     *
     * <p>To process elements in this collection, you should use the <a
     * href="https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)">
     * <code>all</code></a> iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the <code>each</code> iterator, using <code>all</code>
     * processes future elements as the plugin creates them.
     *
     * <p>The following sample iterates through all <code>libraryVariants</code> elements to <a
     * href="https://developer.android.com/studio/build/manifest-build-variables.html">inject a
     * build variable into the manifest</a>:
     *
     * <pre>
     * android.libraryVariants.all { variant -&gt;
     *     def mergedFlavor = variant.getMergedFlavor()
     *     // Defines the value of a build variable you can use in the manifest.
     *     mergedFlavor.manifestPlaceholders = [hostName:"www.example.com"]
     * }
     * </pre>
     */
    public DefaultDomainObjectSet<LibraryVariant> getLibraryVariants() {
        return (DefaultDomainObjectSet<LibraryVariant>) libraryVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant, VariantScope variantScope) {
        libraryVariantList.add((LibraryVariant) variant);
    }

    public void aidlPackageWhiteList(String ... aidlFqcns) {
        if (aidlPackageWhiteList == null) {
            aidlPackageWhiteList = Lists.newArrayList();
        }
        Collections.addAll(aidlPackageWhiteList, aidlFqcns);
    }

    public void setAidlPackageWhiteList(Collection<String> aidlPackageWhiteList) {
        this.aidlPackageWhiteList = Lists.newArrayList(aidlPackageWhiteList);
    }

    @Override
    public Collection<String> getAidlPackageWhiteList() {
        return aidlPackageWhiteList;
    }
}
