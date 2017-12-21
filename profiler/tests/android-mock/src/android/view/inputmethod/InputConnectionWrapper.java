package android.view.inputmethod;

import android.mock.MockInputConnection;

public class InputConnectionWrapper implements InputConnection {

    private Object mLock = new Object();
    private InputConnection mInputConnection;

    public InputConnectionWrapper() {
        mInputConnection = new MockInputConnection();
    }

    public InputConnectionWrapper(InputConnection ic, boolean mutable) {
        mInputConnection = ic;
    }

    public void setTarget(InputConnection connection) {
        mInputConnection = connection;
    }

    public boolean setComposingText(CharSequence charSequence, int i) {
        return false;
    }

    public boolean commitText(CharSequence charSequence, int i) {
        return false;
    }

    // Function to expose underlaying InputConnection used only by test
    public InputConnection getConnection() {
        return mInputConnection;
    }
}
