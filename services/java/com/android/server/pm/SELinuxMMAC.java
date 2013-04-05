/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.pm;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageParser;
import android.content.pm.Signature;
import android.os.Environment;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Centralized access to SELinux MMAC (middleware MAC) implementation.
 * {@hide}
 */
public final class SELinuxMMAC {

    private static final String TAG = "SELinuxMMAC";
    private static final String MMAC_DENY = "MMAC_DENIAL:";
    private static final String MMAC_ENFORCE_PROPERTY = "persist.mmac.enforce";
    private static final boolean DEBUG_POLICY = true;
    private static final boolean DEBUG_POLICY_INSTALL = DEBUG_POLICY || false;

    // Signature based policy.
    private static final HashMap<Signature, InstallPolicy> SIG_POLICY =
        new HashMap<Signature, InstallPolicy>();

    // Package name based policy.
    private static final HashMap<String, InstallPolicy> PKG_POLICY =
        new HashMap<String, InstallPolicy>();

    // Locations of potential install policy files.
    private static final File[] INSTALL_POLICY_FILE = {
        new File(Environment.getDataDirectory(), "security/mac_permissions.xml"),
        new File(Environment.getRootDirectory(), "etc/security/mac_permissions.xml"),
        null};

    private static void flushInstallPolicy() {
        SIG_POLICY.clear();
        PKG_POLICY.clear();
    }

    /**
     * Parses an MMAC install policy from a predefined list of locations.
     * @param none
     * @return boolean indicating whether an install policy was correctly parsed.
     */
    public static boolean readInstallPolicy() {

        return readInstallPolicy(INSTALL_POLICY_FILE);
    }

    /**
     * Returns the current status of MMAC enforcing mode.
     * @param none
     * @return boolean indicating whether or not the device is in enforcing mode.
     */
    public static boolean getEnforcingMode() {
        return SystemProperties.getBoolean(MMAC_ENFORCE_PROPERTY, false);
    }

    /**
     * Sets the current status of MMAC enforcing mode.
     * @param boolean value to set the enforcing state too.
     */
    public static void setEnforcingMode(boolean value) {
        SystemProperties.set(MMAC_ENFORCE_PROPERTY, value ? "1" : "0");
    }

    /**
     * Parses an MMAC install policy given as an argument.
     * @param File object representing the path of the policy.
     * @return boolean indicating whether the install policy was correctly parsed.
     */
    public static boolean readInstallPolicy(File policyFile) {

        return readInstallPolicy(new File[]{policyFile,null});
    }

