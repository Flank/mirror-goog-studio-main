<?xml version="1.0" encoding="utf-8"?>
<#-- TODO: Switch Instant Apps back to ConstraintLayout once library dependency bugs are resolved -->
<#if isInstantApp><LinearLayout
<#else><android.support.constraint.ConstraintLayout</#if>
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
<#if hasAppBar && appBarLayoutName??>
    tools:showIn="@layout/${appBarLayoutName}"
</#if>
    tools:context="${relativePackage}.${fragmentClass}">

<#if isNewProject!false>
    <TextView
<#if includeCppSupport!false>
        android:id="@+id/sample_text"
</#if>
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        <#-- TODO: Switch Instant Apps back to ConstraintLayout once library dependency bugs are resolved -->
        <#if !isInstantApp>app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent"</#if> />

</#if>
<#if isInstantApp></LinearLayout>
<#else></android.support.constraint.ConstraintLayout></#if>
