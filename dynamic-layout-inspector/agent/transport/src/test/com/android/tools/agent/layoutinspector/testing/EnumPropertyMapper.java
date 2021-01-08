/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector.testing;

import android.view.inspector.PropertyMapper;
import androidx.annotation.NonNull;
import java.util.Set;
import java.util.function.IntFunction;

/** Wrapper around {@link PropertyMapper} with an enum identifier. */
public class EnumPropertyMapper<E extends Enum<E>> {
    private final PropertyMapper myMapper;
    private final int myOffset;
    private final String myPrefix;

    public EnumPropertyMapper(@NonNull PropertyMapper mapper, int offset) {
        this(mapper, offset, "");
    }

    public EnumPropertyMapper(@NonNull PropertyMapper mapper, int offset, @NonNull String prefix) {
        myMapper = mapper;
        myOffset = offset;
        myPrefix = prefix;
    }

    public void mapBoolean(@NonNull E e) {
        myMapper.mapBoolean(nameOf(e), indexOf(e));
    }

    public void mapByte(@NonNull E e) {
        myMapper.mapByte(nameOf(e), indexOf(e));
    }

    public void mapChar(@NonNull E e) {
        myMapper.mapChar(nameOf(e), indexOf(e));
    }

    public void mapDouble(@NonNull E e) {
        myMapper.mapDouble(nameOf(e), indexOf(e));
    }

    public void mapFloat(@NonNull E e) {
        myMapper.mapFloat(nameOf(e), indexOf(e));
    }

    public void mapInt(@NonNull E e) {
        myMapper.mapInt(nameOf(e), indexOf(e));
    }

    public void mapLong(@NonNull E e) {
        myMapper.mapLong(nameOf(e), indexOf(e));
    }

    public void mapShort(@NonNull E e) {
        myMapper.mapShort(nameOf(e), indexOf(e));
    }

    public void mapObject(@NonNull E e) {
        myMapper.mapObject(nameOf(e), indexOf(e));
    }

    public void mapColor(@NonNull E e) {
        myMapper.mapColor(nameOf(e), indexOf(e));
    }

    public void mapGravity(@NonNull E e) {
        myMapper.mapGravity(nameOf(e), indexOf(e));
    }

    public void mapIntEnum(@NonNull E e, @NonNull IntFunction<String> mapping) {
        myMapper.mapIntEnum(nameOf(e), indexOf(e), mapping);
    }

    public void mapResourceId(@NonNull E e) {
        myMapper.mapResourceId(nameOf(e), indexOf(e));
    }

    public void mapIntFlag(@NonNull E e, @NonNull IntFunction<Set<String>> mapping) {
        myMapper.mapIntFlag(nameOf(e), indexOf(e), mapping);
    }

    /**
     * Takes an enum instance and converts the uppercase name to an Android style attribute name.
     *
     * <p>Example: STATE_LIST_ANIMATOR -> stateListAnimator
     */
    @NonNull
    private String nameOf(@NonNull E e) {
        StringBuilder builder = new StringBuilder();
        builder.append(myPrefix);
        boolean toLower = true;
        for (char ch : e.name().toCharArray()) {
            if (ch == '_') {
                toLower = false;
            } else if (toLower) {
                builder.append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
                toLower = true;
            }
        }
        return builder.toString();
    }

    private int indexOf(@NonNull E e) {
        return myOffset + e.ordinal();
    }
}
