package android.view.inputmethod;

import android.mock.MockInputConnection;
import java.lang.ref.WeakReference;

public class InputConnectionWrapper implements InputConnection {

    private Object mLock = new Object();
    private WeakReference<InputConnection> mInputConnection;
    private MockInputConnection mMockConnection;

    public InputConnectionWrapper() {
        mMockConnection = new MockInputConnection();
        mInputConnection = new WeakReference<>(mMockConnection);
    }

    public InputConnectionWrapper(InputConnection ic, boolean mutable) {
        mInputConnection = new WeakReference<>(ic);
    }

    public void setTarget(InputConnection connection) {
        mInputConnection = new WeakReference<>(connection);
    }

    public boolean setComposingText(CharSequence charSequence, int i) {
        return false;
    }

    public boolean commitText(CharSequence charSequence, int i) {
        return false;
    }

    // Function to expose underlaying InputConnection used only by test
    public InputConnection getConnection() {
        return mInputConnection.get();
    }
}
