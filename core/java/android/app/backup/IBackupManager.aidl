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

package android.app.backup;

import android.app.backup.IFullBackupRestoreObserver;
import android.app.backup.IRestoreSession;
import android.os.ParcelFileDescriptor;
import android.content.Intent;

/**
 * Direct interface to the Backup Manager Service that applications invoke on.  The only
 * operation currently needed is a simple notification that the app has made changes to
 * data it wishes to back up, so the system should run a backup pass.
 *
 * Apps will use the {@link android.app.backup.BackupManager} class rather than going through
 * this Binder interface directly.
 * 
 * {@hide}
 */
interface IBackupManager {
    /**
     * Tell the system service that the caller has made changes to its
     * data, and therefore needs to undergo an incremental backup pass.
     *
     * Any application can invoke this method for its own package, but
     * only callers who hold the android.permission.BACKUP permission
     * may invoke it for arbitrary packages.
     */
    void dataChanged(String packageName);

    /**
     * Erase all backed-up data for the given package from the given storage
     * destination.
     *
     * Any application can invoke this method for its own package, but
     * only callers who hold the android.permission.BACKUP permission
     * may invoke it for arbitrary packages.
     */
    void clearBackupData(String transportName, String packageName);

    /**
     * Notifies the Backup Manager Service that an agent has become available.  This
     * method is only invoked by the Activity Manager.
     */
    void agentConnected(String packageName, IBinder agent);

    /**
     * Notify the Backup Manager Service that an agent has unexpectedly gone away.
     * This method is only invoked by the Activity Manager.
     */
    void agentDisconnected(String packageName);

    /**
     * Notify the Backup Manager Service that an application being installed will
     * need a data-restore pass.  This method is only invoked by the Package Manager.
     */
    void restoreAtInstall(String packageName, int token);

    /**
     * Enable/disable the backup service entirely.  When disabled, no backup
     * or restore operations will take place.  Data-changed notifications will
     * still be observed and collected, however, so that changes made while the
     * mechanism was disabled will still be backed up properly if it is enabled
     * at some point in the future.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void setBackupEnabled(boolean isEnabled);

    /**
     * Enable/disable automatic restore of application data at install time.  When
     * enabled, installation of any package will involve the Backup Manager.  If data
     * exists for the newly-installed package, either from the device's current [enabled]
     * backup dataset or from the restore set used in the last wholesale restore operation,
     * that data will be supplied to the new package's restore agent before the package
     * is made generally available for launch.
     *
     * <p>Callers must hold  the android.permission.BACKUP permission to use this method.
     *
     * @param doAutoRestore When true, enables the automatic app-data restore facility.  When
     *   false, this facility will be disabled.
     */
    void setAutoRestore(boolean doAutoRestore);

    /**
     * Indicate that any necessary one-time provisioning has occurred.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void setBackupProvisioned(boolean isProvisioned);

    /**
     * Report whether the backup mechanism is currently enabled.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    boolean isBackupEnabled();

    /**
     * Set the device's backup password.  Returns {@code true} if the password was set
     * successfully, {@code false} otherwise.  Typically a failure means that an incorrect
     * current password was supplied.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    boolean setBackupPassword(in String currentPw, in String newPw);

    /**
     * Reports whether a backup password is currently set.  If not, then a null or empty
     * "current password" argument should be passed to setBackupPassword().
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    boolean hasBackupPassword();

    /**
     * Schedule an immediate backup attempt for all pending updates.  This is
     * primarily intended for transports to use when they detect a suitable
     * opportunity for doing a backup pass.  If there are no pending updates to
     * be sent, no action will be taken.  Even if some updates are pending, the
     * transport will still be asked to confirm via the usual requestBackupTime()
     * method.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void backupNow();

    /**
     * Write a full backup of the given package to the supplied file descriptor.
     * The fd may be a socket or other non-seekable destination.  If no package names
     * are supplied, then every application on the device will be backed up to the output.
     *
     * <p>This method is <i>synchronous</i> -- it does not return until the backup has
     * completed.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @param fd The file descriptor to which a 'tar' file stream is to be written
     * @param includeApks If <code>true</code>, the resulting tar stream will include the
     *     application .apk files themselves as well as their data.
     * @param includeObbs If <code>true</code>, the resulting tar stream will include any
     *     application expansion (OBB) files themselves belonging to each application.
     * @param includeShared If <code>true</code>, the resulting tar stream will include
     *     the contents of the device's shared storage (SD card or equivalent).
     * @param allApps If <code>true</code>, the resulting tar stream will include all
     *     installed applications' data, not just those named in the <code>packageNames</code>
     *     parameter.
     * @param allIncludesSystem If {@code true}, then {@code allApps} will be interpreted
     *     as including packages pre-installed as part of the system. If {@code false},
     *     then setting {@code allApps} to {@code true} will mean only that all 3rd-party
     *     applications will be included in the dataset.
     * @param packageNames The package names of the apps whose data (and optionally .apk files)
     *     are to be backed up.  The <code>allApps</code> parameter supersedes this.
     */
    void fullBackup(in ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs,
            boolean includeShared, boolean allApps, boolean allIncludesSystem,
            in String[] packageNames);

