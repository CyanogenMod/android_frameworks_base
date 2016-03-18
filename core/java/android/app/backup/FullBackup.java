/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.os.*;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParserException;
/**
 * Global constant definitions et cetera related to the full-backup-to-fd
 * binary format.  Nothing in this namespace is part of any API; it's all
 * hidden details of the current implementation gathered into one location.
 *
 * @hide
 */
public class FullBackup {
    static final String TAG = "FullBackup";
    /** Enable this log tag to get verbose information while parsing the client xml. */
    static final String TAG_XML_PARSER = "BackupXmlParserLogging";

    public static final String APK_TREE_TOKEN = "a";
    public static final String OBB_TREE_TOKEN = "obb";
    public static final String ROOT_TREE_TOKEN = "r";
    public static final String DATA_TREE_TOKEN = "f";
    public static final String NO_BACKUP_TREE_TOKEN = "nb";
    public static final String DATABASE_TREE_TOKEN = "db";
    public static final String SHAREDPREFS_TREE_TOKEN = "sp";
    public static final String MANAGED_EXTERNAL_TREE_TOKEN = "ef";
    public static final String CACHE_TREE_TOKEN = "c";
    public static final String SHARED_STORAGE_TOKEN = "shared";

    public static final String APPS_PREFIX = "apps/";
    public static final String SHARED_PREFIX = SHARED_STORAGE_TOKEN + "/";

    public static final String FULL_BACKUP_INTENT_ACTION = "fullback";
    public static final String FULL_RESTORE_INTENT_ACTION = "fullrest";
    public static final String CONF_TOKEN_INTENT_EXTRA = "conftoken";

    /**
     * @hide
     */
    static public native int backupToTar(String packageName, String domain,
            String linkdomain, String rootpath, String path, FullBackupDataOutput output);

    private static final Map<String, BackupScheme> kPackageBackupSchemeMap =
            new ArrayMap<String, BackupScheme>();

    static synchronized BackupScheme getBackupScheme(Context context) {
        BackupScheme backupSchemeForPackage =
                kPackageBackupSchemeMap.get(context.getPackageName());
        if (backupSchemeForPackage == null) {
            backupSchemeForPackage = new BackupScheme(context);
            kPackageBackupSchemeMap.put(context.getPackageName(), backupSchemeForPackage);
        }
        return backupSchemeForPackage;
    }

    public static BackupScheme getBackupSchemeForTest(Context context) {
        BackupScheme testing = new BackupScheme(context);
        testing.mExcludes = new ArraySet();
        testing.mIncludes = new ArrayMap();
        return testing;
    }


