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

package com.android.server;


import android.app.backup.AbsoluteFileBackupHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupAgentHelper;
import android.app.backup.FullBackup;
import android.app.backup.FullBackupDataOutput;
import android.app.backup.WallpaperBackupHelper;
import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Slog;


import java.io.File;
import java.io.IOException;

/**
 * Backup agent for various system-managed data, currently just the system wallpaper
 */
public class SystemBackupAgent extends BackupAgentHelper {
    private static final String TAG = "SystemBackupAgent";

    // These paths must match what the WallpaperManagerService uses.  The leaf *_FILENAME
    // are also used in the full-backup file format, so must not change unless steps are
    // taken to support the legacy backed-up datasets.
    private static final String WALLPAPER_IMAGE_FILENAME = "wallpaper";
    private static final String WALLPAPER_INFO_FILENAME = "wallpaper_info.xml";

    private static final String PROFILES_FILENAME =
            ProfileManagerService.PROFILE_FILE.getName();
    private static final String PROFILES_FILE_DIRECTORY =
            ProfileManagerService.PROFILE_FILE.getParentFile().getAbsolutePath();

    // TODO: Will need to change if backing up non-primary user's wallpaper
    private static final String WALLPAPER_IMAGE_DIR =
            Environment.getUserSystemDirectory(UserHandle.USER_OWNER).getAbsolutePath();
    private static final String WALLPAPER_IMAGE = WallpaperBackupHelper.WALLPAPER_IMAGE;

