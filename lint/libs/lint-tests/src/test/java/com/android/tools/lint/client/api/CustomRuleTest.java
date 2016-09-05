/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class CustomRuleTest extends AbstractCheckTest {
    private List<File> myGlobalJars = Collections.emptyList();
    private List<File> myProjectJars = Collections.emptyList();

    public void test() throws Exception {
        File projectDir = getProjectDir(null,
                classpath(),
                manifest().minSdk(1),
                mAppcompat,
                mAppCompatTest,
                mAppCompatTest2
        );

        File lintJar = new File(projectDir, "lint.jar");
        assertTrue(lintJar.getPath(), lintJar.isFile());

        myProjectJars = Collections.singletonList(lintJar);
        assertEquals(""
                + "src/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [AppCompatMethod]\n"
                + "        getActionBar();                    // ERROR\n"
                + "        ~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [AppCompatMethod]\n"
                + "        startActionMode(null);             // ERROR\n"
                + "        ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [AppCompatMethod]\n"
                + "        requestWindowFeature(0);           // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [AppCompatMethod]\n"
                + "        setProgressBarVisibility(true);    // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [AppCompatMethod]\n"
                + "        setProgressBarIndeterminate(true);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [AppCompatMethod]\n"
                + "        setProgressBarIndeterminateVisibility(true);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n",
                checkLint(Collections.singletonList(projectDir)));
    }

    public void test2() throws Exception {
        File projectDir = getProjectDir(null,
                classpath(),
                manifest().minSdk(1),
                mAppcompat,
                mAppCompatTest,
                mAppCompatTest2
        );

        File lintJar = new File(projectDir, "lint.jar");
        assertTrue(lintJar.getPath(), lintJar.isFile());

        myGlobalJars = Collections.singletonList(lintJar);
        assertEquals(""
                + "src/test/pkg/AppCompatTest.java:7: Warning: Should use getSupportActionBar instead of getActionBar name [AppCompatMethod]\n"
                + "        getActionBar();                    // ERROR\n"
                + "        ~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:10: Warning: Should use startSupportActionMode instead of startActionMode name [AppCompatMethod]\n"
                + "        startActionMode(null);             // ERROR\n"
                + "        ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:13: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [AppCompatMethod]\n"
                + "        requestWindowFeature(0);           // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [AppCompatMethod]\n"
                + "        setProgressBarVisibility(true);    // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:17: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [AppCompatMethod]\n"
                + "        setProgressBarIndeterminate(true);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:18: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [AppCompatMethod]\n"
                + "        setProgressBarIndeterminateVisibility(true);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n",
                checkLint(Collections.singletonList(projectDir)));
    }

    @Override
    protected TestLintClient createClient() {
        return new TestLintClient() {
            @NonNull
            @Override
            public List<File> findGlobalRuleJars() {
                return myGlobalJars;
            }

            @NonNull
            @Override
            public List<File> findRuleJars(@NonNull Project project) {
                return myProjectJars;
            }
        };
    }

    @Override
    protected boolean isEnabled(Issue issue) {
        // Allow other issues than the one returned by getDetector below
        return true;
    }

    @Override
    protected Detector getDetector() {
        return new HardcodedValuesDetector();
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mAppCompatTest = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import android.support.v7.app.ActionBarActivity;\n"
            + "\n"
            + "public class AppCompatTest extends ActionBarActivity {\n"
            + "    public void test() {\n"
            + "        getActionBar();                    // ERROR\n"
            + "        getSupportActionBar();             // OK\n"
            + "\n"
            + "        startActionMode(null);             // ERROR\n"
            + "        startSupportActionMode(null);      // OK\n"
            + "\n"
            + "        requestWindowFeature(0);           // ERROR\n"
            + "        supportRequestWindowFeature(0);    // OK\n"
            + "\n"
            + "        setProgressBarVisibility(true);    // ERROR\n"
            + "        setProgressBarIndeterminate(true);\n"
            + "        setProgressBarIndeterminateVisibility(true);\n"
            + "\n"
            + "        setSupportProgressBarVisibility(true); // OK\n"
            + "        setSupportProgressBarIndeterminate(true);\n"
            + "        setSupportProgressBarIndeterminateVisibility(true);\n"
            + "    }\n"
            + "}\n");

    private TestFile mAppCompatTest2 = base64gzip("bin/classes/test/pkg/AppCompatTest.class", ""
            + "H4sIAAAAAAAAAJVU21ITQRA9E0ICcRTkjqAogmwisuIF1AASolRRFS1LqKSK"
            + "t0kyhSNhd92dhPJb/ApfYpUPfoAfZdmzhFzKxOg+nLmd7j7d07M/f33/AeAR"
            + "XscRYZjSMtC2d3piZzwv6555Qh/RThxRBks4Zd9VZTuoep7ra7u2aQvPszMl"
            + "rVxnT/hmUlP6M0NsSzlK7zAMWMk8QzTrlmUCAxjmGESMYSSnHPm2elaU/pEo"
            + "ViTDWM4tiUpe+MqsG5tR/UEFDDO57qrShkFjAgyjHNcxxsBPpG4KYpi1krlL"
            + "2R1a08ZogmMSUwzjZHR4kVSb7VKbba+UQzczHLO4QVkFWlx6eEMZM2xbTQ81"
            + "Jc/t1tlSVlQqRVE6TSd7UULf8xw3cYsuJvTdIfIixKHVTeQ/ROvNDgPf5riD"
            + "RaqRLz9VqcgF5ZTd830pdNU3V2MdJI8Nb4lj2fDmGv7ed6Eb3gqHhSQVK5D6"
            + "ne+e+DIIqIB5FaiiqoRdE7WOk3nDvc+xarhzndwDpyy19M+UI3Toc43DNrzl"
            + "v/BaAYzFOqdWJ4uFoHnjXcUY7hOOp4a72JX7h5hNjmeGvtqf3p504tCt+iW5"
            + "r8JX0NHfax9FTVBLHziO9LMVEQQyiGOb6vMfNx7HS0qhvwHDUMtkj+6/X+PG"
            + "8YphugeLeoceP8wXoRk9esI4rWwaGY2DqW8Y+hoeJwhjF5u4Qsgbc46rNDJc"
            + "axp/CZ0BmVQdIwWC8UKK1TEd4lwhNVDHQoh3C6loHfcMpAw8MPDQwGMDG63A"
            + "G2GQOIUbonUCoyRhivbmKewirVbot7KKMaxjgriTSNPpLqbbhGYaQiN4HuIL"
            + "4oAsItjCDnZjw8TImvE3oCvXeGsFAAA=");

    public static final String LINT_JAR_BASE64_GZIP = ""
            + "H4sIAAAAAAAAAJ2Xd1CTXRbGAyIfRWpCb1KlBZEuHQMECL0jTXoNhISmIEUB"
            + "RYrSW8DGCiJNxQ9pIVQpgVCWJkhoQXqTpiIsrjv7yTfi7Ox95/xxZ87z3DP3"
            + "vHfmd4xhZ8hAAIrjjy+UGQL4aYEAZAADLXMNsK6h9kUDDUNdbS0zcwkD7cMu"
            + "AOCTAa5bHwaW6KeBgUV7cfjXppL/lJ4ibnXixPpp9Azw4hIiJVWmOjiYSBde"
            + "v19MAizccVG0X0x6pJukpA8lLozoxYN1xDfFJSQMYp9/JM4QSQHGsD8oCHhK"
            + "uNnx0dbHYXxcGNW/C/l7YZTH8d/CTk+jOA4nP1ekv5frb7Lof8pCBSEQ/sjA"
            + "32QDf5EdLP8bAeuvBb5efj+O+fXVX/yNSgOBgPjDEU6Bmm6Bbi6B/kgJF18n"
            + "FCrf6nMIxxDTITksN0WKt1eANb3yXHt7MNpaKHpglNc8hWheJi7IPqbtuxu6"
            + "7Wsglcsgza+0Rg62X9SD2erAbGv7G1qN0wavRHr2euEXI5uO+lcxKaUd8eeQ"
            + "KM6e+q9rdcshhIZP+KODzxWAZBfpOBMcdJNVj487ayMm5oKgsuC0FyU1I7NL"
            + "30tQbPQrMmYIXaaLtYYgOZDF+S1FwrqF4CgjJ6UsJV4HIRjKGM4QFHFN9p5G"
            + "6eaZrH18bYyRzn5hjJHufiDoYdNrE+U7+Hv76Lsfn+4rY+hbABjWlqwNN0rH"
            + "aTyLZrhF6whZjHSuXB6zZK4s2vtKdXe5SyYQM2vt0CwFVzNxV5UOf2ewICud"
            + "slmvmAdCLI8FBvDmiLpv1cRKd94CCIZpOA4BM/0qBGQIsqtMM+/zkL74EXik"
            + "YfWSPjaiLFu8qrdnrkhnXSNJIF9sDs88kNvuBsa5yZBUEzR759iVQkJGc/RY"
            + "BmNz9CVsBOJvxm5/HquytMsuSjo4aA5UtEGD+7Tn0r3yDr1IVFr4UaRXRIr0"
            + "tVe7c+W6h17AcOAlGPMsRiHOr2exvHPEwLmjIpubbtDybv2Nsvm3Ec4709ro"
            + "1DdXu54PB0aWsElSRVU1h5e1W9tgX06nlPNxVg72qyrq8YEHp76Y1boRt0X5"
            + "qUGMoNbatTxLPp8bqgFQWVTb8LdaWOZkJLDleV4aKpdHShT3tkOLOhT+VmzZ"
            + "NHPterrFQ/utC6+T61IRmRJ9u+y2egsOstN1ibLZSNAE12X3bMeALvtKV188"
            + "7Whos+YWYdCqQ6vfM/v81P5az3yyv43wo6PbDefnzM/4kE2MxEJjmPjvLhpn"
            + "YUwYFH3kUIuPDC4L7dlZvg68PbnrjfGzQ/f7kFAtyvHq50uVCiW75JqF0MjB"
            + "kVG7H8iwlwqC2OhmW/gwIrO2oSbq60k69OdRhWyYxVGPwFdOPBal4wI4Mt7a"
            + "FpfDyyM2ASIHrVIeN+ZTvXIOa6OaVm3Z8u58KMuN0RruDdm/ub9AixiTDpr/"
            + "/OmZffDwMxuAKwMhc5OrXmCmTmqLlUVy4vH29lVwv1RBY2mqKhuhZU+rXnyW"
            + "yY558wybWusLvgiB2Xf78Ykou1ESh1yay/DevbO9/k/lnRH3R5wJvCCoEnKF"
            + "NeWQzv2jkw8CpNgKxUZZBixZ2pSF6XOFX8OaGdTCcxws892CNhTKODy9YddV"
            + "sU8SRzbM2N5JCs167NckOk5lpe8xR7jnXmtEDfNHFX8qhzSW2Qf5hHnn4jbN"
            + "IXvZReUW2d3m9rJLBevmQ1pdCQyVF0uS3ps0xL2EJY7bTr6ywIZ9YnIHJr3Y"
            + "EhNwWS70zoGVr5XzEF0l0IdhhhmyZ28s1Zr5p3X5JHd7UpOyeCj5WVSbv3Of"
            + "LKxdeStaV2jlw6xUIhGsjt3ocK6LatsdeNRw6Y2OvTqRJZHdrqCEqMDSoC4x"
            + "sv4cA/S0p3YelgzUb12Wioh1TNhPSiTMhAT6BA351ORYuH9VlFfH6ozkQ1vX"
            + "3Q5IIufeHMXdFZOS23gWBRXFOObpPMi0cL8sO58sK5gcgGJ3l3Hj8WlX1RBx"
            + "z2oXjRhK3WQ+1JRcb98GQ8jQEzRf1REfveP8/qh+HN4i1fKQJGmvKaW8TuQD"
            + "oSbcSShnNbhrbKmpYWTccyBoJ2iAvV9l0mRxniOCL8zFySB4AXfbfgfAqC9Y"
            + "bls4/ec0LXJxkud90xbgDnc0FsfIvCxb4jooJplQNjCV4b+dAZfKAwKyLmPG"
            + "53Ub0vScu2StakY38z3Yl0xiJqaUe6x6gGlKLUX8ajuN2klyg70eebTJgewq"
            + "/N31a52TaVyGK+ia1Su7D4JVqeWWKmTqzdSMJK5jXnAc7nj9QywbHj8/b/qu"
            + "6BVwwy4EZe1CS8jPzFNZoY+rkRBT1vrm1RbwPGVVl0gdG1iS/TTADmsd8sn6"
            + "0/ZXgHOYxkASkRxuhjZiRuZMjPnJwvPRK7vNkgvOhV1GbmnbQZfyO5+xVJkf"
            + "ct5U4fR8sVplX+GdmLVnrFRP+sAweGrWaVXv5gOrW7enpVQBFCm0C9/UROq6"
            + "YO4hTau0r8e/2GTJvtppqoFezPLgzHJbcYNin3JVRK8Rpx2+RHCffUzHNxPO"
            + "+IRuD6JzDe1J1/cVTNH/JXRcUX8ZCF+mKOPxtU2oiiiGg/u/QODLwrr0++KG"
            + "jRfikbcKSFVaoJNxCMFrOY8IrCrR/IURoLRtIflsBNX9PjOsl/J7kOQwiP49"
            + "Fuxf1Qslvfpk1bc4aJdfh5FFLaygONy/WH5L4oaeip6OWGx6pSWVGEMZyESz"
            + "TEA01TI1vTiY6SVHm9qoUESxyKE+No3vPWiABhgb0rEh3F/RBvF3wmsqcFvO"
            + "Gaw0JNbNTL94uyNFm+zn7KhcH++A+Xz/CBNCpPL4oMbAIKQpCLpPvyd2V6Qr"
            + "Zl/RPQORgIgCPkvXSUA8ABoKOycgOC5si5km9L2j/JrhmXZetNrobsDmOqvl"
            + "GkVjQujzC4YOaXrI/b4YmdrolqebfUJribBmobUZtiXKK5X6USRtpgJZe2kt"
            + "AcjQKDvuJhkiGHI9j3dqjuLc9a6hy08Kigik8481oAxMuOY2zuH4LWXuFmrh"
            + "aUHl5KxNkEPboEwJeMq/tRNyIBDrf6ZBwAUN0Ir2Jc9rzG+D3iaz48/noTZG"
            + "0fBAM0vn/KVdNe76ht+JVDRhT57VOvwAoQynIJxPaPeqUEcHtB6eNYkZvh85"
            + "S6+yWJF1uMrFG9vGfDVtFFjMZbJmuWZiTpyk2d7TlkMQHCWNFaOaaCaO5OLK"
            + "TSWhcgFzBpwYxvNriq5fOprHAHyowoezaBaCMIY7/pDuO3ap0baaKxzjkgLw"
            + "B3b9Gkpk/hco0UWhgtxM3Ty8UIHI6z/IJMPUwGhcnf4mta7+rfH0qvCMAbrn"
            + "d8gEMpujIfQWGX8ypg7pxSAJCmIh/e4jr0Z0vllVZVly7pMcCLeWAh8xajGp"
            + "4kILCLi8m5MJ3a9RANvQD+pMRsuhcZ/JmStZr4lgpSBwKzbiLuuz8Xz+OD08"
            + "rSBE2lQ7QkT7TA7XvWihQqYx6wX+FBFHI+y9r+XIuB6dbCf3oQcftXLf4zbT"
            + "uMozAg4gubsrwkx+anjq6+eBGXUr0KzHBR2XtFfLgi8oMl+4xWLoxc3QBvwi"
            + "eqmZia2zvhEymuSsNDNS0ns1//7ZOwOdo2OJCeabpo/qoIPsN21dXx6UHgyL"
            + "HU16t8BiinkpNpRbqRzIrGk2GStY5biD1ckj8BEvLVMHeoKrufKP9MLR6KTH"
            + "tYT1yK+tlYcADtZ5f5UVm7L4R6W8RzzELoGrij71b875j7tUv5GkPFJUEHLe"
            + "6+Vp5jBf0P7sQYWr0w6IK4F3JSKDVuVH8VuofOfbr+4z4UzJmi/FlzyQAke/"
            + "a904M2lATtYoufPHYJE8v7fgxMELlewqeVgvsHm+q3qY6Xv3R0e5b6BJAIA9"
            + "0u/dJyEFAf7q/89A/n0eOLlOmw6+u1ABTof3v1bOSZQ/XUZxQoYF/Iz2p6vo"
            + "T6jmf/ED/04NPKEGkZyC/qcbsJ4w0Pq1wX9GgZP3/vOLvHjCJew3LqeMBn83"
            + "/7nhMifM6an+vyduDDtL/qNLFAD5Y1fac993/wK1/pChWw4AAA==";

    private TestFile mAppcompat = base64gzip("lint.jar", LINT_JAR_BASE64_GZIP);
}
