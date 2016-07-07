/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
import static android.content.pm.PackageManager.INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;

import android.app.ActivityManager;
import android.content.pm.FeatureInfo;
import android.content.pm.Signature;
import android.os.*;
import android.os.Process;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import libcore.io.IoUtils;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import static com.android.internal.util.ArrayUtils.appendInt;

/**
 * Loads global system configuration info.
 */
public class SystemConfig {
    static final String TAG = "SystemConfig";

    static SystemConfig sInstance;

    // Group-ids that are given to all packages as read from etc/permissions/*.xml.
    int[] mGlobalGids;

    // These are the built-in uid -> permission mappings that were read from the
    // system configuration files.
    final SparseArray<ArraySet<String>> mSystemPermissions = new SparseArray<>();

    // These are the built-in shared libraries that were read from the
    // system configuration files.  Keys are the library names; strings are the
    // paths to the libraries.
    final ArrayMap<String, String> mSharedLibraries  = new ArrayMap<>();

    // These are the features this devices supports that were read from the
    // system configuration files.
    final ArrayMap<String, FeatureInfo> mAvailableFeatures = new ArrayMap<>();

    // These are the features which this device doesn't support; the OEM
    // partition uses these to opt-out of features from the system image.
    final ArraySet<String> mUnavailableFeatures = new ArraySet<>();

    public static final class PermissionEntry {
        public final String name;
        public int[] gids;
        public boolean perUser;

        PermissionEntry(String name, boolean perUser) {
            this.name = name;
            this.perUser = perUser;
        }
    }

    // These are the permission -> gid mappings that were read from the
    // system configuration files.
    final ArrayMap<String, PermissionEntry> mPermissions = new ArrayMap<>();

    // These are the packages that are white-listed to be able to run in the
    // background while in power save mode (but not whitelisted from device idle modes),
    // as read from the configuration files.
    final ArraySet<String> mAllowInPowerSaveExceptIdle = new ArraySet<>();

    // These are the packages that are white-listed to be able to run in the
    // background while in power save mode, as read from the configuration files.
    final ArraySet<String> mAllowInPowerSave = new ArraySet<>();

    // These are the app package names that should not allow IME switching.
    final ArraySet<String> mFixedImeApps = new ArraySet<>();

    // These are the package names of apps which should be in the 'always'
    // URL-handling state upon factory reset.
    final ArraySet<AppLink> mLinkedApps = new ArraySet<>();

    final ArrayMap<Signature, ArraySet<String>> mSignatureAllowances
            = new ArrayMap<Signature, ArraySet<String>>();

    public static SystemConfig getInstance() {
        synchronized (SystemConfig.class) {
            if (sInstance == null) {
                sInstance = new SystemConfig();
            }
            return sInstance;
        }
    }

    public int[] getGlobalGids() {
        return mGlobalGids;
    }

    public SparseArray<ArraySet<String>> getSystemPermissions() {
        return mSystemPermissions;
    }

    public ArrayMap<String, String> getSharedLibraries() {
        return mSharedLibraries;
    }

    public ArrayMap<String, FeatureInfo> getAvailableFeatures() {
        return mAvailableFeatures;
    }

    public ArrayMap<String, PermissionEntry> getPermissions() {
        return mPermissions;
    }

    public ArraySet<String> getAllowInPowerSaveExceptIdle() {
        return mAllowInPowerSaveExceptIdle;
    }

    public ArraySet<String> getAllowInPowerSave() {
        return mAllowInPowerSave;
    }

    public ArraySet<String> getFixedImeApps() {
        return mFixedImeApps;
    }

    public ArraySet<AppLink> getLinkedApps() {
        return mLinkedApps;
    }

    public ArrayMap<Signature, ArraySet<String>> getSignatureAllowances() {
        return mSignatureAllowances;
    }

    SystemConfig() {
        // Read configuration from system
        readPermissions(Environment.buildPath(
                Environment.getRootDirectory(), "etc", "sysconfig"), false);
        // Read configuration from the old permissions dir
        readPermissions(Environment.buildPath(
                Environment.getRootDirectory(), "etc", "permissions"), false);
        // Only read features from OEM config
        readPermissions(Environment.buildPath(
                Environment.getOemDirectory(), "etc", "sysconfig"), true);
        readPermissions(Environment.buildPath(
                Environment.getOemDirectory(), "etc", "permissions"), true);
    }

