/**
 * Copyright (C) 2012 Svyatoslav Hresyk
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses>.
 */

package android.privacy;
import android.privacy.PrivacySettings;

/** {@hide} */
interface IPrivacySettingsManager
{
    PrivacySettings getSettings(String packageName);
    boolean saveSettings(in PrivacySettings settings);
    boolean deleteSettings(String packageName);
    void notification(String packageName, byte accessMode, String dataType, String output);
    void registerObservers();
    void addObserver(String packageName);
    boolean purgeSettings();
    boolean setEnabled(boolean enable);
    boolean setNotificationsEnabled(boolean enable);
    void setBootCompleted();
}
