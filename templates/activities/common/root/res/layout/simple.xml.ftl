<?xml version="1.0" encoding="utf-8"?>
<#if isInstantApp><LinearLayout
<#else><android.support.constraint.ConstraintLayout</#if>
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
<#if hasAppBar && appBarLayoutName??>
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    tools:showIn="@layout/${appBarLayoutName}"
</#if>
    tools:context="${relativePackage}.${activityClass}">

<#if isNewProject!false>
    <TextView
<#if includeCppSupport!false>
        android:id="@+id/sample_text"
</#if>
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        <#if !isInstantApp>app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent"</#if> />

</#if>
<#if isInstantApp></LinearLayout>
<#else></android.support.constraint.ConstraintLayout></#if>
