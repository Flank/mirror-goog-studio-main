package com.android.test.lint;

import android.os.Bundle;
import com.android.test.applib_library03.LintMainActivity;

public class MainActivity extends LintMainActivity implements Runnable {
    public void run() { }

    public MainActivity() {
        super(false, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