    private static boolean readInstallPolicy(File[] policyFiles) {

        FileReader policyFile = null;
        int i = 0;
        while (policyFile == null && policyFiles != null && policyFiles[i] != null) {
            try {
                policyFile = new FileReader(policyFiles[i]);
                break;
            } catch (FileNotFoundException e) {
                Slog.d(TAG,"Couldn't find install policy " + policyFiles[i].getPath());
            }
            i++;
        }

        if (policyFile == null) {
            Slog.d(TAG, "MMAC install disabled.");
            return false;
        }

        Slog.d(TAG, "MMAC install enabled using file " + policyFiles[i].getPath());

        boolean enforcing = getEnforcingMode();
        String mode = enforcing ? "enforcing" : "permissive";
        Slog.d(TAG, "MMAC install starting in " + mode + " mode.");

        flushInstallPolicy();

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(policyFile);

            XmlUtils.beginDocument(parser, "policy");
            while (true) {
                XmlUtils.nextElement(parser);
                if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                    break;
                }

                String tagName = parser.getName();
                if ("signer".equals(tagName)) {
                    String cert = parser.getAttributeValue(null, "signature");
                    if (cert == null) {
                        Slog.w(TAG, "<signer> without signature at "
                               + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    Signature signature;
                    try {
                        signature = new Signature(cert);
                    } catch (IllegalArgumentException e) {
                        Slog.w(TAG, "<signer> with bad signature at "
                               + parser.getPositionDescription(), e);
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    if (signature == null) {
                        Slog.w(TAG, "<signer> with null signature at "
                               + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    InstallPolicy type = determineInstallPolicyType(parser, true);
                    if (type != null) {
                        if (DEBUG_POLICY_INSTALL) {
                            // Pretty print the cert
                            int rowLength = 75;
                            int certLength = cert.length();
                            int rows = certLength / rowLength;
                            Slog.i(TAG, "<signer> tag:");
                            for (int j = 0; j <= rows; j++) {
                                int start = rowLength * j;
                                int rowEndIndex = (rowLength * j) + rowLength;
                                int end = rowEndIndex < certLength ? rowEndIndex : certLength;
                                Slog.i(TAG,  cert.substring(start, end));
                            }
                            Slog.i(TAG,  "    Assigned: " + type);
                        }

                        SIG_POLICY.put(signature, type);
                    }
                } else if ("default".equals(tagName)) {
                    InstallPolicy type = determineInstallPolicyType(parser, true);
                    if (type != null) {
                        if (DEBUG_POLICY_INSTALL)
                            Slog.i(TAG, "<default> tag assigned " + type);

                        // The 'null' signature is the default seinfo value
                        SIG_POLICY.put(null, type);
                    }
                } else if ("package".equals(tagName)) {
                    String pkgName = parser.getAttributeValue(null, "name");
                    if (pkgName == null) {
                        Slog.w(TAG, "<package> without name at "
                               + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                        continue;
                    }
                    InstallPolicy type = determineInstallPolicyType(parser, false);
                    if (type != null) {
                        if (DEBUG_POLICY_INSTALL)
                            Slog.i(TAG, "<package> outer tag: (" + pkgName +
                                   ") assigned " + type);

                        PKG_POLICY.put(pkgName, type);
                    }
                } else {
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }
            }
        } catch (XmlPullParserException e) {
            Slog.w(TAG, "Got execption parsing ", e);
        } catch (IOException e) {
            Slog.w(TAG, "Got execption parsing ", e);
        }
        try {
            policyFile.close();
        } catch (IOException e) {
            //omit
        }
        return true;
    }

    private static InstallPolicy determineInstallPolicyType(XmlPullParser parser,
                                                            boolean notInsidePackageTag) throws
            IOException, XmlPullParserException {

        final HashSet<String> denyPolicyPerms  = new HashSet<String>();
        final HashSet<String> allowPolicyPerms = new HashSet<String>();

        final HashMap<String, InstallPolicy> pkgPolicy = new HashMap<String, InstallPolicy>();

        int type;
        int outerDepth = parser.getDepth();
        boolean allowAll = false;
        String seinfo = null;
        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
               && (type != XmlPullParser.END_TAG
                   || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG
                || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if ("seinfo".equals(tagName)) {
                String seinfoValue = parser.getAttributeValue(null, "value");
                if (seinfoValue != null) {
                    seinfo = seinfoValue;
                } else {
                    Slog.w(TAG, "<seinfo> without value at "
                           + parser.getPositionDescription());
                }
            } else if ("allow-permission".equals(tagName)) {
                String permName = parser.getAttributeValue(null, "name");
                if (permName != null) {
                    allowPolicyPerms.add(permName);
                } else {
                    Slog.w(TAG, "<allow-permission> without name at "
                           + parser.getPositionDescription());
                }
            } else if ("deny-permission".equals(tagName)) {
                String permName = parser.getAttributeValue(null, "name");
                if (permName != null) {
                    denyPolicyPerms.add(permName);
                } else {
                    Slog.w(TAG, "<deny-permission> without name at "
                           + parser.getPositionDescription());
                }
            } else if ("allow-all".equals(tagName)) {
                allowAll = true;
            } else if ("package".equals(tagName) && notInsidePackageTag) {
                String pkgName = parser.getAttributeValue(null, "name");
                if (pkgName != null) {
                    InstallPolicy policyType = determineInstallPolicyType(parser, false);
                    if (policyType != null) {
                        pkgPolicy.put(pkgName, policyType);
                        if (DEBUG_POLICY_INSTALL) {
                            Slog.i(TAG, "<package> inner tag: (" + pkgName +
                                   ") assigned " + policyType);
                        }
                    }
                    continue;
                } else {
                    Slog.w(TAG, "<package> inner tag without name at " +
                           parser.getPositionDescription());
                }
            }
            XmlUtils.skipCurrentTag(parser);
        }

        // Order is important. Provide the least amount of privilege.
        InstallPolicy permPolicyType = null;
        if (denyPolicyPerms.size() > 0) {
            permPolicyType = new BlackListPolicy(denyPolicyPerms, pkgPolicy, seinfo);
        } else if (allowPolicyPerms.size() > 0) {
            permPolicyType = new WhiteListPolicy(allowPolicyPerms, pkgPolicy, seinfo);
        } else if (allowAll) {
            permPolicyType = new InstallPolicy(null, pkgPolicy, seinfo);
        } else if (!pkgPolicy.isEmpty()) {
            // Consider the case where outside tag has no perms attached
            // but has an inner package stanza. All the above cases assume that
            // the outer stanza has permission tags, but here we want to ensure
            // we capture the inner but deny all outer.
            permPolicyType = new DenyPolicy(null, pkgPolicy, seinfo);
        }

        return permPolicyType;
    }

    static class InstallPolicy {

        final HashSet<String> policyPerms;
        final HashMap<String, InstallPolicy> pkgPolicy;
        final private String seinfo;

        InstallPolicy(HashSet<String> policyPerms, HashMap<String, InstallPolicy> pkgPolicy,
                      String seinfo) {

            this.policyPerms = policyPerms;
            this.pkgPolicy = pkgPolicy;
            this.seinfo = seinfo;
        }

        boolean passedPolicyChecks(PackageParser.Package pkg) {
            // ensure that local package policy takes precedence
            if (pkgPolicy.containsKey(pkg.packageName)) {
                return pkgPolicy.get(pkg.packageName).passedPolicyChecks(pkg);
            }
            return true;
        }

        String getSEinfo(String pkgName) {
            if (pkgPolicy.containsKey(pkgName)) {
                return pkgPolicy.get(pkgName).getSEinfo(pkgName);
            }
            return seinfo;
        }

        public String toString() {
            StringBuilder out = new StringBuilder();
            out.append("[");
            if (policyPerms != null) {
                out.append(TextUtils.join(",\n", new TreeSet<String>(policyPerms)));
            } else {
                out.append("allow-all");
            }
            out.append("]");
            return out.toString();
        }
    }

    static class WhiteListPolicy extends InstallPolicy {

        WhiteListPolicy(HashSet<String> policyPerms, HashMap<String, InstallPolicy> pkgPolicy,
                        String seinfo) {

            super(policyPerms, pkgPolicy, seinfo);
        }

        @Override
        public boolean passedPolicyChecks(PackageParser.Package pkg) {
            // ensure that local package policy takes precedence
            if (pkgPolicy.containsKey(pkg.packageName)) {
                return pkgPolicy.get(pkg.packageName).passedPolicyChecks(pkg);
            }

            Iterator itr = pkg.requestedPermissions.iterator();
            while (itr.hasNext()) {
                String perm = (String)itr.next();
                if (!policyPerms.contains(perm)) {
                    Slog.w(TAG, MMAC_DENY + " Policy whitelist rejected package "
                           + pkg.packageName + ". The rejected permission is " + perm +
                           " The maximal set allowed is: " + toString());
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "allowed-permissions => \n" + super.toString();
        }
    }

    static class BlackListPolicy extends InstallPolicy {

        BlackListPolicy(HashSet<String> policyPerms, HashMap<String, InstallPolicy> pkgPolicy,
                        String seinfo) {

            super(policyPerms, pkgPolicy, seinfo);
        }

        @Override
        public boolean passedPolicyChecks(PackageParser.Package pkg) {
            // ensure that local package policy takes precedence
            if (pkgPolicy.containsKey(pkg.packageName)) {
                return pkgPolicy.get(pkg.packageName).passedPolicyChecks(pkg);
            }

            Iterator itr = pkg.requestedPermissions.iterator();
            while (itr.hasNext()) {
                String perm = (String)itr.next();
                if (policyPerms.contains(perm)) {
                    Slog.w(TAG, MMAC_DENY + " Policy blacklisted permission " + perm +
                           " for package " + pkg.packageName);
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "denied-permissions => \n" + super.toString();
        }
    }

    static class DenyPolicy extends InstallPolicy {

        DenyPolicy(HashSet<String> policyPerms, HashMap<String, InstallPolicy> pkgPolicy,
                        String seinfo) {

            super(policyPerms, pkgPolicy, seinfo);
        }

        @Override
        public boolean passedPolicyChecks(PackageParser.Package pkg) {
            // ensure that local package policy takes precedence
            if (pkgPolicy.containsKey(pkg.packageName)) {
                return pkgPolicy.get(pkg.packageName).passedPolicyChecks(pkg);
            }
            return false;
        }

        @Override
        public String toString() {
            return "deny-all";
        }
    }

    /**
     * Detemines if the package passes policy. If the package does pass
     * policy checks then an seinfo label is also assigned to the package.
     * @param PackageParser.Package object representing the package
     *         to installed and labeled.
     * @return boolean Indicates whether the package passed policy.
     */
    public static boolean passInstallPolicyChecks(PackageParser.Package pkg) {

        /*
         * Non system installed apps should be treated the same. This
         * means that any post-loaded apk will be assigned the default
         * tag, if one exists in the policy, else null, without respect
         * to the signing key.
         */

        if (((pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) ||
            ((pkg.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)) {

            // We just want one of the signatures to match.
            for (Signature s : pkg.mSignatures) {
                if (s == null)
                    continue;

                // Check for a non default signature policy.
                if (SIG_POLICY.containsKey(s)) {
                    InstallPolicy policy = SIG_POLICY.get(s);
                    if (policy.passedPolicyChecks(pkg)) {
                        String seinfo = pkg.applicationInfo.seinfo = policy.getSEinfo(pkg.packageName);
                        if (DEBUG_POLICY_INSTALL)
                            Slog.i(TAG, "package (" + pkg.packageName + ") installed with " +
                                   " seinfo=" + (seinfo == null ? "null" : seinfo));
                        return true;
                    }
                }
            }

            // Check for a global per-package policy.
            if (PKG_POLICY.containsKey(pkg.packageName)) {
                boolean passed = false;
                InstallPolicy policy = PKG_POLICY.get(pkg.packageName);
                if (policy.passedPolicyChecks(pkg)) {
                    String seinfo = pkg.applicationInfo.seinfo = policy.getSEinfo(pkg.packageName);
                    if (DEBUG_POLICY_INSTALL)
                        Slog.i(TAG, "package (" + pkg.packageName + ") installed with " +
                               " seinfo=" + (seinfo == null ? "null" : seinfo));
                    passed = true;
                }
                return passed;
            }
        }

        // Check for a default policy.
        if (SIG_POLICY.containsKey(null)) {
            boolean passed = false;
            InstallPolicy policy = SIG_POLICY.get(null);
            if (policy.passedPolicyChecks(pkg)) {
                String seinfo = pkg.applicationInfo.seinfo = policy.getSEinfo(pkg.packageName);
                if (DEBUG_POLICY_INSTALL)
                    Slog.i(TAG, "package (" + pkg.packageName + ") installed with " +
                           " seinfo=" + (seinfo == null ? "null" : seinfo));
                passed = true;
            }
            return passed;
        }

        // If we get here it's because this package had no policy.
        return false;
    }
}
