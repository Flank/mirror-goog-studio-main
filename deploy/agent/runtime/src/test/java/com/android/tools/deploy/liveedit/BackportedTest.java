/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.liveedit.Utils.buildClass;

import com.android.tools.deploy.liveedit.backported.Byte;
import com.android.tools.deploy.liveedit.backported.Math;
import com.android.tools.deploy.liveedit.backported.Short;
import com.android.tools.deploy.liveedit.backported.StrictMath;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class BackportedTest {

    static {
        LiveEditStubs.init(TestException.class.getClassLoader());
    }

    private static String className = "BackportedInvoke";
    private static Class<BackportedInvoke> clazz = BackportedInvoke.class;
    private static byte[] byteCode;

    static {
        try {
            byteCode = buildClass(clazz);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testByteCompareUnsigned() throws Exception {
        String methodName = "compareByteUnsigned";
        String methodDesc = "(BB)I";
        try (MockedStatic<Byte> mocked = Mockito.mockStatic(Byte.class)) {
            int expected = -9;
            byte v0 = 1;
            byte v1 = 1;
            mocked.when(() -> Byte.compareUnsigned(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testShortCompareUnsigned() throws Exception {
        String methodName = "compareShortUnsigned";
        String methodDesc = "(SS)I";
        try (MockedStatic<Short> mocked = Mockito.mockStatic(Short.class)) {
            int expected = -9;
            short v0 = 1;
            short v1 = 1;
            mocked.when(() -> Short.compareUnsigned(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testMathMultiplyExact() throws Exception {
        String methodName = "mathMultiplyExact";
        String methodDesc = "(JI)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            long v0 = 1;
            int v1 = 1;
            mocked.when(() -> Math.multiplyExact(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testStrictMathMultiplyExact() throws Exception {
        String methodName = "strictMathMultiplyExact";
        String methodDesc = "(JI)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            long v0 = 1;
            int v1 = 1;
            mocked.when(() -> StrictMath.multiplyExact(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testMathMultiplyFull() throws Exception {
        String methodName = "mathMultiplyFull";
        String methodDesc = "(II)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            int v0 = 1;
            int v1 = 1;
            mocked.when(() -> Math.multiplyFull(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testStrictMathMultiplyFull() throws Exception {
        String methodName = "strictMathMultiplyFull";
        String methodDesc = "(II)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            int v0 = 1;
            int v1 = 1;
            mocked.when(() -> StrictMath.multiplyFull(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testMathMultiplyHigh() throws Exception {
        String methodName = "mathMultiplyHigh";
        String methodDesc = "(JJ)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            long v0 = 1;
            long v1 = 1;
            mocked.when(() -> Math.multiplyHigh(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testStrictMathMultiplyHigh() throws Exception {
        String methodName = "strictMathMultiplyHigh";
        String methodDesc = "(JJ)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            long v0 = 1;
            long v1 = 1;
            mocked.when(() -> StrictMath.multiplyHigh(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testMathFloorDiv() throws Exception {
        String methodName = "mathFloorDiv";
        String methodDesc = "(JI)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            long v0 = 1;
            int v1 = 1;
            mocked.when(() -> Math.floorDiv(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testStrictMathFloorDiv() throws Exception {
        String methodName = "strictMathFloorDiv";
        String methodDesc = "(JI)J";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            long expected = -9;
            long v0 = 1;
            int v1 = 1;
            mocked.when(() -> StrictMath.floorDiv(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testMathFloorMod() throws Exception {
        String methodName = "mathFloorMod";
        String methodDesc = "(JI)I";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            int expected = -9;
            long v0 = 1;
            int v1 = 1;
            mocked.when(() -> Math.floorMod(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }

    @Test
    public void testStrictMathFloorMod() throws Exception {
        String methodName = "strictMathFloorMod";
        String methodDesc = "(JI)I";
        try (MockedStatic<Math> mocked = Mockito.mockStatic(Math.class)) {
            int expected = -9;
            long v0 = 1;
            int v1 = 1;
            mocked.when(() -> StrictMath.floorMod(v0, v1)).thenReturn(expected);
            LiveEditStubs.addClass(className, byteCode, false);
            LiveEditStubs.addLiveEditedMethod(className, methodName, methodDesc);
            Object[] parameters = {null, null, v0, v1};
            Object r = LiveEditStubs.stubL(className, methodName, methodDesc, parameters);
            Assert.assertEquals("Bad " + methodName + " invocation", expected, r);
        } finally {
            LiveEditStubs.deleteClass(className);
        }
    }
}
