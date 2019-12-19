package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName('android.support.v4.content.ContextCompat', useAndroidX)};
import android.util.Log;
import android.view.View;

/*
 * This class demonstrates how to extend ErrorFragment
 */
public class ErrorFragment extends ${getMaterialComponentName('android.support.v17.leanback.app.ErrorFragment', useAndroidX)} {
    private static final String TAG = "ErrorFragment";
    private static final boolean TRANSLUCENT = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setTitle(getResources().getString(R.string.app_name));
    }

    void setErrorContent() {
        setImageDrawable(ContextCompat.getDrawable(<#if minApiLevel gte 23>getContext()<#else>getActivity()</#if>, R.drawable.lb_ic_sad_cloud));
        setMessage(getResources().getString(R.string.error_fragment_message));
        setDefaultBackground(TRANSLUCENT);

        setButtonText(getResources().getString(R.string.dismiss_error));
        setButtonClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        getFragmentManager().beginTransaction().remove(ErrorFragment.this).commit();
                    }
                });
    }
}
