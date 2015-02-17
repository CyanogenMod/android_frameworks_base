 /*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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
package android.drm;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import android.content.ContentValues;
import android.content.Context;
import android.drm.DrmManagerClient;
import android.text.TextUtils;
import android.util.Log;

/**
 * @hide
 * Wrapper to interact with OmaDrmEngine.
 */
public class DrmManagerClientWrapper extends DrmManagerClient {
    private static final String TAG = "DrmManagerClientWrapper";
    private static final String ACTION_STRING_RIGHTS = "rights";
    private static final String ACTION_STRING_METADATA = "metadata";
    private static final String ACTION_STRING_CONSTRAINTS = "constraints";
    private static final String FAKE_ACTION = "0";

    public DrmManagerClientWrapper(Context context) {
        super(context);
    }

    @Override
    public ContentValues getConstraints(String path, int action) {

        String actionPath = ACTION_STRING_CONSTRAINTS + action + ":" + path;

        if (null == path || path.equals("")) {
            throw new IllegalArgumentException("Given path should be non null");
        }

        String info = null;

        FileInputStream is = null;
        try {
            FileDescriptor fd = null;
            File file = new File(path);
            if (file.exists()) {
                is = new FileInputStream(file);
                fd = is.getFD();
            }
            info = getInternalInfo(actionPath, fd);
        } catch (IOException ioe) {
            Log.e(TAG, "getConstraints failed! Exception : " + ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "getConstraints failed to close stream! Exception : " + e);
                }
            }
        }
        Log.i(TAG, "Got Constraints info! info = " + info);
        return parseConstraints(info);
    }

    @Override
    public int checkRightsStatus(String path, int action) {

        String actionPath = ACTION_STRING_RIGHTS + action + ":" + path;

        if (null == path || path.equals("")) {
            throw new IllegalArgumentException("Given path should be non null");
        }

        String info = null;

        FileInputStream is = null;
        try {
            FileDescriptor fd = null;
            File file = new File(path);
            if (file.exists()) {
                is = new FileInputStream(file);
                fd = is.getFD();
            }
            info = getInternalInfo(actionPath, fd);
        } catch (IOException ioe) {
             Log.e(TAG, "checkRightsStatus failed! Exception : " + ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "checkRightsStatus failed to close stream! Exception : " + e);
                }
            }
        }
        Log.i(TAG, "Got RightsStatus info! info = " + info);
        return parseRightsStatus(info);
    }

    @Override
    public ContentValues getMetadata(String path) {

        String actionPath = ACTION_STRING_METADATA + FAKE_ACTION + ":" + path;

        if (null == path || path.equals("")) {
            throw new IllegalArgumentException("Given path should be non null");
        }

        String info = null;

        FileInputStream is = null;
        try {
            FileDescriptor fd = null;
            File file = new File(path);
            if (file.exists()) {
                is = new FileInputStream(file);
                fd = is.getFD();
            }
            info = getInternalInfo(actionPath, fd);
        } catch (IOException ioe) {
             Log.e(TAG, "getMetadata failed! IOException : " + ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "getMetadata failed to close stream! Exception : " + e);
                }
            }
        }
        Log.i(TAG, "Got Metadata info! info = " + info);
        return parseMetadata(info);
    }

    private ContentValues parseConstraints(String constraints) {
        ContentValues contentValues = new ContentValues();
        String attrs[] = TextUtils.split(constraints, "\n");
        for (String attr : attrs) {
            String values[] = TextUtils.split(attr, "\t");
            if (values.length > 0 && !TextUtils.isEmpty(values[0])) {
                contentValues.put(values[0], values[1]);
            }
        }
        return contentValues;
    }

    private ContentValues parseMetadata(String message) {
        ContentValues contentValues = new ContentValues();
        if (!TextUtils.isEmpty(message)) {
            String attrs[] = TextUtils.split(message, "\n");
            for (String attr : attrs) {
                String values[] = TextUtils.split(attr, "\t");
                if (values[0].equals("DRM-TYPE")) {
                    if (values[1] != null) {
                        contentValues.put("DRM-TYPE",
                                Integer.parseInt(values[1]));
                    }
                }
                if (values[0].equals("Rights-Issuer")) {
                    if (values[1] != null) {
                        contentValues.put("Rights-Issuer", values[1]);
                    }
                }
            }
        }
        return contentValues;
    }

    private int parseRightsStatus(String message) {
        if (!TextUtils.isEmpty(message)) {
            return Integer.parseInt(message.trim());
        }
        return -1;
    }
}
