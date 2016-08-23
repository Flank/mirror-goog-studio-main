package com.android.tests.multiatom.atomd;

import android.content.Context;

public class AtomD {
    public static String someString(Context context) {
        return context.getString(R.string.atomd_name);
    }
}