    // TODO: Will need to change if backing up non-primary user's wallpaper
    private static final String WALLPAPER_INFO_DIR =
            Environment.getUserSystemDirectory(UserHandle.USER_OWNER).getAbsolutePath();
    private static final String WALLPAPER_INFO = WallpaperBackupHelper.WALLPAPER_INFO;
    // Use old keys to keep legacy data compatibility and avoid writing two wallpapers
    private static final String WALLPAPER_IMAGE_KEY = WallpaperBackupHelper.WALLPAPER_IMAGE_KEY;
    private static final String WALLPAPER_INFO_KEY = WallpaperBackupHelper.WALLPAPER_INFO_KEY;

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        // We only back up the data under the current "wallpaper" schema with metadata
        WallpaperManagerService wallpaper = (WallpaperManagerService)ServiceManager.getService(
                Context.WALLPAPER_SERVICE);
        String[] files = new String[] { WALLPAPER_IMAGE, WALLPAPER_INFO };
        String[] keys = new String[] { WALLPAPER_IMAGE_KEY, WALLPAPER_INFO_KEY };
        if (wallpaper != null && wallpaper.getName() != null && wallpaper.getName().length() > 0) {
            // When the wallpaper has a name, back up the info by itself.
            // TODO: Don't rely on the innards of the service object like this!
            // TODO: Send a delete for any stored wallpaper image in this case?
            files = new String[] { WALLPAPER_INFO };
            keys = new String[] { WALLPAPER_INFO_KEY };
        }
        addHelper("wallpaper", new WallpaperBackupHelper(SystemBackupAgent.this, files, keys));
        addHelper("profiles", new AbsoluteFileBackupHelper(SystemBackupAgent.this,
                    ProfileManagerService.PROFILE_FILE.getAbsolutePath()));
        super.onBackup(oldState, data, newState);
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        // At present we back up only the wallpaper and profiles
        fullWallpaperBackup(data);
        fullProfilesBackup(data);
    }

    private void fullWallpaperBackup(FullBackupDataOutput output) {
        // Back up the data files directly.  We do them in this specific order --
        // info file followed by image -- because then we need take no special
        // steps during restore; the restore will happen properly when the individual
        // files are restored piecemeal.
        FullBackup.backupToTar(getPackageName(), FullBackup.ROOT_TREE_TOKEN, null,
                WALLPAPER_INFO_DIR, WALLPAPER_INFO, output.getData());
        FullBackup.backupToTar(getPackageName(), FullBackup.ROOT_TREE_TOKEN, null,
                WALLPAPER_IMAGE_DIR, WALLPAPER_IMAGE, output.getData());
    }

    private void fullProfilesBackup(FullBackupDataOutput output) {
        FullBackup.backupToTar(getPackageName(), FullBackup.ROOT_TREE_TOKEN, null,
                PROFILES_FILE_DIRECTORY, PROFILES_FILENAME, output.getData());
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // On restore, we also support a previous data schema "system_files"
        addHelper("wallpaper", new WallpaperBackupHelper(SystemBackupAgent.this,
                new String[] { WALLPAPER_IMAGE, WALLPAPER_INFO },
                new String[] { WALLPAPER_IMAGE_KEY, WALLPAPER_INFO_KEY} ));
        addHelper("system_files", new WallpaperBackupHelper(SystemBackupAgent.this,
                new String[] { WALLPAPER_IMAGE },
                new String[] { WALLPAPER_IMAGE_KEY} ));
        addHelper("profiles", new AbsoluteFileBackupHelper(SystemBackupAgent.this,
                ProfileManagerService.PROFILE_FILE.getAbsolutePath()));

        try {
            super.onRestore(data, appVersionCode, newState);

            WallpaperManagerService wallpaper = (WallpaperManagerService)ServiceManager.getService(
                    Context.WALLPAPER_SERVICE);
            ProfileManagerService profiles = (ProfileManagerService)ServiceManager.getService(
                    Context.PROFILE_SERVICE);

            wallpaper.settingsRestored();
            profiles.settingsRestored();
        } catch (IOException ex) {
            // If there was a failure, delete everything for the wallpaper, this is too aggressive,
            // but this is hopefully a rare failure.
            Slog.d(TAG, "restore failed", ex);
            (new File(WALLPAPER_IMAGE)).delete();
            (new File(WALLPAPER_INFO)).delete();
        }
    }

    @Override
    public void onRestoreFile(ParcelFileDescriptor data, long size,
            int type, String domain, String path, long mode, long mtime)
            throws IOException {
        Slog.i(TAG, "Restoring file domain=" + domain + " path=" + path);

        // Bits to indicate postprocessing we may need to perform
        boolean restoredWallpaper = false;
        boolean restoredProfiles = false;

        File outFile = null;
        // Various domain+files we understand a priori
        if (domain.equals(FullBackup.ROOT_TREE_TOKEN)) {
            if (path.equals(WALLPAPER_INFO_FILENAME)) {
                outFile = new File(WALLPAPER_INFO);
                restoredWallpaper = true;
            } else if (path.equals(WALLPAPER_IMAGE_FILENAME)) {
                outFile = new File(WALLPAPER_IMAGE);
                restoredWallpaper = true;
            } else if (path.equals(PROFILES_FILENAME)) {
                outFile = ProfileManagerService.PROFILE_FILE;
                restoredProfiles = true;
            }
        }

        try {
            if (outFile == null) {
                Slog.w(TAG, "Skipping unrecognized system file: [ " + domain + " : " + path + " ]");
            }
            FullBackup.restoreFile(data, size, type, mode, mtime, outFile);

            if (restoredWallpaper) {
                WallpaperManagerService wallpaper =
                        (WallpaperManagerService)ServiceManager.getService(
                        Context.WALLPAPER_SERVICE);
                wallpaper.settingsRestored();
            }
            if (restoredProfiles) {
                ProfileManagerService profiles = (ProfileManagerService)
                        ServiceManager.getService(Context.PROFILE_SERVICE);
                profiles.settingsRestored();
            }
        } catch (IOException e) {
            if (restoredWallpaper) {
                // Make sure we wind up in a good state
                (new File(WALLPAPER_IMAGE)).delete();
                (new File(WALLPAPER_INFO)).delete();
            }
        }
    }
}
