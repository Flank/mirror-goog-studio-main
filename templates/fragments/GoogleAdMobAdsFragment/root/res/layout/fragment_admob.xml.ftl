<${getMaterialComponentName('android.support.constraint.ConstraintLayout', useAndroidX)}
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="${packageName}.${fragmentClass}">

<#if adFormat == "banner">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/hello_world"
        app:layout_constraintBottom_toTopOf="@id/adView"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- view for AdMob Banner Ad -->
    <com.google.android.gms.ads.AdView
        android:id="@+id/adView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:layout_centerHorizontal="true"
        app:adSize="BANNER"
        app:adUnitId="@string/banner_ad_unit_id"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />
<#elseif adFormat == "interstitial">
    <!-- view for AdMob Interstitial Ad -->
    <TextView
        android:id="@+id/app_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="50dp"
        android:text="@string/interstitial_ad_sample"
        android:textAppearance="?android:attr/textAppearanceLarge"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toTopOf="@id/level" />

    <TextView
        android:id="@+id/level"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/start_level"
        android:textAppearance="?android:attr/textAppearanceLarge"
        app:layout_constraintTop_toBottomOf="@id/app_title"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toTopOf="@id/next_level_button" />

    <Button
        android:id="@+id/next_level_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/next_level"
        app:layout_constraintTop_toBottomOf="@id/level"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />
</#if>
</${getMaterialComponentName('android.support.constraint.ConstraintLayout', useAndroidX)}>