    /**
     * Write a backup of the given package to the supplied file descriptor.
     * The fd may be a socket or other non-seekable destination.
     *
     * <p>Use this method instead of fullBackup() when non-interactive operations are needed.
     *
     * <p>This method is <i>synchronous</i> -- it does not return until the backup has
     * completed.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @param fd The file descriptor to which a 'tar' file stream is to be written.
     * @param domainTokens - The specific files paths (expressed as domain tokens) that should
     *        be backedup.
     * @param excludeFilesRegex - The files that should be excluded, specified as a regex.
     * @param packageName - The name of the package.
     * @param shouldKillAfterBackup - Should the process be killed once the backup is done.
     * @param ignoreEncryptionPasswordCheck - Should encryption password check be ignored
     * @param observer - The Backup Observer which is notified of the start / finish / error state
     *        of the backup.
     */
    void fullBackupNoninteractive(in ParcelFileDescriptor fd, in String[] domainTokens,
            in String excludeFilesRegex, in String packageName, boolean shouldKillAfterBackup,
            boolean ignoreEncryptionPasswordCheck, IFullBackupRestoreObserver observer);

    /**
     * Restore device content from the data stream passed through the given socket.  The
     * data stream must be in the format emitted by fullBackup().
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void fullRestore(in ParcelFileDescriptor fd);

    /**
     * Restore device content from the data stream passed through the given socket.  The
     * data stream must be in the format emitted by fullBackup().
     *
     * <p>Use this method instead of fullRestore() when non-interactive operations are needed.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void fullRestoreNoninteractive(in ParcelFileDescriptor fd,
            boolean ignoreEncryptionPasswordCheck, IFullBackupRestoreObserver observer);

    /**
     * Confirm that the requested full backup/restore operation can proceed.  The system will
     * not actually perform the operation described to fullBackup() / fullRestore() unless the
     * UI calls back into the Backup Manager to confirm, passing the correct token.  At
     * the same time, the UI supplies a callback Binder for progress notifications during
     * the operation.
     *
     * <p>The password passed by the confirming entity must match the saved backup or
     * full-device encryption password in order to perform a backup.  If a password is
     * supplied for restore, it must match the password used when creating the full
     * backup dataset being used for restore.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void acknowledgeFullBackupOrRestore(int token, boolean allow,
            in String curPassword, in String encryptionPassword,
            IFullBackupRestoreObserver observer);

    /**
     * Identify the currently selected transport.  Callers must hold the
     * android.permission.BACKUP permission to use this method.
     */
    String getCurrentTransport();

    /**
     * Request a list of all available backup transports' names.  Callers must
     * hold the android.permission.BACKUP permission to use this method.
     */
    String[] listAllTransports();

    /**
     * Specify the current backup transport.  Callers must hold the
     * android.permission.BACKUP permission to use this method.
     *
     * @param transport The name of the transport to select.  This should be one
     * of {@link BackupManager.TRANSPORT_GOOGLE} or {@link BackupManager.TRANSPORT_ADB}.
     * @return The name of the previously selected transport.  If the given transport
     *   name is not one of the currently available transports, no change is made to
     *   the current transport setting and the method returns null.
     */
    String selectBackupTransport(String transport);

    /**
     * Get the configuration Intent, if any, from the given transport.  Callers must
     * hold the android.permission.BACKUP permission in order to use this method.
     *
     * @param transport The name of the transport to query.
     * @return An Intent to use with Activity#startActivity() to bring up the configuration
     *   UI supplied by the transport.  If the transport has no configuration UI, it should
     *   return {@code null} here.
     */
    Intent getConfigurationIntent(String transport);

    /**
     * Get the destination string supplied by the given transport.  Callers must
     * hold the android.permission.BACKUP permission in order to use this method.
     *
     * @param transport The name of the transport to query.
     * @return A string describing the current backup destination.  This string is used
     *   verbatim by the Settings UI as the summary text of the "configure..." item.
     */
    String getDestinationString(String transport);

    /**
     * Begin a restore session.  Either or both of packageName and transportID
     * may be null.  If packageName is non-null, then only the given package will be
     * considered for restore.  If transportID is null, then the restore will use
     * the current active transport.
     * <p>
     * This method requires the android.permission.BACKUP permission <i>except</i>
     * when transportID is null and packageName is the name of the caller's own
     * package.  In that case, the restore session returned is suitable for supporting
     * the BackupManager.requestRestore() functionality via RestoreSession.restorePackage()
     * without requiring the app to hold any special permission.
     *
     * @param packageName The name of the single package for which a restore will
     *        be requested.  May be null, in which case all packages in the restore
     *        set can be restored.
     * @param transportID The name of the transport to use for the restore operation.
     *        May be null, in which case the current active transport is used.
     * @return An interface to the restore session, or null on error.
     */
    IRestoreSession beginRestoreSession(String packageName, String transportID);

    /**
     * Notify the backup manager that a BackupAgent has completed the operation
     * corresponding to the given token.
     *
     * @param token The transaction token passed to a BackupAgent's doBackup() or
     *        doRestore() method.
     * {@hide}
     */
    void opComplete(int token);
}
