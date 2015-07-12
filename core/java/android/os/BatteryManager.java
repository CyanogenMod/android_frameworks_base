/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2015 The CyanogenMod Project
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

package android.os;

import android.app.IBatteryService;
import android.content.Context;
import android.os.BatteryProperty;
import android.os.IBatteryPropertiesRegistrar;
import android.os.RemoteException;
import android.os.ServiceManager;

/**
 * The BatteryManager class contains strings and constants used for values
 * in the {@link android.content.Intent#ACTION_BATTERY_CHANGED} Intent, and
 * provides a method for querying battery and charging properties.
 */
public class BatteryManager {
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current status constant.
     */
    public static final String EXTRA_STATUS = "status";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current health constant.
     */
    public static final String EXTRA_HEALTH = "health";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * boolean indicating whether a battery is present.
     */
    public static final String EXTRA_PRESENT = "present";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer field containing the current battery level, from 0 to
     * {@link #EXTRA_SCALE}.
     */
    public static final String EXTRA_LEVEL = "level";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the maximum battery level.
     */
    public static final String EXTRA_SCALE = "scale";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the resource ID of a small status bar icon
     * indicating the current battery state.
     */
    public static final String EXTRA_ICON_SMALL = "icon-small";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer indicating whether the device is plugged in to a power
     * source; 0 means it is on battery, other constants are different
     * types of power sources.
     */
    public static final String EXTRA_PLUGGED = "plugged";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current battery voltage level.
     */
    public static final String EXTRA_VOLTAGE = "voltage";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current battery temperature.
     */
    public static final String EXTRA_TEMPERATURE = "temperature";
    
    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * String describing the technology of the current battery.
     */
    public static final String EXTRA_TECHNOLOGY = "technology";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current dock status constant.
     * @hide
     */
    public static final String EXTRA_DOCK_STATUS = "dock_status";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current dock health constant.
     * @hide
     */
    public static final String EXTRA_DOCK_HEALTH = "dock_health";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * boolean indicating whether a dock battery is present.
     * @hide
     */
    public static final String EXTRA_DOCK_PRESENT = "dock_present";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer field containing the current dock battery level, from 0 to
     * {@link #EXTRA_SCALE}.
     * @hide
     */
    public static final String EXTRA_DOCK_LEVEL = "dock_level";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the maximum dock battery level.
     * @hide
     */
    public static final String EXTRA_DOCK_SCALE = "dock_scale";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the resource ID of a small status bar icon
     * indicating the current dock battery state.
     * @hide
     */
    public static final String EXTRA_DOCK_ICON_SMALL = "dock_icon-small";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer indicating whether the device is plugged in to a dock power
     * source.
     * @hide
     */
    public static final String EXTRA_DOCK_PLUGGED = "dock_plugged";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current dock battery voltage level.
     * @hide
     */
    public static final String EXTRA_DOCK_VOLTAGE = "dock_voltage";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * integer containing the current dock battery temperature.
     * @hide
     */
    public static final String EXTRA_DOCK_TEMPERATURE = "dock_temperature";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * String describing the technology of the current dock battery.
     * @hide
     */
    public static final String EXTRA_DOCK_TECHNOLOGY = "dock_technology";

    /**
     * Extra for {@link android.content.Intent#ACTION_BATTERY_CHANGED}:
     * Int value set to nonzero if an unsupported charger is attached
     * to the device.
     * {@hide}
     */
    public static final String EXTRA_INVALID_CHARGER = "invalid_charger";

    // values for "status" field in the ACTION_BATTERY_CHANGED Intent
    public static final int BATTERY_STATUS_UNKNOWN = 1;
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_DISCHARGING = 3;
    public static final int BATTERY_STATUS_NOT_CHARGING = 4;
    public static final int BATTERY_STATUS_FULL = 5;

    // values for "health" field in the ACTION_BATTERY_CHANGED Intent
    public static final int BATTERY_HEALTH_UNKNOWN = 1;
    public static final int BATTERY_HEALTH_GOOD = 2;
    public static final int BATTERY_HEALTH_OVERHEAT = 3;
    public static final int BATTERY_HEALTH_DEAD = 4;
    public static final int BATTERY_HEALTH_OVER_VOLTAGE = 5;
    public static final int BATTERY_HEALTH_UNSPECIFIED_FAILURE = 6;
    public static final int BATTERY_HEALTH_COLD = 7;

    // values of the "plugged" field in the ACTION_BATTERY_CHANGED intent.
    // These must be powers of 2.
    /** Power source is an AC charger. */
    public static final int BATTERY_PLUGGED_AC = 1;
    /** Power source is a USB port. */
    public static final int BATTERY_PLUGGED_USB = 2;
    /** Power source is wireless. */
    public static final int BATTERY_PLUGGED_WIRELESS = 4;

    // values of the "dock_plugged" field in the ACTION_BATTERY_CHANGED intent.
    // These must be powers of 2.
    /** Power source is an DockAC charger.
     * @hide*/
    public static final int BATTERY_DOCK_PLUGGED_AC = 1;
    /** Power source is an DockUSB charger.
     * @hide*/
    public static final int BATTERY_DOCK_PLUGGED_USB = 2;

    /** @hide */
    public static final int BATTERY_PLUGGED_ANY =
            BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB | BATTERY_PLUGGED_WIRELESS;

    /** @hide */
    public static final int BATTERY_DOCK_PLUGGED_ANY =
            BATTERY_DOCK_PLUGGED_AC | BATTERY_DOCK_PLUGGED_USB;

    /*
     * Battery property identifiers.  These must match the values in
     * frameworks/native/include/batteryservice/BatteryService.h
     */
    /** Battery capacity in microampere-hours, as an integer. */
    public static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;

    /**
     * Instantaneous battery current in microamperes, as an integer.  Positive
     * values indicate net current entering the battery from a charge source,
     * negative values indicate net current discharging from the battery.
     */
    public static final int BATTERY_PROPERTY_CURRENT_NOW = 2;

    /**
     * Average battery current in microamperes, as an integer.  Positive
     * values indicate net current entering the battery from a charge source,
     * negative values indicate net current discharging from the battery.
     * The time period over which the average is computed may depend on the
     * fuel gauge hardware and its configuration.
     */
    public static final int BATTERY_PROPERTY_CURRENT_AVERAGE = 3;

    /**
     * Remaining battery capacity as an integer percentage of total capacity
     * (with no fractional part).
     */
    public static final int BATTERY_PROPERTY_CAPACITY = 4;

    /**
     * Battery remaining energy in nanowatt-hours, as a long integer.
     */
    public static final int BATTERY_PROPERTY_ENERGY_COUNTER = 5;

    private final IBatteryService mBatteryService;
    private IBatteryPropertiesRegistrar mBatteryPropertiesRegistrar;

    public BatteryManager() {
        this(null);
    }

    /** @hide */
    public BatteryManager(IBatteryService service) {
        super();
        mBatteryService = service;
    }

    /** @hide */
    public boolean isDockBatterySupported() {
        try {
            return mBatteryService != null && mBatteryService.isDockBatterySupported();
        } catch (RemoteException ex) {
            // Ignore
        }
        return false;
    }

    /**
     * Query a battery property from the batteryproperties service.
     *
     * Returns the requested value, or Long.MIN_VALUE if property not
     * supported on this system or on other error.
     * fromDock determines if the property is query from the normal battery
     * or the dock battery.
     */
    private long queryProperty(int id, boolean fromDock) {
        long ret;

        if (mBatteryPropertiesRegistrar == null) {
            IBinder b = ServiceManager.getService("batteryproperties");
            mBatteryPropertiesRegistrar =
                IBatteryPropertiesRegistrar.Stub.asInterface(b);

            if (mBatteryPropertiesRegistrar == null)
                return Long.MIN_VALUE;
        }

        try {
            BatteryProperty prop = new BatteryProperty();

            final int callResult;
            if (!fromDock) {
                callResult = mBatteryPropertiesRegistrar.getProperty(id, prop);
            } else {
                callResult = mBatteryPropertiesRegistrar.getDockProperty(id, prop);
            }
            if (callResult == 0)
                ret = prop.getLong();
            else
                ret = Long.MIN_VALUE;
        } catch (RemoteException e) {
            ret = Long.MIN_VALUE;
        }

        return ret;
    }

    /**
     * Return the value of a battery property of integer type.  If the
     * platform does not provide the property queried, this value will
     * be Integer.MIN_VALUE.
     *
     * @param id identifier of the requested property
     *
     * @return the property value, or Integer.MIN_VALUE if not supported.
     */
    public int getIntProperty(int id) {
        return (int)queryProperty(id, false);
    }

    /**
     * Return the value of a battery property of long type If the
     * platform does not provide the property queried, this value will
     * be Long.MIN_VALUE.
     *
     * @param id identifier of the requested property
     *
     * @return the property value, or Long.MIN_VALUE if not supported.
     */
    public long getLongProperty(int id) {
        return queryProperty(id, false);
    }

    /**
     * Return the value of a dock battery property of integer type.  If the
     * platform does not provide the property queried, this value will
     * be Integer.MIN_VALUE.
     *
     * @param id identifier of the requested property
     *
     * @return the property value, or Integer.MIN_VALUE if not supported.
     * @hide
     */
    public int getIntDockProperty(int id) {
        if (!isDockBatterySupported()) {
            return Integer.MIN_VALUE;
        }
        return (int)queryProperty(id, true);
    }

    /**
     * Return the value of a dock battery property of long type If the
     * platform does not provide the property queried, this value will
     * be Long.MIN_VALUE.
     *
     * @param id identifier of the requested property
     *
     * @return the property value, or Long.MIN_VALUE if not supported.
     * @hide
     */
    public long getLongDockProperty(int id) {
        if (!isDockBatterySupported()) {
            return Long.MIN_VALUE;
        }
        return queryProperty(id, true);
    }
}
