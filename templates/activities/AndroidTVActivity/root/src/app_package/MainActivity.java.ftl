package ${packageName};

import android.app.Activity;
import android.os.Bundle;

/*
 * Main Activity class that loads {@link ${mainFragment}}.
 */
public class ${activityClass} extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
    }
}
