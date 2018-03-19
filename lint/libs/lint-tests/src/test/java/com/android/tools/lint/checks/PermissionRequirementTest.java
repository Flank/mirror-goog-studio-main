/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.tools.lint.checks.AnnotationDetector.PERMISSION_ANNOTATION;
import static com.android.tools.lint.checks.PermissionRequirement.REVOCABLE_PERMISSION_NAMES;
import static com.android.tools.lint.checks.PermissionRequirement.isRevocableSystemPermission;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.checks.PermissionHolder.SetPermissionLookup;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaTokenType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.ULiteralExpression;

public class PermissionRequirementTest extends TestCase {
    public static class MockValue {
        @NonNull public final String name;
        @Nullable public final Object value;

        public MockValue(@NonNull String name, @Nullable Object value) {
            this.name = name;
            this.value = value;
        }
    }

    private static UAnnotation createUAnnotation(
            @NonNull String name, @NonNull MockValue... values) {
        UAnnotation annotation = mock(UAnnotation.class);
        when(annotation.getQualifiedName()).thenReturn(name);

        for (MockValue value : values) {
            ULiteralExpression literal = mock(ULiteralExpression.class);
            when(literal.getValue()).thenReturn(value.value);

            when(annotation.findAttributeValue(value.name)).thenReturn(literal);
            when(annotation.findDeclaredAttributeValue(value.name)).thenReturn(literal);
        }

        return annotation;
    }

