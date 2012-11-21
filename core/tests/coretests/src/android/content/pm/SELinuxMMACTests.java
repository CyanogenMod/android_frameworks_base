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

package android.content.pm;

import android.content.pm.PackageManagerTests;
import android.content.pm.SELinuxMMAC;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.FileUtils;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.DisplayMetrics;
import android.util.Log;

import com.android.frameworks.coretests.R;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;

public class SELinuxMMACTests extends AndroidTestCase {

    private static final String TAG = "SELinuxMMACTests";

    private static File MAC_INSTALL_TMP;
    private static File APK_INSTALL_TMP;
    private static final String MAC_INSTALL_TMP_NAME = "mac_install_policy";
    private static final String APK_INSTALL_TMP_NAME = "install.apk";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Need a tmp file to hold the various mmac install files.
        File filesDir = mContext.getFilesDir();
        MAC_INSTALL_TMP = new File(filesDir, MAC_INSTALL_TMP_NAME);
        assertNotNull(MAC_INSTALL_TMP);

        // Need a tmp file to hold the apk
        APK_INSTALL_TMP = new File(filesDir, APK_INSTALL_TMP_NAME);
        assertNotNull(APK_INSTALL_TMP);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Just in case still around
        MAC_INSTALL_TMP.delete();
        APK_INSTALL_TMP.delete();
    }

    void failStr(String errMsg) {
        Log.w(TAG, "errMsg="+errMsg);
        fail(errMsg);
    }

    void failStr(Exception e) {
        failStr(e.getMessage());
    }

    private PackageParser.Package parsePackage(Uri packageURI) {
        final String archiveFilePath = packageURI.getPath();
        PackageParser packageParser = new PackageParser(archiveFilePath);
        File sourceFile = new File(archiveFilePath);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        PackageParser.Package pkg = packageParser.parsePackage(sourceFile,
                                                               archiveFilePath,
                                                               metrics, 0);
        assertNotNull(pkg);
        assertTrue(packageParser.collectCertificates(pkg,0));
        packageParser = null;
        return pkg;
    }

    Uri getResourceURI(int fileResId, File outFile) {
        Resources res = mContext.getResources();
        InputStream is = null;
        try {
            is = res.openRawResource(fileResId);
        } catch (NotFoundException e) {
            failStr("Failed to load resource with id: " + fileResId);
        }
        assertNotNull(is);
        FileUtils.setPermissions(outFile.getPath(),
                                 FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IRWXO,
                                 -1, -1);
        assertTrue(FileUtils.copyToFile(is, outFile));
        FileUtils.setPermissions(outFile.getPath(),
                                 FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IRWXO,
                                 -1, -1);
        return Uri.fromFile(outFile);
    }

    /**
     * Takes the policy xml file as a resource, the apk as a resource,
     * the expected seinfo string, and the expected install value.
     * We mock a package install here by calling parsePackage.
     */
    void checkInstallMMAC(int policyRes, int apkRes,
                          String expectedSeinfo,
                          boolean expectedPassed) {
        // grab policy file
        Uri policyURI = getResourceURI(policyRes, MAC_INSTALL_TMP);
        assertNotNull(policyURI);
        // parse the policy file
        boolean ret = SELinuxMMAC.readInstallPolicy(new File(policyURI.getPath()));
        assertTrue(ret);
        // grab the apk
        Uri apkURI = getResourceURI(apkRes, APK_INSTALL_TMP);
        assertNotNull(apkURI);
        // "install" the apk
        PackageParser.Package pkg = parsePackage(apkURI);
        assertNotNull(pkg);
        assertNotNull(pkg.packageName);
        // check for correct passed policy value
        boolean passed = SELinuxMMAC.passInstallPolicyChecks(pkg);
        assertEquals(expectedPassed, passed);
        // check for correct seinfo label
        String seinfo = pkg.applicationInfo.seinfo;
        if (seinfo == null)
            seinfo = "null";
        assertEquals(expectedSeinfo, seinfo);

        // delete policy and apk
        MAC_INSTALL_TMP.delete();
        APK_INSTALL_TMP.delete();
    }

    /*
     * Requested policy file doesn't exist.
     */
    @LargeTest
    public void testINSTALL_POLICY_BADPATH() {
        boolean ret = SELinuxMMAC.readInstallPolicy(new File("/d/o/e/s/n/t/e/x/i/s/t"));
        assertFalse(ret);
    }

    /*
     * Requested policy file is null object.
     */
    @LargeTest
    public void testINSTALL_POLICY_NULL() {
        boolean ret = SELinuxMMAC.readInstallPolicy(null);
        assertFalse(ret);
    }

    /*
     * No need to test a valid install policy file. All the tests
     * below test it implicitly.
     */

    /*
     * Signature stanza hits. apk is installed from allow-all.
     */
    @LargeTest
    public void testSIGNATURE_ALLOWALL_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_sig_all, R.raw.signed_platform,
                         "platform", true);
    }

    /*
     * Signature stanza hits. apk is installed from whitelist.
     */
    @LargeTest
    public void testSIGNATURE_WHITELIST_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_sig_white, R.raw.signed_platform,
                         "platform", true);
    }

    /*
     * Signature stanza hits. apk is installed from blacklist.
     */
    @LargeTest
    public void testSIGNATURE_BLACKLIST_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_sig_black, R.raw.signed_platform,
                         "platform", true);
    }

    /*
     * Signature stanza hits. apk is installed. null seinfo tag.
     */
    @LargeTest
    public void testSIGNATURE_INSTALLED_NULL_SEINFO() {
        checkInstallMMAC(R.raw.mmac_sig_null, R.raw.signed_platform,
                         "null", true);
    }

    /*
     * Signature stanza hits. apk is denied.
     * Package stanza allows.
     */
    @LargeTest
    public void testSIGNATURE_DENIED_PACKAGE_ALLOWS() {
        checkInstallMMAC(R.raw.mmac_sig_deny_pkg_allow, R.raw.signed_platform,
                         "package", true);
    }

    /*
     * Signature stanza hits. apk is denied.
     * Package stanza then denys.
     */
    @LargeTest
    public void testSIGNATURE_DENIED_PACKAGE_DENY() {
        checkInstallMMAC(R.raw.mmac_sig_deny_pkg_deny, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Signature stanza hits. apk is denied.
     * Default stanza allows.
     */
    @LargeTest
    public void testSIGNATURE_DENIED_DEFAULT_ALLOWS() {
        checkInstallMMAC(R.raw.mmac_sig_deny_default_allow, R.raw.signed_platform,
                         "default", true);
    }

    /*
     * Signature stanza hits yet denys. Default stanza hits and denys.
     */
    @LargeTest
    public void testSIGNATURE_DENY_DEFAULT_DENY() {
        checkInstallMMAC(R.raw.mmac_sig_deny_default_deny, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Signature stanza hits. apk is denied.
     * No other policy present.
     */
    @LargeTest
    public void testSIGNATURE_DENIED_NOOTHER_POLICY() {
        checkInstallMMAC(R.raw.mmac_sig_deny_noother, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Package stanza hits. apk is installed from allow-all.
     */
    @LargeTest
    public void testPACKAGE_ALLOWALL_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_pkg_all, R.raw.signed_platform,
                         "package", true);
    }

    /*
     * Package stanza hits. apk is installed from whitelist.
     */
    @LargeTest
    public void testPACKAGE_WHITELIST_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_pkg_white, R.raw.signed_platform,
                         "package", true);
    }

    /*
     * Package stanza hits. apk is installed from blacklist.
     */
    @LargeTest
    public void testPACKAGE_BLACKLIST_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_pkg_black, R.raw.signed_platform,
                         "package", true);
    }

    /*
     * Package stanza hits. apk is installed. seinfo is null.
     */
    @LargeTest
    public void testPACKAGE_INSTALLED_NULL_SEINFO() {
        checkInstallMMAC(R.raw.mmac_pkg_null_seinfo, R.raw.signed_platform,
                         "null", true);
    }

    /*
     * Package stanza hits. apk is denied on whitelist.
     */
    @LargeTest
    public void testPACKAGE_WHITELIST_DENIED() {
        checkInstallMMAC(R.raw.mmac_pkg_deny_white, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Package stanza hits. apk is denied on blacklist.
     */
    @LargeTest
    public void testPACKAGE_BLACKLIST_DENIED() {
        checkInstallMMAC(R.raw.mmac_pkg_deny_black, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Default stanza hits. apk is installed from allowall.
     */
     @LargeTest
     public void testDEFAULT_ALLOWALL_INSTALLED() {
         checkInstallMMAC(R.raw.mmac_default_all, R.raw.signed_platform,
                          "default", true);
    }

    /*
     * Default stanza hits. apk is installed from whitelist.
     */
    @LargeTest
    public void testDEFAULT_WHITELIST_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_default_white, R.raw.signed_platform,
                         "default", true);
    }

    /*
     * Default stanza hits. apk is installed from blacklist.
     */
    @LargeTest
    public void testDEFAULT_BLACKLIST_INSTALLED() {
        checkInstallMMAC(R.raw.mmac_default_black, R.raw.signed_platform,
                         "default", true);
    }

    /*
     * Default stanza hits. apk installed. null seinfo.
     */
    @LargeTest
    public void testDEFAULT_INSTALLED_NULL_SEINFO() {
        checkInstallMMAC(R.raw.mmac_default_null_seinfo, R.raw.signed_platform,
                         "null", true);
        }

    /*
     * Default stanza hits. apk is denied on whitelist.
     */
    @LargeTest
    public void testDEFAULT_WHITELIST_DENIED() {
        checkInstallMMAC(R.raw.mmac_default_white_deny, R.raw.signed_platform,
                         "null", false);
        }

    /*
     * Default stanza hits. apk is denied on blacklist.
     */
    @LargeTest
    public void testDEFAULT_BLACKLIST_DENIED() {
        checkInstallMMAC(R.raw.mmac_default_black_deny, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * No matching entry in policy.
     */
    @LargeTest
    public void testNO_MATCHING_POLICY() {
        checkInstallMMAC(R.raw.mmac_no_match, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Signature catches yet there is a package stanza inside that allows
     * based on allow-all.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_ALLOW_ALL() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_allow_all, R.raw.signed_platform,
                         "insidepackage", true);
    }

    /*
     * Signature catches yet there is a package stanza inside that allows
     * based on whitelist.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_ALLOW_WHITE() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_allow_white, R.raw.signed_platform,
                         "insidepackage", true);
    }

    /*
     * Signature catches yet there is a package stanza inside that allows
     * based on blacklist.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_ALLOW_BLACK() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_allow_black, R.raw.signed_platform,
                         "insidepackage", true);
    }

    /*
     * Signature catches yet there is a package stanza inside that denies
     * based on blacklist. Stand alone package stanza then allows.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_DENY_PKG_OUT_ALLOWS() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_deny_pkg, R.raw.signed_platform,
                         "package", true);
    }

    /*
     * Signature catches yet there is a package stanza inside that denies
     * based on whitelist. default stanza catches and allows.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_DENY_DEFAULT_ALLOWS() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_deny_default, R.raw.signed_platform,
                         "default", true);
    }

    /*
     * Signature catches yet there is a package stanza inside that denies.
     * No other policy catches. app is denied.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_DENY_NOOTHER() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_deny_noother, R.raw.signed_platform,
                         "null", false);
    }

    /*
     * Signature catches yet there is a package stanza inside that allows.
     * However, the seingo tag is null.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_ALLOWS_NULL_SEINFO() {
        checkInstallMMAC(R.raw.mmac_inside_pkg_allow_null_seinfo, R.raw.signed_platform,
                         "null", true);
    }

    /*
     * Signature stanza has inner package stanza. Outer sig stanza
     * has no rules. Check app signed with same key, diff pkg name, doesn't
     * catch on outer signer stanza. Catches on default though.
     */
    @LargeTest
    public void testPACKAGE_SAME_CERT_DIFF_NAME_SKIPS_OUTER() {
        checkInstallMMAC(R.raw.mmac_diff_name_skip_outer, R.raw.signed_platform_2,
                         "default", true);
    }

    /*
     * Signature stanza has inner package stanza. Outer sig stanza
     * has no rules. Check app catches on inner.
     */
    @LargeTest
    public void testPACKAGE_INNER_HITS_NO_OUTER_RULES() {
        checkInstallMMAC(R.raw.mmac_outer_no_rule_catch_inner, R.raw.signed_platform,
                         "insidepackage", true);
    }

    /*
     * Signature stanza has inner package stanza with no seinfo tag.
     * Outer sig stanza has no rules but seinfo tag. Check app labeled null.
     */
    @LargeTest
    public void testPACKAGE_INSIDE_SIG_ALLOWS_NULL_SEINFO_OUTER_SEINFO_MISSED() {
        checkInstallMMAC(R.raw.mmac_inner_seinfo_null_outer_seinfo, R.raw.signed_platform,
                         "null", true);
    }

    /*
     * Signature stanza has inner package stanza. Outer sig stanza
     * has blacklist. Check app signed with same key, diff pkg name,
     * denied on outer signer stanza. Catches on default though.
     */
    @LargeTest
    public void testPACKAGE_SAME_CERT_DIFF_NAME_DENIED_OUTER() {
        checkInstallMMAC(R.raw.mmac_diff_name_deny_outer, R.raw.signed_platform_2,
                         "default", true);
    }

    /*
     * Signature stanza has inner package stanza. Check that app
     * with same package name, diff key, catches on another cert.
     */
    @LargeTest
    public void testPACKAGE_DIFF_CERT_SAME_NAME() {
        checkInstallMMAC(R.raw.mmac_same_name_diff_cert, R.raw.signed_media,
                         "media", true);
    }

    /*
     * Default stanza with inner package that hits. Outer not empty.
     */
    @LargeTest
    public void testPACKAGE_INNER_DEFAULT() {
        checkInstallMMAC(R.raw.mmac_default_inner_pkg, R.raw.signed_media,
                         "insidedefault", true);
    }

    /*
     * Default stanza with inner package that hits. Outer empty.
     */
    @LargeTest
    public void testPACKAGE_INNER_DEFAULT_OUTER_EMPTY() {
        checkInstallMMAC(R.raw.mmac_default_inner_pkg_out_empty, R.raw.signed_media,
                         "insidedefault", true);
    }

    /*
     * Default stanza with inner package that denies.
     */
    @LargeTest
    public void testPACKAGE_INNER_DEFAULT_DENY() {
        checkInstallMMAC(R.raw.mmac_default_inner_pkg_deny, R.raw.signed_media,
                         "null", false);
    }
}
