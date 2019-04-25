/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks;

import static com.android.SdkConstants.DOT_XML;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Database for API checking: Allows quick lookup of a given class, method or field to see in which
 * API level it was introduced, and possibly deprecated and/or removed.
 *
 * <p>This class is optimized for quick bytecode lookup used in conjunction with the ASM library: It
 * has lookup methods that take internal JVM signatures, and for a method call for example it
 * processes the owner, name and description parameters separately the way they are provided from
 * ASM.
 *
 * <p>The {@link Api} class provides access to the full Android API along with version information,
 * initialized from an XML file.
 *
 * <p>When creating the memory data structure it performs a few other steps to help memory:
 *
 * <ul>
 *   <li>It strips out the method return types (which takes the binary size down from about 4.7M to
 *       4.0M)
 *   <li>It strips out all APIs that have since=1, since the lookup only needs to find classes,
 *       methods and fields that have an API level *higher* than 1. This drops the memory use down
 *       from 4.0M to 1.7M.
 * </ul>
 */
public class ApiLookup extends ApiDatabase {
    public static final String XML_FILE_PATH = "api-versions.xml"; // relative to the SDK data/ dir
    /** Database moved from platform-tools to SDK in API level 26 */
    public static final int SDK_DATABASE_MIN_VERSION = 26;

    private static final int API_LOOKUP_BINARY_FORMAT_VERSION = 0;
    private static final int CLASS_HEADER_MEMBER_OFFSETS = 1;
    private static final int CLASS_HEADER_API = 2;
    private static final int CLASS_HEADER_DEPRECATED = 3;
    private static final int CLASS_HEADER_REMOVED = 4;
    private static final int CLASS_HEADER_INTERFACES = 5;

    @VisibleForTesting static final boolean DEBUG_FORCE_REGENERATE_BINARY = false;

    private final Api<ApiClass> mInfo;

    private static final Map<AndroidVersion, WeakReference<ApiLookup>> instances = new HashMap<>();

    private final IAndroidTarget target;

    /**
     * Returns an instance of the API database
     *
     * @param client the client to associate with this database - used only for logging. The
     *     database object may be shared among repeated invocations, and in that case client used
     *     will be the one originally passed in. In other words, this parameter may be ignored if
     *     the client created is not new.
     * @return a (possibly shared) instance of the API database, or null if its data can't be found
     */
    @Nullable
    public static ApiLookup get(@NonNull LintClient client) {
        return get(client, null);
    }

    /**
     * Returns an instance of the API database
     *
     * @param client the client to associate with this database - used only for logging. The
     *     database object may be shared among repeated invocations, and in that case client used
     *     will be the one originally passed in. In other words, this parameter may be ignored if
     *     the client created is not new.
     * @param target the corresponding Android target, if known
     * @return a (possibly shared) instance of the API database, or null if its data can't be found
     */
    @Nullable
    public static ApiLookup get(@NonNull LintClient client, @Nullable IAndroidTarget target) {
        synchronized (ApiLookup.class) {
            AndroidVersion version = target != null ? target.getVersion() : AndroidVersion.DEFAULT;
            WeakReference<ApiLookup> reference = instances.get(version);
            ApiLookup db = reference != null ? reference.get() : null;
            if (db == null) {
                // Fallbacks: Allow the API database to be read from a custom location
                String env = System.getProperty("LINT_API_DATABASE");
                File file = null;
                if (env != null) {
                    file = new File(env);
                    if (!file.exists()) {
                        file = null;
                    }
                } else {
                    if (target != null && version.getFeatureLevel() >= SDK_DATABASE_MIN_VERSION) {
                        file = new File(target.getFile(IAndroidTarget.DATA), XML_FILE_PATH);
                        if (!file.isFile()) {
                            file = null;
                        }
                    }

                    if (file == null) {
                        target = null; // The API database will no longer be tied to this target
                        file = client.findResource(XML_FILE_PATH);
                    }
                }

                if (file == null) {
                    return null;
                } else {
                    db = get(client, file, target);
                }
                instances.put(version, new WeakReference<>(db));
            }

            return db;
        }
    }

