package com.android.builder.compiling

sealed class BuildConfigClassField {
    class StringField(val name: String, val value: String) : BuildConfigClassField()
    class IntField(val name: String, val value: Int) : BuildConfigClassField()
    class DebugField(val name: String, val value : Boolean) : BuildConfigClassField()
}