package com.android.tests.multiatom.libc;

import android.content.Context;

public class LibC {
    public static String someString(Context context) {
        return context.getString(R.string.libc_name);
    }
}
