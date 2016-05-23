/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

public class AppOpsPolicy {
    static final String TAG = "AppOpsPolicy";
    static final boolean DEBUG = false;
    final File mFile;
    final Context mContext;
    public static final int CONTROL_SHOW = 0;

    public static final int CONTROL_NOSHOW = 1;

    public static final int CONTROL_UNKNOWN = 2;

    // Rate limiting thresholds for ask operations
    public static final int RATE_LIMIT_OP_COUNT = 3;
    public static final int RATE_LIMIT_OPS_TOTAL_PKG_COUNT = 4;
    public static final int RATE_LIMIT_OP_DELAY_CEILING = 10;

    public static int stringToControl(String show) {
        if ("true".equalsIgnoreCase(show)) {
            return CONTROL_SHOW;
        } else if ("false".equalsIgnoreCase(show)) {
            return CONTROL_NOSHOW;
        }
        return CONTROL_UNKNOWN;
    }

    HashMap<String, PolicyPkg> mPolicy = new HashMap<String, PolicyPkg>();

    public AppOpsPolicy(File file, Context context) {
        super();
        mFile = file;
        mContext = context;
    }

    public final static class PolicyPkg extends SparseArray<PolicyOp> {
        public String packageName;
        public int mode;
        public int show;
        public String type;

        public PolicyPkg(String packageName, int mode, int show, String type) {
            this.packageName = packageName;
            this.mode = mode;
            this.show = show;
            this.type = type;
        }

        @Override
        public String toString() {
            return "PolicyPkg [packageName=" + packageName + ", mode=" + mode
                    + ", show=" + show + ", type=" + type + "]";
        }

    }

    public final static class PolicyOp {
        public int op;
        public int mode;
        public int show;

        public PolicyOp(int op, int mode, int show) {
            this.op = op;
            this.mode = mode;
            this.show = show;
        }

        @Override
        public String toString() {
            return "PolicyOp [op=" + op + ", mode=" + mode + ", show=" + show
                    + "]";
        }
    }

    void readPolicy() {
        FileInputStream stream;
        synchronized (mFile) {
            try {
                stream = new FileInputStream(mFile);
            } catch (FileNotFoundException e) {
                Slog.i(TAG, "App ops policy file (" + mFile.getPath()
                        + ") not found; Skipping.");
                return;
            }
            boolean success = false;
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, null);
                int type;
                success = true;
                while ((type = parser.next()) != XmlPullParser.START_TAG
                        && type != XmlPullParser.END_DOCUMENT) {
                    ;
                }
                if (type != XmlPullParser.START_TAG) {
                    throw new IllegalStateException("no start tag found");
                }

                int outerDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG
                            || type == XmlPullParser.TEXT) {
                        continue;
                    }

