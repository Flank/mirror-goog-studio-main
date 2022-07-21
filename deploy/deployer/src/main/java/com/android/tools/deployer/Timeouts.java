/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deployer;

import java.util.concurrent.TimeUnit;

public class Timeouts {

    private static final long T_5_SECONDS = TimeUnit.SECONDS.toMillis(5);
    private static final long T_5_MINUTES = TimeUnit.MINUTES.toMillis(5);

    static final long CMD_DUMP_MS = T_5_SECONDS;
    static final long CMD_SWAP_MS = T_5_MINUTES;
    static final long CMD_OSWAP_MS = T_5_MINUTES;
    static final long CMD_OINSTALL_MS = T_5_MINUTES;
    static final long CMD_ROOT_PUSH_INSTALL = T_5_MINUTES;
    static final long CMD_VERIFY_OID_MS = T_5_SECONDS;
    static final long CMD_DELTA_PREINSTALL_MS = T_5_MINUTES;
    static final long CMD_DELTA_INSTALL_MS = T_5_MINUTES;
    static final long CMD_UPDATE_LL = T_5_SECONDS;
    static final long CMD_LIVE_EDIT = T_5_SECONDS;
    static final long CMD_INSTALL_COROUTINE = T_5_SECONDS;
    static final long CMD_NETTEST = T_5_MINUTES;
    static final long CMD_TIMEOUT = T_5_SECONDS;

    static final long SHELL_MKDIR = T_5_SECONDS;
    static final long SHELL_RMFR = T_5_SECONDS;
    static final long SHELL_CHMOD = T_5_SECONDS;
    static final long SHELL_AM_STOP = T_5_SECONDS;
    static final long SHELL_ABORT_INSTALL_MS = T_5_SECONDS;
}