    /**
     * Copy data from a socket to the given File location on permanent storage.  The
     * modification time and access mode of the resulting file will be set if desired,
     * although group/all rwx modes will be stripped: the restored file will not be
     * accessible from outside the target application even if the original file was.
     * If the {@code type} parameter indicates that the result should be a directory,
     * the socket parameter may be {@code null}; even if it is valid, no data will be
     * read from it in this case.
     * <p>
     * If the {@code mode} argument is negative, then the resulting output file will not
     * have its access mode or last modification time reset as part of this operation.
     *
     * @param data Socket supplying the data to be copied to the output file.  If the
     *    output is a directory, this may be {@code null}.
     * @param size Number of bytes of data to copy from the socket to the file.  At least
     *    this much data must be available through the {@code data} parameter.
     * @param type Must be either {@link BackupAgent#TYPE_FILE} for ordinary file data
     *    or {@link BackupAgent#TYPE_DIRECTORY} for a directory.
     * @param mode Unix-style file mode (as used by the chmod(2) syscall) to be set on
     *    the output file or directory.  group/all rwx modes are stripped even if set
     *    in this parameter.  If this parameter is negative then neither
     *    the mode nor the mtime values will be applied to the restored file.
     * @param mtime A timestamp in the standard Unix epoch that will be imposed as the
     *    last modification time of the output file.  if the {@code mode} parameter is
     *    negative then this parameter will be ignored.
     * @param outFile Location within the filesystem to place the data.  This must point
     *    to a location that is writeable by the caller, preferably using an absolute path.
     * @throws IOException
     */
    static public void restoreFile(ParcelFileDescriptor data,
            long size, int type, long mode, long mtime, File outFile) throws IOException {
        if (type == BackupAgent.TYPE_DIRECTORY) {
            // Canonically a directory has no associated content, so we don't need to read
            // anything from the pipe in this case.  Just create the directory here and
            // drop down to the final metadata adjustment.
            if (outFile != null) outFile.mkdirs();
        } else {
            FileOutputStream out = null;

            // Pull the data from the pipe, copying it to the output file, until we're done
            try {
                if (outFile != null) {
                    File parent = outFile.getParentFile();
                    if (!parent.exists()) {
                        // in practice this will only be for the default semantic directories,
                        // and using the default mode for those is appropriate.
                        // This can also happen for the case where a parent directory has been
                        // excluded, but a file within that directory has been included.
                        parent.mkdirs();
                    }
                    out = new FileOutputStream(outFile);
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to create/open file " + outFile.getPath(), e);
            }

            byte[] buffer = new byte[32 * 1024];
            final long origSize = size;
            FileInputStream in = new FileInputStream(data.getFileDescriptor());
            while (size > 0) {
                int toRead = (size > buffer.length) ? buffer.length : (int)size;
                int got = in.read(buffer, 0, toRead);
                if (got <= 0) {
                    Log.w(TAG, "Incomplete read: expected " + size + " but got "
                            + (origSize - size));
                    break;
                }
                if (out != null) {
                    try {
                        out.write(buffer, 0, got);
                    } catch (IOException e) {
                        // Problem writing to the file.  Quit copying data and delete
                        // the file, but of course keep consuming the input stream.
                        Log.e(TAG, "Unable to write to file " + outFile.getPath(), e);
                        out.close();
                        out = null;
                        outFile.delete();
                    }
                }
                size -= got;
            }
            if (out != null) out.close();
        }

        // Now twiddle the state to match the backup, assuming all went well
        if (mode >= 0 && outFile != null) {
            try {
                // explicitly prevent emplacement of files accessible by outside apps
                mode &= 0700;
                Os.chmod(outFile.getPath(), (int)mode);
            } catch (ErrnoException e) {
                e.rethrowAsIOException();
            }
            outFile.setLastModified(mtime);
        }
    }

    @VisibleForTesting
    public static class BackupScheme {
        private final File FILES_DIR;
        private final File DATABASE_DIR;
        private final File ROOT_DIR;
        private final File SHAREDPREF_DIR;
        private final File EXTERNAL_DIR;
        private final File CACHE_DIR;
        private final File NOBACKUP_DIR;

        final int mFullBackupContent;
        final PackageManager mPackageManager;
        final StorageManager mStorageManager;
        final StorageVolume[] mVolumes;
        final String mPackageName;

        /**
         * Parse out the semantic domains into the correct physical location.
         */
        String tokenToDirectoryPath(String domainToken) {
            try {
                if (domainToken.equals(FullBackup.DATA_TREE_TOKEN)) {
                    return FILES_DIR.getCanonicalPath();
                } else if (domainToken.equals(FullBackup.DATABASE_TREE_TOKEN)) {
                    return DATABASE_DIR.getCanonicalPath();
                } else if (domainToken.equals(FullBackup.ROOT_TREE_TOKEN)) {
                    return ROOT_DIR.getCanonicalPath();
                } else if (domainToken.equals(FullBackup.SHAREDPREFS_TREE_TOKEN)) {
                    return SHAREDPREF_DIR.getCanonicalPath();
                } else if (domainToken.equals(FullBackup.CACHE_TREE_TOKEN)) {
                    return CACHE_DIR.getCanonicalPath();
                } else if (domainToken.equals(FullBackup.MANAGED_EXTERNAL_TREE_TOKEN)) {
                    if (EXTERNAL_DIR != null) {
                        return EXTERNAL_DIR.getCanonicalPath();
                    } else {
                        return null;
                    }
                } else if (domainToken.startsWith(FullBackup.SHARED_PREFIX)) {
                    int slash = domainToken.indexOf('/');
                    int i = Integer.parseInt(domainToken.substring(slash + 1));

                    if (i < mVolumes.length) {
                        return mVolumes[i].getPath();
                    } else {
                        Log.e(TAG, "Could not find volume for " + domainToken);
                    }
                } else if (domainToken.equals(FullBackup.NO_BACKUP_TREE_TOKEN)) {
                    return NOBACKUP_DIR.getCanonicalPath();
                }
                // Not a supported location
                Log.i(TAG, "Unrecognized domain " + domainToken);
                return null;
            } catch (IOException e) {
                Log.i(TAG, "Error reading directory for domain: " + domainToken);
                return null;
            }

        }
        /**
        * A map of domain -> list of canonical file names in that domain that are to be included.
        * We keep track of the domain so that we can go through the file system in order later on.
        */
        Map<String, Set<String>> mIncludes;
        /**e
         * List that will be populated with the canonical names of each file or directory that is
         * to be excluded.
         */
        ArraySet<String> mExcludes;

        BackupScheme(Context context) {
            mFullBackupContent = context.getApplicationInfo().fullBackupContent;
            mPackageManager = context.getPackageManager();
            mPackageName = context.getPackageName();
            FILES_DIR = context.getFilesDir();
            DATABASE_DIR = context.getDatabasePath("foo").getParentFile();
            ROOT_DIR = new File(context.getApplicationInfo().dataDir);
            SHAREDPREF_DIR = context.getSharedPrefsFile("foo").getParentFile();
            CACHE_DIR = context.getCacheDir();
            NOBACKUP_DIR = context.getNoBackupFilesDir();
            mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            mVolumes = mStorageManager.getVolumeList();
            if (android.os.Process.myUid() != Process.SYSTEM_UID) {
                EXTERNAL_DIR = context.getExternalFilesDir(null);
            } else {
                EXTERNAL_DIR = null;
            }
        }

        boolean isFullBackupContentEnabled() {
            if (mFullBackupContent < 0) {
                // android:fullBackupContent="false", bail.
                if (Log.isLoggable(FullBackup.TAG_XML_PARSER, Log.VERBOSE)) {
                    Log.v(FullBackup.TAG_XML_PARSER, "android:fullBackupContent - \"false\"");
                }
                return false;
            }
            return true;
        }

        /**
         * @return A mapping of domain -> canonical paths within that domain. Each of these paths
         * specifies a file that the client has explicitly included in their backup set. If this
         * map is empty we will back up the entire data directory (including managed external
         * storage).
         */
        public synchronized Map<String, Set<String>> maybeParseAndGetCanonicalIncludePaths()
                throws IOException, XmlPullParserException {
            if (mIncludes == null) {
                maybeParseBackupSchemeLocked();
            }
            return mIncludes;
        }

        /**
         * @return A set of canonical paths that are to be excluded from the backup/restore set.
         */
        public synchronized ArraySet<String> maybeParseAndGetCanonicalExcludePaths()
                throws IOException, XmlPullParserException {
            if (mExcludes == null) {
                maybeParseBackupSchemeLocked();
            }
            return mExcludes;
        }

        private void maybeParseBackupSchemeLocked() throws IOException, XmlPullParserException {
            // This not being null is how we know that we've tried to parse the xml already.
            mIncludes = new ArrayMap<String, Set<String>>();
            mExcludes = new ArraySet<String>();

            if (mFullBackupContent == 0) {
                // android:fullBackupContent="true" which means that we'll do everything.
                if (Log.isLoggable(FullBackup.TAG_XML_PARSER, Log.VERBOSE)) {
                    Log.v(FullBackup.TAG_XML_PARSER, "android:fullBackupContent - \"true\"");
                }
            } else {
                // android:fullBackupContent="@xml/some_resource".
                if (Log.isLoggable(FullBackup.TAG_XML_PARSER, Log.VERBOSE)) {
                    Log.v(FullBackup.TAG_XML_PARSER,
                            "android:fullBackupContent - found xml resource");
                }
                XmlResourceParser parser = null;
                try {
                    parser = mPackageManager
                            .getResourcesForApplication(mPackageName)
                            .getXml(mFullBackupContent);
                    parseBackupSchemeFromXmlLocked(parser, mExcludes, mIncludes);
                } catch (PackageManager.NameNotFoundException e) {
                    // Throw it as an IOException
                    throw new IOException(e);
                } finally {
                    if (parser != null) {
                        parser.close();
                    }
                }
            }
        }

        @VisibleForTesting
        public void parseBackupSchemeFromXmlLocked(XmlPullParser parser,
                                                   Set<String> excludes,
                                                   Map<String, Set<String>> includes)
                throws IOException, XmlPullParserException {
            int event = parser.getEventType(); // START_DOCUMENT
            while (event != XmlPullParser.START_TAG) {
                event = parser.next();
            }

            if (!"full-backup-content".equals(parser.getName())) {
                throw new XmlPullParserException("Xml file didn't start with correct tag" +
                        " (<full-backup-content>). Found \"" + parser.getName() + "\"");
            }

            if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                Log.v(TAG_XML_PARSER, "\n");
                Log.v(TAG_XML_PARSER, "====================================================");
                Log.v(TAG_XML_PARSER, "Found valid fullBackupContent; parsing xml resource.");
                Log.v(TAG_XML_PARSER, "====================================================");
                Log.v(TAG_XML_PARSER, "");
            }

            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        validateInnerTagContents(parser);
                        final String domainFromXml = parser.getAttributeValue(null, "domain");
                        final File domainDirectory =
                                getDirectoryForCriteriaDomain(domainFromXml);
                        if (domainDirectory == null) {
                            if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                                Log.v(TAG_XML_PARSER, "...parsing \"" + parser.getName() + "\": "
                                        + "domain=\"" + domainFromXml + "\" invalid; skipping");
                            }
                            break;
                        }
                        final File canonicalFile =
                                extractCanonicalFile(domainDirectory,
                                        parser.getAttributeValue(null, "path"));
                        if (canonicalFile == null) {
                            break;
                        }

                        Set<String> activeSet = parseCurrentTagForDomain(
                                parser, excludes, includes, domainFromXml);
                        activeSet.add(canonicalFile.getCanonicalPath());
                        if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                            Log.v(TAG_XML_PARSER, "...parsed " + canonicalFile.getCanonicalPath()
                                    + " for domain \"" + domainFromXml + "\"");
                        }

