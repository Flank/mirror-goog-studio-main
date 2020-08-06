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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class SslCertificateSocketFactoryDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return SslCertificateSocketFactoryDetector()
    }

    fun test() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.net.SSLCertificateSocketFactory;
                import java.net.InetAddress;
                import java.net.Inet4Address;
                import java.net.Inet6Address;
                import javax.net.ssl.HttpsURLConnection;

                public class SSLCertificateSocketFactoryTest {
                    public void foo() {
                        byte[] ipv4 = new byte[4];
                        byte[] ipv6 = new byte[16];
                        InetAddress inet = Inet4Address.getByAddress(ipv4);
                        Inet4Address inet4 = (Inet4Address) Inet4Address.getByAddress(ipv4);
                        Inet6Address inet6 = Inet6Address.getByAddress(null, ipv6, 0);

                        SSLCertificateSocketFactory sf = (SSLCertificateSocketFactory)
                        SSLCertificateSocketFactory.getDefault(0);
                        sf.createSocket("www.google.com", 80); // ok
                        sf.createSocket("www.google.com", 80, inet, 2000); // ok
                        sf.createSocket(inet, 80);
                        sf.createSocket(inet4, 80);
                        sf.createSocket(inet6, 80);
                        sf.createSocket(inet, 80, inet, 2000);
                        sf.createSocket(inet4, 80, inet, 2000);
                        sf.createSocket(inet6, 80, inet, 2000);

                        HttpsURLConnection.setDefaultSSLSocketFactory(
                                SSLCertificateSocketFactory.getInsecure(-1,null));
                    }
                }
                """
            ).indented()
        )
            .allowCompilationErrors()
            .run().expect(
                """
            src/test/pkg/SSLCertificateSocketFactoryTest.java:21: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]
                    sf.createSocket(inet, 80);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SSLCertificateSocketFactoryTest.java:22: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]
                    sf.createSocket(inet4, 80);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SSLCertificateSocketFactoryTest.java:23: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]
                    sf.createSocket(inet6, 80);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SSLCertificateSocketFactoryTest.java:24: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]
                    sf.createSocket(inet, 80, inet, 2000);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SSLCertificateSocketFactoryTest.java:25: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]
                    sf.createSocket(inet4, 80, inet, 2000);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SSLCertificateSocketFactoryTest.java:26: Warning: Use of SSLCertificateSocketFactory.createSocket() with an InetAddress parameter can cause insecure network traffic due to trusting arbitrary hostnames in TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryCreateSocket]
                    sf.createSocket(inet6, 80, inet, 2000);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SSLCertificateSocketFactoryTest.java:29: Warning: Use of SSLCertificateSocketFactory.getInsecure() can cause insecure network traffic due to trusting arbitrary TLS/SSL certificates presented by peers [SSLCertificateSocketFactoryGetInsecure]
                            SSLCertificateSocketFactory.getInsecure(-1,null));
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 7 warnings
            """
            )
    }
}
