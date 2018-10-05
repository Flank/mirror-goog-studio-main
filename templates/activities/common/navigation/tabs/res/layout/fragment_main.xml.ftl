<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        xmlns:tools="http://schemas.android.com/tools" >

    <data>
        <variable
            name="viewModel"
            type="${packageName}.ui.main.PageViewModel"/>
    </data>
    <${getMaterialComponentName('android.support.constraint.ConstraintLayout', useAndroidX)}
        android:id="@+id/constraintLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        tools:context="${packageName}.MainActivity$PlaceholderFragment">

        <TextView
            android:id="@+id/section_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/activity_horizontal_margin"
            android:layout_marginTop="@dimen/activity_vertical_margin"
            android:layout_marginEnd="@dimen/activity_horizontal_margin"
            android:layout_marginBottom="@dimen/activity_vertical_margin"
            android:text="@{viewModel.text}"
            app:layout_constraintLeft_toLeftOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

    </${getMaterialComponentName('android.support.constraint.ConstraintLayout', useAndroidX)}>
</layout>