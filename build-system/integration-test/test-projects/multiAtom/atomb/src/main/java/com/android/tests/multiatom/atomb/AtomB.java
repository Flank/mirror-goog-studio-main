package com.android.tests.multiatom.atomb;

import android.content.Context;

public class AtomB {
    public static String someString(Context context) {
        return context.getString(R.string.atomb_name);
    }
}
