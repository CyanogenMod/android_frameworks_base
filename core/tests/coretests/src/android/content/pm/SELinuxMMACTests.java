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
     * and the expected seinfo string.
     * We mock a package install here by calling parsePackage.
     */
    void checkSeinfoWithPolicy(int policyRes, int apkRes,
                               String expectedSeinfo) {
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
        // check for correct seinfo label
        SELinuxMMAC.assignSeinfoValue(pkg);
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
    public void testPOLICY_BADPATH() {
        boolean ret = SELinuxMMAC.readInstallPolicy(new File("/d/o/e/s/n/t/e/x/i/s/t"));
        assertFalse(ret);
    }

    /*
     * Requested policy file is null object.
     */
    @LargeTest
    public void testPOLICY_NULL() {
        boolean ret = SELinuxMMAC.readInstallPolicy(null);
        assertFalse(ret);
    }

    /*
     * Parse an apk that should be labeled with signature stanza.
     */
    @LargeTest
    public void testSIGNATURE_LABEL() {
        checkSeinfoWithPolicy(R.raw.mac_permissions_signature, R.raw.signed_platform,
                              "platform");
    }

    /*
     * Parse an apk that should be labeled with default stanza.
     */
    @LargeTest
    public void testDEFAULT_LABEL() {
        checkSeinfoWithPolicy(R.raw.mac_permissions_default, R.raw.signed_platform,
                              "default");
    }

    /*
     * Parse an apk that should be labeled with package stanza.
     */
    @LargeTest
    public void testPACKAGENAME_LABEL() {
        checkSeinfoWithPolicy(R.raw.mac_permissions_package_name, R.raw.signed_platform,
                              "per-package");
    }

    /*
     * Parse an apk that should not be labeled. No matching entry in policy.
     */
    @LargeTest
    public void testNO_MATCHING_POLICY() {
        checkSeinfoWithPolicy(R.raw.mac_permissions_no_match, R.raw.signed_platform,
                              "null");
    }
}
