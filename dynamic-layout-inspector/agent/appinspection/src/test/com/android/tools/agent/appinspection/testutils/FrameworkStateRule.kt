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

package com.android.tools.agent.appinspection.testutils

import android.view.View
import android.view.ViewGroup
import android.view.WindowManagerGlobal
import android.view.inspector.StaticInspectionCompanionProvider
import android.widget.TextView
import com.android.tools.agent.appinspection.testutils.property.companions.TextViewInspectionCompanion
import com.android.tools.agent.appinspection.testutils.property.companions.ViewGroupLayoutParamsInspectionCompanion
import com.android.tools.agent.appinspection.testutils.property.companions.ViewInspectionCompanion
import org.junit.rules.ExternalResource

/**
 * Simple rule for setting up / clearing global framework state between tests.
 */
class FrameworkStateRule : ExternalResource() {
    public override fun before() {
        StaticInspectionCompanionProvider.register(View::class.java, ViewInspectionCompanion())
        StaticInspectionCompanionProvider.register(
            TextView::class.java,
            TextViewInspectionCompanion()
        )
        StaticInspectionCompanionProvider.register(
            ViewGroup.LayoutParams::class.java,
            ViewGroupLayoutParamsInspectionCompanion()
        )
    }

    public override fun after() {
        WindowManagerGlobal.getInstance().rootViews.clear()

        StaticInspectionCompanionProvider.cleanup()
    }

}
