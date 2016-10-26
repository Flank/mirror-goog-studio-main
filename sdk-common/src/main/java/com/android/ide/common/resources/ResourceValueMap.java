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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link Map} that treats all the keys as resources names. This class takes care of the key flattening done by AAPT where
 * '.', '-' and ':' are all replaced with '_' and will be able to find resources with those characters in the name no matter the format.
 * Because of that, for {@link ResourceValueMap} keys like 'my.key', 'my_key' and 'my-key' are exactly the same key.
 * <p>
 * {@link ResourceValueMap} will keep the original names given to the resources so calls to {@link #keySet()} will return the names without
 * modifications.
 */
public class ResourceValueMap extends ForwardingMap<String, ResourceValue> {
  private final Map<String, ResourceValue> myDelegate;
  private final Set<String> myKeys;

  private ResourceValueMap(@NonNull Map<String, ResourceValue> delegate, @NonNull Set<String> keySet) {
    myDelegate = delegate;
    myKeys = keySet;
  }

  @NonNull
  public static ResourceValueMap createWithExpectedSize(int expectedSize) {
    return new ResourceValueMap(Maps.newHashMapWithExpectedSize(expectedSize), Sets.newHashSetWithExpectedSize(expectedSize));
  }

  @NonNull
  public static ResourceValueMap create() {
    return new ResourceValueMap(Maps.newHashMap(), Sets.newHashSet());
  }

  /**
   * Method that replicates the key flattening done by AAPT. If the passed key contains '.', '-' or ':', they will be replaced by '_' and a
   * a new {@link String} returned. If none of those characters are contained, the same {@link String} passed as input will be returned.
   */
  @VisibleForTesting
  @Nullable
  static String flattenKey(@Nullable String key) {
    if (key == null) {
      return null;
    }

    for (int i = 0, n = key.length(); i < n; i++) {
      char c = key.charAt(i);
      if (c == ':' || c == '.' || c == '-') {
        // We found one instance that we need to replace. Allocate the buffer, copy everything up to this point and start replacing.
        char[] buffer = new char[key.length()];
        key.getChars(0, i, buffer, 0);
        buffer[i] = '_';
        for (int j = i + 1; j < n; j++) {
          c = key.charAt(j);
          buffer[j] = (c == ':' || c == '.' || c == '-') ? '_' : c;
        }
        return new String(buffer);
      }
    }

    return key;
  }

  @Override
  @NonNull
  protected Map<String, ResourceValue> delegate() {
    return myDelegate;
  }

  @Override
  public ResourceValue put(@Nullable String key, @NonNull ResourceValue value) {
    assert key != null : "ResourceValueMap does not support null keys";
    myKeys.add(key);

    return super.put(flattenKey(key), value);
  }

  @Override
  public ResourceValue get(@Nullable Object key) {
    assert key != null : "ResourceValueMap does not support null keys";
    return super.get(flattenKey((String)key));
  }

  @Override
  public boolean containsKey(Object key) {
    assert key != null : "ResourceValueMap does not support null keys";
    return super.containsKey(flattenKey((String)key));
  }

  @Override
  public ResourceValue remove(@Nullable Object key) {
    assert key != null : "ResourceValueMap does not support null keys";
    myKeys.remove((String)key);
    return super.remove(flattenKey((String)key));
  }

  @Override
  public Set<String> keySet() {
    return myKeys;
  }
}
