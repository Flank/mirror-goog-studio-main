package android.view.inputmethod;

import android.mock.MockInputConnection;

public class InputConnectionWrapper implements InputConnection {

    private Object mLock = new Object();
    private InputConnection mInputConnection;

    public InputConnectionWrapper() {
        mInputConnection = new MockInputConnection();
    }

    public InputConnectionWrapper(InputConnection ic, boolean owner) {
        mInputConnection = ic;
    }

    public boolean setComposingText(CharSequence charSequence, int i) {
        return false;
    }

    public boolean commitText(CharSequence charSequence, int i) {
        return false;
    }
}