                    String tagName = parser.getName();
                    if (tagName.equals("user-app")
                            || tagName.equals("system-app")) {
                        readDefaultPolicy(parser, tagName);
                    } else if (tagName.equals("application")) {
                        readApplicationPolicy(parser);
                    } else {
                        Slog.w(TAG, "Unknown element under <appops-policy>: "
                                + parser.getName());
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
            } catch (IllegalStateException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (NullPointerException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (NumberFormatException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (XmlPullParserException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (IOException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (IndexOutOfBoundsException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } finally {
                if (!success) {
                    mPolicy.clear();
                }
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void readDefaultPolicy(XmlPullParser parser, String packageName)
            throws NumberFormatException, XmlPullParserException, IOException {
        if (!"user-app".equalsIgnoreCase(packageName)
                && !"system-app".equalsIgnoreCase(packageName)) {
            return;
        }
        int mode = AppOpsManager.stringToMode(parser.getAttributeValue(null,
                "permission"));
        int show = stringToControl(parser.getAttributeValue(null, "show"));
        if (mode == AppOpsManager.MODE_ERRORED && show == CONTROL_UNKNOWN) {
            return;
        }
        PolicyPkg pkg = this.mPolicy.get(packageName);
        if (pkg == null) {
            pkg = new PolicyPkg(packageName, mode, show, packageName);
            this.mPolicy.put(packageName, pkg);
        } else {
            Slog.w(TAG, "Duplicate policy found for package: " + packageName
                    + " of type: " + packageName);
            pkg.mode = mode;
            pkg.show = show;
        }
    }

    private void readApplicationPolicy(XmlPullParser parser)
            throws NumberFormatException, XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("pkg")) {
                readPkgPolicy(parser);
            } else {
                Slog.w(TAG,
                        "Unknown element under <application>: "
                                + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private void readPkgPolicy(XmlPullParser parser)
            throws NumberFormatException, XmlPullParserException, IOException {
        String packageName = parser.getAttributeValue(null, "name");
        if (packageName == null)
            return;
        String appType = parser.getAttributeValue(null, "type");
        if (appType == null)
            return;
        int mode = AppOpsManager.stringToMode(parser.getAttributeValue(null,
                "permission"));
        int show = stringToControl(parser.getAttributeValue(null, "show"));
        String key = packageName + "." + appType;
        PolicyPkg pkg = this.mPolicy.get(key);
        if (pkg == null) {
            pkg = new PolicyPkg(packageName, mode, show, appType);
            this.mPolicy.put(key, pkg);
        } else {
            Slog.w(TAG, "Duplicate policy found for package: " + packageName
                    + " of type: " + appType);
            pkg.mode = mode;
            pkg.show = show;
        }

        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("op")) {
                readOpPolicy(parser, pkg);
            } else {
                Slog.w(TAG, "Unknown element under <pkg>: " + parser.getName());
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }

    private void readOpPolicy(XmlPullParser parser, PolicyPkg pkg)
            throws NumberFormatException, XmlPullParserException, IOException {
        if (pkg == null) {
            return;
        }
        String opName = parser.getAttributeValue(null, "name");
        if (opName == null) {
            Slog.w(TAG, "Op name is null");
            return;
        }
        int code = AppOpsManager.stringOpToOp(opName);
        if (code == AppOpsManager.OP_NONE) {
            Slog.w(TAG, "Unknown Op: " + opName);
            return;
        }
        int mode = AppOpsManager.stringToMode(parser.getAttributeValue(null,
                "permission"));
        int show = stringToControl(parser.getAttributeValue(null, "show"));
        if (mode == AppOpsManager.MODE_ERRORED && show == CONTROL_UNKNOWN) {
            return;
        }
        PolicyOp op = pkg.get(code);
        if (op == null) {
            op = new PolicyOp(code, mode, show);
            pkg.put(code, op);
        } else {
            Slog.w(TAG, "Duplicate policy found for package: "
                    + pkg.packageName + " type: " + pkg.type + " op: " + op.op);
            op.mode = mode;
            op.show = show;
        }
    }

    void debugPoilcy() {
        Iterator<Map.Entry<String, PolicyPkg>> iterator = mPolicy.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            String key = iterator.next().getKey();
            if (DEBUG)
                Slog.d(TAG, "Key: " + key);
            PolicyPkg pkg = mPolicy.get(key);
            if (pkg == null) {
                if (DEBUG)
                    Slog.d(TAG, "Pkg is null for key: " + key);
                continue;
            }
            if (DEBUG)
                Slog.d(TAG, pkg.toString());
            for (int i = 0; i < pkg.size(); i++) {
                PolicyOp op = pkg.valueAt(i);
                if (DEBUG)
                    Slog.d(TAG, op.toString());
            }
        }
    }

    private String getAppType(String packageName) {
        String appType = null;
        ApplicationInfo appInfo = null;
        if (mContext != null) {
            try {
                appInfo = mContext.getPackageManager().getApplicationInfo(
                        packageName, 0);
            } catch (NameNotFoundException e) {
                appInfo = null;
            }
            if (appInfo != null) {
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    appType = "system-app";
                } else {
                    appType = "user-app";
                }
            }
        } else {
            Slog.e(TAG, "Context is null");
        }
        return appType;
    }

    public boolean isControlAllowed(int code, String packageName) {
        boolean isShow = true;
        int show = CONTROL_UNKNOWN;
        PolicyPkg pkg;
        String key;
        String type;

        if (mPolicy == null) {
            return isShow;
        }

        type = getAppType(packageName);
        if (type != null) {
            key = type;
            pkg = mPolicy.get(key);
            if (pkg != null && pkg.show != CONTROL_UNKNOWN) {
                show = pkg.show;
            }
        }
        key = packageName;
        if (type != null) {
            key = key + "." + type;
        }
        pkg = mPolicy.get(key);
        if (pkg != null) {
            if (pkg.show != CONTROL_UNKNOWN) {
                show = pkg.show;
            }
            PolicyOp op = pkg.get(code);
            if (op != null) {
                if (op.show != CONTROL_UNKNOWN) {
                    show = op.show;
                }
            }
        }
        if (show == CONTROL_NOSHOW) {
            isShow = false;
        }
        return isShow;
    }

    public int getDefualtMode(int code, String packageName) {
        int mode = AppOpsManager.MODE_ERRORED;
        PolicyPkg pkg;
        String key;
        String type;

        if (mPolicy == null) {
            return mode;
        }
        if (DEBUG)
            Slog.d(TAG, "Default mode requested for op=" + code + " package="
                    + packageName);
        type = getAppType(packageName);
        if (type != null) {
            // Get value based on 'type'
            key = type;
            pkg = mPolicy.get(key);
            if (pkg != null && pkg.mode != AppOpsManager.MODE_ERRORED) {
                if (DEBUG)
                    Slog.d(TAG, "Setting value based on type: " + pkg);
                mode = pkg.mode;
            }
        }
        // Get value based on 'pkg'.
        key = packageName;
        if (type != null) {
            key = key + "." + type;
        }
        pkg = mPolicy.get(key);
        if (pkg != null) {
            if (pkg.mode != AppOpsManager.MODE_ERRORED) {
                if (DEBUG)
                    Slog.d(TAG, "Setting value based on packageName: " + pkg);
                mode = pkg.mode;
            }
            // Get value base on 'op'
            PolicyOp op = pkg.get(code);
            if (op != null) {
                if (op.mode != AppOpsManager.MODE_ERRORED) {
                    if (DEBUG)
                        Slog.d(TAG, "Setting value based on op: " + op);
                    mode = op.mode;
                }
            }
        }
        if (DEBUG)
            Slog.d(TAG, "Returning mode=" + mode);
        return mode;
    }
}
