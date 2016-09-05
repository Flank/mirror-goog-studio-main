/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector;

public class ClickableViewAccessibilityDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new ClickableViewAccessibilityDetector();
    }

    public void testWarningWhenViewOverridesOnTouchEventButNotPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:16: Warning: Custom view test/pkg/ClickableViewAccessibilityTest$ViewOverridesOnTouchEventButNotPerformClick overrides onTouchEvent but not performClick [ClickableViewAccessibility]\n"
                + "        public boolean onTouchEvent(MotionEvent event) {\n"
                + "                       ~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewOverridesOnTouchEventButNotPerformClick.class", ""
                            + "H4sIAAAAAAAAAJVRTUvDQBB9m9ZEY9pavYmCqKC1YA4eK4IGBaFWoaUHb2my"
                            + "6tp0V5JN1P/kxZPgwR/gjxJng/hxEd3DvJk3bx4z7Ovb8wuAHSy4qMBzUHfQ"
                            + "ZLB3hRR6j2FpsxvKOFUi9iMlNZfaDwze6U5ryFANVMwZGl0heS+fjHg6CEcJ"
                            + "MZ6SA5VHV4cFjTAsf9kUgt/6J0oLJctmp3XO4PZVnkb8SJjZtSAR0dj4DEm7"
                            + "H0U8y8RIJELfD3imt6/DIvRQxZSDeYa+Js6/GV/6v4+tG/a04GkqYp6dflvv"
                            + "INc9pc94eqHSSWnC0P6X2juWkqdBEmYZzxiaP241Tgwbf1wTK7DoJ8yzwMyV"
                            + "FG2qVqm2CO2t9hPYI2UMDkW3ZGukqxMzjZkP/SKh6ViVh0+tTQg0iXdL/1nM"
                            + "lQ5GWUMD7jvKS0TGDwIAAA==")
            ));
    }

    public void testWarningWhenOnTouchEventDoesNotCallPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:28: Warning: test/pkg/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick#onTouchEvent should call test/pkg/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick#performClick when a click is detected [ClickableViewAccessibility]\n"
                + "        public boolean onTouchEvent(MotionEvent event) {\n"
                + "                       ~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewDoesNotCallPerformClick.class", ""
                            + "H4sIAAAAAAAAAI1Ry0oDMRQ96bQdHUdrfeKj4AtsKzgLlxVBRgWhFsHShbvp"
                            + "NGrsNCkzadV/cuOq4MIP8KPEmypKXRQTuMk959yTx33/eH0DcIBlB2nkTJi1"
                            + "MWdjkSF7KKTQRwzrxWogW7ESLS9UUnOpPd+sj7pSajCkfdXiDLmqkLzW6zR5"
                            + "XA+aESGuknXVC+9O+1TCUPi16Qv+4F0oLZQckpXSNcm7PL5RccePRNhmsIoG"
                            + "dK5ULw75mTCG20PKmDfI4DgMeZKIpoiEfqrzRO/fB/3ARQZZF1NwbSwx+JoI"
                            + "r9u+9cbX7hj0RPGkprQfRNHlyF3WxrLuuZQ89qMgSXjCkB95palk2P3nNbAB"
                            + "i3pghgVmnkLRpmwLKZpAtrw3AHuhHcMERWeIrpBylZBJyr/0JjNMynr+0Rov"
                            + "oECRfudbZzKDZsoDpP5KN8li2phgBgvDw6gtyGMeziecSLk6OQIAAA==")
            ));
    }

    public void testWarningWhenPerformClickDoesNotCallSuper() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:44: Warning: test/pkg/ClickableViewAccessibilityTest$PerformClickDoesNotCallSuper#performClick should call super#performClick [ClickableViewAccessibility]\n"
                + "        public boolean performClick() {\n"
                + "                       ~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$PerformClickDoesNotCallSuper.class", ""
                            + "H4sIAAAAAAAAAI1QPUsDQRB9k8Q7PU9jtBMLUcEkEq+wVAS5IAghCAkp7C6X"
                            + "VdZcdsPtJup/srESLPwB/ihx9lCxCm4xH2/evJnZj8+3dwAn2ApQRuhj3UeN"
                            + "4J1JJe05YafeSdQo13IUpVpZoWwUO/9oTxsDQiXWI0GodqQS3dlkKPJ+MswY"
                            + "Caciv9X5JM5kOiaU640bQtDTszwVl9Ix9ouSYw+keLhIU2GMHMpM2qe+MPb4"
                            + "PpknISpY8rFJaFvGoun4LlrcdnD9Z25bC9PVNk6yrDfjhficxeXwSimRx1li"
                            + "jDCE2s/pcx4VuXmEw38ugl2U+EfdK4HcHWw9zvY4L7H3mkevoBeOCD7boEAb"
                            + "zGsysoyVb/42e3Iq5edfrlcgLe4ICv1VbBQKxPEaqgi+AKzXrlnXAQAA")
            ));
    }

    public void testNoWarningOnValidView() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                mClickableViewAccessibilityTest$ValidView
            ));
    }

    public void testNoWarningOnNonViewSubclass() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
        lintProject(
            classpath(),
            manifest().minSdk(10),
            mClickableViewAccessibilityTest,
            mClickableViewAccessibilityTest2,
            mClickableViewAccessibilityTest$NotAView
        ));
    }

    public void testWarningOnViewSubclass() throws Exception {
        // ViewSubclass is actually a subclass of ValidView. This tests that we can detect
        // tests further down in the inheritance hierarchy than direct children of View.
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:84: Warning: test/pkg/ClickableViewAccessibilityTest$ViewSubclass#performClick should call super#performClick [ClickableViewAccessibility]\n"
                + "        public boolean performClick() {\n"
                + "                       ~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                mClickableViewAccessibilityTest$ValidView,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewSubclass.class", ""
                            + "H4sIAAAAAAAAAI1Ry0rDQBQ909TExtT6WokLUcHWgkF0pwgSEIQiaGsX7iaT"
                            + "Ucamk5JMfPyTG1eCCz/AjxLvBBFxIZ3FPfecOfcwj4/Pt3cA+1jx4SDwMO9h"
                            + "kcE9UlqZY4a1do/rJM9UEopMG6lNGFl8NIedIUM9yhLJ0OopLc/LcSzzAY9T"
                            + "UoKJzG+yfBylSowYnHbnmsHvZ2Uu5Kmyjs1qy7qHSj6cCCGLQsUqVeZpIAuz"
                            + "e8fveYA6ZjwsMxwY0sLJ6Db8f2zLqv0yFikvCjrGH3qmtcwjSyTRvalDeaoS"
                            + "u8XQ+NVvTzmPddTode2qgdk7UXWJbRCvEbo73VewF+oYPKp+pV6Q75KUWTS+"
                            + "/auEzKY4zz9et1KuaMKv8uewQLhUJTfRgv3XJnH/C3JD8nDrAQAA")
                ));
    }

    public void testNoWarningOnOnTouchEventWithDifferentSignature() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewWithDifferentOnTouchEvent.class", ""
                            + "H4sIAAAAAAAAAI1QTUvDQBSc19REY7RWb6IgKtgqmINHRZBYQSh6sFQQFNJ0"
                            + "a7eNG0k2Uf+TF0+CB3+AP0p8G1Twou7hfczOzL63b+8vrwB2sODCgudg1kGd"
                            + "YO9JJfU+YanRDlU/TWTfjxKlhdJ+YPK93m12CdUg6QtCrS2VOMlveiLthL2Y"
                            + "ES9RnSSPhq2CJQSr0bwguGdJnkbiSBrGWhDLaGzYXSnuDqJIZJnsyVjqh47I"
                            + "9PYoLEIPVUw4mCe0NGP+7fja/122btBzqYeHcjAQKT9++mOQ5T/uvWOlRBrE"
                            + "YZaJjFD/Wr5gmW+0hI1/joIVVPhPzamAzCYcbe5Wua9wtje3nkFPXBEcjm6J"
                            + "XjLvipFJTH3yFzmTcbEev7l2iYSscEv/acyVDsT1DGpwPwCVrcSW2QEAAA==")
            ));
    }

    public void testNoWarningOnPerformClickWithDifferentSignature() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ViewWithDifferentPerformClick.class", ""
                            + "H4sIAAAAAAAAAI1QTUvDQBScTWuiMVqrN1EQFfwomINHRZCoIBQRLBW8pelW"
                            + "X5tsymZb9T958SR48Af4o8S3QQUv1T282Tc7M7y37x+vbwD2seSjgsDDvIe6"
                            + "gHtIisyRwMp2M1ZdnVM3THJlpDJhZPHBHOy0BapR3pUCtSYpeTHKOlK34k7K"
                            + "TDCUupfrLEopGUyOuRHwr/KRTuQZWetG6bExbZL3x0kii4I6lJJ5bMnC7PXj"
                            + "cRygiikPiwKnhrlwOLgNJ9s2LXtN5u6Eej2peYLLXxOu/vEenCsldZTGRSEL"
                            + "gfr3OmO2hdYrsPXPUbAGhz/bHgfCbsLV5W6de4fR3W28QDzzTcDj6pcssa7P"
                            + "zDRmvvTLjPbFqTz9aF1GIGPeL/NnsVAmWOUcavA/AezA717yAQAA")
            ));
    }

    public void testWarningWhenSetOnTouchListenerCalledOnViewWithNoPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:124: Warning: Custom view test/pkg/ClickableViewAccessibilityTest$NoPerformClick has setOnTouchListener called on it but does not override performClick [ClickableViewAccessibility]\n"
                + "            view.setOnTouchListener(new ValidOnTouchListener());\n"
                + "                 ~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                mClickableViewAccessibilityTest$NoPerformClick,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$NoPerformClickOnTouchListenerSetter.class", ""
                            + "H4sIAAAAAAAAAJ1TbUvbUBR+Tt+uxlT7ok7dZHPtauugYQj7ogxGQRBLO7AU"
                            + "/JimV70ak5GkimP7UX5ZQWE/YD9q7NwwGLaiwQTy5J77nOe83HN//7n9BWAb"
                            + "dQM5lAQWDaSwbMDEC4EVgTVCbld5KvpESNcbfUKm5Q8lYaGtPNkZXQxk0LMH"
                            + "Lltqju26hzLqej1/5Jy2VRhJTwZdr+N/kcGxH1y0XOWcC7wkzN+3Ecx9j7kt"
                            + "1w5DGRJ26u1IhpH19fzEihk6RF/Jq8+OI8NQDZSrouseU6r3lXZ0isahPwoc"
                            + "uad0WpXH/Ztn9qVtQmBG4BVhN2nYvu2q4USphMWHzFr9tYk3qBAOnlfWhCJ3"
                            + "OdLhKolYBV2i5dreidUdnEknInx8XhqEzYSOAlUCCM3kB/khPrtSODVBAjWe"
                            + "t6le1+pt2xsGvhpalyxoadXqBCuWbCTOQGCTsPGkKqE4xeHjzfIV0k+KX54n"
                            + "rn6WV+uM3Apkt8agG+imGPzNxcZvTJ3Dwj9qAxleA/n3d0gd0Rjpn8j8dzHi"
                            + "ze98N3/wXyGOVMQ84xbHNlFGPuaUscSYZVxlXGdEYQYbeIe3udm/Nzy4QfID"
                            + "AAA="),
                mClickableViewAccessibilityTest$ValidOnTouchListener
            ));
    }

    public void testNoWarningWhenSetOnTouchListenerNotCalledOnViewWithNoPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                mClickableViewAccessibilityTest$NoPerformClick
            ));
    }

    public void testNoWarningWhenSetOnTouchListenerCalledOnViewWithPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$HasPerformClick.class", ""
                            + "H4sIAAAAAAAAAI1QTUvDQBB9049EY7TWj5uCqGA/wBw8eFAECYhCEcHSg7c0"
                            + "XWVtuinZbdWzgn/Gi6eCB3+AP0qcDSqIIO5h3sybNzM78/b+8gpgB8seSpi1"
                            + "Zs7FvItFgrMvlTQHhJVaK1K9LJW9IE6VEcoEocVbs1fvEEph2hOESksqcToa"
                            + "dEXWjroJM/5QZJdpNggTGfcJxVr9guCdp6MsFkfSKjbylFV3pLg5jGOhtezK"
                            + "RJq7ttBm+zoaRz7KcHxMw3OxRNg1nAiG/avg79rN40if/Zhf+cX4J0qJLEwi"
                            + "rYUmVL+2HHPDwHYlbP1zHNZQ5OPZVwDZL7N1OVrnuMDoNJoT0DN7hCm2Xs7e"
                            + "s/KBGd7uU7/KSIzlxgSFp2+5k5OPXDSTj/CxkDfhs6KCKrwPU2Rb3coBAAA="),
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$HasPerformClickOnTouchListenerSetter.class", ""
                            + "H4sIAAAAAAAAAJ1T3UobQRg9E5NMXDc1/tuf0KpREwUX8aIXilACpYXQFBIC"
                            + "vZxsxjjJuis7G8VnEH0XbxQUfIA+VOk3i1DYlHbJLuzZ+ebM+X7n56/HZwAH"
                            + "qFrIY55j0UIGyxZsrHCscrxhyB8pX0XHDFPVWochWw96kmG2oXz5bXTWlWFb"
                            + "dD2ybLvC81oyavrtYOSeNpSOpC/Dpv9F6O8yPAnCs7qn3CHHWzqeMDLYX31i"
                            + "1z2htdQMR9VGJHXknA/7TswwTjpKXn5yXam16ipPRVdtolQSUocmSqsVjEJX"
                            + "flYmso1/C+wNxIWwwVHgeEee0/rtCE/1EtkyLP7NbNTf2/iADYZJ80pIUqUj"
                            + "4y8lrWSSdDzh951mdyDdiOHjhIFQq1Oe5KgwgGEvfTP34/bN67E54tiisRkr"
                            + "91a1IfxeGKiec0GCjlGtJFixZC11BBzbDGv/VWWYG+NQh3N0kcyToZdGirKf"
                            + "plWZkEqB3M4D2B1MUSz65mPjNVFnMPtCrSFLa6C4+4TMD/aAqXtk/xyx4s0b"
                            + "uqG39FeKPc3hFeEO+baxgGLMWcASYY7wNWGZEKUC1rCJ9fz0b7zs4y34AwAA")
            ));
    }

    public void testNoWarningWhenOnTouchListenerCalledOnNonViewSubclass() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                mClickableViewAccessibilityTest$NotAView,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$NotAViewOnTouchListenerSetter.class", ""
                            + "H4sIAAAAAAAAAJVT20ojQRA9lVvHcdRovOwtGDWrSQRHkX1SBAm7sBDMgyHg"
                            + "42TSuK3jjGQ6WfY7RP/DFwUFP8CPkq0eVoRE1jgDU1Ndp05d+/Hp7gHANsoW"
                            + "MpgRmLWQwLwFGwsCHwQ+ETK7KlB6j5AsV1qEVC3sSMJUXQXyoHfWlt2m2/b5"
                            + "pOi5vn8odSNohj3vV11FWgay2wgOQr3fUvK3wGdC9lkj2D8Dttd8N4pkRPhW"
                            + "rmsZaef89Nip+co7NbQGue95MopUW/lK/2kypPTMsWMSsg7DXteTP5RJYuX/"
                            + "nhsnbt+1IZAV+ELYHTVgy/VVZ6Awwuxrx4Z90UYRK4Tv7y1ogIu7qU2gwhv2"
                            + "nCnL8d3g2Gm0T6SnCZvvDU1YG9FFoEQAYWP0gW3Fk5qJhrZDYJV3aaizq+W6"
                            + "G3S6oeo4fSZ0DGtpABVTVkbOQGCNsPQmK2F6CMPDTPP1ME+CX94ern6MtQJL"
                            + "bgXS1VvQNUxTLP5m4sMLho5j6h+0ghTrwMT6PRJHdIvkDVIvLlZsvOR7d8V/"
                            + "uTjSNCZZVjm2jTwmYkwecyzTLD+yLLBELoslfMVyZuwvvUMDec4DAAA=")
            ));
    }

    public void testNoWarningWhenOnTouchCallsPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(
            "No warnings.",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                mClickableViewAccessibilityTest$ValidOnTouchListener
            ));
    }

    public void testWarningWhenOnTouchDoesNotCallPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:162: Warning: test/pkg/ClickableViewAccessibilityTest$InvalidOnTouchListener#onTouch should call View#performClick when a click is detected [ClickableViewAccessibility]\n"
                + "        public boolean onTouch(View v, MotionEvent event) {\n"
                + "                       ~~~~~~~\n"
                + "0 errors, 1 warnings\n",
            lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$InvalidOnTouchListener.class", ""
                            + "H4sIAAAAAAAAAI1RTUvDQBB927RZW6v9UKsIgmKhrQcjeKwUpCgUqj1YevCW"
                            + "j6VuGzeSbCP+HPHqxZPgwR/gjxIn1YsGRRbmzbydeTO78/b+8grgEBsFGFji"
                            + "KHFUOVYZzCOppO4wGM3WiCHbDTzBUOpLJc5n144Ih7bjE8MDNQxm7hXDQbNv"
                            + "Ky8MpGfFUtxaIzLt79RZoGWgTmKhdLt1yVC4CGahK05lorTb9aU7TVSTymPX"
                            + "FVEkHelLfTcUkd6f2LFdRA4mR42ho4mzbqZj6++yek/Fti+9weeYfRlpoUTI"
                            + "UPvtothThF3fjiIRMZSTvpZvq7E1cCbC1RzrDDupp9ZTQqUU0/jn0AyVlD62"
                            + "kaEd0SpoYRk69BMUcYq2CBlhbu8Z7IkchgWy5py8J5tH4St1k9BIBIzHH3kP"
                            + "JLk4ly6iQlidN1lGmWqzWMGamf8AXG41Ni4CAAA=")
            ));
    }

    public void testNoWarningWhenAnonymousOnTouchListenerCallsPerformClick() throws Exception {
        //noinspection all // Sample code
        assertEquals(
           "No warnings.",
           lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$AnonymousValidOnTouchListener.class", ""
                            + "H4sIAAAAAAAAAK1TXUsbQRQ9E5OsbmNj/IitbfyMmii4SB98UAoSarWEKiQE"
                            + "fJxsxji6mZWdTST/SHwRXxQq+AP8UeKdRSiopEE6C3v33jn3nLt37jw8/rkH"
                            + "8A2LNpIYtzBhI4asjTQmLXy2MMWQ21a+6rb8tq5xTzb2VdVvu8dlqUOhRMCQ"
                            + "2lNkSx7XWmgGMCS3pJLhd4aBQrHGEC/5DcGQLkslfrdbdRFUed2jSNblnlcR"
                            + "4QtKC18Ivcv1gQiO/KBV8qR7yrBVKIdCh87ZadOJQoakJsX5tusKrWVdejLs"
                            + "VgmSf5G7aaqwK347cMWONMoLvQnWTniHp2DjA8PPfkV7tim/bui+ppDDrIU5"
                            + "hh//hZZhxJTqeFw1nf36iXBDho13tonhV/8t7llW1PBR/cbJLtDJvvqHpUKZ"
                            + "q0bgy4bTITHHKObfolzuszoLeYa5f3IyZF5hMIsEXQWzYvTQCNBAp8jLkaXp"
                            + "RmLlFuwaZs6H6Z2MghcE/YjMM7SIOPnA8OodYoeEH7hB/G+KHW1eEvSKvkYj"
                            + "pTEMkp2nxAQ+wXrGmJUmf4T8adqdSQ49AQGSSu6yAwAA"),
               base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$AnonymousValidOnTouchListener$1.class", ""
                            + "H4sIAAAAAAAAAK1Uy27TQBQ90zxMjCEp0PIQoS+3zUPCRSxYpEKqovBSSpEa"
                            + "ZcHOsafJtM5M5ZkE9ZeqblgAEgs+gI9C3EnZNJGqCsWS7525c+aee31m/PvP"
                            + "z18AXqJeQBbLLnJ46OIRHjt44uCpgzJD3gyE9nccrDKU96SSZ0M10t0wEfGB"
                            + "7KhRNGgLbbjkKYP3XpJvJqHWXDO8bRuuTXB60g+aiYhOwl7Cu4J/2YsirrXo"
                            + "iUSYsw5B/GvzNqiIXSGFec3woTKnnNUuQ7apYs5QbAvJP46GPZ52bDoGR12i"
                            + "GXYq7VDGqRJxMCaWwFI1rob2lRFKtsZcmkb1M4N7qEZpxN8Im2nj+iKfH4fj"
                            + "kCpoyShRWsj+PjcDFXtYg+/BwW0PHjYdbHnYRoW+6Fya918wgKFkyYMklP3g"
                            + "oHfMI8OwNtOsPyNycSrioMrQmkthDMtRmCSH3Mxw1Ij4Xag/8fRIpcMJCcPu"
                            + "zU/D1N6J/pmKtYszPdNBPr3CQ0ASdvuGXAyv/rMqrNI1zJE0ebBSyepPt3OB"
                            + "Xg93KHqXRus0txG3Vv8GVvuBha+wYhbJ0i6y51ZaLP7Dl8lnyOfq35G5mIJe"
                            + "0NK9CcV9PCCfxS0swZ3Q2PEKCjTL4vLJ05/hWb5AayvYgPsXTF/ngD0EAAA=")
            ));
    }


    public void testWarningWhenAnonymousOnTouchListenerDoesNotCallPerformClick() throws Exception {
      //noinspection all // Sample code
      assertEquals(""
                + "src/test/pkg/ClickableViewAccessibilityTest.java:182: Warning: test/pkg/ClickableViewAccessibilityTest$AnonymousInvalidOnTouchListener$1#onTouch should call View#performClick when a click is detected [ClickableViewAccessibility]\n"
                + "                public boolean onTouch(View v, MotionEvent event) {\n"
                + "                               ~~~~~~~\n"
                + "0 errors, 1 warnings\n",
           lintProject(
                classpath(),
                manifest().minSdk(10),
                mClickableViewAccessibilityTest,
                mClickableViewAccessibilityTest2,
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$AnonymousInvalidOnTouchListener.class", ""
                            + "H4sIAAAAAAAAAK1T20rDQBA9W9tGY7Xe73erVgWD+OCDIkhBLQQVLIKP23TV"
                            + "1XQj2bTiZ4mooOAH+FHibBAEFVvEDWQys2fOmczOvr49vQBYw5yNNPos9NtI"
                            + "YMBGFoMWhi2MMExuq0DdVIOaLqo692XlQJWCmnfuSh0JJUKGTFGRLfhca6EZ"
                            + "wJDelEpGWwwt+cVjhmQhqAiGrCuV2K9VyyIs8bJPkQGP+/6RiL5QWhgl9B7X"
                            + "hyI8DcJqwZfeJcNm3o2EjpyryzMnDhmSYymutz1PaC3L0pfRTYkguS+5G6YK"
                            + "+yiohZ7YkUZ59neClQte5xnYaGcoNivaoFG5VUM4lsE4pixMM+z+EzFDlynX"
                            + "8bk6cw7KF8KLGNb/2CoGt/k2NygsbnuP/uF8Z+l8v/3FfN7lqhIGsuLUSc4x"
                            + "mrmfKBearM9CjmG6ISdD9zcMppCiK2FWgh4aBBrrDHnjZGnGkVp6BLuFmfYO"
                            + "eqfj4B1BO9H9AV1EknygY/kZiRPCtzwg+Zlix5v3BH2ir55YqRetZGcoMYUh"
                            + "WB8Ys7Lkd5E/QbuT6bZ3wusDwboDAAA="),
                base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$AnonymousInvalidOnTouchListener$1.class", ""
                            + "H4sIAAAAAAAAAK1TXU8TQRQ9l5auXVZbUDF+IAIrFExcjA8+lJiQBrXJIiQ0"
                            + "PPi23R3bge2M2ZnW8J988cGQ+OAP8EcZ71RfbBMlppvM3JmTO+ecu3fm+4+v"
                            + "3wA8x1YVJdz2Ucayhzse7nq4T6jYvjThroeHhNV9pdXFQA9NW42SXGZHqqOH"
                            + "aT+WxgolCkLQVhxbeWKMMIR2bIWx0YfzXtTKZXqedHNxKsXH/TQVxsiuzKW9"
                            + "6HBK+A/mJhvZk0ral4S4MTPW7VNCuaUzQajFUom3w0FXFB1HSPD0r2zCbiNO"
                            + "VFZomUUj1omcWPNP6FBbqdXBSCjb3H5H8E/0sEjFK+mYNv5u8+lZMkrYwYFK"
                            + "c22k6h0K29dZgFWsB6jAD7CADf6bMyo7fEYAoe5kozxRveioeyZS6yEkrE1V"
                            + "Gk51uTaBeHhMeD0jd4TlNMnzE2GnVDZZ+k1ijkXxXheDsQxh7+rXYeLsuP2l"
                            + "hpsXp6ombF2RlvDiPw3gEb+4MrdiHlSvu07zQ5zjsYCA0eu8Wue9Q/ydJ19A"
                            + "O5eY+wzXvBs8VzgCDKOG+u/8exxLjqX0aSLvkvHFMf8SbnIsw8MtVMcabr2C"
                            + "a6zvzrpvnpkeVKrscAVr8H8CZD22eiUEAAA=")
            ));
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mClickableViewAccessibilityTest = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import android.content.Context;\n"
            + "import android.view.MotionEvent;\n"
            + "import android.view.View;\n"
            + "\n"
            + "public class ClickableViewAccessibilityTest {\n"
            + "\n"
            + "    // Fails because should also implement performClick.\n"
            + "    private static class ViewOverridesOnTouchEventButNotPerformClick extends View {\n"
            + "\n"
            + "        public ViewOverridesOnTouchEventButNotPerformClick(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean onTouchEvent(MotionEvent event) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Fails because should call performClick.\n"
            + "    private static class ViewDoesNotCallPerformClick extends View {\n"
            + "\n"
            + "        public ViewDoesNotCallPerformClick(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean onTouchEvent(MotionEvent event) {\n"
            + "            return false;\n"
            + "        }\n"
            + "\n"
            + "        public boolean performClick() {\n"
            + "            return super.performClick();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Fails because performClick should call super.performClick.\n"
            + "    private static class PerformClickDoesNotCallSuper extends View {\n"
            + "\n"
            + "        public PerformClickDoesNotCallSuper(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean performClick() {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Valid view.\n"
            + "    private static class ValidView extends View {\n"
            + "\n"
            + "        public ValidView(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean onTouchEvent(MotionEvent event) {\n"
            + "            performClick();\n"
            + "            return false;\n"
            + "        }\n"
            + "\n"
            + "        public boolean performClick() {\n"
            + "            return super.performClick();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay because it's not actually a view subclass.\n"
            + "    private static class NotAView {\n"
            + "\n"
            + "        public boolean onTouchEvent(MotionEvent event) {\n"
            + "            return false;\n"
            + "        }\n"
            + "\n"
            + "        public void setOnTouchListener(View.OnTouchListener onTouchListener) { }\n"
            + "    }\n"
            + "\n"
            + "    // Should fail because it's a view subclass. This tests that we can detect Views that are\n"
            + "    // not just direct sub-children.\n"
            + "    private static class ViewSubclass extends ValidView {\n"
            + "\n"
            + "        public ViewSubclass(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean performClick() {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay because it's declaring onTouchEvent with a different signature.\n"
            + "    private static class ViewWithDifferentOnTouchEvent extends View {\n"
            + "\n"
            + "        public ViewWithDifferentOnTouchEvent(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean onTouchEvent() {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay because it's declaring performClick with a different signature.\n"
            + "    private static class ViewWithDifferentPerformClick extends View {\n"
            + "\n"
            + "        public ViewWithDifferentPerformClick(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean performClick(Context context) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay when NoPerformClickOnTouchListenerSetter in project.\n"
            + "    // When NoPerformClickOnTouchListenerSetter is in the project, fails because no perform click\n"
            + "    // and setOnTouchListener is called below.\n"
            + "    private static class NoPerformClick extends View {\n"
            + "        public NoPerformClick(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private static class NoPerformClickOnTouchListenerSetter {\n"
            + "        private void callSetOnTouchListenerOnNoPerformClick(NoPerformClick view) {\n"
            + "            view.setOnTouchListener(new ValidOnTouchListener());\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Succeeds because has performClick call.\n"
            + "    private static class HasPerformClick extends View {\n"
            + "       public HasPerformClick(Context context) {\n"
            + "            super(context);\n"
            + "        }\n"
            + "\n"
            + "        public boolean performClick() {\n"
            + "            return super.performClick();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private static class HasPerformClickOnTouchListenerSetter {\n"
            + "        private void callSetOnTouchListenerOnHasPerformClick(HasPerformClick view) {\n"
            + "            view.setOnTouchListener(new ValidOnTouchListener());\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay because even though NotAView doesn't have a performClick call, it isn't\n"
            + "    // a View subclass.\n"
            + "    private static class NotAViewOnTouchListenerSetter {\n"
            + "        private void callSetOnTouchListenerOnNotAView(NotAView notAView) {\n"
            + "            notAView.setOnTouchListener(new ValidOnTouchListener());\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay because onTouch calls view.performClick().\n"
            + "    private static class ValidOnTouchListener implements View.OnTouchListener {\n"
            + "        public boolean onTouch(View v, MotionEvent event) {\n"
            + "            return v.performClick();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Fails because onTouch does not call view.performClick().\n"
            + "    private static class InvalidOnTouchListener implements View.OnTouchListener {\n"
            + "        public boolean onTouch(View v, MotionEvent event) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Okay because anonymous OnTouchListener calls view.performClick().\n"
            + "    private static class AnonymousValidOnTouchListener {\n"
            + "        private void callSetOnTouchListener(HasPerformClick view) {\n"
            + "            view.setOnTouchListener(new View.OnTouchListener() {\n"
            + "                public boolean onTouch(View v, MotionEvent event) {\n"
            + "                    return v.performClick();\n"
            + "                }\n"
            + "            });\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Fails because anonymous OnTouchListener does not call view.performClick().\n"
            + "    private static class AnonymousInvalidOnTouchListener {\n"
            + "        private void callSetOnTouchListener(HasPerformClick view) {\n"
            + "            view.setOnTouchListener(new View.OnTouchListener() {\n"
            + "                public boolean onTouch(View v, MotionEvent event) {\n"
            + "                    return false;\n"
            + "                }\n"
            + "            });\n"
            + "        }\n"
            + "    }\n"
            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mClickableViewAccessibilityTest$NoPerformClick = base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$NoPerformClick.class", ""
            + "H4sIAAAAAAAAAI1QTUvDQBB9U2uiMbb27kFU8AvMQehFESQgCKUIlt7zMcra"
            + "dFd2t6n+LE+CB3+AP0qcBLx4EHdg5s1787G7n1/vHwDOMIiwgihEHKJPCC6U"
            + "Vv6SsH04ynRpjSqTwmjP2idpE5/9+dGU0E1NyYT+SGkeL+Y520mWV8JEd2Zh"
            + "C75WTbKXVqqYNcJU8fKqKNg5latK+ZcJO3/6mNVZjC5WQ2wRhl645Gn2kPzd"
            + "tj82t2zvjZ23dYTebyK+0ZptWmXOsSMMfl5Sy7ikmUk4+Ocy7KAjH9QcEpO7"
            + "ig8k2xW+IzE4PnkDvbZ6KD5q2VrqloLWBHewjl6rkOANbCL6BmfElwt+AQAA");

    @SuppressWarnings("all") // Sample code
    private TestFile mClickableViewAccessibilityTest$NotAView = base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$NotAView.class", ""
            + "H4sIAAAAAAAAAI1RXUsCQRQ946qrm+VHnwpCkZT20AY9GoEsCYLpQ+JDb+s6"
            + "1Og2G7uj0X/qpaegh35APyq6Y0KwQTUP95w999yzd5j3j9c3AKeoWDBQNLFu"
            + "YpMhfSakUOcMRr0xZEg6wZgz5LtC8t7sbsTDgTvySckFchDMvNuLOZeKoVrv"
            + "unIcBmJszwV/sC8DJQK5aDYb1wyliKv+10RXRIpLHprYpuCYSMEdSej4bhTx"
            + "iOEgFjykUosNNfWm1lUwCz3eFnq7fccX3lRvqv0tz+NRJEbCF+pxwCN1PHHn"
            + "bg5JpEzsMJwo0uz76Y39+1itF6iW7jBkvmlBh9m+K2/s/mjCPWWizLD359YM"
            + "h//8L0PxRxp2kaB308cgRlcBPR59VQkZYeroBeyZCINJNb0QHaoZZJfWCmGC"
            + "MGE8xXxtqhbWlr6y9mhHPK5Dcn7RLGCVsERsBVvIpbPEStiA9Qm719ZIZwIA"
            + "AA==");

    @SuppressWarnings("all") // Sample code
    private TestFile mClickableViewAccessibilityTest$ValidOnTouchListener = base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ValidOnTouchListener.class", ""
            + "H4sIAAAAAAAAAI1STU/CQBB9W6BFxAB+i6IYSQRNqMYjxsQQTUxQDhIO3EpZ"
            + "daFuTVsw/hXjT/DiRRMP/gB/lHEWPaA1hstM9uXNe7Mz8/7x+gZgD+sJRDGT"
            + "QEyFWcwZWDCwZCDLoO8LKYIDhkix1GSIVt0OZ0jVhORn/es29xpW2yHEcGXD"
            + "7dtXDDvFmiU7nis65kDwW7NJofITOnUD4cqjAZdBpdQysMIAhuSJlNyrOpbv"
            + "c5+hXKwF3A/Mm96lWXWE3VNGSuzQtrnvi7ZwRHDXIEpht6J6S5y7fc/mx0L1"
            + "s/F/SblrDawkDMQN5JJYxZqBPMP+uI5NyxGd+teXa8IPOHXOMPs3nFZmpmPJ"
            + "S7Pe7nI7YFgPjagQKkuFkNLYA2HIhBxowjfcu3C962H1cKUths0xRZGnI4nR"
            + "nnS6mQg0NTx6TdArR5lWiNjWC7QnqGUmKOpD8J7iJJIj1Iiibj8j8viL+pAG"
            + "qaZHqNq3KgurashQ1jCNFOUsqU5hHkjHqc15LBJXp1te1ic+AVDrhlfnAgAA");

    @SuppressWarnings("all") // Sample code
    private TestFile mClickableViewAccessibilityTest$ValidView = base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest$ValidView.class", ""
            + "H4sIAAAAAAAAAI1Ry0rDQBQ901c0Rmt9LyyICrYVDCIKWilIUBCqG0sFd2ky"
            + "6th0piRp1X9y48aCCz/AjxLvxILVhTiBOXfOuefMI+8fr28AdrBkIotpExkU"
            + "dFUwMGtggSF3KKSIawzLpbor/VAJ3/aUjLmMbUfjQ1wtNxkyjvI5Q74uJD/v"
            + "dVo8bLitgBhLyYbqebfHfbIwFL9j+oLf22cqFkomYrV8Re1dHl6rsOMEwmsz"
            + "pEuaNC9UL/T4idCBa4mkw5sUcOR5PIpESwQifmzwKN66c/uuhRwMCxYmDSwy"
            + "bMck2N32jf23d73pBsLXEsP4SG2dSslDJ3CjiEcMhR83+GrZ+OcWWKEnzkKP"
            + "NJg+Js1jtFpFij4gV9kcgD1TRWeg2UzYXfLsEWNiYti/RqgVo/KC1GX66Zdh"
            + "nwwHxNATDA1FQkaYrQww0p5LyBqZpghTyGM+CaFfihnMwfwEsQXl6iMCAAA=");

    @SuppressWarnings("all") // Sample code
    private TestFile mClickableViewAccessibilityTest2 = base64gzip("bin/classes/test/pkg/ClickableViewAccessibilityTest.class", ""
            + "H4sIAAAAAAAAAKWVa08TQRSG3wPIQkWoiHgBRATlphQEwQuipUUlNsWkBD9v"
            + "lwEGll2ylxr+hv/E+MHED/4Af5Tx3SKmkLbuYjfZZ2fOe86cOZk5/fnr+w8A"
            + "88in0IqMgVkDcwYeCyDoWncc5eVs0/eVb2BeMJx1XOf40A39dadi2np7w9l0"
            + "Q2uvoP1AUWtgQTD0V7RVV/JE0N/IfVHQV99riYGLbpDd0urTOWNJBUEkeSoY"
            + "e2f6H5S343qHOVtbBw2UzwQ955QGngtGi24M7xeC7rNCA8tML0rtow728npn"
            + "R3nKCc5KXtaT/FlircJvAysseiQphWUrKruBV4KO020beC3orJbnZJgVDNau"
            + "kXeVT3HOtO1SeBSluioYiLQ1lrNJ5QTT1ZJWlOfpbeXXJrQaBnSqdRC0L2tH"
            + "B0y0dWJyS9CWc7cVq1nQjiqGh2XlbZplmzOpkht6lnqjo8Fo1TkyRGtlLUv5"
            + "vi5rWwfHm8oPZvbNitmFSUwJxgNOZI4OdjPNfQTpyCtjm85uZqO8ryxOTcZ0"
            + "HpsTvI2r/ceZF6wljlTviAtW4sZplMhy3AD114+9j6b3UFCIGyfOZRUsXTCc"
            + "4H38DcXIY/Fi0RLUtWkD+Z84tZdasJAkzmkrEswmPR6CuUQH8sQnH9enWecT"
            + "5JJsskF7FJSSRInZQzGCFv7ZRj/hw67H9zRHI9UxcGnqG+RL1fyQ7xTlgIE2"
            + "fOXXI363YAbt5Gf0cpbWdAftLeigtpNM8blMdpFXyG6yh0yTV8le8hrZR14n"
            + "+8kb5E3yFnmbHCAHySHyDjlM3iWj5O+Ro+QYeZ98QI6TE0j9BnXS9/dRCAAA");
}