                        // Special case journal files (not dirs) for sqlite database. frowny-face.
                        // Note that for a restore, the file is never a directory (b/c it doesn't
                        // exist). We have no way of knowing a priori whether or not to expect a
                        // dir, so we add the -journal anyway to be safe.
                        if ("database".equals(domainFromXml) && !canonicalFile.isDirectory()) {
                            final String canonicalJournalPath =
                                    canonicalFile.getCanonicalPath() + "-journal";
                            activeSet.add(canonicalJournalPath);
                            if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                                Log.v(TAG_XML_PARSER, "...automatically generated "
                                        + canonicalJournalPath + ". Ignore if nonexistant.");
                            }
                        }
                }
            }
            if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                Log.v(TAG_XML_PARSER, "\n");
                Log.v(TAG_XML_PARSER, "Xml resource parsing complete.");
                Log.v(TAG_XML_PARSER, "Final tally.");
                Log.v(TAG_XML_PARSER, "Includes:");
                if (includes.isEmpty()) {
                    Log.v(TAG_XML_PARSER, "  ...nothing specified (This means the entirety of app"
                            + " data minus excludes)");
                } else {
                    for (Map.Entry<String, Set<String>> entry : includes.entrySet()) {
                        Log.v(TAG_XML_PARSER, "  domain=" + entry.getKey());
                        for (String includeData : entry.getValue()) {
                            Log.v(TAG_XML_PARSER, "  " + includeData);
                        }
                    }
                }

                Log.v(TAG_XML_PARSER, "Excludes:");
                if (excludes.isEmpty()) {
                    Log.v(TAG_XML_PARSER, "  ...nothing to exclude.");
                } else {
                    for (String excludeData : excludes) {
                        Log.v(TAG_XML_PARSER, "  " + excludeData);
                    }
                }

                Log.v(TAG_XML_PARSER, "  ");
                Log.v(TAG_XML_PARSER, "====================================================");
                Log.v(TAG_XML_PARSER, "\n");
            }
        }

        private Set<String> parseCurrentTagForDomain(XmlPullParser parser,
                                                     Set<String> excludes,
                                                     Map<String, Set<String>> includes,
                                                     String domain)
                throws XmlPullParserException {
            if ("include".equals(parser.getName())) {
                final String domainToken = getTokenForXmlDomain(domain);
                Set<String> includeSet = includes.get(domainToken);
                if (includeSet == null) {
                    includeSet = new ArraySet<String>();
                    includes.put(domainToken, includeSet);
                }
                return includeSet;
            } else if ("exclude".equals(parser.getName())) {
                return excludes;
            } else {
                // Unrecognised tag => hard failure.
                if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                    Log.v(TAG_XML_PARSER, "Invalid tag found in xml \""
                            + parser.getName() + "\"; aborting operation.");
                }
                throw new XmlPullParserException("Unrecognised tag in backup" +
                        " criteria xml (" + parser.getName() + ")");
            }
        }

        /**
         * Map xml specified domain (human-readable, what clients put in their manifest's xml) to
         * BackupAgent internal data token.
         * @return null if the xml domain was invalid.
         */
        private String getTokenForXmlDomain(String xmlDomain) {
            if ("root".equals(xmlDomain)) {
                return FullBackup.ROOT_TREE_TOKEN;
            } else if ("file".equals(xmlDomain)) {
                return FullBackup.DATA_TREE_TOKEN;
            } else if ("database".equals(xmlDomain)) {
                return FullBackup.DATABASE_TREE_TOKEN;
            } else if ("sharedpref".equals(xmlDomain)) {
                return FullBackup.SHAREDPREFS_TREE_TOKEN;
            } else if ("external".equals(xmlDomain)) {
                return FullBackup.MANAGED_EXTERNAL_TREE_TOKEN;
            } else {
                return null;
            }
        }

        /**
         *
         * @param domain Directory where the specified file should exist. Not null.
         * @param filePathFromXml parsed from xml. Not sanitised before calling this function so may be
         *                        null.
         * @return The canonical path of the file specified or null if no such file exists.
         */
        private File extractCanonicalFile(File domain, String filePathFromXml) {
            if (filePathFromXml == null) {
                // Allow things like <include domain="sharedpref"/>
                filePathFromXml = "";
            }
            if (filePathFromXml.contains("..")) {
                if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                    Log.v(TAG_XML_PARSER, "...resolved \"" + domain.getPath() + " " + filePathFromXml
                            + "\", but the \"..\" path is not permitted; skipping.");
                }
                return null;
            }
            if (filePathFromXml.contains("//")) {
                if (Log.isLoggable(TAG_XML_PARSER, Log.VERBOSE)) {
                    Log.v(TAG_XML_PARSER, "...resolved \"" + domain.getPath() + " " + filePathFromXml
                            + "\", which contains the invalid \"//\" sequence; skipping.");
                }
                return null;
            }
            return new File(domain, filePathFromXml);
        }

        /**
         * @param domain parsed from xml. Not sanitised before calling this function so may be null.
         * @return The directory relevant to the domain specified.
         */
        private File getDirectoryForCriteriaDomain(String domain) {
            if (TextUtils.isEmpty(domain)) {
                return null;
            }
            if ("file".equals(domain)) {
                return FILES_DIR;
            } else if ("database".equals(domain)) {
                return DATABASE_DIR;
            } else if ("root".equals(domain)) {
                return ROOT_DIR;
            } else if ("sharedpref".equals(domain)) {
                return SHAREDPREF_DIR;
            } else if ("external".equals(domain)) {
                return EXTERNAL_DIR;
            } else {
                return null;
            }
        }

        /**
         * Let's be strict about the type of xml the client can write. If we see anything untoward,
         * throw an XmlPullParserException.
         */
        private void validateInnerTagContents(XmlPullParser parser)
                throws XmlPullParserException {
            if (parser.getAttributeCount() > 2) {
                throw new XmlPullParserException("At most 2 tag attributes allowed for \""
                        + parser.getName() + "\" tag (\"domain\" & \"path\".");
            }
            if (!"include".equals(parser.getName()) && !"exclude".equals(parser.getName())) {
                throw new XmlPullParserException("A valid tag is one of \"<include/>\" or" +
                        " \"<exclude/>. You provided \"" + parser.getName() + "\"");
            }
        }
    }
}
