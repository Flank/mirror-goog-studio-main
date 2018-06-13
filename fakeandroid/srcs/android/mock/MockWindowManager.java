package android.mock;

import android.view.Display;
import android.view.WindowManager;

public class MockWindowManager implements WindowManager {

    @Override
    public Display getDefaultDisplay() {
        return new Display();
    }
}