    /**
     * Returns the associated Android target, if known
     *
     * @return the target, if known
     */
    @Nullable
    public IAndroidTarget getTarget() {
        return target;
    }

    @VisibleForTesting
    @Nullable
    static String getPlatformVersion(@NonNull LintClient client) {
        AndroidSdkHandler sdk = client.getSdk();
        if (sdk != null) {
            LocalPackage pkgInfo =
                    sdk.getLocalPackage(
                            SdkConstants.FD_PLATFORM_TOOLS, client.getRepositoryLogger());
            if (pkgInfo != null) {
                return pkgInfo.getVersion().toShortString();
            }
        }
        return null;
    }

    @VisibleForTesting
    @NonNull
    static String getCacheFileName(@NonNull String xmlFileName, @Nullable String platformVersion) {
        if (Lint.endsWith(xmlFileName, DOT_XML)) {
            xmlFileName = xmlFileName.substring(0, xmlFileName.length() - DOT_XML.length());
        }

        StringBuilder sb = new StringBuilder(100);
        sb.append(xmlFileName);

        // Incorporate version number in the filename to avoid upgrade filename
        // conflicts on Windows (such as issue #26663)
        sb.append('-').append(getBinaryFormatVersion(API_LOOKUP_BINARY_FORMAT_VERSION));

        if (platformVersion != null) {
            sb.append('-').append(platformVersion.replace(' ', '_'));
        }

        sb.append(".bin");
        return sb.toString();
    }

    /**
     * Returns an instance of the API database
     *
     * @param client the client to associate with this database - used only for logging
     * @param xmlFile the XML file containing configuration data to use for this database
     * @param target the associated Android target, if known
     * @return a (possibly shared) instance of the API database, or null if its data can't be found
     */
    private static ApiLookup get(
            @NonNull LintClient client, @NonNull File xmlFile, @Nullable IAndroidTarget target) {
        if (!xmlFile.exists()) {
            client.log(null, "The API database file %1$s does not exist", xmlFile);
            return null;
        }

        File cacheDir = client.getCacheDir(null, true);
        if (cacheDir == null) {
            cacheDir = xmlFile.getParentFile();
        }

        String platformVersion = getPlatformVersion(client);
        File binaryData = new File(cacheDir, getCacheFileName(xmlFile.getName(), platformVersion));

        if (DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println(
                    "\nTemporarily regenerating binary data unconditionally \nfrom "
                            + xmlFile
                            + "\nto "
                            + binaryData);
            if (!cacheCreator(xmlFile).create(client, binaryData)) {
                return null;
            }
        } else if (!binaryData.exists()
                || binaryData.lastModified() < xmlFile.lastModified()
                || binaryData.length() == 0) {
            if (!cacheCreator(xmlFile).create(client, binaryData)) {
                return null;
            }
        }

        if (!binaryData.exists()) {
            client.log(null, "The API database file %1$s does not exist", binaryData);
            return null;
        }

        return new ApiLookup(client, xmlFile, binaryData, null, target);
    }

    private static CacheCreator cacheCreator(File xmlFile) {
        return (client, binaryData) -> {
            long begin = WRITE_STATS ? System.currentTimeMillis() : 0;

            Api<ApiClass> info;
            try {
                info = Api.parseApi(xmlFile);
            } catch (RuntimeException e) {
                client.log(e, "Can't read API file " + xmlFile.getAbsolutePath());
                return false;
            }

            if (WRITE_STATS) {
                long end = System.currentTimeMillis();
                System.out.println("Reading XML data structures took " + (end - begin) + " ms");
            }

            try {
                writeDatabase(binaryData, info, API_LOOKUP_BINARY_FORMAT_VERSION);
                return true;
            } catch (IOException e) {
                client.log(e, "Can't write API cache file");
            }

            return false;
        };
    }

