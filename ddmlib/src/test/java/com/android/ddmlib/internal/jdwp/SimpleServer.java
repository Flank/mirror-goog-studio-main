/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmlib.internal.jdwp;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

class SimpleServer implements Runnable {

    private ServerSocketChannel mListenChannel;

    private int mListenPort = 0;

    protected ByteBuffer mData = ByteBuffer.allocate(1024);

    private SocketChannel mClient = null;

    protected byte[] mConnectMessage;

    SimpleServer() throws IOException {
        this(new byte[0]);
    }

    SimpleServer(String onConnectMessage) throws IOException {
        this(onConnectMessage.getBytes());
    }

    SimpleServer(byte[] onConnectMessage) throws IOException {
        mConnectMessage = onConnectMessage;
        mListenChannel = ServerSocketChannel.open();
        InetSocketAddress addr =
                new InetSocketAddress(
                        InetAddress.getByName("localhost"), // $NON-NLS-1$
                        0);
        mListenChannel.socket().setReuseAddress(true); // enable SO_REUSEADDR
        mListenChannel.socket().bind(addr);
        mListenPort = mListenChannel.socket().getLocalPort();
    }

    public ServerSocketChannel getServerSocket() {
        return mListenChannel;
    }

    public int getPort() {
        return mListenPort;
    }

    public ByteBuffer getData() {
        return mData;
    }

    public SocketChannel getClient() {
        return mClient;
    }

    @Override
    public void run() {
        try {
            mClient = mListenChannel.accept();
            mClient.write(ByteBuffer.wrap(mConnectMessage));
            mClient.read(mData);
        } catch (Exception ex) {
            // End thread.
        }
    }
}
