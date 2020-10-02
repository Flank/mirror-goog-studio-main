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

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.FakeAdbTestRule;
import com.android.fakeadbserver.DeviceState;
import java.io.IOException;
import java.nio.channels.Selector;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JdwpClientManagerFactoryTest {

  private JdwpClientManagerFactory myFactory;
  @Rule
  public FakeAdbTestRule myAdb = new FakeAdbTestRule();

  @Before
  public void setup() throws IOException {
    Selector selector = Selector.open();
        myFactory = new JdwpClientManagerFactory(selector);
  }

  @Test
  public void duplicateKeyReturnsSameInstance() throws Exception {
    DeviceState state = myAdb.connectAndWaitForDevice();
    ClientImpl client = FakeAdbTestRule.launchAndWaitForProcess(state, true);
    JdwpClientManager connection =
      myFactory.createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, client.getClientData().getPid()));
    assertThat(myFactory.createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, client.getClientData().getPid())))
      .isEqualTo(connection);
    try {
      assertThat(myFactory.createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, 5555))).isNotEqualTo(connection);
    }
    catch (Exception ex) {
      assertThat(ex).isInstanceOf(AdbCommandRejectedException.class);
      assertThat(ex.getMessage()).endsWith("5555");
    }
  }

  @Test
  public void terminatingConnectionRemovesItFromFactory() throws Exception {
    DeviceState state = myAdb.connectAndWaitForDevice();
    ClientImpl client = FakeAdbTestRule.launchAndWaitForProcess(state, true);
    JdwpClientManager connection =
      myFactory.createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, client.getClientData().getPid()));
    assertThat(myFactory.createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, client.getClientData().getPid())))
      .isEqualTo(connection);
    connection.shutdown();
    assertThat(myFactory.createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, client.getClientData().getPid())))
      .isNotEqualTo(connection);
  }
}
