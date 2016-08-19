package com.android.tests.multiatom.atomc;

import android.content.Context;

public class AtomC {
    public static String someString(Context context) {
        return context.getString(R.string.atomc_name);
    }
}
