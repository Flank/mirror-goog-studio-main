package android.com.java.profilertester.fragment;

import android.com.java.profilertester.R;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * A simple fragment view that has a button which allows the activity switch to another fragment.
 *
 * <p>The activity must implement {@link NavigateToNextFragmentListener} for it to work with this
 * fragment.
 */
public class FragmentB extends Fragment implements View.OnClickListener {
    @Nullable NavigateToNextFragmentListener navigateListener;

    public FragmentB() {}

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_b, container, false);
        Button navToA = view.findViewById(R.id.button_nav_to_a);
        navToA.setOnClickListener(this);

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof NavigateToNextFragmentListener) {
            navigateListener = (NavigateToNextFragmentListener) context;
        }
    }

    @Override
    public void onClick(View v) {
        if (navigateListener != null) {
            navigateListener.onNavigateRequested();
        }
    }
}
