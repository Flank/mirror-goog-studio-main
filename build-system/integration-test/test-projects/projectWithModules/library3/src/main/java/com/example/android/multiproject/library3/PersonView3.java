package com.example.android.multiproject.library3;

import android.content.Context;
import android.widget.TextView;

class PersonView3 extends TextView {
    public PersonView3(Context context, String name) {
        super(context);
        setTextSize(20);
        setText(name);
    }
}
