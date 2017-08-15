package com.android.fakeadbserver.hostcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import java.io.IOException;
import java.net.Socket;

/**
 * host-prefix:killforward-all ADB command removes all port forwarding from this device. This
 * implementation only handles tcp sockets, and not Unix domain sockets.
 */
public class KillForwardAllCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "killforward-all";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        assert device != null;
        device.removeAllPortForwarders();
        try {
            writeOkay(responseSocket.getOutputStream());
        } catch (IOException ignored) {
            return false;
        }
        return true;
    }
}
