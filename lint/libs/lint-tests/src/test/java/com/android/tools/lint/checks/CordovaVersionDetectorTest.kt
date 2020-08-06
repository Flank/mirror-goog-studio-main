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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class CordovaVersionDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return CordovaVersionDetector()
    }

    /** Test to check that a valid version does not cause lint to report an issue. */
    fun testValidCordovaVersionInJS() {
        lint().files(
            source(
                "assets/www/cordova.js",
                """
                ;(function() {
                var PLATFORM_VERSION_BUILD_LABEL = '7.1.1';
                })();
                """
            ).indented()
        ).run().expectClean()
    }

    /** Check that vulnerable versions in cordova.js are flagged */
    fun testVulnerableCordovaVersionInJS() {
        lint().files(
            source(
                "assets/www/cordova.js",
                """
                ;(function() {
                var CORDOVA_JS_BUILD_LABEL = '3.7.1-dev';
                })();
                """
            ).indented()
        ).run().expect(
            """
            assets/www/cordova.js: Warning: You are using a vulnerable version of Cordova: 3.7.1 [VulnerableCordovaVersion]
            0 errors, 1 warnings
            """
        )

        lint().files(
            source(
                "assets/www/cordova.js",
                """
                ;(function() {
                var PLATFORM_VERSION_BUILD_LABEL = '4.0.0';
                })();
                """
            ).indented()
        ).run().expect(
            """
            assets/www/cordova.js: Warning: You are using a vulnerable version of Cordova: 4.0.0 [VulnerableCordovaVersion]
            0 errors, 1 warnings
            """
        )
    }

    /** Test to ensure that cordova.js.X.X.android is also detected. */
    fun testVulnerableCordovaVersionInJS2() {
        /** Test to ensure that cordova.js.X.X.android is also detected. */
        lint().files(
            source(
                "assets/www/cordova.js.4.0.android",
                """
                ;(function() {
                var CORDOVA_JS_BUILD_LABEL = '4.0.0';})();
                """
            ).indented()
        ).run().expect(
            """
            assets/www/cordova.js.4.0.android: Warning: You are using a vulnerable version of Cordova: 4.0.0 [VulnerableCordovaVersion]
            0 errors, 1 warnings
            """
        )
    }

    /** Check whether the detector picks up the version from the Device class. */
    fun testVulnerableCordovaVersionInClasses() {
        lint().files(
            base64gzip(
                "bin/classes/org/apache/cordova/Device.class",
                "" +
                    "yv66vgAAADIAFAoABQAPCAAQCQAEABEHABIHABMBAA5jb3Jkb3ZhVmVyc2lv" +
                    "bgEAEkxqYXZhL2xhbmcvU3RyaW5nOwEABjxpbml0PgEAAygpVgEABENvZGUB" +
                    "AA9MaW5lTnVtYmVyVGFibGUBAAg8Y2xpbml0PgEAClNvdXJjZUZpbGUBAAtE" +
                    "ZXZpY2UuamF2YQwACAAJAQAFMi43LjAMAAYABwEAGW9yZy9hcGFjaGUvY29y" +
                    "ZG92YS9EZXZpY2UBABBqYXZhL2xhbmcvT2JqZWN0ACEABAAFAAAAAQAJAAYA" +
                    "BwAAAAIAAQAIAAkAAQAKAAAAHQABAAEAAAAFKrcAAbEAAAABAAsAAAAGAAEA" +
                    "AAAEAAgADAAJAAEACgAAAB4AAQAAAAAABhICswADsQAAAAEACwAAAAYAAQAA" +
                    "AAUAAQANAAAAAgAO"
            )
        ).run().expect(
            """
            bin/classes/org/apache/cordova/Device.class: Warning: You are using a vulnerable version of Cordova: 2.7.0 [VulnerableCordovaVersion]
            0 errors, 1 warnings
            """
        )
    }

    /**
     * In the presence of both a class as well as the js, detecting the version in the .class wins.
     * In the real world, this won't happen since cordova versions >= 3.x.x have this version
     * declared only in the JS.
     */
    fun testVulnerableVersionInBothJsAndClasses() {
        lint().files(
            base64gzip(
                "bin/classes/org/apache/cordova/Device.class",
                "" +
                    "yv66vgAAADIAFAoABQAPCAAQCQAEABEHABIHABMBAA5jb3Jkb3ZhVmVyc2lv" +
                    "bgEAEkxqYXZhL2xhbmcvU3RyaW5nOwEABjxpbml0PgEAAygpVgEABENvZGUB" +
                    "AA9MaW5lTnVtYmVyVGFibGUBAAg8Y2xpbml0PgEAClNvdXJjZUZpbGUBAAtE" +
                    "ZXZpY2UuamF2YQwACAAJAQAFMi43LjAMAAYABwEAGW9yZy9hcGFjaGUvY29y" +
                    "ZG92YS9EZXZpY2UBABBqYXZhL2xhbmcvT2JqZWN0ACEABAAFAAAAAQAJAAYA" +
                    "BwAAAAIAAQAIAAkAAQAKAAAAHQABAAEAAAAFKrcAAbEAAAABAAsAAAAGAAEA" +
                    "AAAEAAgADAAJAAEACgAAAB4AAQAAAAAABhICswADsQAAAAEACwAAAAYAAQAA" +
                    "AAUAAQANAAAAAgAO"
            ),
            source(
                "assets/www/cordova.js.4.0.android",
                """
                ;(function() {
                var CORDOVA_JS_BUILD_LABEL = '4.0.1';
                })();
                """
            ).indented()
        ).run().expect(
            """
            bin/classes/org/apache/cordova/Device.class: Warning: You are using a vulnerable version of Cordova: 2.7.0 [VulnerableCordovaVersion]
            0 errors, 1 warnings
            """
        )
    }

    /** Ensure that the version string is read from the CordovaWebView.class. */
    fun testVulnerableVersionInWebView() {
        lint().files(
            base64gzip(
                "bin/classes/org/apache/cordova/CordovaWebView.class",
                "" +
                    "yv66vgAAADIAEgoAAwAOBwAPBwAQAQAPQ09SRE9WQV9WRVJTSU9OAQASTGph" +
                    "dmEvbGFuZy9TdHJpbmc7AQANQ29uc3RhbnRWYWx1ZQgAEQEABjxpbml0PgEA" +
                    "AygpVgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAApTb3VyY2VGaWxlAQAT" +
                    "Q29yZG92YVdlYlZpZXcuamF2YQwACAAJAQAhb3JnL2FwYWNoZS9jb3Jkb3Zh" +
                    "L0NvcmRvdmFXZWJWaWV3AQAQamF2YS9sYW5nL09iamVjdAEABTQuMC4wACEA" +
                    "AgADAAAAAQAZAAQABQABAAYAAAACAAcAAQABAAgACQABAAoAAAAdAAEAAQAA" +
                    "AAUqtwABsQAAAAEACwAAAAYAAQAAAAMAAQAMAAAAAgAN"
            )
        ).run().expect(
            """
            bin/classes/org/apache/cordova/CordovaWebView.class: Warning: You are using a vulnerable version of Cordova: 4.0.0 [VulnerableCordovaVersion]
            0 errors, 1 warnings
            """
        )
    }
}
