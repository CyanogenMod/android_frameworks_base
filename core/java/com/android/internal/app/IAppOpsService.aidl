/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.app;

import android.app.AppOpsManager;
import com.android.internal.app.IAppOpsCallback;

interface IAppOpsService {
    // These first methods are also called by native code, so must
    // be kept in sync with frameworks/native/include/binder/IAppOpsService.h
    int checkOperation(int code, int uid, String packageName);
    int noteOperation(int code, int uid, String packageName);
    int startOperation(int code, int uid, String packageName);
    void finishOperation(int code, int uid, String packageName);
    void startWatchingMode(int op, String packageName, IAppOpsCallback callback);
    void stopWatchingMode(IAppOpsCallback callback);

    // Remaining methods are only used in Java.
    List<AppOpsManager.PackageOps> getPackagesForOps(in int[] ops);
    List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName, in int[] ops);
    void setMode(int code, int uid, String packageName, int mode);
    void resetAllModes();
    void resetCounters();

    // Privacy guard methods
    boolean getPrivacyGuardSettingForPackage(int uid, String packageName);
    void setPrivacyGuardSettingForPackage(int uid, String packageName, boolean state);
}