    /** Use one of the {@link #get} factory methods instead. */
    private ApiLookup(
            @NonNull LintClient client,
            @NonNull File xmlFile,
            @Nullable File binaryFile,
            @Nullable Api<ApiClass> info,
            @Nullable IAndroidTarget target) {
        mInfo = info;
        this.target = target;

        if (binaryFile != null) {
            readData(client, binaryFile, cacheCreator(xmlFile), API_LOOKUP_BINARY_FORMAT_VERSION);
        }
    }

    /**
     * Returns the API version required by the given class reference, or -1 if this is not a known
     * API class. Note that it may return -1 for classes introduced in version 1; internally the
     * database only stores version data for version 2 and up.
     *
     * @param className the internal name of the class, e.g. its fully qualified name (as returned
     *     by Class.getName())
     * @return the minimum API version the method is supported for, or -1 if it's unknown <b>or
     *     version 1</b>
     */
    public int getClassVersion(@NonNull String className) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            return getClassVersion(findClass(className));
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(className);
            if (cls != null) {
                return cls.getSince();
            }
        }

        return -1;
    }

    private int getClassVersion(int classNumber) {
        if (classNumber >= 0) {
            int offset = seekClassData(classNumber, CLASS_HEADER_API);
            int api = Byte.toUnsignedInt(mData[offset]) & API_MASK;
            return api > 0 ? api : -1;
        }
        return -1;
    }

    /**
     * Returns the API version required to perform the given cast, or -1 if this is valid for all
     * versions of the class (or, if these are not known classes or if the cast is not valid at
     * all.)
     *
     * <p>Note also that this method should only be called for interfaces that are actually
     * implemented by this class or extending the given super class (check elsewhere); it doesn't
     * distinguish between interfaces implemented in the initial version of the class and interfaces
     * not implemented at all.
     *
     * @param sourceClass the internal name of the class, e.g. its fully qualified name (as returned
     *     by Class.getName())
     * @param destinationClass the class to cast the sourceClass to
     * @return the minimum API version the method is supported for, or 1 or -1 if it's unknown
     */
    public int getValidCastVersion(@NonNull String sourceClass, @NonNull String destinationClass) {
        if (mData != null) {
            int classNumber = findClass(sourceClass);
            if (classNumber >= 0) {
                int interfaceNumber = findClass(destinationClass);
                if (interfaceNumber >= 0) {
                    int offset = seekClassData(classNumber, CLASS_HEADER_INTERFACES);
                    int interfaceCount = mData[offset++];
                    for (int i = 0; i < interfaceCount; i++) {
                        int clsNumber = get3ByteInt(mData, offset);
                        offset += 3;
                        int api = mData[offset++];
                        if (clsNumber == interfaceNumber) {
                            return api;
                        }
                    }
                    return getClassVersion(classNumber);
                }
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(sourceClass);
            if (cls != null) {
                List<Pair<String, Integer>> interfaces = cls.getInterfaces();
                for (Pair<String, Integer> pair : interfaces) {
                    String interfaceName = pair.getFirst();
                    if (interfaceName.equals(destinationClass)) {
                        return pair.getSecond();
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Returns the API version the given class was deprecated in, or -1 if the class is not
     * deprecated.
     *
     * @param className the internal name of the method's owner class, e.g. its fully qualified name
     *     (as returned by Class.getName())
     * @return the API version the API was deprecated in, or -1 if it's unknown <b>or version 0</b>
     */
    public int getClassDeprecatedIn(@NonNull String className) {
        if (mData != null) {
            int classNumber = findClass(className);
            if (classNumber >= 0) {
                int offset = seekClassData(classNumber, CLASS_HEADER_DEPRECATED);
                if (offset < 0) {
                    // Not deprecated
                    return -1;
                }
                int deprecatedIn = Byte.toUnsignedInt(mData[offset]) & API_MASK;

                return deprecatedIn != 0 ? deprecatedIn : -1;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(className);
            if (cls != null) {
                int deprecatedIn = cls.getDeprecatedIn();
                return deprecatedIn != 0 ? deprecatedIn : -1;
            }
        }

        return -1;
    }

    /**
     * Returns the API version the given class was removed in, or -1 if the class was not removed.
     *
     * @param className the internal name of the method's owner class, e.g. its fully qualified name
     *     (as returned by Class.getName())
     * @return the API version the API was removed in, or -1 if it's unknown <b>or version 0</b>
     */
    public int getClassRemovedIn(@NonNull String className) {
        if (mData != null) {
            int classNumber = findClass(className);
            if (classNumber >= 0) {
                int offset = seekClassData(classNumber, CLASS_HEADER_REMOVED);
                if (offset < 0) {
                    // Not removed
                    return -1;
                }
                int removedIn = Byte.toUnsignedInt(mData[offset]) & API_MASK;
                return removedIn != 0 ? removedIn : -1;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(className);
            if (cls != null) {
                int removedIn = cls.getRemovedIn();
                return removedIn != 0 ? removedIn : -1;
            }
        }

        return -1;
    }

    /**
     * Returns true if the given owner class is known in the API database.
     *
     * @param className the internal name of the class, e.g. its fully qualified name (as returned
     *     by Class.getName(), but with '.' replaced by '/' (and '$' for inner classes)
     * @return true if this is a class found in the API database
     */
    public boolean containsClass(@NonNull String className) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            return findClass(className) >= 0;
        } else if (mInfo != null) {
            return mInfo.getClass(className) != null;
        }

        return false;
    }

    /**
     * Returns the API version required by the given method call. The method is referred to by its
     * {@code owner}, {@code name} and {@code desc} fields. If the method is unknown it returns -1.
     * Note that it may return -1 for classes introduced in version 1; internally the database only
     * stores version data for version 2 and up.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @param desc the method's descriptor - see {@link org.objectweb.asm.Type}
     * @return the minimum API version the method is supported for, or -1 if it's unknown
     */
    public int getMethodVersion(@NonNull String owner, @NonNull String name, @NonNull String desc) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int api = findMember(classNumber, name, desc);
                if (api < 0) {
                    return -1;
                }
                return api;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                String signature = name + desc;
                int since = cls.getMethod(signature, mInfo);
                if (since == 0) {
                    since = -1;
                }
                return since;
            }
        }

        return -1;
    }

    /**
     * Returns the API version the given call was deprecated in, or -1 if the method is not
     * deprecated.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @param desc the method's descriptor - see {@link org.objectweb.asm.Type}
     * @return the API version the API was deprecated in, or -1 if the method is not deprecated
     */
    public int getMethodDeprecatedIn(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int deprecatedIn = findMemberDeprecatedIn(classNumber, name, desc);
                return deprecatedIn == 0 ? -1 : deprecatedIn;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                String signature = name + desc;
                int deprecatedIn = cls.getMemberDeprecatedIn(signature, mInfo);
                return deprecatedIn == 0 ? -1 : deprecatedIn;
            }
        }

        return -1;
    }

    /**
     * Returns the API version the given call was removed in, or -1 if the method was not removed.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @param desc the method's descriptor - see {@link org.objectweb.asm.Type}
     * @return the API version the API was removed in, or -1 if the method was not removed
     */
    public int getMethodRemovedIn(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int removedIn = findMemberRemovedIn(classNumber, name, desc);
                return removedIn == 0 ? -1 : removedIn;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                String signature = name + desc;
                int removedIn = cls.getMemberRemovedIn(signature, mInfo);
                return removedIn == 0 ? -1 : removedIn;
            }
        }

        return -1;
    }

    /**
     * Returns all removed fields of the given class and all its super classes and interfaces.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @return the removed fields, or null if the owner class was not found
     */
    @Nullable
    public Collection<ApiMember> getRemovedFields(@NonNull String owner) {
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                return getRemovedMembers(classNumber, false);
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                return cls.getAllRemovedFields(mInfo);
            }
        }
        return null;
    }

    /**
     * Returns all removed methods of the given class and all its super classes and interfaces.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @return the removed methods, or null if the owner class was not found
     */
    @Nullable
    public Collection<ApiMember> getRemovedMethods(@NonNull String owner) {
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                return getRemovedMembers(classNumber, true);
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                return cls.getAllRemovedMethods(mInfo);
            }
        }
        return null;
    }

    /**
     * Returns all removed methods or fields depending on the value of the {@code method} parameter.
     *
     * @param classNumber the index of the class
     * @param methods true to return methods, false to return fields.
     * @return all removed methods or fields
     */
    @NonNull
    private Collection<ApiMember> getRemovedMembers(int classNumber, boolean methods) {
        int curr = seekClassData(classNumber, CLASS_HEADER_MEMBER_OFFSETS);

        // 3 bytes for first offset
        int start = get3ByteInt(mData, curr);
        curr += 3;

        int length = get2ByteInt(mData, curr);
        if (length == 0) {
            return Collections.emptyList();
        }

        List<ApiMember> result = null;
        int end = start + length;
        for (int index = start; index < end; index++) {
            int offset = mIndices[index];
            boolean methodSignatureDetected = false;
            int i;
            for (i = offset; i < mData.length; i++) {
                byte b = mData[i];
                if (b == 0) {
                    break;
                }
                if (b == '(') {
                    methodSignatureDetected = true;
                }
            }
            if (i >= mData.length) {
                assert false;
                break;
            }
            if (methodSignatureDetected != methods) {
                continue;
            }
            int endOfSignature = i++;
            int since = Byte.toUnsignedInt(mData[i++]);
            if ((since & HAS_EXTRA_BYTE_FLAG) != 0) {
                int deprecatedIn = Byte.toUnsignedInt(mData[i++]);
                if ((deprecatedIn & HAS_EXTRA_BYTE_FLAG) != 0) {
                    int removedIn = Byte.toUnsignedInt(mData[i]);
                    if (removedIn != 0) {
                        StringBuilder sb = new StringBuilder(endOfSignature - offset);
                        for (i = offset; i < endOfSignature; i++) {
                            sb.append((char) Byte.toUnsignedInt(mData[i]));
                        }
                        since &= API_MASK;
                        deprecatedIn &= API_MASK;
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(new ApiMember(sb.toString(), since, deprecatedIn, removedIn));
                    } else {
                        assert false;
                    }
                }
            }
        }
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * Returns the API version required to access the given field, or -1 if this is not a known API
     * method. Note that it may return -1 for classes introduced in version 1; internally the
     * database only stores version data for version 2 and up.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @return the minimum API version the method is supported for, or -1 if it's unknown
     */
    public int getFieldVersion(@NonNull String owner, @NonNull String name) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int api = findMember(classNumber, name, null);
                if (api < 0) {
                    return -1;
                }
                return api;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                int since = cls.getField(name, mInfo);
                if (since == 0) {
                    since = -1;
                }
                return since;
            }
        }

        return -1;
    }

    /**
     * Returns the API version the given field was deprecated in, or -1 if the field is not
     * deprecated.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @return the API version the API was deprecated in, or -1 if the field is not deprecated
     */
    public int getFieldDeprecatedIn(@NonNull String owner, @NonNull String name) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int deprecatedIn = findMemberDeprecatedIn(classNumber, name, null);
                return deprecatedIn == 0 ? -1 : deprecatedIn;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                int deprecatedIn = cls.getMemberDeprecatedIn(name, mInfo);
                return deprecatedIn == 0 ? -1 : deprecatedIn;
            }
        }

        return -1;
    }

    /**
     * Returns the API version the given field was removed in, or -1 if the field was not removed.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @return the API version the API was removed in, or -1 if the field was not removed
     */
    public int getFieldRemovedIn(@NonNull String owner, @NonNull String name) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int removedIn = findMemberRemovedIn(classNumber, name, null);
                return removedIn == 0 ? -1 : removedIn;
            }
        } else if (mInfo != null) {
            ApiClass cls = mInfo.getClass(owner);
            if (cls != null) {
                int removedIn = cls.getMemberRemovedIn(name, mInfo);
                return removedIn == 0 ? -1 : removedIn;
            }
        }

        return -1;
    }

    /**
     * Returns true if the given owner (in VM format) is relevant to the database. This allows quick
     * filtering out of owners that won't return any data for the various {@code #getFieldVersion}
     * etc methods.
     *
     * @param owner the owner to look up
     * @return true if the owner might be relevant to the API database
     */
    public boolean isRelevantOwner(@NonNull String owner) {
        return findClass(owner) >= 0;
    }

    /**
     * Returns true if the given package is a valid Java package supported in any version of
     * Android.
     *
     * @param classOrPackageName the name of a package or a class
     * @param packageNameLength the length of the package part of the name
     * @return true if the package is included in one or more versions of Android
     */
    public boolean isValidJavaPackage(@NonNull String classOrPackageName, int packageNameLength) {
        return findContainer(classOrPackageName, packageNameLength, true) >= 0;
    }

    /**
     * Checks if the two given class or package names are equal or differ only by separators.
     * Separators '.', '/', and '$' are considered equivalent.
     */
    public static boolean equivalentName(@NonNull String name1, @NonNull String name2) {
        int len1 = name1.length();
        int len2 = name2.length();
        if (len1 != len2) {
            return false;
        }
        for (int i = 0; i < len1; i++) {
            if (normalizeSeparator(name1.charAt(i)) != normalizeSeparator(name2.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the beginning part of the given class or package name is equal to the given prefix,
     * or differs only by separators. Separators '.', '/', and '$' are considered equivalent.
     */
    public static boolean startsWithEquivalentPrefix(
            @NonNull String classOrPackageName, @NonNull String prefix) {
        return equivalentFragmentAtOffset(classOrPackageName, 0, prefix);
    }

    /**
     * Checks if the substring of the given class or package name at the given offset is equal to
     * the given fragment or differs only by separators. Separators '.', '/', and '$' are considered
     * equivalent.
     */
    public static boolean equivalentFragmentAtOffset(
            @NonNull String classOrPackageName, int offset, @NonNull String fragment) {
        int prefixLength = fragment.length();
        if (offset < 0 || offset > classOrPackageName.length() - prefixLength) {
            return false;
        }
        for (int prefixOffset = 0; prefixOffset < prefixLength; prefixOffset++) {
            if (normalizeSeparator(classOrPackageName.charAt(offset++))
                    != normalizeSeparator(fragment.charAt(prefixOffset))) {
                return false;
            }
        }
        return true;
    }

    private static char normalizeSeparator(char c) {
        if (c == '/' || c == '$') {
            c = '.';
        }
        return c;
    }

    private int findMember(int classNumber, @NonNull String name, @Nullable String desc) {
        return findMember(classNumber, name, desc, CLASS_HEADER_API);
    }

    private int findMemberDeprecatedIn(
            int classNumber, @NonNull String name, @Nullable String desc) {
        return findMember(classNumber, name, desc, CLASS_HEADER_DEPRECATED);
    }

    private int findMemberRemovedIn(int classNumber, @NonNull String name, @Nullable String desc) {
        return findMember(classNumber, name, desc, CLASS_HEADER_REMOVED);
    }

    private int seekClassData(int classNumber, int field) {
        int offset = mIndices[classNumber];
        offset += mData[offset] & 0xFF;
        if (field == CLASS_HEADER_MEMBER_OFFSETS) {
            return offset;
        }
        offset += 5; // 3 bytes for start, 2 bytes for length
        if (field == CLASS_HEADER_API) {
            return offset;
        }
        boolean hasDeprecatedIn = (mData[offset] & HAS_EXTRA_BYTE_FLAG) != 0;
        boolean hasRemovedIn = false;
        offset++;
        if (field == CLASS_HEADER_DEPRECATED) {
            return hasDeprecatedIn ? offset : -1;
        } else if (hasDeprecatedIn) {
            hasRemovedIn = (mData[offset] & HAS_EXTRA_BYTE_FLAG) != 0;
            offset++;
        }
        if (field == CLASS_HEADER_REMOVED) {
            return hasRemovedIn ? offset : -1;
        } else if (hasRemovedIn) {
            offset++;
        }
        assert field == CLASS_HEADER_INTERFACES;
        return offset;
    }

    private int findMember(
            int classNumber, @NonNull String name, @Nullable String desc, int apiLevelField) {
        int curr = seekClassData(classNumber, CLASS_HEADER_MEMBER_OFFSETS);

        // 3 bytes for first offset
        int low = get3ByteInt(mData, curr);
        curr += 3;

        int length = get2ByteInt(mData, curr);
        if (length == 0) {
            return -1;
        }
        int high = low + length;

        while (low < high) {
            int middle = (low + high) >>> 1;
            int offset = mIndices[middle];

            if (DEBUG_SEARCH) {
                System.out.println(
                        "Comparing string "
                                + (name + ';' + desc)
                                + " with entry at "
                                + offset
                                + ": "
                                + dumpEntry(offset));
            }

            int compare;
            if (desc != null) {
                // Method
                int nameLength = name.length();
                compare = compare(mData, offset, (byte) '(', name, 0, nameLength);
                if (compare == 0) {
                    offset += nameLength;
                    int argsEnd = desc.indexOf(')');
                    // Only compare up to the ) -- after that we have a return value in the
                    // input description, which isn't there in the database.
                    compare = compare(mData, offset, (byte) ')', desc, 0, argsEnd);
                    if (compare == 0) {
                        if (DEBUG_SEARCH) {
                            System.out.println("Found " + dumpEntry(offset));
                        }

                        offset += argsEnd + 1;

                        if (mData[offset++] == 0) {
                            // Yes, terminated argument list: get the API level
                            return getApiLevel(offset, apiLevelField);
                        }
                    }
                }
            } else {
                // Field
                int nameLength = name.length();
                compare = compare(mData, offset, (byte) 0, name, 0, nameLength);
                if (compare == 0) {
                    offset += nameLength;
                    if (mData[offset++] == 0) {
                        // Yes, terminated argument list: get the API level
                        return getApiLevel(offset, apiLevelField);
                    }
                }
            }

            if (compare < 0) {
                low = middle + 1;
            } else if (compare > 0) {
                high = middle;
            } else {
                assert false; // compare == 0 already handled above
                return -1;
            }
        }

        return -1;
    }

    private int getApiLevel(int offset, int apiLevelField) {
        int api = Byte.toUnsignedInt(mData[offset]);
        if (apiLevelField == CLASS_HEADER_API) {
            return api & API_MASK;
        }
        if ((api & HAS_EXTRA_BYTE_FLAG) == 0) {
            return -1;
        }
        api = Byte.toUnsignedInt(mData[++offset]);
        if (apiLevelField == CLASS_HEADER_DEPRECATED) {
            api &= API_MASK;
            return api == 0 ? -1 : api;
        }
        assert apiLevelField == CLASS_HEADER_REMOVED;
        if ((api & HAS_EXTRA_BYTE_FLAG) == 0 || apiLevelField != CLASS_HEADER_REMOVED) {
            return -1;
        }
        api = Byte.toUnsignedInt(mData[++offset]);
        return api == 0 ? -1 : api;
    }

    /** Clears out any existing lookup instances */
    @VisibleForTesting
    static void dispose() {
        instances.clear();
    }
}
