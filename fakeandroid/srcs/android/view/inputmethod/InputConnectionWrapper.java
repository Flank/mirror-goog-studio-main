package android.view.inputmethod;

import android.mock.MockInputConnection;
import java.lang.ref.WeakReference;

public class InputConnectionWrapper implements InputConnection {

    private Object mLock = new Object();
    private WeakReference<InputConnection> mInputConnection;
    private MockInputConnection mMockConnection;

    public InputConnectionWrapper() {
        mMockConnection = new MockInputConnection();
        mInputConnection = new WeakReference<InputConnection>(mMockConnection);
    }

    public InputConnectionWrapper(InputConnection ic, boolean mutable) {
        mInputConnection = new WeakReference<InputConnection>(ic);
    }

    public void setTarget(InputConnection connection) {
        mInputConnection = new WeakReference<InputConnection>(connection);
    }

    public boolean setComposingText(CharSequence charSequence, int i) {
        return false;
    }

    public boolean commitText(CharSequence charSequence, int i) {
        return false;
    }

    @Override
    public void closeConnection() {
        mInputConnection.get().closeConnection();
    }

    // Function to expose underlaying InputConnection used only by test
    public InputConnection getConnection() {
        return mInputConnection.get();
    }
}
