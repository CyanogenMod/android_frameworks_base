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
import android.os.ParcelUuid;

/** {@hide} */
interface IProfileManager
{
    boolean setActiveProfile(in ParcelUuid profileParcelUuid);
    boolean setActiveProfileByName(String profileName);
    Profile getActiveProfile();

    boolean addProfile(in Profile profile);
    boolean removeProfile(in Profile profile);
    void updateProfile(in Profile profile);

    Profile getProfile(in ParcelUuid profileParcelUuid);
    Profile getProfileByName(String profileName);
    Profile[] getProfiles();
    boolean profileExists(in ParcelUuid profileUuid);
    boolean profileExistsByName(String profileName);
    boolean notificationGroupExistsByName(String notificationGroupName);

    NotificationGroup[] getNotificationGroups();
    void addNotificationGroup(in NotificationGroup group);
    void removeNotificationGroup(in NotificationGroup group);
    void updateNotificationGroup(in NotificationGroup group);
    NotificationGroup getNotificationGroupForPackage(in String pkg);
    NotificationGroup getNotificationGroup(in ParcelUuid groupParcelUuid);

    void resetAll();
}
