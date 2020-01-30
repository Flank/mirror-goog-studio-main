<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/${navigationGraphName}"
    app:startDestination="@id/${firstFragmentClass}">

    <fragment
        android:id="@+id/${firstFragmentClass}"
        android:name="${packageName}.${firstFragmentClass}"
        android:label="@string/first_fragment_label"
        tools:layout="@layout/${firstFragmentLayoutName}" >

        <action
            android:id="@+id/action_${firstFragmentClass}_to_${secondFragmentClass}"
            app:destination="@id/${secondFragmentClass}" />
    </fragment>
    <fragment
        android:id="@+id/${secondFragmentClass}"
        android:name="${packageName}.${secondFragmentClass}"
        android:label="@string/second_fragment_label"
        tools:layout="@layout/${secondFragmentLayoutName}" >

        <action
            android:id="@+id/action_${secondFragmentClass}_to_${firstFragmentClass}"
            app:destination="@id/${firstFragmentClass}" />
    </fragment>
</navigation>
