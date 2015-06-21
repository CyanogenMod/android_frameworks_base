/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public final class DockBatteryStatsImpl extends BatteryStatsImpl {
    public DockBatteryStatsImpl() {
        super();
    }

    public DockBatteryStatsImpl(Parcel p) {
        super(p);
    }

    public DockBatteryStatsImpl(File systemDir, Handler handler, ExternalStatsSync externalSync) {
        super(systemDir, handler, externalSync);
    }

    protected String getStatsName() {
        return "dockbatterystats";
    }

    protected String getLogName() {
        return "DockBatteryStats";
    }

    public static final Parcelable.Creator<DockBatteryStatsImpl> CREATOR =
        new Parcelable.Creator<DockBatteryStatsImpl>() {
        public DockBatteryStatsImpl createFromParcel(Parcel in) {
            return new DockBatteryStatsImpl(in);
        }

        public DockBatteryStatsImpl[] newArray(int size) {
            return new DockBatteryStatsImpl[size];
        }
    };
}
