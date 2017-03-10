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

package com.android.tools.perflib.heap;

import com.android.testutils.TestResources;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.hprof.Hprof;
import com.android.tools.perflib.heap.hprof.HprofClassDump;
import com.android.tools.perflib.heap.hprof.HprofConstant;
import com.android.tools.perflib.heap.hprof.HprofDumpRecord;
import com.android.tools.perflib.heap.hprof.HprofHeapDump;
import com.android.tools.perflib.heap.hprof.HprofHeapDumpInfo;
import com.android.tools.perflib.heap.hprof.HprofInstanceDump;
import com.android.tools.perflib.heap.hprof.HprofInstanceField;
import com.android.tools.perflib.heap.hprof.HprofLoadClass;
import com.android.tools.perflib.heap.hprof.HprofRecord;
import com.android.tools.perflib.heap.hprof.HprofStackFrame;
import com.android.tools.perflib.heap.hprof.HprofStackTrace;
import com.android.tools.perflib.heap.hprof.HprofStaticField;
import com.android.tools.perflib.heap.hprof.HprofStringBuilder;
import com.android.tools.perflib.heap.hprof.HprofType;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.proguard.ProguardMap;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import junit.framework.TestCase;

public class HprofParserTest extends TestCase {
    private static final String TEST_MAP =
            "class.that.is.Empty -> a:\n"
                    + "class.that.is.Empty$subclass -> b:\n"
                    + "class.with.only.Fields -> c:\n"
                    + "    int prim_type_field -> a\n"
                    + "    int[] prim_array_type_field -> b\n"
                    + "    class.that.is.Empty class_type_field -> c\n"
                    + "    class.that.is.Empty[] array_type_field -> d\n"
                    + "    int longObfuscatedNameField -> abc\n"
                    + "class.with.Methods -> d:\n"
                    + "    int some_field -> a\n"
                    + "    12:23:void <clinit>() -> <clinit>\n"
                    + "    42:43:void boringMethod() -> m\n"
                    + "    45:48:void methodWithPrimArgs(int,float) -> m\n"
                    + "    49:50:void methodWithPrimArrArgs(int[],float) -> m\n"
                    + "    52:55:void methodWithClearObjArg(class.not.in.Map) -> m\n"
                    + "    57:58:void methodWithClearObjArrArg(class.not.in.Map[]) -> m\n"
                    + "    59:61:void methodWithObfObjArg(class.with.only.Fields) -> m\n"
                    + "    64:66:class.with.only.Fields methodWithObfRes() -> n\n"
                    + "    80:80:void lineObfuscatedMethod():8:8 -> o\n"
                    + "    90:90:void lineObfuscatedMethod2():9 -> p\n"
                    + "    120:121:void method.from.a.Superclass.supermethod() -> q\n";

