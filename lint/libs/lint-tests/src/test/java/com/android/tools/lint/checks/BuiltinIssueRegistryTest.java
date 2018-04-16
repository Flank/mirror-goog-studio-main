/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestIssueRegistry;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

public class BuiltinIssueRegistryTest extends TestCase {
    public void testNoListResize() {
        BuiltinIssueRegistry registry = new TestIssueRegistry();
        List<Issue> issues = registry.getIssues();
        int issueCount = issues.size();
        assertTrue(
                Integer.toString(issueCount), BuiltinIssueRegistry.INITIAL_CAPACITY >= issueCount);
    }

    @SuppressWarnings("unchecked")
    public void testCapacities() throws IllegalAccessException {
        TestIssueRegistry registry = new TestIssueRegistry();
        for (Scope scope : Scope.values()) {
            EnumSet<Scope> scopeSet = EnumSet.of(scope);
            checkCapacity(registry, scopeSet);
        }

        // Also check the commonly used combinations
        for (Field field : Scope.class.getDeclaredFields()) {
            if (field.getType().isAssignableFrom(EnumSet.class)) {
                checkCapacity(registry, (EnumSet<Scope>) field.get(null));
            }
        }
    }

    public void testUnique() {
        // Check that ids are unique
        Set<String> ids = new HashSet<>();
        Set<Category> categories = new HashSet<>();
        for (Issue issue : new TestIssueRegistry().getIssues()) {
            String id = issue.getId();
            assertTrue("Duplicate id " + id, !ids.contains(id));
            ids.add(id);
            categories.add(issue.getCategory());
        }

        // Also make sure that none of the category names clash with
        // the id's since we want to let you enable/disable checks
        // with category names too
        for (Category category : categories) {
            String id = category.getName();
            if (ids.contains(id)) {
                assertTrue("Category id clashes with issue id " + id, !ids.contains(id));
            }
        }

        // Might as well make sure category id's are unique too
        ids.clear();
        for (Category category : categories) {
            String id = category.getName();
            if (ids.contains(id)) {
                assertTrue("Duplicate category name " + id, !ids.contains(id));
            }
        }
    }

    private static void checkCapacity(TestIssueRegistry registry, EnumSet<Scope> scopeSet) {
        List<Issue> issuesForScope = registry.getIssuesForScope(scopeSet);
        int requiredSize = issuesForScope.size();
        int capacity = registry.getIssueCapacity(scopeSet);
        if (requiredSize > capacity) {
            fail(
                    "For Scope set "
                            + scopeSet
                            + ": capacity "
                            + capacity
                            + " < actual "
                            + requiredSize);
        }
    }
}
