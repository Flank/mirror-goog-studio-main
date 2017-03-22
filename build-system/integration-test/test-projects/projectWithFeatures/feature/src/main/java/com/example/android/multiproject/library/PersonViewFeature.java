package com.example.android.multiproject.library;

import android.content.Context;
import android.widget.TextView;

class PersonViewFeature extends TextView {
    public PersonViewFeature(Context context, String name) {
        super(context);
        setTextSize(20);
        setText(name);
    }
}
