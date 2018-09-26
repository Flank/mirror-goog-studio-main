<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto" >

    <data>
        <variable
            name="viewModel"
            type="${packageName}.ui.${navFragmentPrefix}.${navViewModelClass}"/>
    </data>

    <${getMaterialComponentName('android.support.constraint.ConstraintLayout', useAndroidX)}
        android:layout_width="match_parent"
        android:layout_height="match_parent" >
 
        <TextView
            android:id="@+id/text_${navFragmentPrefix}"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:layout_marginTop="8dp"
            android:layout_marginEnd="8dp"
            android:text="@{viewModel.text}"
            android:textAlignment="center"
            android:textSize="20sp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent"/>
    </${getMaterialComponentName('android.support.constraint.ConstraintLayout', useAndroidX)}>

</layout>