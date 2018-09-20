package android.com.java.profilertester.fragment;

import android.com.java.profilertester.R;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;

/**
 * An empty activity whose purpose is to own and navigate between multiple fragments, for testing
 * the profiling of fragments.
 */
public class FragmentHostActivity extends AppCompatActivity
        implements NavigateToNextFragmentListener {
    Fragment[] fragments =
            new Fragment[] {
                new FragmentA(), new FragmentB(),
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_host);

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.add(R.id.fragment_container, fragments[0]);
        transaction.commit();
    }

    @Override
    public void onNavigateRequested() {
        toggleActiveFragment();
    }

    private void toggleActiveFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment toHide = fm.getFragments().get(0);
        Fragment toShow = (fragments[0] == toHide) ? fragments[1] : fragments[0];

        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.fragment_container, toShow);
        transaction.commit();
    }
}
