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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.res.layout

fun fragmentReflowXml() = """
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageView
        android:id="@+id/image_view_reflow_1"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:srcCompat="@drawable/avatar_1"
        tools:src="@tools:sample/avatars" />

    <ImageView
        android:id="@+id/image_view_reflow_2"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:srcCompat="@drawable/avatar_2"
        tools:src="@tools:sample/avatars" />

    <ImageView
        android:id="@+id/image_view_reflow_3"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:srcCompat="@drawable/avatar_3"
        tools:src="@tools:sample/avatars" />

    <ImageView
        android:id="@+id/image_view_reflow_4"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:srcCompat="@drawable/avatar_4"
        tools:src="@tools:sample/avatars" />

    <androidx.constraintlayout.widget.Guideline
        android:id="@+id/guideline_flow"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        app:layout_constraintGuide_percent="0.5" />

    <androidx.constraintlayout.helper.widget.Flow
        android:id="@+id/flow_images"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="8dp"
        android:layout_marginBottom="8dp"
        android:orientation="horizontal"
        app:constraint_referenced_ids="image_view_reflow_1,image_view_reflow_2,image_view_reflow_3,image_view_reflow_4"
        app:flow_horizontalAlign="start"
        app:flow_maxElementsWrap="@integer/flow_images_max_elements"
        app:flow_verticalGap="8dp"
        app:flow_wrapMode="aligned"
        app:layout_constraintBottom_toBottomOf="@id/guideline_flow"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/text_block1"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintWidth_max="wrap"
        app:layout_constraintWidth_percent="@dimen/reflow_text_block_max_percent">

        <TextView
            android:id="@+id/text_block1_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:text="@string/lorem_ipsum_title"
            android:textSize="@dimen/reflow_text_title_size"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/text_block1_description"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/lorem_ipsum"
            android:textSize="@dimen/reflow_text_description_size"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/text_block1_title" />
    </androidx.constraintlayout.widget.ConstraintLayout>

    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/text_block2"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintWidth_max="wrap"
        app:layout_constraintWidth_percent="@dimen/reflow_text_block_max_percent" >

        <TextView
            android:id="@+id/text_block2_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:text="@string/lorem_ipsum_title"
            android:textSize="@dimen/reflow_text_title_size"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/text_block2_description"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/lorem_ipsum"
            android:textSize="@dimen/reflow_text_description_size"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/text_block2_title"
            app:layout_constraintWidth_max="wrap"
            app:layout_constraintWidth_percent="0.4" />
    </androidx.constraintlayout.widget.ConstraintLayout>

    <androidx.constraintlayout.helper.widget.Flow
        android:id="@+id/flow_texts"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginTop="8dp"
        android:layout_marginBottom="8dp"
        android:orientation="horizontal"
        app:constraint_referenced_ids="text_block1,text_block2"
        app:flow_horizontalAlign="start"
        app:flow_maxElementsWrap="@integer/flow_images_max_elements"
        app:flow_verticalGap="8dp"
        app:flow_verticalBias="0"
        app:flow_verticalAlign="top"
        app:flow_horizontalGap="@dimen/reflow_text_block_horizontal_gap"
        app:flow_wrapMode="aligned"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/guideline_flow"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintVertical_bias="0" />
</androidx.constraintlayout.widget.ConstraintLayout>
"""
