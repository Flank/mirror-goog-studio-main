/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;

public class WakelockDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WakelockDetector();
    }

    public void test1() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity1.java:15: Warning: Found a wakelock acquire() but no release() calls anywhere [Wakelock]\n"
                        + "        mWakeLock.acquire(); // Never released\n"
                        + "                  ~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.Bundle;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class WakelockActivity1 extends Activity {\n"
                                                + "    private PowerManager.WakeLock mWakeLock;\n"
                                                + "\n"
                                                + "    @Override\n"
                                                + "    public void onCreate(Bundle savedInstanceState) {\n"
                                                + "        super.onCreate(savedInstanceState);\n"
                                                + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "        mWakeLock.acquire(); // Never released\n"
                                                + "    }\n"
                                                + "}\n"),
                                0xc0ebdd47,
                                "test/pkg/WakelockActivity1.class:"
                                        + "H4sIAAAAAAAAAI1SXWsTQRQ9kybZzXbbtGlNNVptYq2bbXHxqQ8RQQNCJVUh"
                                        + "RZ8nm2GZZjMbZyep/VmCpuCD+OyPEu8GlwYV9OXcD+499/P7jy9fARzjyIGL"
                                        + "Rga3LdyxsetgFXct3LOx56CMZoXsloN13Lewb+EBQ2X8jo9ELwlHFg4Y7Nxi"
                                        + "cE+UErob8zQVKUOrx9VQJ3IYJGnwJrkQ+pQrHgm9n6d0GMpPpJLmKcOK137L"
                                        + "UOwmQ8FQ7UklXk3HA6HP+CAmj52orhbckFr3lomfT9UwFp0s2eknUx2KFzJL"
                                        + "qGdFYiryLDRyJs3l40fnfMZd1LDloo4dhp2cJkyUEcoE3Ux+MAylSdaui4fw"
                                        + "lsJ+G4O6PROpcdGG72INmy4OscXQMOQNJqMo+KMFhu2cjE8mQe5n2PvXqhg2"
                                        + "ImH6l6kR477QMxnSjAdeL5spiLmKgr7RUkWd9pLr9eBchIa2vKrExTXTsXfy"
                                        + "t7z/uJbFw/dTqQWa9BYufRBDIZuctALptFrCbbJ2STKSJf8K7OMi8AZheeG0"
                                        + "COkAv0JfoogVki3/8AoFv1aco/QN5VP/qFiz5rA/o+J/QmUO55qnjhKhS7iG"
                                        + "Kj1ng7CJDWri5qKVW6QDDkWvU80qNn8CVqzvjPECAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void test2() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity2.java:13: Warning: Wakelocks should be released in onPause, not onDestroy [Wakelock]\n"
                        + "            mWakeLock.release(); // Should be done in onPause instead\n"
                        + "                      ~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class WakelockActivity2 extends Activity {\n"
                                                + "    private PowerManager.WakeLock mWakeLock;\n"
                                                + "\n"
                                                + "    @Override\n"
                                                + "    protected void onDestroy() {\n"
                                                + "        super.onDestroy();\n"
                                                + "        if (mWakeLock != null && mWakeLock.isHeld()) {\n"
                                                + "            mWakeLock.release(); // Should be done in onPause instead\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    @Override\n"
                                                + "    protected void onPause() {\n"
                                                + "        super.onDestroy();\n"
                                                + "        if (mWakeLock != null && mWakeLock.isHeld()) {\n"
                                                + "            mWakeLock.release(); // OK\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "}\n"),
                                0xc96ea509,
                                "test/pkg/WakelockActivity2.class:"
                                        + "H4sIAAAAAAAAAKVRy0rDQBQ9k9bExNSqffmoWh8LdWHAjQtFkIqotCIoCu6m"
                                        + "7SCxcaYkqdJP8TMEUXDh0oUfJd6pDS5EXLg5987JvefcQ94/Xl4BbGLJgYWi"
                                        + "hpINE5MObExpmLYwY6HMYN9c8LaoqWbbwhzDcPJicA+lFGE14FEkIobFGpet"
                                        + "UPktT0XeiboTYZ1LfiXC5WRli8Hc9qUf7zCkVlbPGdJV1RIM2ZovxXH3piHC"
                                        + "M94IiLGV3BNRHKoeQ+Y05s12nXcG3ywlT3g3os45Vd2wKfZ9TRe1TUA2u83Y"
                                        + "v/Xj3sb6Nb/lLjIYdTGuYRiui3lUXCxglGE6Jguv077yfqwy5JM0vNPxEt7C"
                                        + "IkPlr5wU048ORNDqx7yki0MRCK4vLv2ySweZ9BMABkOfSV2KejqdMEuvWaqM"
                                        + "6tDaM9hDf3CM0OyTJtI64GD0aDA6S6PG2iNSb8jr8oT0PZyvbkhLGH2JCVoG"
                                        + "RghdFMixTCITX/5lInP/080R5km3QLqlb12DSD1YIGPAIV2bIjoY/wQMNijk"
                                        + "mgIAAA=="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void test3() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity3.java:13: Warning: The release() call is not always reached [Wakelock]\n"
                        + "        lock.release(); // Should be in finally block\n"
                        + "             ~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class WakelockActivity3 extends Activity {\n"
                                                + "    void wrongFlow() {\n"
                                                + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "        PowerManager.WakeLock lock =\n"
                                                + "                manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "        lock.acquire();\n"
                                                + "        randomCall();\n"
                                                + "        lock.release(); // Should be in finally block\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    static void randomCall() {\n"
                                                + "        System.out.println(\"test\");\n"
                                                + "    }\n"
                                                + "}\n"),
                                0xd6baedd7,
                                "test/pkg/WakelockActivity3.class:"
                                        + "H4sIAAAAAAAAAIVTXU8TQRQ9Qz92u13oh7QISgUFbAuyKioajIlpQkJSkKQE"
                                        + "n6fbSbN0O1N3p638LH2oxgfjsz/KeLemtIk1brL3a+6cc+7M7s9f374DOMQr"
                                        + "CxncNrBqYs3CEu4YuGti3UICJQv3sBEVN6PofgoPsGVi28IOHhooG6gwJF97"
                                        + "0tNvGGLlyiVDvKZagiFT96Q463ebIrjgTZ8qqWGgZPvYV0MGK+Cypbo17vuU"
                                        + "NFQ/cMWxF3UV3/OO8JXbeetqb+Dp64P9Kz7gNrLIMazQtkB5LcdVUgupnVrk"
                                        + "P2qGRE8NRWCjit2ZNhU651H9lEveFgGpuxChtrEHx8BjG0+Qs1GIzFPkDBzY"
                                        + "eIbn1KWpy8ALm47nJcNalDq9Ttv5SxzD8oSL93rOpM6QbQvduA616DZEMPBc"
                                        + "Gm2nXI9GcXwu205DB55sH1VmSu+aV8LVRwxpKYYRU52YGMxpaJ9IKYKaz8NQ"
                                        + "hAyH5ZN5iP8YfmuCQwwb/+thMLj7oe8FpNsIhC94SFF2hm08G1266tPpF/7o"
                                        + "8JRzTiI0SRG8Szy35pQJsBdlvqR9847kEpv0xWUQPTGw6ObJ5ilbJ8/IJ6pf"
                                        + "wD5RQAxkk+Nikt7lm9Yz2hojX6rmYyPEfyBR343nkyMYp3sjmF+RImdNMUow"
                                        + "yJqwkCJyizDSKMLGKhaJdolWCjfY21gYi0t9Rjpvj7A4hbHGSzlqz1NUpHgB"
                                        + "K9gfrzD6hRJ4hNxvSaO6sXsDAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void test4() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity4.java:10: Warning: The release() call is not always reached [Wakelock]\n"
                        + "        getLock().release(); // Should be in finally block\n"
                        + "                  ~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class WakelockActivity4 extends Activity {\n"
                                                + "    void wrongFlow2() {\n"
                                                + "        getLock().acquire();\n"
                                                + "        randomCall();\n"
                                                + "        getLock().release(); // Should be in finally block\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    private PowerManager.WakeLock mLock;\n"
                                                + "\n"
                                                + "    PowerManager.WakeLock getLock() {\n"
                                                + "        if (mLock == null) {\n"
                                                + "            PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "            mLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "        }\n"
                                                + "\n"
                                                + "        return mLock;\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    static void randomCall() {\n"
                                                + "        System.out.println(\"test\");\n"
                                                + "    }\n"
                                                + "}\n"),
                                0xd4e36181,
                                "test/pkg/WakelockActivity4.class:"
                                        + "H4sIAAAAAAAAAI1Ta08TQRQ9U0q3XRZplzeCAgK2RVlFBBV8YBMSkoIkJfh5"
                                        + "WCbN0u1s3Z1S+VdqtBo/GL+Z+KOMdxYLjZJINnvn3NeZO2d2f/76+g3AGl6a"
                                        + "yGHWRBa3TAxiTqN5jRYyhG4byKdR0MGigcU07pgwcTeDJThp3DNxH8sGHhhY"
                                        + "YeitlwO3ZuAhQ/o1rwntMVjbUoqw5PMoEhHDbJnLozDwjpwgcvaClgh3uORV"
                                        + "Ec51WtYZUhue9NQzhp584YAhWQqOBMNA2ZNit1k/FOE+P/QpYrbCQFa3/KC1"
                                        + "zGBUhTrbci5fuMou/RXF3doOb3TYQmoK6iXu++RUgmboii1PZ0Z0l09dm67y"
                                        + "Tjx1urJ0zE+4hVGMWZjElIVVjW5qs6aNjRGG0c4QbiCVkMop6fWtIqkaeiYL"
                                        + "j/C4q+yvWeng+yJSFp5g3cCGhacgSZKKYgaeW3iBTYYJ7TqNWtX5Z0aGoQ4z"
                                        + "bzScTpxh+n/ikJjcfdP0Qjq7EQpf8IhQlgSunEZK1CsiPPFcCi3ky1oIx+ey"
                                        + "6lRU6MnqeqEr9OrwWLiKtO6TonXBvpbfvqzvCneW7WqLR6FvJGiSosNnhF7g"
                                        + "7BGbIk7B69QweEmYDtXQni+p77ITHGCGPvgc/R8MCX2XhJKE6brJjpM3RSuj"
                                        + "tbf4Gex9XDhBNhUHU/RePy9d/VNqF9tItNHzBckz1HvRZxM9kIaBDPQPlkMf"
                                        + "+ZPxNppjl/IJWmeKH5H6gbGinW4j8x1mubiYtPvasD4hpXPvqCjRRZklyhw9"
                                        + "NpEN4wZFesAmaaeb5/PNx8xA5gP67WttDFyMZcapcSqfIDQdc88Qj86QtGSH"
                                        + "kPsN7hmM0UoEAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void test5() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity5.java:13: Warning: The release() call is not always reached [Wakelock]\n"
                        + "        lock.release(); // Should be in finally block\n"
                        + "             ~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class WakelockActivity5 extends Activity {\n"
                                                + "    void wrongFlow() {\n"
                                                + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "        PowerManager.WakeLock lock = \n"
                                                + "                manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "        lock.acquire();\n"
                                                + "        randomCall();\n"
                                                + "        lock.release(); // Should be in finally block\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    static void randomCall() {\n"
                                                + "        System.out.println(\"test\");\n"
                                                + "    }\n"
                                                + "}\n"),
                                0x651fd32a,
                                "test/pkg/WakelockActivity5.class:"
                                        + "H4sIAAAAAAAAAIVTW08TQRg9Qy/bbhd6kRZBqaCAbUHWG6LBmJgmJCQFSUrw"
                                        + "ebqdbJZuZ+vutJWfpQ/V+GB89kcZv6kpbWKNm+x3m2/OOd/M7s9f374DOMQr"
                                        + "E1ncNrCawpqJJdwxcDeFdRMJlE3cw4YuburofhoPsJXCtokdPDRQMVBlSL72"
                                        + "pKfeMMQq1UuGeD1oC4Zsw5PirN9tifCCt3yqpIdhIN1jPxgymCGX7aBb575P"
                                        + "STPoh4449nRX6T3vCD9wOm8d5Q08dX2wf8UH3EIOeYYV2hYGXtt2AqmEVHZd"
                                        + "+4+KIdELhiK0UMPuTFsQ2ee6fsold0VI6i5EpCzswTbw2MIT5C0UtXmKvIFn"
                                        + "Fp7jgLoUdRl4YdHxvGRY06nd67j2X+IYlidcvNezJ3WGnCtU8zpSotsU4cBz"
                                        + "aLSdSkOPYvtcunZThZ50j6ozpXetK+GoI4aMFEPN1CAmhtQ0tE6kFGHd51Ek"
                                        + "IobDysk8xH8MvzXBIYaN//UwGNz50PdC0m2Ewhc8oig3wzaejS496NPpF//o"
                                        + "8AL7nEQokiJ4l3huzSkTYE9nvqR9847kEpv0xWWhnxiYvnmyBcrWyTPyidoX"
                                        + "sE8UEAPZ5LiYpHf5pvWMtsbIl2uF2AjxH0g0duOF5AjG6d4Iqa9IkzOnGGUY"
                                        + "ZFMwkSZykzAyKMHCKhaJdolWijfY21gYi0t/RqZgjbA4hTHHS3lqL1BUongB"
                                        + "K9gfrzD6hRJ4hPxvBWIRLHsDAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void test6() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity6.java:17: Warning: The release() call is not always reached [Wakelock]\n"
                        + "            lock.release(); // Wrong\n"
                        + "                 ~~~~~~~\n"
                        + "src/test/pkg/WakelockActivity6.java:26: Warning: The release() call is not always reached [Wakelock]\n"
                        + "            lock.release(); // Wrong\n"
                        + "                 ~~~~~~~\n"
                        + "src/test/pkg/WakelockActivity6.java:63: Warning: The release() call is not always reached [Wakelock]\n"
                        + "        lock.release(); // Wrong\n"
                        + "             ~~~~~~~\n"
                        + "0 errors, 3 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.annotation.SuppressLint;\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "import android.os.PowerManager.WakeLock;;\n"
                                                + "\n"
                                                + "public class WakelockActivity6 extends Activity {\n"
                                                + "    void wrongFlow1() {\n"
                                                + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "        PowerManager.WakeLock lock =\n"
                                                + "                manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "        lock.acquire();\n"
                                                + "        if (getTaskId() == 50) {\n"
                                                + "            randomCall();\n"
                                                + "        } else {\n"
                                                + "            lock.release(); // Wrong\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    void wrongFlow2(PowerManager.WakeLock lock) {\n"
                                                + "        lock.acquire();\n"
                                                + "        if (getTaskId() == 50) {\n"
                                                + "            randomCall();\n"
                                                + "        } else {\n"
                                                + "            lock.release(); // Wrong\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    void okFlow1(WakeLock lock) {\n"
                                                + "        lock.acquire();\n"
                                                + "        try {\n"
                                                + "            randomCall();\n"
                                                + "        } catch (Exception e) {\n"
                                                + "            e.printStackTrace();\n"
                                                + "        } finally {\n"
                                                + "            lock.release(); // OK\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    public void checkNullGuard(WakeLock lock) {\n"
                                                + "        lock.acquire();\n"
                                                + "        if (lock != null) {\n"
                                                + "            lock.release(); // OK\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    @SuppressLint(\"Wakelock\")\n"
                                                + "    public void checkDisabled1(PowerManager.WakeLock lock) {\n"
                                                + "        lock.acquire();\n"
                                                + "        randomCall();\n"
                                                + "        lock.release(); // Wrong, but disabled\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    void wrongFlow3(WakeLock lock) {\n"
                                                + "        int id = getTaskId();\n"
                                                + "        lock.acquire();\n"
                                                + "        if (id < 50) {\n"
                                                + "            System.out.println(1);\n"
                                                + "        } else {\n"
                                                + "            System.out.println(2);\n"
                                                + "        }\n"
                                                + "        lock.release(); // Wrong\n"
                                                + "    }\n"
                                                + "\n"
                                                + "    static void randomCall() {\n"
                                                + "        System.out.println(\"test\");\n"
                                                + "    }\n"
                                                + "}\n"),
                                0x6a6b5888,
                                "test/pkg/WakelockActivity6.class:"
                                        + "H4sIAAAAAAAAAIVVXVcTVxTdExImGYavIFiRKqChIUFGQaUaUWkQjYaPGpZ9"
                                        + "6NMwuSuOGWbSmQnob+if6OqLz/Yh7epD26c+9DfZru57IQHUtcys3HPvuefu"
                                        + "vc+5J5N//vv9DwAreG5gDEUdC2lcM5DFog4rjesGUrhhYBJL0rksh5tyeUvH"
                                        + "bQMmVjL4GncM3EUpjXvSruq4r+OBhv57ru/G9zX05edfaEiWg7rQMFx1fbHV"
                                        + "3t8T4a6959FjHIaB39jwgsMbGgZrse00N+2W2tTx8PT+kob0d3ZTVAOnqcGs"
                                        + "+L4Iy54dRSLSkMtXbb8eBm7dCiJrJzgU4abt2w0RXu2eKUkdetBUXDrWNAw5"
                                        + "L4XT3Gp73uO2Hda7jnU3kvR1Crr4vO3H7r6o+Adu5NK55vtBbMdu4JN0psdp"
                                        + "99xWrd1qhSKKmGlc0pA6sL22OJbuKeknKS1zERIj2C/bnsdFLWiHjthwZWUm"
                                        + "uifWnNg9cOM3txdf2Qe2iXMY13C+S+0Efiz82CpL+zomY0tmb+IblE+FfVAV"
                                        + "3siuiGIT63hkYgPjJh7jiYm8nFUkwZgkszzbb1iPXjuiJZMz8RTjOp6ZqGJT"
                                        + "x5aJbewQKlZQ2/hWw6ScW61mw/pIvoZzvXq1WlbXr2H6c1d3Rs3uyzA4PGqe"
                                        + "kYaIa2+iWOzXRHjgOnTN5asnobU4dP1Gaf6Ua3vvlXDkxQz44vAEfyVf+dS5"
                                        + "z/YUO8p2fmi7IakzVLNrR81KXXV9hXuh8IQdycZvETFW7b0b2lLoyCk6lQIP"
                                        + "BW3e3/iREDewdo7OhMLeL3VLcNZNCgXs+byFfEV2+PinCvACM/ztjkF+0tBk"
                                        + "C3Gc4OpLWo02VfgV2jtO2DMc+5Uzze8XvdAW+vgAhUK2r4PkX0hVi8lsfwf6"
                                        + "5kIH6UIHmZGln5D5DcZb6HQNSLyEwptVWAafAQzxzZHFIDGHcBnDyGEU8xR3"
                                        + "gREmEv8ipyOlY1JyXcSlY/anREpIPcWPuYpnuCb41pJjkqtB6h/jG+s8phR+"
                                        + "EokxiXu5h/sjbVIqlLiEk1hvMSFTGjyaG1vSXvvzHbNPQlf6pZ3kqSGF3mVe"
                                        + "ZHYSO8ly68xrAHOMmGWOVxiXY8wco3OMmGPEV0rRAPq2dJjPdEzLos/0hN2l"
                                        + "lQlLFeni3x9mmVWqCxyLrO0CNS0qvD5oJk/OEiehcK4f45inEjy56COYJY7L"
                                        + "lHyTcm5RrExMw1V+c99DiygXlNtVtkc8WcScvIZVCTs1svQzBn/BULKDYVaM"
                                        + "sxRnZxVfIgH4h5HCHWSY3jBKVLyKaTwg1UOlXkfiPS5o/Uwp32u8nNIPZAib"
                                        + "Helg9ES+obbKDF/nbF5xFdjUcocvI3JNYfR/L6FM7+cGAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void test7() {
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        mOnclick,
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.os.PowerManager.WakeLock;\n"
                                                + "\n"
                                                + "public class WakelockActivity7 {\n"
                                                + "    public void test(WakeLock lock) {\n"
                                                + "        try {\n"
                                                + "            lock.acquire();\n"
                                                + "            new Runnable() {\n"
                                                + "                public void run() {\n"
                                                + "                }\n"
                                                + "            };\n"
                                                + "        } finally {\n"
                                                + "            lock.release();\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "}\n"),
                                0x60d5f773,
                                "test/pkg/WakelockActivity7$1.class:"
                                        + "H4sIAAAAAAAAAIWR20oDMRCG/9ja7WG126qtZ6tUqBVcveqFIogoCvWAil6n"
                                        + "21Bj10R204qP5YUIXvgAPpQ42yqCUg0kk/z/x8wkeXt/eQVQw1IKMYynEceE"
                                        + "hYKFSQtTDAlzLcPyOsNs3YjQuHftlnvF28LXXnvHM7IrzUNtk7gtqaTZZlio"
                                        + "/AWuXDLEd3VTMGTrUonjzm1DBBe84ZMSCzqK1koEpc91J/DEvoyMwq9Eaze8"
                                        + "yynHnvJ8HUrVOhLmWjctTNuYwTxD8piaONChsZGAZSOJEbrD4M7KGwz2oVIi"
                                        + "2PV5GIqQwYlquD5XLfekcSM8w5D/ls46SvXbnh6clS4bmRZK1FHk1sllWK7U"
                                        + "uWoGWjZdHbqn+l4ER1zxlgjKXxC9lIVFhtJ/IENxAIISfWgcDMNgjhM9A/3z"
                                        + "EM0kUqSmabdE50hJV1efwKrPGHqkE0OG1gRF9EgbI5/8VM+l+RPLUKHRXvos"
                                        + "HIpx5JDHWE/JoUgxT7sY+mMWC5hD7gMeKwn1ewIAAA==",
                                "test/pkg/WakelockActivity7.class:"
                                        + "H4sIAAAAAAAAAIVSS0sDMRD+0l0bXfuwWq2P+q5QH7h46kERRBCEVgWL4jHd"
                                        + "hrp23dXs1uIv8uxFQUHv/ihxslU8SDGHmczkm++bSfLx+fIGoIKSBY4JCxkU"
                                        + "OCYtGJjSwTTHDEeRIXXo+1LteyIMZciQ3HF9N9plMMqrZwzmftCUDNmq68uj"
                                        + "znVDqrpoeJQxIxlGHHMMg+eiLauB02ZYKVeF31SB27SD0D4JulLVhC9aUpV+"
                                        + "QNuaNX0aCaddEzcxGcc8g3UadJQjD1xNPqHRHqH3nMi9c6P7yuaVuBMMw0ek"
                                        + "WpO6jzCFIVgpLMBiKOpu7Jt2y/5TWdrSwMUUljRwuj+QYUSL2J7wW/Zx40o6"
                                        + "NN8yw8J/IzGM/RbWL1XQ7V0RF85tx1W0my9X++vGN8KV9KQICVvoI4dFJOkl"
                                        + "9UqA6dnJDlM0S56RH1h7BnukDb0p2WQvSTaNkW+ooNc3yBfXn5B4hXFBJeY5"
                                        + "BQMPsGrab7w/xnVp5GMhzTQXyw7CJNE0fZ0csnSawRT5GeLO0SlHos4xmiT6"
                                        + "sbgwj3HyJpEZVAqMUs5Ab2Wo5yxyX0RSDfGjAgAA"))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expectClean();
    }

    public void test8() {
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.Bundle;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "import android.os.PowerManager.WakeLock;\n"
                                                + "\n"
                                                + "public class WakelockActivity8 extends Activity {\n"
                                                + "\tprivate WakeLock mWakeLock;\n"
                                                + "\n"
                                                + "\t@Override\n"
                                                + "\tprotected void onCreate(Bundle savedInstanceState) {\n"
                                                + "\t\tsuper.onCreate(savedInstanceState);\n"
                                                + "\t\tPowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "\t\tmWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "\t\tmWakeLock.acquire();\n"
                                                + "\t\tif (mWakeLock.isHeld()) {\n"
                                                + "\t\t\tmWakeLock.release();\n"
                                                + "\t\t}\n"
                                                + "\t}\n"
                                                + "}\n"),
                                0xba033ad3,
                                "test/pkg/WakelockActivity8.class:"
                                        + "H4sIAAAAAAAAAI1SW2sTQRT+Jk2yyXbbXGxTjbY2tdZcaldToUJE0IBYSFRI"
                                        + "UfBtshnCNpvZdHeS2p/izyhoCj6Iz/4mEc+sDQYVlIFzm3P5zuXr90+fARzg"
                                        + "gYllrGuyYeBmCpsmllAysJXCLRNJbKdJv20iix1N7mhSNlAxUGVID9/wgWj5"
                                        + "zsBAjSE10xisQylF0PR4GIqQYavFZS/w3Z7th/Yr/1QEbS55XwTbs5AGQ/KR"
                                        + "K131mGGhXHnNEG/6PcGQablSvBgPuyI44l2PLClfNgPBFYmF8nzip2PZ80RD"
                                        + "By91FHcGbT66jDE7/jhwxDNXKwVd1KOiTxzlTlx19nDvmE+4hRWsWriKawxr"
                                        + "s7SOL5WQym5q/k4xJEYavoVd3J1z+60tQn8kQmVhD7aFDK5YuKdz30fdwj5W"
                                        + "GYqK/u3RoG//AYZhZZaWj0b2zM6w+a8hMmT7QnXOQiWGHRFMXIe63Sm3dHe2"
                                        + "x2Xf7qjAlf1GZc70snssHEXzX5Ti9Femg/Lh3+L+Y48Gd07GbkClk274XHi9"
                                        + "aKNv6SMQnuChQIluapnOjyGmh0NSjGSaPtECaevEGfFE9QLsPHJcI5qMjCnE"
                                        + "9Y4uXU9IWyC+X61dIFbNx6dIfEGyXd2N540pUh+Rrn5AegrzJ1t8P5Os86iu"
                                        + "TrwBg+gSEoQqQ69IR15CDjXkUSdcxags+4a6QSBiuB5F3qBfwCQgWbLmkPsB"
                                        + "PqvE+FUDAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expectClean();
    }

    public void test9() {
        // Regression test for 66040
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.Bundle;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "import android.os.PowerManager.WakeLock;\n"
                                                + "\n"
                                                + "public class WakelockActivity9 extends Activity {\n"
                                                + "    private WakeLock mWakeLock;\n"
                                                + "\n"
                                                + "    @Override\n"
                                                + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                                + "        super.onCreate(savedInstanceState);\n"
                                                + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "        mWakeLock.acquire(2000L);\n"
                                                + "    }\n"
                                                + "}\n"),
                                0x6249352d,
                                "test/pkg/WakelockActivity9.class:"
                                        + "H4sIAAAAAAAAAI1STW/TQBB9mzqxY9x8EVIIFNqSFsct9bFCQUgQCakogUqp"
                                        + "ynnjrCw3zjrYm5T+LCRIJQ6IMyd+EWIcYTUCJNjDfGnmzZvZ+fbj8xcAR3BN"
                                        + "lHAvFZs67ht4YGIdWzq2DeyYKOBhkfxWHsunfzdRwa6OPR2PGIqTt3wsepE3"
                                        + "1mEzGJnHYB1LKeJuyJNEJAw7PS5HcRSM3ChxT6ILEfe55L6IW1lJh6HwNJCB"
                                        + "esawZrfPGLRuNBIM5V4gxevZZCjiUz4MKWJEshsLrshs2KvAL2ZyFIpOWmwO"
                                        + "olnsiZdBWtBIm4TU5LmngnmgLp8cnvM5t1DHLQu3cYdhI4PxIqmEVG431e8V"
                                        + "Q36a0rXQhrOS9tsYxPZUJMrCPg4slHHTwmMcMjQVRd3p2Hf/oMBQz8D4dOpm"
                                        + "cYatf62KoeILNbhMlJgMRDwPPJpxz+6lM7khl747UHEg/U57JfRmeC48RVu+"
                                        + "IcXFNdKRffy3uv/4LZ1772ZBTK01+1X7DNt0JiU6EYZcugCycmTThkk2yNsk"
                                        + "zUjnnSuwD8vEDZKFZdCAlv7Dr9Q+eWukd539K+ScmrZA/isKfedAq+kLGJ9Q"
                                        + "dD6iWIe5gHUN1UB6peskS8SgjCadagtV4tFcsrmLGmmTsivUtorqT4pi3QEE"
                                        + "AwAA"))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expectClean();
    }

    public void test10() {
        // Regression test for 43212
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import android.app.Activity;\n"
                                                + "import android.os.Bundle;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class WakelockActivity10 extends Activity {\n"
                                                + "    @Override\n"
                                                + "    public void onCreate(Bundle savedInstanceState) {\n"
                                                + "        super.onCreate(savedInstanceState);\n"
                                                + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                                + "        PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n"
                                                + "\n"
                                                + "        try {\n"
                                                + "            wakeLock.acquire();\n"
                                                + "            throw new Exception();\n"
                                                + "        } catch (Exception e) {\n"
                                                + "\n"
                                                + "        } finally {\n"
                                                + "            wakeLock.release();\n"
                                                + "        }\n"
                                                + "    }\n"
                                                + "}\n"),
                                0x123f396e,
                                "test/pkg/WakelockActivity9.class:"
                                        + "H4sIAAAAAAAAAI1STW/TQBB9mzqxY9x8EVIIFNqSFsct9bFCQUgQCakogUqp"
                                        + "ynnjrCw3zjrYm5T+LCRIJQ6IMyd+EWIcYTUCJNjDfGnmzZvZ+fbj8xcAR3BN"
                                        + "lHAvFZs67ht4YGIdWzq2DeyYKOBhkfxWHsunfzdRwa6OPR2PGIqTt3wsepE3"
                                        + "1mEzGJnHYB1LKeJuyJNEJAw7PS5HcRSM3ChxT6ILEfe55L6IW1lJh6HwNJCB"
                                        + "esawZrfPGLRuNBIM5V4gxevZZCjiUz4MKWJEshsLrshs2KvAL2ZyFIpOWmwO"
                                        + "olnsiZdBWtBIm4TU5LmngnmgLp8cnvM5t1DHLQu3cYdhI4PxIqmEVG431e8V"
                                        + "Q36a0rXQhrOS9tsYxPZUJMrCPg4slHHTwmMcMjQVRd3p2Hf/oMBQz8D4dOpm"
                                        + "cYatf62KoeILNbhMlJgMRDwPPJpxz+6lM7khl747UHEg/U57JfRmeC48RVu+"
                                        + "IcXFNdKRffy3uv/4LZ1772ZBTK01+1X7DNt0JiU6EYZcugCycmTThkk2yNsk"
                                        + "zUjnnSuwD8vEDZKFZdCAlv7Dr9Q+eWukd539K+ScmrZA/isKfedAq+kLGJ9Q"
                                        + "dD6iWIe5gHUN1UB6peskS8SgjCadagtV4tFcsrmLGmmTsivUtorqT4pi3QEE"
                                        + "AwAA"))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expectClean();
    }

    public void testFlags() {
        String expected =
                ""
                        + "src/test/pkg/PowerManagerFlagTest.java:15: Warning: Should not set both PARTIAL_WAKE_LOCK and ACQUIRE_CAUSES_WAKEUP. If you do not want the screen to turn on, get rid of ACQUIRE_CAUSES_WAKEUP [Wakelock]\n"
                        + "        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, \"Test\"); // Bad\n"
                        + "           ~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        classpath(),
                        manifest().minSdk(10),
                        compiled(
                                "bin/classes",
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "\n"
                                                + "import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;\n"
                                                + "import static android.os.PowerManager.FULL_WAKE_LOCK;\n"
                                                + "import static android.os.PowerManager.PARTIAL_WAKE_LOCK;\n"
                                                + "import android.content.Context;\n"
                                                + "import android.os.PowerManager;\n"
                                                + "\n"
                                                + "public class PowerManagerFlagTest {\n"
                                                + "    @SuppressWarnings(\"deprecation\")\n"
                                                + "    public void test(Context context) {\n"
                                                + "        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);\n"
                                                + "\n"
                                                + "        pm.newWakeLock(PARTIAL_WAKE_LOCK, \"Test\"); // OK\n"
                                                + "        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, \"Test\"); // Bad\n"
                                                + "        pm.newWakeLock(FULL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP, \"Test\"); // OK\n"
                                                + "    }\n"
                                                + "}\n"),
                                0x133baae,
                                "test/pkg/PowerManagerFlagTest.class:"
                                        + "H4sIAAAAAAAAAIVSS0vDQBD+pq+0NWqtWp/1WaVqMUcPFS8FQUhVqOh5my4h"
                                        + "tm4kWV8/y0sFD+LZHyXOqkURxUDmm5n95pthZ19eH58A7GDNwkQeQ5i0UMpi"
                                        + "Ko8kprOYyYMwmywAZMyshTkL84TMbqACvUdIVjdOCalG2JGEUTdQ8vDqoi2j"
                                        + "E9HucSalZawJ81VXqE4UBh3HC5WWSjsNg7e6bsrzrfAq8uR+YEpmjsMbGTWF"
                                        + "Er6M9nvCP2GJ7XNxLQhTA5Uwdr7TbNgY/nb8owkhfWnYNspY4KGMoo1FrBLK"
                                        + "ZkDnsus7v7UlFExjpyeU7xy1z6VnUr7UrbtYy4uWjK4Dj2der7pfvJaOAuXX"
                                        + "N9yfpXXCkJI3Z6Ir3dDrWqgQsoOIYB8oJaNGT8SxjAk71YPfRP+4gcpAh5ss"
                                        + "/cfBMsyqzZfg/fLdsR3hqMxIjOnNByTu2eGdss28J3NsCxj7pDb5gSQZ17aK"
                                        + "qT7Sz6BmLVXM9GGd1YrZgZP7cL6kSkiztbn/CIqcmWPJCosmODLTjGOF0by6"
                                        + "Jf6XMfYG8MgACKECAAA="))
                .issues(WakelockDetector.ISSUE)
                .run()
                .expect(expected);
    }

    public void testTimeout() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "import static android.os.PowerManager.PARTIAL_WAKE_LOCK;\n"
                                        + "\n"
                                        + "public abstract class WakelockTest extends Context {\n"
                                        + "    public PowerManager.WakeLock createWakelock() {\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        PowerManager.WakeLock wakeLock = manager.newWakeLock(PARTIAL_WAKE_LOCK, \"Test\");\n"
                                        + "        wakeLock.acquire(); // ERROR\n"
                                        + "        return wakeLock;\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public PowerManager.WakeLock createWakelockWithTimeout(long timeout) {\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        PowerManager.WakeLock wakeLock = manager.newWakeLock(PARTIAL_WAKE_LOCK, \"Test\");\n"
                                        + "        wakeLock.acquire(timeout); // OK\n"
                                        + "        return wakeLock;\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(WakelockDetector.TIMEOUT)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/WakelockTest.java:11: Warning: Provide a timeout when requesting a wakelock with PowerManager.Wakelock.acquire(long timeout). This will ensure the OS will cleanup any wakelocks that last longer than you intend, and will save your user's battery. [WakelockTimeout]\n"
                                + "        wakeLock.acquire(); // ERROR\n"
                                + "        ~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings\n")
                .expectFixDiffs(
                        ""
                                + "Fix for src/test/pkg/WakelockTest.java line 10: Set timeout to 10 minutes:\n"
                                + "@@ -11 +11\n"
                                + "-         wakeLock.acquire(); // ERROR\n"
                                + "+         wakeLock.acquire(10*60*1000L /*10 minutes*/); // ERROR\n");
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mOnclick =
            xml(
                    "res/layout/onclick.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\"\n"
                            + "    android:orientation=\"vertical\" >\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"nonexistent\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong1\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong2\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong3\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong4\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong5\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong6\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"ok\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"simple_typo\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"my\\u1234method\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"wrong7\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "    <Button\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:onClick=\"@string/ok\"\n"
                            + "        android:text=\"Button\" />\n"
                            + "\n"
                            + "</LinearLayout>\n");
}
