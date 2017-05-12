package android.com.java.profilertester.evnet;

import android.app.Activity;
import android.com.java.profilertester.R;
import android.os.Bundle;
import android.view.Menu;


public class EmptyActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_empty);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

}
