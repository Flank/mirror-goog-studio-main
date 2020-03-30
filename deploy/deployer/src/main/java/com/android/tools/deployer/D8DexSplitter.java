/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.inspector.ClassInspector;
import com.android.tools.r8.inspector.FieldInspector;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.ValueInspector;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.tracer.Trace;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class D8DexSplitter implements DexSplitter {

    /** @param keepCode Needs to be threadsafe. */
    @Override
    public Collection<DexClass> split(ApkEntry dex, Predicate<DexClass> keepCode) {
        try (Trace ignored = Trace.begin("split " + dex.getName())) {
            D8Command.Builder newBuilder = D8Command.builder();
            DexConsumer consumer = new DexConsumer(dex, keepCode);
            newBuilder.addDexProgramData(readDex(dex), Origin.unknown());
            newBuilder.setDexClassChecksumFilter(consumer::parseFilter);
            newBuilder.addOutputInspection(consumer);
            newBuilder.setProgramConsumer(consumer);
            D8.run(newBuilder.build());
            consumer.join();

            return consumer.classes.values().stream()
                    .map(
                            dexClass -> {
                                Collection<Deploy.ClassDef.FieldReInitState> states =
                                        consumer.variableStates.get(dexClass.name);
                                if (states == null) {
                                    return dexClass;
                                } else {
                                    return new DexClass(dexClass, ImmutableList.copyOf(states));
                                }
                            })
                    .collect(Collectors.toList());

        } catch (InterruptedException | CompilationFailedException e) {
            throw new RuntimeException(e);
        }
    }

    protected byte[] readDex(ApkEntry dex) {
        // TODO Check if opening the file several times matters
        try (ZipFile file = new ZipFile(dex.getApk().path)) {
            ZipEntry entry = file.getEntry(dex.getName());
            return ByteStreams.toByteArray(file.getInputStream(entry));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class DexConsumer implements DexFilePerClassFileConsumer, Consumer<Inspector> {
        private final Map<String, DexClass> classes = new HashMap<>();

        // Note D8 does NOT guarantee any thread / ordering / duplicates of the inspection API.
        // We must compute both the classes (filtering out what we don't need) and the variables
        // separately and reduce them at the end after the compilation is done.
        private final Multimap<String, Deploy.ClassDef.FieldReInitState> variableStates =
                ArrayListMultimap.create();

        private final CountDownLatch finished = new CountDownLatch(1);
        private final Predicate<DexClass> keepCode;
        private final ApkEntry dex;

        private DexConsumer(ApkEntry dex, Predicate<DexClass> keepCode) {
            this.dex = dex;
            this.keepCode = keepCode;
        }

        @Override
        public void finished(DiagnosticsHandler handler) {
            finished.countDown();
        }

        public void join() throws InterruptedException {
            finished.await();
        }

        /**
         * Performs a filter of Java classes during parse time. If we can already decide if we are
         * not keeping the code of this class, we tell the compiler to skip parsing the rest of the
         * class body.
         */
        public boolean parseFilter(String classDescriptor, Long checksum) {
            DexClass c =
                    new DexClass(
                            typeNameToClassName(classDescriptor),
                            checksum == null ? 0 : checksum,
                            null,
                            dex);

            // D8 is free to use multiple thread to parse (although it mostly do it base on number of dex input). We can
            // potentially have multiple parsing calling us back.
            synchronized (this) {
                classes.put(classDescriptor, c);
            }
            if (keepCode != null) {
                return keepCode.test(c);
            } else {
                return false;
            }
        }

        @Override
        public synchronized void accept(
                String name,
                ByteDataView data,
                Set<String> descriptors,
                DiagnosticsHandler handler) {
            DexClass clazz = classes.get(name);
            String className = typeNameToClassName(name);

            // It is possible that some classes has no checksum information. They would not appear on the previous filter step.
            if (clazz == null) {
                CRC32 crc = new CRC32();
                crc.update(data.getBuffer(), data.getOffset(), data.getLength());
                long newChecksum = crc.getValue();
                clazz = new DexClass(className, newChecksum, null, dex);
                classes.put(name, clazz);
            }

            if (keepCode != null && keepCode.test(clazz)) {
                classes.put(
                        name, new DexClass(className, clazz.checksum, data.copyByteData(), dex));
            }
        }

        @Override
        public void accept(Inspector inspector) {
            inspector.forEachClass(
                    classInspector -> {
                        classInspector.forEachField(
                                fieldInspector -> {
                                    inspectField(classInspector, fieldInspector);
                                });
                    });
        }

        private void inspectField(ClassInspector classInspector, FieldInspector fieldInspector) {
            Deploy.ClassDef.FieldReInitState.Builder state =
                    Deploy.ClassDef.FieldReInitState.newBuilder();
            FieldReference field = fieldInspector.getFieldReference();
            state.setName(field.getFieldName());
            state.setType(field.getFieldType().getDescriptor());
            state.setStaticVar(fieldInspector.isStatic());
            Optional<ValueInspector> value = fieldInspector.getInitialValue();
            if (fieldInspector.isStatic() && fieldInspector.isFinal() && value.isPresent()) {
                state.setState(Deploy.ClassDef.FieldReInitState.VariableState.CONSTANT);
                ValueInspector valueInspector = value.get();
                if (valueInspector.isByteValue()) {
                    state.setValue(Byte.toString(value.get().asByteValue().getByteValue()));
                } else if (valueInspector.isCharValue()) {
                    state.setValue(Character.toString(value.get().asCharValue().getCharValue()));
                } else if (valueInspector.isIntValue()) {
                    state.setValue(Integer.toString(value.get().asIntValue().getIntValue()));
                } else if (valueInspector.isLongValue()) {
                    state.setValue(Long.toString(value.get().asLongValue().getLongValue()));
                } else if (valueInspector.isShortValue()) {
                    state.setValue(Short.toString(value.get().asShortValue().getShortValue()));
                } else if (valueInspector.isDoubleValue()) {
                    state.setValue(Double.toString(value.get().asDoubleValue().getDoubleValue()));
                } else if (valueInspector.isFloatValue()) {
                    state.setValue(Float.toString(value.get().asFloatValue().getFloatValue()));
                } else if (valueInspector.isBooleanValue()) {
                    state.setValue(
                            Boolean.toString(value.get().asBooleanValue().getBooleanValue()));
                }
            } else {
                state.setState(Deploy.ClassDef.FieldReInitState.VariableState.UNKNOWN);
            }
            // D8 can call this in any threads.
            synchronized (this) {
                variableStates.put(
                        typeNameToClassName(classInspector.getClassReference().getDescriptor()),
                        state.build());
            }
        }
    }

    /** VM type names to the more readable class names. */
    private static String typeNameToClassName(String typeName) {
        assert typeName.startsWith("L");
        assert typeName.endsWith(";");
        return typeName.substring(1, typeName.length() - 1).replace('/', '.');
    }
}
