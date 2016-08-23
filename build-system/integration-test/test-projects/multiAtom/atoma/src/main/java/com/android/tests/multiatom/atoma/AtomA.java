package com.android.tests.multiatom.atoma;

import android.content.Context;

public class AtomA {
    public static String someString(Context context) {
        return context.getString(R.string.atoma_name);
    }
}
