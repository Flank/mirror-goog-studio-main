<?xml version="1.0" encoding="utf-8"?>
<layout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools" >

    <${getMaterialComponentName('android.support.design.widget.CoordinatorLayout', useAndroidX)}
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        tools:context="${packageName}.${activityClass}">

        <${getMaterialComponentName('android.support.design.widget.AppBarLayout', useMaterial2)}
            android:layout_height="wrap_content"
            android:layout_width="match_parent"
            android:theme="@style/${themeNameAppBarOverlay}">

            <${getMaterialComponentName('android.support.v7.widget.Toolbar', useAndroidX)}
                android:id="@+id/toolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize"
                android:background="?attr/colorPrimary"
                app:popupTheme="@style/${themeNamePopupOverlay}" />

        </${getMaterialComponentName('android.support.design.widget.AppBarLayout', useMaterial2)}>

        <include 
            android:id="@+id/content_main"
            layout="@layout/${simpleLayoutName}"/>

<#if navigationType != "Bottom Navigation" >
        <${getMaterialComponentName('android.support.design.widget.FloatingActionButton', useMaterial2)}
            android:id="@+id/fab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|end"
            android:layout_margin="@dimen/fab_margin"
            app:srcCompat="@android:drawable/ic_dialog_email" />
</#if>
    </${getMaterialComponentName('android.support.design.widget.CoordinatorLayout', useAndroidX)}>
</layout>