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
package com.android.resources.base;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.TextResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Resource item representing a value resource, e.g. a string or a color.
 */
public class BasicTextValueResourceItem extends BasicValueResourceItem implements TextResourceValue {
  private final String myRawXmlValue;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param textValue the text value associated with the resource
   * @param rawXmlValue the raw xml value associated with the resource (see {@link ResourceValue#getRawXmlValue()})
   */
  public BasicTextValueResourceItem(@NotNull ResourceType type,
                                    @NotNull String name,
                                    @NotNull ResourceSourceFile sourceFile,
                                    @NotNull ResourceVisibility visibility,
                                    @Nullable String textValue,
                                    @Nullable String rawXmlValue) {
    super(type, name, sourceFile, visibility, textValue);
    myRawXmlValue = rawXmlValue;
  }

  @Override
  @Nullable
  public String getRawXmlValue() {
    return myRawXmlValue == null ? getValue() : myRawXmlValue;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicTextValueResourceItem other = (BasicTextValueResourceItem) obj;
    return Objects.equals(myRawXmlValue, other.myRawXmlValue);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), Objects.hashCode(myRawXmlValue));
  }
}