    Snapshot mSnapshot;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        File file = TestResources.getFile(getClass(), "/dialer.android-hprof");
        mSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        mSnapshot.resolveReferences();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mSnapshot.dispose();
        mSnapshot = null;
    }

    public void testHierarchy() {
        ClassObj application = mSnapshot.findClass("android.app.Application");
        assertNotNull(application);

        ClassObj contextWrapper = application.getSuperClassObj();
        assertNotNull(contextWrapper);
        assertEquals("android.content.ContextWrapper", contextWrapper.getClassName());
        contextWrapper.getSubclasses().contains(application);

        ClassObj context = contextWrapper.getSuperClassObj();
        assertNotNull(context);
        assertEquals("android.content.Context", context.getClassName());
        context.getSubclasses().contains(contextWrapper);

        ClassObj object = context.getSuperClassObj();
        assertNotNull(object);
        assertEquals("java.lang.Object", object.getClassName());
        object.getSubclasses().contains(context);

        ClassObj none = object.getSuperClassObj();
        assertNull(none);
    }

    public void testClassLoaders() {
        ClassObj application = mSnapshot.findClass("android.app.Application");
        assertNull(application.getClassLoader());

        ClassObj dialer = mSnapshot.findClass("com.android.dialer.DialerApplication");
        Instance classLoader = dialer.getClassLoader();
        assertNotNull(classLoader);
        assertEquals("dalvik.system.PathClassLoader", classLoader.getClassObj().getClassName());
    }

    public void testPrimitiveArrays() {
        ClassObj byteArray = mSnapshot.findClass("byte[]");
        assertEquals(1406, byteArray.getInstancesList().size());
        assertEquals(0, byteArray.getInstanceSize());
        assertEquals(681489, byteArray.getShallowSize());

        ArrayInstance byteArrayInstance = (ArrayInstance) mSnapshot.findInstance(0xB0D60401);
        assertEquals(byteArray, byteArrayInstance.getClassObj());
        assertEquals(43224, byteArrayInstance.getSize());
        assertEquals(43224, byteArrayInstance.getCompositeSize());

        ClassObj intArrayArray = mSnapshot.findClass("int[][]");
        assertEquals(37, intArrayArray.getInstancesList().size());

        ArrayInstance intArrayInstance = (ArrayInstance) mSnapshot.findInstance(0xB0F69F58);
        assertEquals(intArrayArray, intArrayInstance.getClassObj());
        assertEquals(40, intArrayInstance.getSize());
        assertEquals(52, intArrayInstance.getCompositeSize());

        ClassObj stringArray = mSnapshot.findClass("java.lang.String[]");
        assertEquals(1396, stringArray.getInstancesList().size());
    }

    /**
     * Tests the creation of an Enum class which covers static values, fields of type references,
     * strings and primitive values.
     */
    public void testObjectConstruction() {
        ClassObj clazz = mSnapshot.findClass("java.lang.Thread$State");
        assertNotNull(clazz);

        Object object = clazz.getStaticField(Type.OBJECT, "$VALUES");
        assertTrue(object instanceof ArrayInstance);
        ArrayInstance array = (ArrayInstance) object;
        Object[] values = array.getValues();
        assertEquals(6, values.length);

        Collection<Instance> instances = clazz.getInstancesList();
        for (Object value : values) {
            assertTrue(value instanceof Instance);
            assertTrue(instances.contains(value));
        }

        Object enumValue = clazz.getStaticField(Type.OBJECT, "NEW");
        assertTrue(enumValue instanceof ClassInstance);
        ClassInstance instance = (ClassInstance) enumValue;
        assertSame(clazz, instance.getClassObj());

        List<ClassInstance.FieldValue> fields = instance.getFields("name");
        assertEquals(1, fields.size());
        assertEquals(Type.OBJECT, fields.get(0).getField().getType());
        Object name = fields.get(0).getValue();

        assertTrue(name instanceof ClassInstance);
        ClassInstance string = (ClassInstance) name;
        assertEquals("java.lang.String", string.getClassObj().getClassName());
        fields = string.getFields("value");
        assertEquals(1, fields.size());
        assertEquals(Type.OBJECT, fields.get(0).getField().getType());
        Object value = fields.get(0).getValue();
        assertTrue(value instanceof ArrayInstance);
        Object[] data = ((ArrayInstance) value).getValues();
        assertEquals(3, data.length);
        assertEquals('N', data[0]);
        assertEquals('E', data[1]);
        assertEquals('W', data[2]);

        fields = instance.getFields("ordinal");
        assertEquals(1, fields.size());
        assertEquals(Type.INT, fields.get(0).getField().getType());
        assertEquals(0, fields.get(0).getValue());
    }

    /**
     * Tests getValues to make sure it's not adding duplicate entries to the back references.
     */
    public void testDuplicateEntries() {
        mSnapshot = new SnapshotBuilder(2).addReferences(1, 2).addRoot(1).build();
        mSnapshot.computeDominators();

        assertEquals(2, mSnapshot.getReachableInstances().size());
        ClassInstance parent = (ClassInstance)mSnapshot.findInstance(1);
        List<ClassInstance.FieldValue> firstGet = parent.getValues();
        List<ClassInstance.FieldValue> secondGet = parent.getValues();
        assertEquals(1, firstGet.size());
        assertEquals(firstGet.size(), secondGet.size());
        Instance child = mSnapshot.findInstance(2);
        assertEquals(1, child.getHardReverseReferences().size());
    }

    public void testResolveReferences() {
        mSnapshot = new SnapshotBuilder(1).addRoot(1).build();
        ClassObj subSoftReferenceClass = new ClassObj(98, null, "SubSoftReference", 0);
        subSoftReferenceClass.setSuperClassId(SnapshotBuilder.SOFT_REFERENCE_ID);
        ClassObj subSubSoftReferenceClass = new ClassObj(97, null, "SubSubSoftReference", 0);
        subSubSoftReferenceClass.setSuperClassId(98);

        mSnapshot.findClass(SnapshotBuilder.SOFT_REFERENCE_ID).addSubclass(subSoftReferenceClass);
        subSoftReferenceClass.addSubclass(subSubSoftReferenceClass);

        mSnapshot.addClass(98, subSoftReferenceClass);
        mSnapshot.addClass(97, subSubSoftReferenceClass);

        mSnapshot.identifySoftReferences();

        assertTrue(subSoftReferenceClass.getIsSoftReference());
        assertTrue(subSubSoftReferenceClass.getIsSoftReference());
    }

    public void testHprofParser() throws IOException, ParseException {
        // Set up a heap dump with a single stack frame, stack trace, class,
        // and instance to test deobfuscation.
        HprofStringBuilder strings = new HprofStringBuilder(0);
        List<HprofRecord> records = new ArrayList<HprofRecord>();
        List<HprofDumpRecord> dump = new ArrayList<HprofDumpRecord>();

        final int classSerialNumber = 1;
        final int classObjectId = 2;
        records.add(new HprofLoadClass(0, classSerialNumber, classObjectId, 0, strings.get("d")));
        dump.add(
                new HprofClassDump(
                        classObjectId,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        4,
                        new HprofConstant[0],
                        new HprofStaticField[0],
                        new HprofInstanceField[] {
                            new HprofInstanceField(strings.get("a"), HprofType.TYPE_INT)
                        }));

        records.add(
                new HprofStackFrame(
                        0,
                        1,
                        strings.get("m"),
                        strings.get("()V"),
                        strings.get("SourceFile.java"),
                        classSerialNumber,
                        43));
        records.add(new HprofStackTrace(0, 0x52, 1, new long[] {1}));

        dump.add(new HprofHeapDumpInfo(0xA, strings.get("heapA")));

        ByteArrayDataOutput values = ByteStreams.newDataOutput();
        values.writeInt(42);
        dump.add(new HprofInstanceDump(0xA1, 0x52, classObjectId, values.toByteArray()));
        records.add(new HprofHeapDump(0, dump.toArray(new HprofDumpRecord[0])));

        // TODO: When perflib can handle the case where strings are referred to
        // before they are defined, just add the string records to the records
        // list.
        List<HprofRecord> actualRecords = new ArrayList<HprofRecord>();
        actualRecords.addAll(strings.getStringRecords());
        actualRecords.addAll(records);

        Hprof hprof = new Hprof("JAVA PROFILE 1.0.3", 2, new Date(), actualRecords);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        hprof.write(os);
        InMemoryBuffer buffer = new InMemoryBuffer(os.toByteArray());

        ProguardMap map = new ProguardMap();
        map.readFromReader(new StringReader(TEST_MAP));

        Snapshot snapshot = Snapshot.createSnapshot(buffer, map);

        ClassInstance inst = (ClassInstance) snapshot.findInstance(0xA1);
        ClassObj cls = inst.getClassObj();
        assertEquals("class.with.Methods", cls.getClassName());

        Field[] fields = cls.getFields();
        assertEquals(1, fields.length);
        assertEquals("some_field", fields[0].getName());

        StackTrace stack = inst.getStack();
        StackFrame[] frames = stack.getFrames();
        assertEquals(1, frames.length);
        assertEquals("boringMethod", frames[0].getMethodName());
        assertEquals("()V", frames[0].getSignature());
        assertEquals("Methods.java", frames[0].getFilename());
        assertEquals(43, frames[0].getLineNumber());
    }
}
