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
import android.os.Bundle;
import com.android.internal.app.IAppOpsCallback;

interface IAppOpsService {
    // These first methods are also called by native code, so must
    // be kept in sync with frameworks/native/include/binder/IAppOpsService.h
    int checkOperation(int code, int uid, String packageName);
    int noteOperation(int code, int uid, String packageName);
    int startOperation(IBinder token, int code, int uid, String packageName);
    void finishOperation(IBinder token, int code, int uid, String packageName);
    void startWatchingMode(int op, String packageName, IAppOpsCallback callback);
    void stopWatchingMode(IAppOpsCallback callback);
    IBinder getToken(IBinder clientToken);

    // Remaining methods are only used in Java.
    int checkPackage(int uid, String packageName);
    List<AppOpsManager.PackageOps> getPackagesForOps(in int[] ops);
    List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName, in int[] ops);
    void setMode(int code, int uid, String packageName, int mode);
    void resetAllModes();
    int checkAudioOperation(int code, int usage, int uid, String packageName);
    void setAudioRestriction(int code, int usage, int uid, int mode, in String[] exceptionPackages);

    void setUserRestrictions(in Bundle restrictions, int userHandle);
    void removeUser(int userHandle);
    boolean isControlAllowed(int code, String packageName);

    // Privacy guard methods
    boolean getPrivacyGuardSettingForPackage(int uid, String packageName);
    void setPrivacyGuardSettingForPackage(int uid, String packageName, boolean state);

    // AppOps accounting
    void resetCounters();
}
