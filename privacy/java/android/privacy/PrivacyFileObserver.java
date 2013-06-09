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

import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.StringTokenizer;

/** {@hide} */
public final class PrivacyFileObserver extends FileObserver {

	public static final String TAG = "PrivacyFileObserver";
	public static final int PACKAGE_PATH_INDEX = 3;
	public static final int SETTINGS_TYPE_INDEX = PACKAGE_PATH_INDEX + 1;

	public String absolutePath;
	private PrivacySettingsManagerService pSetManServ;

	public HashMap<String, PrivacyFileObserver> children;

	public PrivacyFileObserver(String path,
			PrivacySettingsManagerService pSetManServ) {
		super(path, FileObserver.ALL_EVENTS);
		this.absolutePath = path;
		this.pSetManServ = pSetManServ;

		this.children = new HashMap<String, PrivacyFileObserver>();
		File thisFile = new File(absolutePath);
		if (thisFile.isDirectory()) {
			File[] subfiles = thisFile.listFiles();
			for (File file : subfiles) {
				String observePath = file.getAbsolutePath();
				PrivacyFileObserver child = new PrivacyFileObserver(
						observePath, pSetManServ);
				children.put(observePath, child);
				// don't watch directories, only the settings files
				if (file.isFile())
					child.startWatching();
			}
		}

	}

	@Override
	public void onEvent(int event, String path) {
		if ((FileObserver.ACCESS & event) != 0) { // data was read from a file
		// Log.d(TAG, "onEvent - file accessed: " + absolutePath);
			StringTokenizer tokenizer = new StringTokenizer(absolutePath, "/");
			for (int i = 0; i < PACKAGE_PATH_INDEX
					&& tokenizer.hasMoreElements(); i++) {
				tokenizer.nextToken();
			}

			// get the package and UID of accessing application
			String packageName = tokenizer.nextToken();
			String settingsType = null;
			if (tokenizer.hasMoreElements())
				settingsType = tokenizer.nextToken();
			// int uid = 0;
			// try {
			// uid = Integer.parseInt(tokenizer.nextToken());
			// } catch (NumberFormatException e) {
			// Log.e(TAG,
			// "onEvent - could not get the UID of accessing application", e);
			// // we still can continue, UID is optional here
			// }

			// read the setting
			try {
				if (settingsType != null
						&& settingsType.equals("ipTableProtectSetting")) {
					PrivacySettings pSet = pSetManServ.getSettings(packageName);
					pSetManServ.notification(packageName,
							pSet.getIpTableProtectSetting(),
							PrivacySettings.DATA_IP_TABLES, null);
				} else {
					PrivacySettings pSet = pSetManServ.getSettings(packageName);
					pSetManServ.notification(packageName,
							pSet.getSystemLogsSetting(),
							PrivacySettings.DATA_SYSTEM_LOGS, null);
				}
			} catch (Exception e) {
				// nothing here
			}
		}

	}

	public void addObserver(String relativePath) {
		String observePath = absolutePath + "/" + relativePath;
		// remove existing observer(s) if any
		children.remove(observePath); // child observers should be destroyed at
										// next GC
		// create new observer(s)
		PrivacyFileObserver child = new PrivacyFileObserver(observePath,
				pSetManServ);
		children.put(observePath, child);
	}

	@Override
	public void startWatching() {
		// Log.d("PrivacyFileObserver",
		// "PrivacyFileObserver - observing directory: " + absolutePath);
		super.startWatching();
	}

	// public void verifyObserver() {
	// Log.d(TAG, "verifyObservers - observer path: " + absolutePath);
	// for (PrivacyFileObserver obs : children.values()) obs.verifyObserver();
	// }

}
