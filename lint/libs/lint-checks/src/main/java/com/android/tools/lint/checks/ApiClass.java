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

import static com.android.SdkConstants.CONSTRUCTOR_NAME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.Pair;
import com.google.common.collect.Iterables;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a class and its methods/fields.
 *
 * <p>{@link #getSince()} gives the API level it was introduced.
 *
 * <p>{@link #getMethod} returns the API level when the method was introduced.
 *
 * <p>{@link #getField} returns the API level when the field was introduced.
 */
public final class ApiClass extends ApiClassBase {
    private final int mSince;
    private final int mDeprecatedIn;
    private final int mRemovedIn;

    private final List<Pair<String, Integer>> mSuperClasses = new ArrayList<>();
    private final List<Pair<String, Integer>> mInterfaces = new ArrayList<>();

    private final Map<String, Integer> mFields = new HashMap<>();
    private final Map<String, Integer> mMethods = new HashMap<>();
    /* Deprecated fields and methods and the API levels when they were deprecated. */
    @Nullable private Map<String, Integer> mMembersDeprecatedIn;
    /**
     * Removed fields, methods, superclasses and interfaces and the API levels when they were
     * removed.
     */
    @Nullable private Map<String, Integer> mElementsRemovedIn;

    ApiClass(String name, int since, int deprecatedIn, int removedIn) {
        super(name);
        mSince = since;
        mDeprecatedIn = deprecatedIn;
        mRemovedIn = removedIn;
    }

    /**
     * Returns when the class was introduced.
     *
     * @return the api level the class was introduced.
     */
    int getSince() {
        return mSince;
    }

    /**
     * Returns the API level the class was deprecated in, or 0 if the class is not deprecated.
     *
     * @return the API level the class was deprecated in, or 0 if the class is not deprecated
     */
    int getDeprecatedIn() {
        return mDeprecatedIn;
    }

    /**
     * Returns the API level the class was removed in, or 0 if the class was not removed.
     *
     * @return the API level the class was removed in, or 0 if the class was not removed
     */
    int getRemovedIn() {
        return mRemovedIn;
    }

    /**
     * Returns the API level when a field was added, or 0 if it doesn't exist.
     *
     * @param name the name of the field.
     * @param info the information about the rest of the API
     */
    int getField(String name, Api<? extends ApiClassBase> info) {
        // The field can come from this class or from a super class or an interface
        // The value can never be lower than this introduction of this class.
        // When looking at super classes and interfaces, it can never be lower than when the
        // super class or interface was added as a super class or interface to this class.
        // Look at all the values and take the lowest.
        // For instance:
        // This class A is introduced in 5 with super class B.
        // In 10, the interface C was added.
        // Looking for SOME_FIELD we get the following:
        // Present in A in API 15
        // Present in B in API 11
        // Present in C in API 7.
        // The answer is 10, which is when C became an interface
        int apiLevel = getValueWithDefault(mFields, name, 0);

        // Look at the super classes and interfaces.
        for (Pair<String, Integer> superClassPair : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClassBase superClass = info.getClass(superClassPair.getFirst());
            if (superClass instanceof ApiClass) {
                int i = ((ApiClass) superClass).getField(name, info);
                if (i != 0) {
                    int tmp = Math.max(superClassPair.getSecond(), i);
                    if (apiLevel == 0 || tmp < apiLevel) {
                        apiLevel = tmp;
                    }
                }
            }
        }

        return apiLevel;
    }

    /**
     * Returns when a field or a method was deprecated, or 0 if it's not deprecated.
     *
     * @param name the name of the field.
     * @param info the information about the rest of the API
     */
    int getMemberDeprecatedIn(@NonNull String name, Api info) {
        // This follows the same logic as getField/getMethod.
        // However, it also incorporates deprecation versions from the class.
        int apiLevel = getValueWithDefault(mMembersDeprecatedIn, name, 0);
        return apiLevel == 0
                ? mDeprecatedIn
                : mDeprecatedIn == 0 ? apiLevel : Math.min(apiLevel, mDeprecatedIn);
    }

    /**
     * Returns the API level when a field or a method was removed, or 0 if it's not removed.
     *
     * @param name the name of the field or method
     * @param info the information about the rest of the API
     */
    int getMemberRemovedIn(@NonNull String name, Api info) {
        int removedIn = getMemberRemovedInInternal(name, info);
        return removedIn == Integer.MAX_VALUE ? mRemovedIn : removedIn > 0 ? removedIn : 0;
    }

    /**
     * Returns the API level when a field or a method was removed, or Integer.MAX_VALUE if it's not
     * removed, or -1 if the field or method never existed in this class or its super classes and
     * interfaces.
     *
     * @param name the name of the field or method
     * @param info the information about the rest of the API
     */
    private int getMemberRemovedInInternal(String name, Api<ApiClass> info) {
        int apiLevel = getValueWithDefault(mElementsRemovedIn, name, Integer.MAX_VALUE);
        if (apiLevel == Integer.MAX_VALUE) {
            if (mMethods.containsKey(name) || mFields.containsKey(name)) {
                return mRemovedIn == 0 ? Integer.MAX_VALUE : mRemovedIn;
            }
            apiLevel = -1; // Never existed in this class.
        }

        // Look at the super classes and interfaces.
        for (Pair<String, Integer> superClassPair : Iterables.concat(mSuperClasses, mInterfaces)) {
            String superClassName = superClassPair.getFirst();
            int superClassRemovedIn =
                    getValueWithDefault(mElementsRemovedIn, superClassName, Integer.MAX_VALUE);
            if (superClassRemovedIn > apiLevel) {
                ApiClass superClass = info.getClass(superClassName);
                if (superClass != null) {
                    int i = superClass.getMemberRemovedInInternal(name, info);
                    if (i != -1) {
                        int tmp = Math.min(superClassRemovedIn, i);
                        if (tmp > apiLevel) {
                            apiLevel = tmp;
                        }
                    }
                }
            }
        }

        return apiLevel;
    }

    private int getValueWithDefault(
            @Nullable Map<String, Integer> map, @NonNull String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Integer value = map.get(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns the API level when a method was added, or 0 if it doesn't exist. This goes through
     * the super class and interfaces to find method only present there.
     *
     * @param methodSignature the method signature
     * @param info the information about the rest of the API
     */
    int getMethod(String methodSignature, Api<? extends ApiClassBase> info) {
        // The method can come from this class or from a super class.
        // The value can never be lower than this introduction of this class.
        // When looking at super classes, it can never be lower than when the super class became
        // a super class of this class.
        // Look at all the values and take the lowest.
        // For instance:
        // This class A is introduced in 5 with super class B.
        // In 10, the super class changes to C.
        // Looking for foo() we get the following:
        // Present in A in API 15
        // Present in B in API 11
        // Present in C in API 7.
        // The answer is 10, which is when C became the super class.
        int apiLevel = getValueWithDefault(mMethods, methodSignature, 0);
        // Constructors aren't inherited.
        if (!methodSignature.startsWith(CONSTRUCTOR_NAME)) {
            // Look at the super classes and interfaces.
            for (Pair<String, Integer> pair : Iterables.concat(mSuperClasses, mInterfaces)) {
                ApiClassBase superClass = info.getClass(pair.getFirst());
                if (superClass instanceof ApiClass) {
                    int i = ((ApiClass) superClass).getMethod(methodSignature, info);
                    if (i != 0) {
                        int tmp = Math.max(pair.getSecond(), i);
                        if (apiLevel == 0 || tmp < apiLevel) {
                            apiLevel = tmp;
                        }
                    }
                }
            }
        }

        return apiLevel;
    }

    void addField(String name, int since, int deprecatedIn, int removedIn) {
        mFields.put(name, since);
        addToDeprecated(name, deprecatedIn);
        addToRemoved(name, removedIn);
    }

    void addMethod(String name, int since, int deprecatedIn, int removedIn) {
        // Strip off the method type at the end to ensure that the code which
        // produces inherited methods doesn't get confused and end up multiple entries.
        // For example, java/nio/Buffer has the method "array()Ljava/lang/Object;",
        // and the subclass java/nio/ByteBuffer has the method "array()[B". We want
        // the lookup on mMethods to associate the ByteBuffer array method to be
        // considered overriding the Buffer method.
        int index = name.indexOf(')');
        if (index != -1) {
            name = name.substring(0, index + 1);
        }
        mMethods.put(name, since);
        addToDeprecated(name, deprecatedIn);
        addToRemoved(name, removedIn);
    }

    void addSuperClass(String superClass, int since, int removedIn) {
        addToArray(mSuperClasses, superClass, since);
        addToRemoved(superClass, removedIn);
    }

    void addInterface(String interfaceClass, int since, int removedIn) {
        addToArray(mInterfaces, interfaceClass, since);
        addToRemoved(interfaceClass, removedIn);
    }

    static void addToArray(List<Pair<String, Integer>> list, String name, int value) {
        // check if we already have that name (at a lower level)
        for (Pair<String, Integer> pair : list) {
            if (name.equals(pair.getFirst())) {
                assert false;
                return;
            }
        }

        list.add(Pair.of(name, value));
    }

    private void addToDeprecated(String name, int deprecatedIn) {
        if (deprecatedIn > 0) {
            if (mMembersDeprecatedIn == null) {
                mMembersDeprecatedIn = new HashMap<>();
            }
            mMembersDeprecatedIn.put(name, deprecatedIn);
        }
    }

    private void addToRemoved(String name, int removedIn) {
        if (removedIn > 0) {
            if (mElementsRemovedIn == null) {
                mElementsRemovedIn = new HashMap<>();
            }
            mElementsRemovedIn.put(name, removedIn);
        }
    }

    /**
     * Returns the set of all methods, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a set containing all the members fields
     */
    Set<String> getAllMethods(Api info) {
        Set<String> members = new HashSet<>(100);
        addAllMethods(info, members, true /*includeConstructors*/);

        return members;
    }

    @NonNull
    List<Pair<String, Integer>> getInterfaces() {
        return mInterfaces;
    }

    @NonNull
    List<Pair<String, Integer>> getSuperClasses() {
        return mSuperClasses;
    }

    private void addAllMethods(Api<ApiClass> info, Set<String> set, boolean includeConstructors) {
        if (includeConstructors) {
            set.addAll(mMethods.keySet());
        } else {
            for (String method : mMethods.keySet()) {
                if (!method.startsWith(CONSTRUCTOR_NAME)) {
                    set.add(method);
                }
            }
        }

        for (Pair<String, Integer> superClass : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClass cls = info.getClass(superClass.getFirst());
            if (cls != null) {
                cls.addAllMethods(info, set, false);
            }
        }
    }

    /**
     * Returns the set of all fields, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a set containing all the fields
     */
    Set<String> getAllFields(Api info) {
        Set<String> members = new HashSet<>(100);
        addAllFields(info, members);

        return members;
    }

    private void addAllFields(Api<ApiClass> info, Set<String> set) {
        set.addAll(mFields.keySet());

        for (Pair<String, Integer> superClass : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClass cls = info.getClass(superClass.getFirst());
            assert cls != null : superClass.getSecond();
            cls.addAllFields(info, set);
        }
    }

    private void addRemovedFields(Api<ApiClass> info, Set<String> set) {
        set.addAll(mFields.keySet());

        for (Pair<String, Integer> superClass : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClass cls = info.getClass(superClass.getFirst());
            assert cls != null : superClass.getSecond();
            cls.addAllFields(info, set);
        }
    }

    /**
     * Returns all removed fields, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a collection containing all removed fields
     */
    @NonNull
    Collection<ApiMember> getAllRemovedFields(Api info) {
        Set<String> fields = getAllFields(info);
        if (fields.isEmpty()) {
            return Collections.emptySet();
        }

        List<ApiMember> removedFields = new ArrayList<>();
        for (String fieldName : fields) {
            int removedIn = getMemberRemovedIn(fieldName, info);
            if (removedIn > 0) {
                int since = getField(fieldName, info);
                assert since > 0;
                int deprecatedIn = getMemberDeprecatedIn(fieldName, info);
                removedFields.add(new ApiMember(fieldName, since, deprecatedIn, removedIn));
            }
        }
        return removedFields;
    }

    /**
     * Returns all removed fields, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a collection containing all removed fields
     */
    @NonNull
    Collection<ApiMember> getAllRemovedMethods(Api info) {
        Set<String> methods = getAllMethods(info);
        if (methods.isEmpty()) {
            return Collections.emptySet();
        }

        List<ApiMember> removedMethods = new ArrayList<>();
        for (String methodSignature : methods) {
            int removedIn = getMemberRemovedIn(methodSignature, info);
            if (removedIn > 0) {
                int since = getMethod(methodSignature, info);
                assert since > 0;
                int deprecatedIn = getMemberDeprecatedIn(methodSignature, info);
                removedMethods.add(new ApiMember(methodSignature, since, deprecatedIn, removedIn));
            }
        }
        return removedMethods;
    }

    /**
     * Extends the basic format by additionally writing:
     *
     * <pre>
     * 1. One, two or three bytes representing the API levels when the class was introduced,
     * deprecated, and removed, respectively. The third byte is present only if the class
     * was removed. The second byte is present only if the class was deprecated or removed.
     *
     * 2. The number of new super classes and interfaces [1 byte]. This counts only
     * super classes and interfaces added after the original API level of the class.
     *
     * 3. For each super class or interface counted in g,
     *     a. The index of the class [a 3-byte integer]
     *     b. The API level the class/interface was added [1 byte]
     * </pre>
     */
    @Override
    void writeSuperInterfaces(Api<? extends ApiClassBase> info, ByteBuffer buffer) {
        int since = getSince();
        int deprecatedIn = getDeprecatedIn();
        int removedIn = getRemovedIn();
        writeSinceDeprecatedInRemovedIn(buffer, since, deprecatedIn, removedIn);

        List<Pair<String, Integer>> interfaces = getInterfaces();
        int count = 0;
        if (!interfaces.isEmpty()) {
            for (Pair<String, Integer> pair : interfaces) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    count++;
                }
            }
        }
        List<Pair<String, Integer>> supers = getSuperClasses();
        if (!supers.isEmpty()) {
            for (Pair<String, Integer> pair : supers) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    count++;
                }
            }
        }
        buffer.put((byte) count);
        if (count > 0) {
            for (Pair<String, Integer> pair : supers) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    ApiClassBase superClass = info.getClasses().get(pair.getFirst());
                    assert superClass != null : this;
                    ApiDatabase.put3ByteInt(buffer, superClass.index);
                    buffer.put((byte) api);
                }
            }
            for (Pair<String, Integer> pair : interfaces) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    ApiClassBase interfaceClass = info.getClasses().get(pair.getFirst());
                    assert interfaceClass != null : this;
                    ApiDatabase.put3ByteInt(buffer, interfaceClass.index);
                    buffer.put((byte) api);
                }
            }
        }
    }

    @Override
    int computeExtraStorageNeeded(Api<? extends ApiClassBase> info) {
        int estimatedSize = 0;

        Set<String> allMethods = getAllMethods(info);
        Set<String> allFields = getAllFields(info);
        members = new ArrayList<>(allMethods.size() + allFields.size());
        members.addAll(allMethods);
        members.addAll(allFields);

        estimatedSize += 2 + 4 * (getInterfaces().size());
        if (getSuperClasses().size() > 1) {
            estimatedSize += 2 + 4 * (getSuperClasses().size());
        }

        // Only include classes that have one or more members requiring version 2 or higher:
        Collections.sort(members);
        for (String member : members) {
            estimatedSize += member.length();
            estimatedSize += 16;
        }
        return estimatedSize;
    }

    /**
     * Writes one, two or three bytes representing the API levels when the member was introduced,
     * deprecated, and removed, respectively. The third byte is present only if the member was
     * removed. The second byte is present only if the member was deprecated or removed.
     */
    @Override
    void writeMemberData(Api<? extends ApiClassBase> info, String member, ByteBuffer buffer) {
        int since;
        if (member.indexOf('(') >= 0) {
            since = getMethod(member, info);
        } else {
            since = getField(member, info);
        }
        if (since == 0) {
            assert false : getName() + ':' + member;
            since = 1;
        }

        int deprecatedIn = getMemberDeprecatedIn(member, info);
        assert deprecatedIn >= 0 : "Invalid deprecatedIn " + deprecatedIn + " for " + member;
        int removedIn = getMemberRemovedIn(member, info);
        assert removedIn >= 0 : "Invalid removedIn " + removedIn + " for " + member;

        byte[] signature = member.getBytes(StandardCharsets.UTF_8);
        for (byte b : signature) {
            // Make sure all signatures are really just simple ASCII.
            assert b == (b & 0x7f) : member;
            buffer.put(b);
            // Skip types on methods
            if (b == (byte) ')') {
                break;
            }
        }
        buffer.put((byte) 0);
        writeSinceDeprecatedInRemovedIn(buffer, since, deprecatedIn, removedIn);
    }

    private static void writeSinceDeprecatedInRemovedIn(
            ByteBuffer buffer, int since, int deprecatedIn, int removedIn) {
        assert since != 0 && since == (since & ApiDatabase.API_MASK); // Must fit in 7 bits.
        assert deprecatedIn == (deprecatedIn & ApiDatabase.API_MASK); // Must fit in 7 bits.
        assert removedIn == (removedIn & ApiDatabase.API_MASK); // Must fit in 7 bits.

        boolean isDeprecated = deprecatedIn > 0;
        boolean isRemoved = removedIn > 0;
        // Writing "since" and, optionally, "deprecatedIn" and "removedIn".
        if (isDeprecated || isRemoved) {
            since |= ApiDatabase.HAS_EXTRA_BYTE_FLAG;
        }
        buffer.put((byte) since);
        if (isDeprecated || isRemoved) {
            if (isRemoved) {
                deprecatedIn |= ApiDatabase.HAS_EXTRA_BYTE_FLAG;
            }
            buffer.put((byte) deprecatedIn);
            if (isRemoved) {
                buffer.put((byte) removedIn);
            }
        }
    }

    /* This code can be used to scan through all the fields and look for fields
       that have moved to a higher class:
            Field android/view/MotionEvent#CREATOR has api=1 but parent android/view/InputEvent provides it as 9
            Field android/provider/ContactsContract$CommonDataKinds$Organization#PHONETIC_NAME has api=5 but parent android/provider/ContactsContract$ContactNameColumns provides it as 11
            Field android/widget/ListView#CHOICE_MODE_MULTIPLE has api=1 but parent android/widget/AbsListView provides it as 11
            Field android/widget/ListView#CHOICE_MODE_NONE has api=1 but parent android/widget/AbsListView provides it as 11
            Field android/widget/ListView#CHOICE_MODE_SINGLE has api=1 but parent android/widget/AbsListView provides it as 11
            Field android/view/KeyEvent#CREATOR has api=1 but parent android/view/InputEvent provides it as 9
       This is used for example in the ApiDetector to filter out warnings which result
       when people follow Eclipse's advice to replace
            ListView.CHOICE_MODE_MULTIPLE
       references with
            AbsListView.CHOICE_MODE_MULTIPLE
       since the latter has API=11 and the former has API=1; since the constant is unchanged
       between the two, and the literal is copied into the class, using the AbsListView
       reference works.
    public void checkFields(Api info) {
        fieldLoop:
        for (String field : mFields.keySet()) {
            Integer since = getField(field, info);
            if (since == null || since == Integer.MAX_VALUE) {
                continue;
            }

            for (Pair<String, Integer> superClass : mSuperClasses) {
                ApiClass cls = info.getClass(superClass.getFirst());
                assert cls != null : superClass.getSecond();
                if (cls != null) {
                    Integer superSince = cls.getField(field, info);
                    if (superSince == Integer.MAX_VALUE) {
                        continue;
                    }

                    if (superSince != null && superSince > since) {
                        String declaredIn = cls.findFieldDeclaration(info, field);
                        System.out.println("Field " + getName() + "#" + field + " has api="
                                + since + " but parent " + declaredIn + " provides it as "
                                + superSince);
                        continue fieldLoop;
                    }
                }
            }

            // Get methods from implemented interfaces as well;
            for (Pair<String, Integer> superClass : mInterfaces) {
                ApiClass cls = info.getClass(superClass.getFirst());
                assert cls != null : superClass.getSecond();
                if (cls != null) {
                    Integer superSince = cls.getField(field, info);
                    if (superSince == Integer.MAX_VALUE) {
                        continue;
                    }
                    if (superSince != null && superSince > since) {
                        String declaredIn = cls.findFieldDeclaration(info, field);
                        System.out.println("Field " + getName() + "#" + field + " has api="
                                + since + " but parent " + declaredIn + " provides it as "
                                + superSince);
                        continue fieldLoop;
                    }
                }
            }
        }
    }

    private String findFieldDeclaration(Api info, String name) {
        if (mFields.containsKey(name)) {
            return getName();
        }
        for (Pair<String, Integer> superClass : mSuperClasses) {
            ApiClass cls = info.getClass(superClass.getFirst());
            assert cls != null : superClass.getSecond();
            if (cls != null) {
                String declaredIn = cls.findFieldDeclaration(info, name);
                if (declaredIn != null) {
                    return declaredIn;
                }
            }
        }

        // Get methods from implemented interfaces as well;
        for (Pair<String, Integer> superClass : mInterfaces) {
            ApiClass cls = info.getClass(superClass.getFirst());
            assert cls != null : superClass.getSecond();
            if (cls != null) {
                String declaredIn = cls.findFieldDeclaration(info, name);
                if (declaredIn != null) {
                    return declaredIn;
                }
            }
        }

        return null;
    }
    */
}
