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

package com.android.tools.profiler;

import com.android.tools.profiler.io.IoAdapter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * The profiler transform added by Studio. This transform can read input configuration arguments
 * from a property file stored at:
 *
 * <p>System.getProperty("android.profiler.properties").
 */
@SuppressWarnings("unused")
public final class ProfilerTransform implements BiConsumer<InputStream, OutputStream> {

    private static final Properties PROPERTIES = loadTransformProperties();
    private static final boolean OKHTTP_PROFILING_ENABLED =
        "true".equals(PROPERTIES.getProperty("android.profiler.okhttp.enabled"));
    private static final boolean IO_PROFILING_ENABLED = "true"
        .equals(PROPERTIES.getProperty("android.profiler.io.enabled"));

    // Flag that controls whether to track network request body, should be the same as
    // StudioFlags.PROFILER_NETWORK_REQUEST_PAYLOAD.getId().
    private static final String NETWORK_REQUEST_PAYLOAD = "profiler.network.request.payload";
    public static final boolean NETWORK_REQUEST_PAYLOAD_ENABLED =
            "true".equals(PROPERTIES.getProperty(NETWORK_REQUEST_PAYLOAD));

    private static Logger getLog() {
        return Logger.getLogger(ProfilerTransform.class.getName());
    }

    @Override
    public void accept(InputStream in, OutputStream out) {
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = writer;
        visitor = new InitializerAdapter(visitor);
        visitor = new HttpURLAdapter(visitor);
        if (OKHTTP_PROFILING_ENABLED) {
            visitor = new OkHttpAdapter(visitor);
        }
        if (IO_PROFILING_ENABLED) {
            visitor = IoAdapter.addIoAdapters(visitor);
        }

        try {
            ClassReader cr = new ClassReader(in);
            cr.accept(visitor, 0);
            out.write(writer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties loadTransformProperties() {
        Properties properties = new Properties();
        String propertiesFile = System.getProperty("android.profiler.properties");
        if (propertiesFile != null && !propertiesFile.trim().isEmpty()) {
            try (InputStream inputStream = new FileInputStream(propertiesFile)) {
                properties.load(inputStream);
            } catch (FileNotFoundException e) {
                getLog().warning("Profiler properties file cannot be found.");
            } catch (IOException e) {
                getLog().warning("Profiler properties file is not read properly.");
            }
        }
        return properties;
    }
}
