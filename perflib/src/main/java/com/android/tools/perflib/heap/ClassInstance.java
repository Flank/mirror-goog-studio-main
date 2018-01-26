/*
 * Copyright (C) 2008 Google Inc.
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class ClassInstance extends Instance {

    private final long mValuesOffset;

    public ClassInstance(long id, @NonNull StackTrace stack, long valuesOffset) {
        super(id, stack);
        mValuesOffset = valuesOffset;
    }

    @VisibleForTesting
    @NonNull
    List<FieldValue> getFields(String name) {
        ArrayList<FieldValue> result = new ArrayList<FieldValue>();
        for (FieldValue value : getValues()) {
            if (value.getField().getName().equals(name)) {
                result.add(value);
            }
        }
        return result;
    }

    @NonNull
    public List<FieldValue> getValues() {
        ArrayList<FieldValue> result = new ArrayList<FieldValue>();

        ClassObj clazz = getClassObj();
        getBuffer().setPosition(mValuesOffset);
        while (clazz != null) {
            for (Field field : clazz.getFields()) {
                result.add(new FieldValue(field, readValue(field.getType())));
            }
            clazz = clazz.getSuperClassObj();
        }
        return result;
    }

    @Override
    public final void resolveReferences() {
        for (FieldValue fieldValue : getValues()) {
            if (fieldValue.getValue() instanceof Instance) {
                Instance referencedInstance = (Instance)fieldValue.getValue();
                referencedInstance.addReverseReference(fieldValue.getField(), this);
                if (getIsSoftReference() && fieldValue.getField().getName().equals("referent")) {
                    mSoftForwardReference = referencedInstance;
                } else {
                    mHardForwardReferences.add(referencedInstance);
                }
            }
        }
        mHardForwardReferences.trimToSize(); // Don't wait until the compactMemory stage to trim.
    }

    @Override
    public final void accept(@NonNull Visitor visitor) {
        visitor.visitClassInstance(this);
        for (Instance instance : mHardForwardReferences) {
            visitor.visitLater(this, instance);
        }
    }

    @Override
    public boolean getIsSoftReference() {
        return getClassObj().getIsSoftReference();
    }

    public final String toString() {
        return String
                .format("%s@%d (0x%x)", getClassObj().getClassName(), getUniqueId(), getUniqueId());
    }

    public boolean isStringInstance() {
        return getClassObj() != null && "java.lang.String".equals(getClassObj().getClassName());
    }

    @Nullable
    public final String getAsString() {
        return getAsString(Integer.MAX_VALUE);
    }

    @Nullable
    public final String getAsString(int maxDecodeStringLength) {
        int count = -1;
        int offset = 0;
        ArrayInstance charBufferArray = null;
        // In later versions of Android, the underlying storage format changed to byte buffers.
        ArrayInstance byteBufferArray = null;
        for (ClassInstance.FieldValue entry : getValues()) {
            if (charBufferArray == null && "value".equals(entry.getField().getName())) {
                if (entry.getValue() instanceof ArrayInstance) {
                    if (((ArrayInstance) entry.getValue()).getArrayType() == Type.CHAR) {
                        charBufferArray = (ArrayInstance) entry.getValue();
                    } else if (((ArrayInstance) entry.getValue()).getArrayType() == Type.BYTE) {
                        byteBufferArray = (ArrayInstance) entry.getValue();
                    }
                }
            } else if ("count".equals(entry.getField().getName())) {
                if (entry.getValue() instanceof Integer) {
                    count = (Integer) entry.getValue();
                }
            } else if ("offset".equals(entry.getField().getName())) {
                if (entry.getValue() instanceof Integer) {
                    offset = (Integer) entry.getValue();
                }
            }
        }

        if (byteBufferArray != null) {
            try {
                return new String(
                        byteBufferArray.asRawByteArray(
                                offset >= 0 ? offset : 0,
                                Math.max(Math.min(count, maxDecodeStringLength), 0)),
                        "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        }
        return charBufferArray == null
                ? null
                : new String(
                        charBufferArray.asCharArray(
                                offset >= 0 ? offset : 0,
                                Math.max(Math.min(count, maxDecodeStringLength), 0)));
    }

    public static class FieldValue {

        private Field mField;

        private Object mValue;

        public FieldValue(@NonNull Field field, @Nullable Object value) {
            this.mField = field;
            this.mValue = value;
        }

        public Field getField() {
            return mField;
        }

        public Object getValue() {
            return mValue;
        }
    }
}
