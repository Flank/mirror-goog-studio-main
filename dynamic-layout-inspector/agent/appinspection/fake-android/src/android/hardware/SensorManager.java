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

package android.hardware;

import androidx.annotation.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;

public class SensorManager {
    public static final int SENSOR_DELAY_NORMAL = 3;

    private Map<Integer, Sensor> sensors = new HashMap();

    @VisibleForTesting
    public void addSensor(int type, Sensor sensor) {
        sensors.put(type, sensor);
    }

    public Sensor getDefaultSensor(int type) {
        return sensors.get(type);
    }

    public boolean registerListener(
            SensorEventListener sensorEventListener, Sensor sensor, int samplingPeriodUs) {
        sensor.addListener(sensorEventListener);
        return true;
    }
}
