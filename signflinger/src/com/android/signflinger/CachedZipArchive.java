/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.signflinger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Cache the content of BytesSource for fast retrieval. This is useful in the context of V1 signing
 * where a file is added to a zip only to be later extracted from it when the V1 Signing Engine
 * decides to inspect its content.
 *
 * <p>There is not point keeping an entry in the cache once it has been requested by the V1 sign
 * engine. Therefore, a cache entry is removed upon a cache hit.
 *
 * <p>Since the V1 signing engine ask to inspect an entry right away (as soon as "add" is called),
 * and since each cache entry is deleted when the content has been retrieved, this class has little
 * impact on the memory usage.
 */
public class CachedZipArchive extends ZipArchive {

    private HashMap<String, ByteBuffer> cache = new HashMap<>();

    public CachedZipArchive(@NonNull File file) throws IOException {
        super(file);
    }

    @Nullable
    @Override
    public ByteBuffer getContent(@NonNull String name) throws IOException {
        ByteBuffer byteBuffer = cache.remove(name);
        if (byteBuffer != null) {
            return byteBuffer;
        }
        return super.getContent(name);
    }

    @Override
    public void add(@NonNull BytesSource source) throws IOException {
        super.add(source);
        cache.put(source.getName(), source.getBuffer());
    }

    @Override
    public void delete(@NonNull String name) {
        super.delete(name);
        cache.remove(name);
    }
}
