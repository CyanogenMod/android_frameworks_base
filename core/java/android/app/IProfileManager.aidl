/* //device/java/android/android/app/IProfileManager.aidl
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.app;

import android.app.Profile;
import android.app.NotificationGroup;

/** {@hide} */
interface IProfileManager
{
    void setActiveProfile(in String profileName);
    Profile getActiveProfile();
    void addProfile(in Profile profile);
    void removeProfile(in Profile profile);
    Profile getProfile(String profileName);
    Profile[] getProfiles();
    void persist();

    NotificationGroup[] getNotificationGroups();
    void addNotificationGroup(in NotificationGroup group);
    void removeNotificationGroup(in NotificationGroup group);
    NotificationGroup getNotificationGroupForPackage(in String pkg);
    NotificationGroup getNotificationGroup(in String name);
 }

