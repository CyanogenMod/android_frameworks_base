/**
 * Copyright (c) 2014, The Android Open Source Project
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
package com.android.server.notification;

public interface RankingConfig {

    int getPriority(String packageName, int uid);

    void setPriority(String packageName, int uid, int priority);

    int getVisibilityOverride(String packageName, int uid);

    void setVisibilityOverride(String packageName, int uid, int visibility);

    void setImportance(String packageName, int uid, int importance);

    int getImportance(String packageName, int uid);

    void setPackageVisibilityOverride(String packageName, int uid, int visibility);

    void setShowNotificationForPackageOnKeyguard(String packageName, int uid, int status);

    int getShowNotificationForPackageOnKeyguard(String packageName, int uid);

}
