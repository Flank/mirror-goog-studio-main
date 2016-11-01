/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.ide.common.resources;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import static org.junit.Assert.*;

public class ResourceMapTest {
  @Test
  public void testFlattenKey() {
    assertEquals(null, ResourceValueMap.flattenKey(null));
    assertEquals("", ResourceValueMap.flattenKey(""));
    assertEquals("my_key_test", ResourceValueMap.flattenKey("my.key:test"));
    assertEquals("my_key_test", ResourceValueMap.flattenKey("my_key_test"));
    assertEquals("_key_test", ResourceValueMap.flattenKey(".key_test"));
    assertEquals("_key_test_", ResourceValueMap.flattenKey(".key_test:"));
    assertEquals("_key test_", ResourceValueMap.flattenKey("-key test:"));
  }

  @Test
  public void testResourceMap() {
    ResourceValueMap resourceValueMap = ResourceValueMap.create();

    // Check null key
    ResourceValue value1 = new ResourceValue(ResourceType.STRING, "test1", false);
    ResourceValue value2 = new ResourceValue(ResourceType.STYLE, "test1", false);
    ResourceValue value3 = new ResourceValue(ResourceType.STRING, "test1", false);
    ResourceValue value4 = new ResourceValue(ResourceType.INTEGER, "test1", false);

    assertNull(resourceValueMap.put("test_key", value1));
    assertNull(resourceValueMap.put("key2", value2));
    assertNull(resourceValueMap.put("key3", value3));
    assertNull(resourceValueMap.put("key4", value4));

    assertEquals(value1, resourceValueMap.get("test_key"));
    assertEquals(value1, resourceValueMap.get("test.key"));
    assertEquals(value1, resourceValueMap.get("test-key"));
    assertEquals(value1, resourceValueMap.get("test:key"));
    assertEquals(ImmutableSet.of("test_key", "key2", "key3", "key4"), resourceValueMap.keySet());
    assertEquals(value1, resourceValueMap.remove("test_key"));
    assertFalse(resourceValueMap.keySet().contains("test_key"));

    // Check key replace
    assertEquals(value2, resourceValueMap.put("key2", value1));
    assertEquals(value1, resourceValueMap.put("key2", value2));

    // Check key flattening
    resourceValueMap.put("test:key", value1);
    assertFalse(resourceValueMap.keySet().contains("test_key"));
    assertTrue(resourceValueMap.keySet().contains("test:key"));
    assertTrue(resourceValueMap.containsKey("test_key"));
    assertTrue(resourceValueMap.containsKey("test:key"));
    assertEquals(value1, resourceValueMap.get("test:key"));
    assertEquals(value1, resourceValueMap.get("test_key"));
    assertEquals(value1, resourceValueMap.get("test-key"));
    assertEquals(value1, resourceValueMap.put("test-key", value2));
  }
}