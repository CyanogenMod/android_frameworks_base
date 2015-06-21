/* Copyright 2013, The Android Open Source Project
 * Copyright 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package android.os;

/**
 * {@hide}
 */
public class BatteryProperties implements Parcelable {
    public boolean chargerAcOnline;
    public boolean chargerUsbOnline;
    public boolean chargerWirelessOnline;
    public int batteryStatus;
    public int batteryHealth;
    public boolean batteryPresent;
    public int batteryLevel;
    public int batteryVoltage;
    public int batteryTemperature;
    public String batteryTechnology;

    public boolean dockBatterySupported;
    public boolean chargerDockAcOnline;
    public int dockBatteryStatus;
    public int dockBatteryHealth;
    public boolean dockBatteryPresent;
    public int dockBatteryLevel;
    public int dockBatteryVoltage;
    public int dockBatteryTemperature;
    public String dockBatteryTechnology;

    public BatteryProperties() {
    }

    public void set(BatteryProperties other) {
        chargerAcOnline = other.chargerAcOnline;
        chargerUsbOnline = other.chargerUsbOnline;
        chargerWirelessOnline = other.chargerWirelessOnline;
        batteryStatus = other.batteryStatus;
        batteryHealth = other.batteryHealth;
        batteryPresent = other.batteryPresent;
        batteryLevel = other.batteryLevel;
        batteryVoltage = other.batteryVoltage;
        batteryTemperature = other.batteryTemperature;
        batteryTechnology = other.batteryTechnology;

        dockBatterySupported = other.dockBatterySupported;
        chargerDockAcOnline = other.chargerDockAcOnline;
        dockBatteryStatus = other.dockBatteryStatus;
        dockBatteryHealth = other.dockBatteryHealth;
        dockBatteryPresent = other.dockBatteryPresent;
        dockBatteryLevel = other.dockBatteryLevel;
        dockBatteryVoltage = other.dockBatteryVoltage;
        dockBatteryTemperature = other.dockBatteryTemperature;
        dockBatteryTechnology = other.dockBatteryTechnology;
    }

    /*
     * Parcel read/write code must be kept in sync with
     * frameworks/native/services/batteryservice/BatteryProperties.cpp
     */

    private BatteryProperties(Parcel p) {
        chargerAcOnline = p.readInt() == 1 ? true : false;
        chargerUsbOnline = p.readInt() == 1 ? true : false;
        chargerWirelessOnline = p.readInt() == 1 ? true : false;
        batteryStatus = p.readInt();
        batteryHealth = p.readInt();
        batteryPresent = p.readInt() == 1 ? true : false;
        batteryLevel = p.readInt();
        batteryVoltage = p.readInt();
        batteryTemperature = p.readInt();
        batteryTechnology = p.readString();

        dockBatterySupported = p.readInt() == 1 ? true : false;
        if (dockBatterySupported) {
            chargerDockAcOnline = p.readInt() == 1 ? true : false;
            dockBatteryStatus = p.readInt();
            dockBatteryHealth = p.readInt();
            dockBatteryPresent = p.readInt() == 1 ? true : false;
            dockBatteryLevel = p.readInt();
            dockBatteryVoltage = p.readInt();
            dockBatteryTemperature = p.readInt();
            dockBatteryTechnology = p.readString();
        } else {
            chargerDockAcOnline = false;
            dockBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
            dockBatteryHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN;
            dockBatteryPresent = false;
            dockBatteryLevel = 0;
            dockBatteryVoltage = 0;
            dockBatteryTemperature = 0;
            dockBatteryTechnology = "";
        }
    }

    public void writeToParcel(Parcel p, int flags) {
        p.writeInt(chargerAcOnline ? 1 : 0);
        p.writeInt(chargerUsbOnline ? 1 : 0);
        p.writeInt(chargerWirelessOnline ? 1 : 0);
        p.writeInt(batteryStatus);
        p.writeInt(batteryHealth);
        p.writeInt(batteryPresent ? 1 : 0);
        p.writeInt(batteryLevel);
        p.writeInt(batteryVoltage);
        p.writeInt(batteryTemperature);
        p.writeString(batteryTechnology);

        p.writeInt(dockBatterySupported ? 1 : 0);
        if (dockBatterySupported) {
            p.writeInt(chargerDockAcOnline ? 1 : 0);
            p.writeInt(dockBatteryStatus);
            p.writeInt(dockBatteryHealth);
            p.writeInt(dockBatteryPresent ? 1 : 0);
            p.writeInt(dockBatteryLevel);
            p.writeInt(dockBatteryVoltage);
            p.writeInt(dockBatteryTemperature);
            p.writeString(dockBatteryTechnology);
        }
    }

    public static final Parcelable.Creator<BatteryProperties> CREATOR
        = new Parcelable.Creator<BatteryProperties>() {
        public BatteryProperties createFromParcel(Parcel p) {
            return new BatteryProperties(p);
        }

        public BatteryProperties[] newArray(int size) {
            return new BatteryProperties[size];
        }
    };

    public int describeContents() {
        return 0;
    }
}
