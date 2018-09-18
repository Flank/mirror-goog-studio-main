<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
<#assign navComponentUsed=navigationType == "Navigation Drawer" || navigationType == "Bottom Navigation">
<#if navComponentUsed>
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
</#if>
    android:id="@+id/mobile_navigation"
    <#if navComponentUsed>app:startDestination="@+id/home_fragment"</#if>>

<#if navComponentUsed>
    <fragment
        android:id="@+id/home_fragment"
        android:name="${packageName}.ui.home.HomeFragment"
        android:label="fragment_home"
        tools:layout="@layout/fragment_home" />

    <fragment
        android:id="@+id/page1_fragment"
        android:name="${packageName}.ui.page1.Page1Fragment"
        android:label="fragment_page1"
        tools:layout="@layout/fragment_page1" />

    <fragment
        android:id="@+id/page2_fragment"
        android:name="${packageName}.ui.page2.Page2Fragment"
        android:label="fragment_page2"
        tools:layout="@layout/fragment_page2" />

</#if>
</navigation>
