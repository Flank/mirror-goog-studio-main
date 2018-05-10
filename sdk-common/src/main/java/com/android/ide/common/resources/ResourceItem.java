package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import java.util.Comparator;

/**
 * Describes resources that exist in an {@link AbstractResourceRepository} (and so the project). Can
 * be turned into a {@link ResourceValue} if the contents of a given resource need to be inspected,
 * not just its presence.
 */
public interface ResourceItem extends Configurable {
    String XLIFF_NAMESPACE_PREFIX = "urn:oasis:names:tc:xliff:document:";
    String XLIFF_G_TAG = "g";
    String ATTR_EXAMPLE = "example";

    /** Returns the name of this resource. */
    @NonNull
    String getName();

    /** Returns the type of this resource. */
    @NonNull
    ResourceType getType();

    /** Returns the {@lnk ResourceNamespace} of this resource. */
    @NonNull
    ResourceNamespace getNamespace();

    /**
     * Returns name of the library which defines this resource, or null for app resources.
     *
     * <p>The contents of the string depend on the build system used to create the {@link
     * ResourceItem}s.
     */
    @Nullable
    String getLibraryName();

    /** Returns a {@link ResourceReference} that points to this resource. */
    @NonNull
    ResourceReference getReferenceToSelf();

    /**
     * Returns a string that combines the namespace, type, name and qualifiers and should uniquely
     * identify a resource in a "correct" {@link AbstractResourceRepository}.
     *
     * <p>The returned string is not unique if the same resource is declared twice for the same
     * {@link FolderConfiguration} (by mistake most likely) and the resource items were not
     * merged together during creation.
     */
    @NonNull
    String getKey();

    /**
     * Returns a {@link ResourceValue} built from parsing the XML for this resource. This can be
     * used to inspect the value of the resource.
     *
     * <p>The concrete type of the returned object depends on {@link #getType()}.
     *
     * @return the parsed {@link ResourceValue} or null if there was an error parsing the XML or
     *     the XML is no longer accessible (this may be the case in the IDE, when the item is based
     *     on old PSI).
     */
    @Nullable
    ResourceValue getResourceValue();

    /**
     * Returns the {@link PathString} for the file from which this {@link ResourceItem} was
     * created, or null if the resource is not associated with a file. The returned
     * {@link PathString} may point to a file on the local file system, or to a zip file entry as
     * possible for AAR resources.
     */
    @Nullable
    PathString getSource();

    /**
     * Returns true if the {@link ResourceItem} represents a whole file or a whole zip file entry,
     * not an XML tag within a values XML file. This is the case for e.g. layouts or colors defined
     * as state lists.
     */
    boolean isFileBased();

    /** Compares {@link ResourceItem} instances using {@link #getKey()}. */
    Comparator<ResourceItem> BY_KEY = Comparator.comparing(ResourceItem::getKey);
}
