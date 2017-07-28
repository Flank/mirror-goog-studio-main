/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;

/**
 * A {@link Map} that treats all the keys as resources names. This class takes care of the key
 * flattening done by AAPT where '.', '-' and ':' are all replaced with '_' and will be able to find
 * resources with those characters in the name no matter the format. Because of that, for {@link
 * ResourceNameKeyedMap} keys like 'my.key', 'my_key' and 'my-key' are exactly the same key.
 *
 * <p>{@link ResourceNameKeyedMap} will keep the original names given to the resources so calls to
 * {@link #keySet()} will return the names without modifications.
 */
public class ResourceNameKeyedMap<T> extends ForwardingMap<String, T> {
    private final Map<String, T> myDelegate;
    private final Set<String> myKeys;

    protected ResourceNameKeyedMap(@NonNull Map<String, T> delegate, @NonNull Set<String> keySet) {
        myDelegate = delegate;
        myKeys = keySet;
    }

    public ResourceNameKeyedMap() {
        this(Maps.newHashMap(), Sets.newHashSet());
    }

    private static boolean isInvalidResourceNameCharacter(char c) {
        return c == ':' || c == '.' || c == '-';
    }

    /**
     * Method that replicates the key flattening done by AAPT. If the passed key contains '.', '-'
     * or ':', they will be replaced by '_' and a a new {@link String} returned. If none of those
     * characters are contained, the same {@link String} passed as input will be returned.
     */
    @VisibleForTesting
    @Nullable
    static String flattenKey(@Nullable String key) {
        if (key == null) {
            return null;
        }

        for (int i = 0, n = key.length(); i < n; i++) {
            char c = key.charAt(i);
            if (isInvalidResourceNameCharacter(c)) {
                // We found one instance that we need to replace. Allocate the buffer, copy everything up to this point and start replacing.
                char[] buffer = new char[key.length()];
                key.getChars(0, i, buffer, 0);
                buffer[i] = '_';
                for (int j = i + 1; j < n; j++) {
                    c = key.charAt(j);
                    buffer[j] = (isInvalidResourceNameCharacter(c)) ? '_' : c;
                }
                return new String(buffer);
            }
        }

        return key;
    }

    @Override
    @NonNull
    protected Map<String, T> delegate() {
        return myDelegate;
    }

    @Override
    public T put(@Nullable String key, @NonNull T value) {
        assert key != null : "ResourceValueMap does not support null keys";
        myKeys.add(key);

        return super.put(flattenKey(key), value);
    }

    @Override
    public T get(@Nullable Object key) {
        assert key != null : "ResourceValueMap does not support null keys";
        return super.get(flattenKey((String) key));
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        assert key != null : "ResourceValueMap does not support null keys";
        return super.containsKey(flattenKey((String) key));
    }

    @Override
    public T remove(@Nullable Object key) {
        assert key != null : "ResourceValueMap does not support null keys";
        myKeys.remove((String) key);
        return super.remove(flattenKey((String) key));
    }

    @NonNull
    @Override
    public Set<String> keySet() {
        return myKeys;
    }
}
