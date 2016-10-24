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

    public void testLLegacyLombokJavaLintRule() throws Exception {
        @SuppressWarnings("all") // sample code
        File projectDir = getProjectDir(null,
                classpath(),
                manifest().minSdk(1),
                base64gzip("lint.jar", ""
                        + "H4sIAAAAAAAAAI1WezgTeh+fy2xTaJp6c0m5TDQjIYWGMYYxM5dJHMZEzV0R"
                        + "vee4tCG3yK0sl6hXCLnrOl1cNsQ0iYiG5DK3UHR6nafnfU7pPe97Pr/n+8fv"
                        + "eT6f7/f7x/eGtxISFgUAwGDAuJklFrAJQcA3QDYNZ0Y0VsfaYDSEAPgfiI0x"
                        + "3V7oTYL5psG+J+KMbbAYM3siEodZwrFZ1lbqyB4xK3W1LnZ3DUGz7/Db8cV2"
                        + "9sEeMUtcNwKperuWYNHdbduN7WT3W2E7O/uXKpYRSCTuInYpeCFYYEvQrdn5"
                        + "BAT4nPH2Dfgpu++J4t8TvbypP7OtdjTESgABgPPbAYC9P7FxEabeod7k0IBg"
                        + "JPmMR0hIieOJczsdYL/T4LlK1qrFvYRi6SoTS1/QPOYaIcmX+Uu65y0Y04ph"
                        + "bcOJzj0aNKEkeWH+3PyEbVqzo8eUC/LCx9W198FN3Up1yVQSxICdx5phNTyq"
                        + "WT2+NIl6tCHyVu2hrSdsW9CQxRial74GjpCywpoGHU45EbyhF+0B42UPBkUp"
                        + "tHrKluh1BCFUAkH58CpB9LPbifcg5aNukqpRWU8vGOAXmhI1YKbbaEHnsExi"
                        + "z9gd//MO+YMOJwPnhJvJkSaVWkwE88Kb8sHz+8HSiLJ9ugGR9c54zbRcV/sd"
                        + "7Utq9prEWkNH/0apicqrUvSzBDP/3K6rU4PBbwaS4nH6+MBm53f3JzSvZUhk"
                        + "T5dwPVuljuo5kBWcKvXrlcMsrc8s+g2E7pkq0cp4Rqdw+mRazi4XyYHTbf9x"
                        + "tvOw2ROV9clVCLuhP2XqZSh6rd6JhUzr7bkp96qI6sqk3FQR9c8oMVgVjkTc"
                        + "NaPfk/rd4ohQ4aRgNbwTuvFgkDoNrMSagq9Fktqd2LNrBvUtgnPapDBQnOO8"
                        + "wcSk3GGwV3m/RKzXUd0bHqAXchyJHFQPNVmBWKFLADp9Fk+pvTb7Gyxpr6Sq"
                        + "3M3de7+sbSM77ljVf0GMMrObA6jTOwayLxHUMqaM0TncG+kkCc7BWlR173zc"
                        + "rFaMkZFvtrZqhPBSyPVWM3DiK8PB8f6hL8XZTU7+euIk1LnG7RCLVK3mCJPX"
                        + "yPhipszDZ/6vtTj0syHnMZQCWn8QLZESGyI1Lf2QVlpUxBRcUEgIv8jhVkVL"
                        + "D8AjxK5V6SSN1tLo4cVvnxadxDWEK85qcaycrL/SREBIhlupQ0G5jEZdr9QI"
                        + "RWaqYLT55ojlUnLtKy/fE1z9y0YjcnMvc/IpqdNEzH7gSRM1AzHZMKmRU/PG"
                        + "M26zpYWNYfQmmOspO+6X/YbGwIsvjewJaxWJrdIuazbMEre8mVf4wAr5BUh7"
                        + "nUfuXYVSUmgFxf0YWSpviGoTMafHK2OkxArowOmQufbrZqS8AU591kEJrGak"
                        + "0TRnHXHm9rTNIbUDPitcqjM+o5KYRGe0RneIprB0+f3SWkKLeYm69+1ERWmd"
                        + "Xf7tMC58m+OJsBj9dNUZPi9D4n1gm001hwrjfY6/falkzP9J4qfyrgstS+iA"
                        + "fpN1ytBkA6oBJNzAJadM4ph+QwHQ3Z/GMzNLNvwi2jVh7OW4X8zXgSqvlAOq"
                        + "4EtU4+G+yKbM4MKoMidb/as837sqEZdYGfLNjBOsihEELJubvLEo99ljz7A9"
                        + "xMrtiU4OgrYt8jT8PqYG1IZ1jFx0O7X6KGf8sKcF0z33i3kfx8adjn+f2j68"
                        + "WFPWs+IsMVv8nPSZJQvU8Tn/nFdn8ugW9CrHG8MPcpnSVtPGaByPXbwCN58X"
                        + "eUAendg3XNzA3WV0lR09MTm/C4sdZ8mNO39oC9m+sYya7TF8XZ2yYnpgSXeW"
                        + "0XoxJXPSSx/zbqhvWVch5HJ1m3z8ceayM++c6WQRuYf6VAHlGYU4lkZogP4a"
                        + "09e3S5JDMMg91iyGG9sLNswXV2dEW2bRT/nEaboqjdTwSEWkAr5iHCpWddHy"
                        + "uH4jb0byJro8JGe/S0pcDavRJMvhPvrXj3xoGJ/jL9sxkuOwIHCnxy/8tysU"
                        + "tOdEy+qZA+lfUkdJSmiQj31sKbm0UF0WtXewJcH9cnO+9HDL6e7AxaOYf2LG"
                        + "fP/1kDVyqEVTfjGM4ZlWLbZjwswaIxnnA+X3hic6/66Vtbb7x1GqR9axSxMA"
                        + "AB5sTl/F/zJKsSEhYd4Ebx/fkNDgiG/zNJOAs5U5BJNFp1N7L8bBOo4UEiQg"
                        + "Fs5XyEXLAKKJi5HC4zcDzpcUWzH3hi03DnYA+4RQBajbHwapwmBhycj5Gp/J"
                        + "TpnUjUk+PxIwUuVmDM3vyHnmCsw/xPdgPlFAl95ICj5Ja6dM5xVWnQalF0of"
                        + "UEzttXN/fquoElIK0oV9dn2qpp/KjLmHh6AV48uTa4lZEry2xSdlsUrY8YQk"
                        + "4thYmS76aDqyNf5kQH1M4Zpq+IcrEwJ2Wia2ZphGbfhLvgaYsa9WEcneV+/3"
                        + "mFF3bNRlj/m7vO2M5KZGooH8i7l0lxIfjt4VFJESvJLLqLAeNIdcpyvLs5wX"
                        + "qj6QxcRoL49otDx2X4D6KZcaNIbPOIffaTxSz8YbvP/YtbJHdyRIn9bUeups"
                        + "BdcUz8WX2ydoabeRMpSfz9d9QpmqWz9NXUcr1LsLp2oceHGv7SGkCrHTBfXC"
                        + "l652w2WoEbie+RXJcSkUjSqG568U6KhAx+yVd+bAMfAqw13z/ppB9PZpUAJB"
                        + "vP96beS7Fb+uUqhuBv/D183dKCAIE/rrpfsNOwCzxoDv9/5W2dYD4T8ojlb9"
                        + "i3Ph7we+/n1l/S+Z+A+y1p8Kcqt26yXwZ9If/+9dsNXX1lb405eRyN9qDLwV"
                        + "UOQPgcjmS9h0BAX/8fs37IxfaLAJAAA="),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "public class Test {\n"
                        + "    public void foo(int var) {\n"
                        + "        foo(5);\n"
                        + "    }\n"
                        + "}")
        );

        File lintJar = new File(projectDir, "lint.jar");
        assertTrue(lintJar.getPath(), lintJar.isFile());

        myGlobalJars = Collections.singletonList(lintJar);
        assertEquals(""
                        + "src/test/pkg/Test.java:5: Warning: Did you mean bar? [MyId]\n"
                        + "        foo(5);\n"
                        + "        ~~~~~~\n"
                        + "0 errors, 1 warnings\n",
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
