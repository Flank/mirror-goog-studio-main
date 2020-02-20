package com.example.app;

class Dummy {
    public void dummy() {
        int x = R.string.used_from_app;
        int y = R.string.overridden_in_feature1;
        android.util.Log.d("lint", "Resource used: " + x + " and " + y);
    }
}