    void readPermissions(File libraryDir, boolean onlyFeatures) {
        // Read permissions from given directory.
        if (!libraryDir.exists() || !libraryDir.isDirectory()) {
            if (!onlyFeatures) {
                Slog.w(TAG, "No directory " + libraryDir + ", skipping");
            }
            return;
        }
        if (!libraryDir.canRead()) {
            Slog.w(TAG, "Directory " + libraryDir + " cannot be read");
            return;
        }

        // Iterate over the files in the directory and scan .xml files
        File platformFile = null;
        for (File f : libraryDir.listFiles()) {
            // We'll read platform.xml last
            if (f.getPath().endsWith("etc/permissions/platform.xml")) {
                platformFile = f;
                continue;
            }

            if (!f.getPath().endsWith(".xml")) {
                Slog.i(TAG, "Non-xml file " + f + " in " + libraryDir + " directory, ignoring");
                continue;
            }
            if (!f.canRead()) {
                Slog.w(TAG, "Permissions library file " + f + " cannot be read");
                continue;
            }

            readPermissionsFromXml(f, onlyFeatures);
        }

        // Read platform permissions last so it will take precedence
        if (platformFile != null) {
            readPermissionsFromXml(platformFile, onlyFeatures);
        }
    }

    private void readPermissionsFromXml(File permFile, boolean onlyFeatures) {
        FileReader permReader = null;
        try {
            permReader = new FileReader(permFile);
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "Couldn't find or open permissions file " + permFile);
            return;
        }

        final boolean lowRam = ActivityManager.isLowRamDeviceStatic();

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(permReader);

            int type;
            while ((type=parser.next()) != parser.START_TAG
                       && type != parser.END_DOCUMENT) {
                ;
            }

            if (type != parser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }

            if (!parser.getName().equals("permissions") && !parser.getName().equals("config")) {
                throw new XmlPullParserException("Unexpected start tag in " + permFile
                        + ": found " + parser.getName() + ", expected 'permissions' or 'config'");
            }

            while (true) {
                XmlUtils.nextElement(parser);
                if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                    break;
                }

