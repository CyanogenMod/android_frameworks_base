/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

/**
 * implementation of PkgUsageStats associated with an
 * application package.
 *  @hide
 */
public class PkgUsageStats implements Parcelable {
    public String packageName;
    public int launchCount;
    public long usageTime;
    public Map<String, Long> componentResumeTimes;
    
    public static final Parcelable.Creator<PkgUsageStats> CREATOR
    = new Parcelable.Creator<PkgUsageStats>() {
        public PkgUsageStats createFromParcel(Parcel in) {
            return new PkgUsageStats(in);
        }

        public PkgUsageStats[] newArray(int size) {
            return new PkgUsageStats[size];
        }
    };
    
    public String toString() {
        return "PkgUsageStats{"
        + Integer.toHexString(System.identityHashCode(this))
        + " " + packageName + "}";
    }
    
    public PkgUsageStats(String pkgName, int count, long time, Map<String, Long> lastResumeTimes) {
        packageName = pkgName;
        launchCount = count;
        usageTime = time;
        componentResumeTimes = new HashMap<String, Long>(lastResumeTimes);
    }
    
    public PkgUsageStats(Parcel source) {
        packageName = source.readString();
        launchCount = source.readInt();
        usageTime = source.readLong();
        final int N = source.readInt();
        componentResumeTimes = new HashMap<String, Long>(N);
        for (int i = 0; i < N; i++) {
            String component = source.readString();
            long lastResumeTime = source.readLong();
            componentResumeTimes.put(component, lastResumeTime);
        }
    }
    
    public PkgUsageStats(PkgUsageStats pStats) {
        packageName = pStats.packageName;
        launchCount = pStats.launchCount;
        usageTime = pStats.usageTime;
        componentResumeTimes = new HashMap<String, Long>(pStats.componentResumeTimes);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(packageName);
        dest.writeInt(launchCount);
        dest.writeLong(usageTime);
        dest.writeInt(componentResumeTimes.size());
        for (Map.Entry<String, Long> ent : componentResumeTimes.entrySet()) {
            dest.writeString(ent.getKey());
            dest.writeLong(ent.getValue());
        }
    }
}
