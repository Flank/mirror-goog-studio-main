<?xml version="1.0" encoding="utf-8"?>
<${getMaterialComponentName('android.support.v4.widget.NestedScrollView', useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
<#if layoutName??>
    xmlns:app="http://schemas.android.com/apk/res-auto"
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    tools:showIn="@layout/${layoutName}"
</#if>
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${activityClass}">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="@dimen/text_margin"
        android:text="@string/large_text" />

</${getMaterialComponentName('android.support.v4.widget.NestedScrollView', useAndroidX)}>