                String name = parser.getName();
                if ("group".equals(name) && !onlyFeatures) {
                    String gidStr = parser.getAttributeValue(null, "gid");
                    if (gidStr != null) {
                        int gid = android.os.Process.getGidForName(gidStr);
                        mGlobalGids = appendInt(mGlobalGids, gid);
                    } else {
                        Slog.w(TAG, "<group> without gid in " + permFile + " at "
                                + parser.getPositionDescription());
                    }

                    XmlUtils.skipCurrentTag(parser);
                    continue;
                } else if ("permission".equals(name) && !onlyFeatures) {
                    String perm = parser.getAttributeValue(null, "name");
                    if (perm == null) {
                        Slog.w(TAG, "<permission> without name in " + permFile + " at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    perm = perm.intern();
                    readPermission(parser, perm);

                } else if ("assign-permission".equals(name) && !onlyFeatures) {
                    String perm = parser.getAttributeValue(null, "name");
                    if (perm == null) {
                        Slog.w(TAG, "<assign-permission> without name in " + permFile + " at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    String uidStr = parser.getAttributeValue(null, "uid");
                    if (uidStr == null) {
                        Slog.w(TAG, "<assign-permission> without uid in " + permFile + " at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    int uid = Process.getUidForName(uidStr);
                    if (uid < 0) {
                        Slog.w(TAG, "<assign-permission> with unknown uid \""
                                + uidStr + "  in " + permFile + " at "
                                + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    perm = perm.intern();
                    ArraySet<String> perms = mSystemPermissions.get(uid);
                    if (perms == null) {
                        perms = new ArraySet<String>();
                        mSystemPermissions.put(uid, perms);
                    }
                    perms.add(perm);
                    XmlUtils.skipCurrentTag(parser);

                } else if ("allow-permission".equals(name)) {
                    String perm = parser.getAttributeValue(null, "name");
                    if (perm == null) {
                        Slog.w(TAG,
                                "<allow-permission> without name at "
                                        + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    String signature = parser.getAttributeValue(null, "signature");
                    if (signature == null) {
                        Slog.w(TAG,
                                "<allow-permission> without signature at "
                                        + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    Signature sig = null;
                    try {
                        sig = new Signature(signature);
                    } catch (IllegalArgumentException e) {
                        // sig will be null so we will log it below
                    }
                    if (sig != null) {
                        ArraySet<String> perms = mSignatureAllowances.get(sig);
                        if (perms == null) {
                            perms = new ArraySet<String>();
                            mSignatureAllowances.put(sig, perms);
                        }
                        perms.add(perm);
                    } else {
                        Slog.w(TAG,
                                "<allow-permission> with bad signature at "
                                        + parser.getPositionDescription());
                    }
                    XmlUtils.skipCurrentTag(parser);

                } else if ("library".equals(name) && !onlyFeatures) {
                    String lname = parser.getAttributeValue(null, "name");
                    String lfile = parser.getAttributeValue(null, "file");
                    if (lname == null) {
                        Slog.w(TAG, "<library> without name in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else if (lfile == null) {
                        Slog.w(TAG, "<library> without file in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else {
                        //Log.i(TAG, "Got library " + lname + " in " + lfile);
                        mSharedLibraries.put(lname, lfile);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("feature".equals(name)) {
                    String fname = parser.getAttributeValue(null, "name");
                    boolean allowed;
                    if (!lowRam) {
                        allowed = true;
                    } else {
                        String notLowRam = parser.getAttributeValue(null, "notLowRam");
                        allowed = !"true".equals(notLowRam);
                    }
                    if (fname == null) {
                        Slog.w(TAG, "<feature> without name in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else if (allowed) {
                        //Log.i(TAG, "Got feature " + fname);
                        FeatureInfo fi = new FeatureInfo();
                        fi.name = fname;
                        mAvailableFeatures.put(fname, fi);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("unavailable-feature".equals(name)) {
                    String fname = parser.getAttributeValue(null, "name");
                    if (fname == null) {
                        Slog.w(TAG, "<unavailable-feature> without name in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else {
                        mUnavailableFeatures.add(fname);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("allow-in-power-save-except-idle".equals(name) && !onlyFeatures) {
                    String pkgname = parser.getAttributeValue(null, "package");
                    if (pkgname == null) {
                        Slog.w(TAG, "<allow-in-power-save-except-idle> without package in "
                                + permFile + " at " + parser.getPositionDescription());
                    } else {
                        mAllowInPowerSaveExceptIdle.add(pkgname);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("allow-in-power-save".equals(name) && !onlyFeatures) {
                    String pkgname = parser.getAttributeValue(null, "package");
                    if (pkgname == null) {
                        Slog.w(TAG, "<allow-in-power-save> without package in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else {
                        mAllowInPowerSave.add(pkgname);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("fixed-ime-app".equals(name) && !onlyFeatures) {
                    String pkgname = parser.getAttributeValue(null, "package");
                    if (pkgname == null) {
                        Slog.w(TAG, "<fixed-ime-app> without package in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else {
                        mFixedImeApps.add(pkgname);
                    }
                    XmlUtils.skipCurrentTag(parser);
                    continue;

                } else if ("app-link".equals(name)) {
                    String pkgname = parser.getAttributeValue(null, "package");
                    String state = parser.getAttributeValue(null, "state");
                    if (pkgname == null) {
                        Slog.w(TAG, "<app-link> without package in " + permFile + " at "
                                + parser.getPositionDescription());
                    } else {
                        mLinkedApps.add(makeLink(pkgname, state));
                    }
                    XmlUtils.skipCurrentTag(parser);

                } else {
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }
            }
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "Got exception parsing permissions.", e);
        } catch (IOException e) {
            Slog.w(TAG, "Got exception parsing permissions.", e);
        } finally {
            IoUtils.closeQuietly(permReader);
        }

        for (String fname : mUnavailableFeatures) {
            if (mAvailableFeatures.remove(fname) != null) {
                Slog.d(TAG, "Removed unavailable feature " + fname);
            }
        }
    }

    private AppLink makeLink(String pkgname, String state) {
        AppLink al = new AppLink();
        al.pkgname = pkgname;
        if (state == null || "always".equals(state)) { // default
            al.state = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS;
        } else if ("always-ask".equals(state)) {
            al.state = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ALWAYS_ASK;
        } else if ("ask".equals("state")) {
            al.state = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_ASK;
        } else if ("never".equals("state")) {
            al.state = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_NEVER;
        } else {
            al.state = INTENT_FILTER_DOMAIN_VERIFICATION_STATUS_UNDEFINED;
        }
        return al;
    }

    void readPermission(XmlPullParser parser, String name)
            throws IOException, XmlPullParserException {
        if (mPermissions.containsKey(name)) {
            throw new IllegalStateException("Duplicate permission definition for " + name);
        }

        final boolean perUser = XmlUtils.readBooleanAttribute(parser, "perUser", false);
        final PermissionEntry perm = new PermissionEntry(name, perUser);
        mPermissions.put(name, perm);

        int outerDepth = parser.getDepth();
        int type;
        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
               && (type != XmlPullParser.END_TAG
                       || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG
                    || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if ("group".equals(tagName)) {
                String gidStr = parser.getAttributeValue(null, "gid");
                if (gidStr != null) {
                    int gid = Process.getGidForName(gidStr);
                    perm.gids = appendInt(perm.gids, gid);
                } else {
                    Slog.w(TAG, "<group> without gid at "
                            + parser.getPositionDescription());
                }
            }
            XmlUtils.skipCurrentTag(parser);
        }
    }

    /** Simple value class to hold an app-link entry.
     *  It is public because PackageManagerService needs to see it */
    public static class AppLink {
        public String pkgname;
        public int state;

        @Override
        public int hashCode() { return pkgname.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof AppLink)) { return false; }
            return pkgname.equals(((AppLink)other).pkgname);
        }
    }
}
