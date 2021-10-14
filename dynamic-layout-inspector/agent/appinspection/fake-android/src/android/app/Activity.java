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

package android.app;

import android.content.Context;
import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.View;
import java.util.HashMap;

public class Activity extends Context implements View.OnCreateContextMenuListener {

    public Activity() {
        super("myPackage", new Resources(new HashMap<Integer, String>()));
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
        throw new UnsupportedOperationException();
    }
}
