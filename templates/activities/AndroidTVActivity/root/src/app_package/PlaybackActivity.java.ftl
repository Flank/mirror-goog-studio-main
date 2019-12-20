package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName('android.support.v4.app.FragmentActivity', useAndroidX)};

/** Loads {@link PlaybackVideoFragment}. */
public class PlaybackActivity extends FragmentActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if( savedInstanceState == null ){
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new PlaybackVideoFragment())
                    .commit();
        }
    }
}
