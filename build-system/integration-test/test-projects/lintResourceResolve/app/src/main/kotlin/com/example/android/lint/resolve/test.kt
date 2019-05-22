package com.example.android.lint.resolve

import android.support.annotation.DrawableRes
import android.support.annotation.StringRes

enum class Foo {
    NOT_STARTED, IN_PROGRESS, PENDING, IN_ERROR, ACCEPTED, REJECTED
}

fun test2(state: Foo) {
    @DrawableRes val imageId = when (state) {
        Foo.NOT_STARTED -> R.drawable.ic_launcher_foreground
        Foo.IN_PROGRESS -> R.drawable.ic_launcher_foreground
        Foo.PENDING -> R.drawable.ic_launcher_foreground
        Foo.IN_ERROR -> R.drawable.ic_launcher_foreground
        Foo.ACCEPTED -> R.drawable.ic_launcher_foreground
        Foo.REJECTED -> R.drawable.ic_launcher_foreground
    }
}


fun test3() {
    @StringRes val foo = R.string.app_name
}
