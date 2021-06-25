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

package com.android.build.gradle.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.testutils.MockLog;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link SdkLibDataFactory} */
public class SdkLibDataFactoryTest {

    MockLog log;
    Properties properties;
    SdkLibDataFactory factory;
    SdkLibDataFactory.Environment environment =
            new SdkLibDataFactory.Environment() {
                @Nullable
                @Override
                public String getSystemProperty(@NotNull SystemProperty property) {
                    return properties.getProperty(property.getKey());
                }
            };

    @Before
    public void setup() {
        log = new MockLog();
        properties = new Properties();
        properties.clear();
        factory = new SdkLibDataFactory(true, null, log);
    }

    @Test
    public void createProxy_https_priority() throws Exception {
        InetAddress loopback = InetAddress.getByName(null);
        properties.setProperty("https.proxyHost", "localhost");
        properties.setProperty("http.proxyHost", "8.8.8.8");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress(loopback, 443), proxy.address());
    }

    @Test
    public void createProxy_localhost_https_defaultPort() throws Exception {
        InetAddress loopback = InetAddress.getByName(null);
        properties.setProperty("https.proxyHost", "localhost");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress(loopback, 443), proxy.address());
        assertTrue(log.getMessages().isEmpty());
    }

    @Test
    public void createProxy_localhost_port_override() throws Exception {
        InetAddress loopback = InetAddress.getByName(null);
        properties.setProperty("https.proxyHost", "localhost");
        properties.setProperty("https.proxyPort", "123");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress(loopback, 123), proxy.address());
        assertTrue(log.getMessages().isEmpty());
    }

    @Test
    public void createProxy_localhost_bad_port_override() throws Exception {
        InetAddress loopback = InetAddress.getByName(null);
        properties.setProperty("https.proxyHost", "localhost");
        properties.setProperty("https.proxyPort", "bad");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress(loopback, 443), proxy.address());
        assertTrue(log.getMessages().get(0).contains("bad"));
        assertTrue(log.getMessages().get(0).contains("443"));
    }

    @Test
    public void createProxy_remote_http() {
        properties.setProperty("http.proxyHost", "8.8.8.8");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress("8.8.8.8", 80), proxy.address());
    }

    @Test
    public void createProxy_remote_http_port_override() {
        properties.setProperty("http.proxyHost", "8.8.8.8");
        properties.setProperty("http.proxyPort", "123");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress("8.8.8.8", 123), proxy.address());
    }

    @Test
    public void createProxy_remote_http_bad_port_override() {
        properties.setProperty("http.proxyHost", "8.8.8.8");
        properties.setProperty("http.proxyPort", "bad");
        Proxy proxy = factory.createProxy(environment, log);
        assertEquals(new InetSocketAddress("8.8.8.8", 80), proxy.address());
        assertTrue(log.getMessages().get(0).contains("bad"));
        assertTrue(log.getMessages().get(0).contains("80"));
    }
}
