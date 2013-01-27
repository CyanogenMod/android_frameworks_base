/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.internal.os;

import android.content.Intent;

public interface DeviceDockBatteryHandler {

    /**
     * Invoked when the system request an update of the dock battery status. Device should
     * access specific sysfs and read status, present and level of the device dock battery.
     */
    public void handleUpdate();

    /**
     * Invoked when the system request an update of the dock battery status. Device should
     * access specific sysfs and read status, present and level of the device dock battery.
     * Device should communicate the status of his battery dock (if was changed) sending a
     * {@link Intent#ACTION_DOCK_BATTERY_CHANGED}.
     */
    public void handleProcessValues();

    /**
     * Method that returns if the dock battery is plugged (is powering device main battery)
     *
     * @return If the dock battery is plugged
     */
    public boolean isPlugged();
}
