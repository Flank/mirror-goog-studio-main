/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.common.rendering.api;

import java.net.URL;

/**
 * Callback for project information needed by the Layout Library. Classes implementing this
 * interface provide methods giving access to some project data, like resource resolution, namespace
 * information, and instantiation of custom view.
 *
 * @deprecated use {@link LayoutlibCallback}
 */
@Deprecated
public interface IProjectCallback {

    enum ViewAttribute {
        TEXT(String.class),
        IS_CHECKED(Boolean.class),
        SRC(URL.class),
        COLOR(Integer.class);

        private final Class<?> mClass;

        ViewAttribute(Class<?> theClass) {
            mClass = theClass;
        }

        public Class<?> getAttributeClass() {
            return mClass;
        }
    }
}
