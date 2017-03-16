<?xml version="1.0" encoding="utf-8"?>
<#-- TODO: Switch Instant Apps back to ConstraintLayout once library dependency bugs are resolved -->
<#if isInstantApp><LinearLayout
<#else><android.support.constraint.ConstraintLayout</#if>
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${activityClass}">

    <TextView
        android:id="@+id/message"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="16dp"
        android:text="@string/title_home"
        <#-- TODO: Switch Instant Apps back to ConstraintLayout once library dependency bugs are resolved -->
        <#if !isInstantApp>app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintTop_toTopOf="parent" </#if>/>

    <android.support.design.widget.BottomNavigationView
        android:id="@+id/navigation"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginEnd="0dp"
        android:layout_marginStart="0dp"
        android:background="?android:attr/windowBackground"
        <#-- TODO: Switch Instant Apps back to ConstraintLayout once library dependency bugs are resolved -->
        <#if !isInstantApp>app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"</#if>
        app:menu="@menu/navigation" />

<#if isInstantApp></LinearLayout>
<#else></android.support.constraint.ConstraintLayout></#if>