    public void testSingle() {
        MockValue values = new MockValue("value", "android.permission.ACCESS_FINE_LOCATION");
        Set<String> emptySet = Collections.emptySet();
        Set<String> fineSet = Collections.singleton("android.permission.ACCESS_FINE_LOCATION");
        UAnnotation annotation = createUAnnotation(PERMISSION_ANNOTATION.defaultName(), values);
        PermissionRequirement req = PermissionRequirement.create(annotation);
        assertTrue(req.isRevocable(new SetPermissionLookup(emptySet)));

        assertFalse(req.isSatisfied(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(Collections.singleton(""))));
        assertTrue(req.isSatisfied(new SetPermissionLookup(fineSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(fineSet, req.getMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(emptySet, req.getMissingPermissions(new SetPermissionLookup(fineSet)));
        assertEquals(fineSet, req.getRevocablePermissions(new SetPermissionLookup(emptySet)));
        assertNull(req.getOperator());
        assertFalse(req.getChildren().iterator().hasNext());
    }

    public void testAny() {
        MockValue values =
                new MockValue(
                        "anyOf",
                        new String[] {
                            "android.permission.ACCESS_FINE_LOCATION",
                            "android.permission.ACCESS_COARSE_LOCATION"
                        });
        Set<String> emptySet = Collections.emptySet();
        Set<String> fineSet = Collections.singleton("android.permission.ACCESS_FINE_LOCATION");
        Set<String> coarseSet = Collections.singleton("android.permission.ACCESS_COARSE_LOCATION");
        Set<String> bothSet =
                Sets.newHashSet(
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION");

        UAnnotation annotation = createUAnnotation(PERMISSION_ANNOTATION.defaultName(), values);
        PermissionRequirement req = PermissionRequirement.create(annotation);
        assertTrue(req.isRevocable(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(Collections.singleton(""))));
        assertTrue(req.isSatisfied(new SetPermissionLookup(fineSet)));
        assertTrue(req.isSatisfied(new SetPermissionLookup(coarseSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(bothSet, req.getMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(bothSet, req.getRevocablePermissions(new SetPermissionLookup(emptySet)));
        assertSame(JavaTokenType.OROR, req.getOperator());
    }

    public void testAll() {
        MockValue values =
                new MockValue(
                        "allOf",
                        new String[] {
                            "android.permission.ACCESS_FINE_LOCATION",
                            "android.permission.ACCESS_COARSE_LOCATION"
                        });
        Set<String> emptySet = Collections.emptySet();
        Set<String> fineSet = Collections.singleton("android.permission.ACCESS_FINE_LOCATION");
        Set<String> coarseSet = Collections.singleton("android.permission.ACCESS_COARSE_LOCATION");
        Set<String> bothSet =
                Sets.newHashSet(
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION");

        UAnnotation annotation = createUAnnotation(PERMISSION_ANNOTATION.defaultName(), values);
        PermissionRequirement req = PermissionRequirement.create(annotation);
        assertTrue(req.isRevocable(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(Collections.singleton(""))));
        assertFalse(req.isSatisfied(new SetPermissionLookup(fineSet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(coarseSet)));
        assertTrue(req.isSatisfied(new SetPermissionLookup(bothSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION and android.permission.ACCESS_COARSE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(bothSet, req.getMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(
                "android.permission.ACCESS_COARSE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(fineSet)));
        assertEquals(coarseSet, req.getMissingPermissions(new SetPermissionLookup(fineSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(coarseSet)));
        assertEquals(fineSet, req.getMissingPermissions(new SetPermissionLookup(coarseSet)));
        assertEquals(bothSet, req.getRevocablePermissions(new SetPermissionLookup(emptySet)));
        assertSame(JavaTokenType.ANDAND, req.getOperator());
    }

    public void testSingleAsArray() {
        // Annotations let you supply a single string to an array method
        MockValue values = new MockValue("allOf", "android.permission.ACCESS_FINE_LOCATION");
        UAnnotation annotation = createUAnnotation(PERMISSION_ANNOTATION.defaultName(), values);
        assertTrue(PermissionRequirement.create(annotation).isSingle());
    }

    public void testRevocable() {
        assertTrue(isRevocableSystemPermission("android.permission.ACCESS_FINE_LOCATION"));
        assertTrue(isRevocableSystemPermission("android.permission.ACCESS_COARSE_LOCATION"));
        assertFalse(isRevocableSystemPermission("android.permission.UNKNOWN_PERMISSION_NAME"));
        assertFalse(isRevocableSystemPermission("android.permission.GET_ACCOUNTS"));
    }

    public void testRevocable2() {
        assertTrue(
                new SetPermissionLookup(
                                Collections.emptySet(),
                                Sets.newHashSet("my.permission1", "my.permission2"))
                        .isRevocable("my.permission2"));
    }

    public void testAppliesTo() {
        UAnnotation annotation;
        PermissionRequirement req;

        // No date range applies to permission
        annotation =
                createUAnnotation(
                        PERMISSION_ANNOTATION.defaultName(),
                        new MockValue("value", "android.permission.AUTHENTICATE_ACCOUNTS"));
        req = PermissionRequirement.create(annotation);
        assertTrue(req.appliesTo(getHolder(15, 1)));
        assertTrue(req.appliesTo(getHolder(15, 19)));
        assertTrue(req.appliesTo(getHolder(15, 23)));
        assertTrue(req.appliesTo(getHolder(22, 23)));
        assertTrue(req.appliesTo(getHolder(23, 23)));

        // Permission discontinued in API 23:
        annotation =
                createUAnnotation(
                        PERMISSION_ANNOTATION.defaultName(),
                        new MockValue("value", "android.permission.AUTHENTICATE_ACCOUNTS"),
                        new MockValue("apis", "..22"));
        req = PermissionRequirement.create(annotation);
        assertTrue(req.appliesTo(getHolder(15, 1)));
        assertTrue(req.appliesTo(getHolder(15, 19)));
        assertTrue(req.appliesTo(getHolder(15, 23)));
        assertTrue(req.appliesTo(getHolder(22, 23)));
        assertFalse(req.appliesTo(getHolder(23, 23)));

        // Permission requirement started in API 23
        annotation =
                createUAnnotation(
                        PERMISSION_ANNOTATION.defaultName(),
                        new MockValue("value", "android.permission.AUTHENTICATE_ACCOUNTS"),
                        new MockValue("apis", "23.."));
        req = PermissionRequirement.create(annotation);
        assertFalse(req.appliesTo(getHolder(15, 1)));
        assertFalse(req.appliesTo(getHolder(1, 19)));
        assertFalse(req.appliesTo(getHolder(15, 22)));
        assertTrue(req.appliesTo(getHolder(22, 23)));
        assertTrue(req.appliesTo(getHolder(23, 30)));

        // Permission requirement applied from API 14 through API 18
        annotation =
                createUAnnotation(
                        PERMISSION_ANNOTATION.defaultName(),
                        new MockValue("value", "android.permission.AUTHENTICATE_ACCOUNTS"),
                        new MockValue("apis", "14..18"));
        req = PermissionRequirement.create(annotation);
        assertFalse(req.appliesTo(getHolder(1, 5)));
        assertTrue(req.appliesTo(getHolder(15, 19)));
    }

    private static PermissionHolder getHolder(int min, int target) {
        return new PermissionHolder.SetPermissionLookup(
                Collections.emptySet(),
                Collections.emptySet(),
                new AndroidVersion(min, null),
                new AndroidVersion(target, null));
    }

    public void testDangerousPermissionsOrder() {
        // The order must be alphabetical to ensure binary search will work correctly
        String prev = null;
        for (String permission : REVOCABLE_PERMISSION_NAMES) {
            assertTrue(prev == null || prev.compareTo(permission) < 0);
            prev = permission;
        }
    }

    public void testDbUpToDate() {
        PermissionDataGenerator generator = new PermissionDataGenerator();
        List<Permission> permissions = generator.getDangerousPermissions(false, 23);
        if (permissions.isEmpty()) {
            return;
        }
        String[] names = generator.getPermissionNames(permissions);
        generator.assertSamePermissions(REVOCABLE_PERMISSION_NAMES, names);
    }
}
