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

import org.junit.Test;

public class JdwpClientManagerIdTest {
  @Test
  public void idEquality() {
    String serial = "DEVICE_SERIAL";
    int pid = 1234;
    JdwpClientManagerId connectionId = new JdwpClientManagerId(serial, pid);
    assertThat(connectionId).isEqualTo(new JdwpClientManagerId(serial, pid));
    assertThat(connectionId).isNotEqualTo(new JdwpClientManagerId(serial, 1111));
  }

  @Test
  public void idHashCodes() {
    String serial = "DEVICE_SERIAL";
    int pid = 1234;
    JdwpClientManagerId connectionIdA = new JdwpClientManagerId(serial, pid);
    JdwpClientManagerId connectionIdB = new JdwpClientManagerId(serial, pid);
    JdwpClientManagerId connectionIdC = new JdwpClientManagerId("Something", pid);
    JdwpClientManagerId connectionIdD = new JdwpClientManagerId(serial, 1111);
    assertThat(connectionIdA.hashCode()).isEqualTo(connectionIdB.hashCode());
    assertThat(connectionIdA.hashCode()).isNotEqualTo(connectionIdC.hashCode());
    assertThat(connectionIdA.hashCode()).isNotEqualTo(connectionIdD.hashCode());
  }
}
