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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.drm.DrmManagerClientWrapper;
import android.drm.DrmStore.DrmDeliveryType;
import android.drm.DrmStore.Action;
import android.drm.DrmStore.RightsStatus;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

/**
 * Utility APIs for OMA DRM v1 supports.
 *
 * @hide
 */
public class DrmHelper {

    public static final String TAG = "Gallery2/DrmHelper";
    /** The MIME type of special DRM files */
    public static final String MIMETYPE_DRM_MESSAGE = "application/vnd.oma.drm.message";
    public static final String MIMETYPE_DRM_CONTENT = "application/vnd.oma.drm.content";

    /** The extensions of DRM files */
    public static final String EXTENSION_DM = ".dm";
    public static final String EXTENSION_FL = ".fl";
    public static final String EXTENSION_DCF = ".dcf";

    public static final String BUY_LICENSE = "android.drmservice.intent.action.BUY_LICENSE";

    /**
    * @hide
    * Internal DRM api
    */
    public static final int checkRightsStatus(Context context, String path,
            String mimeType) {
        int status = -1;
        DrmManagerClientWrapper drmClient = null;
        try {
            drmClient = new DrmManagerClientWrapper(context);
            status = checkRightsStatus(drmClient, path, mimeType);
        } catch (Exception e) {
            Log.e(TAG, "Exception while checking rights");
        } finally {
            if (drmClient != null) {
                drmClient.release();
            }
        }
        return status;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final int checkRightsStatus(
            DrmManagerClientWrapper drmClient, String path, String mimeType) {
        int status = -1;
        if (!TextUtils.isEmpty(mimeType)) {
            if (mimeType.startsWith("image")) {
                status = drmClient.checkRightsStatus(path, Action.DISPLAY);
            } else {
                status = drmClient.checkRightsStatus(path, Action.PLAY);
            }
        } else {
            path = path.replace("/storage/emulated/0",
                    "/storage/emulated/legacy");
            mimeType = drmClient.getOriginalMimeType(path);
            if (mimeType.startsWith("image")) {
                status = drmClient.checkRightsStatus(path, Action.DISPLAY);
            } else {
                status = drmClient.checkRightsStatus(path, Action.PLAY);
            }
        }

        return status;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static boolean consumeDrmRights(String path, String mimeType) {
        if (!isDrmFile(path)) {
            Log.e(TAG, "Could not consume rights from non-drm file. path = "
                    + path);
            return false;
        }

        // here we will consume rights of the image file only because video
        // files right consumption will be taken care by the Media player
        // service.
        if (TextUtils.isEmpty(mimeType) || !mimeType.startsWith("image")) {
            Log.e(TAG, "Can not comsume rights of a non-image file");
            return false;
        }

        return BitmapFactory.consumeDrmImageRights(path);
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static BitmapRegionDecoder createBitmapRegionDecoder(String path,
            boolean isShareable) {
        if (!isDrmFile(path)) {
            Log.e(TAG, "Could not decode non-drm file. path = " + path);
            return null;
        }

        try {
            return BitmapRegionDecoder.newInstanceDrmFile(path, isShareable);
        } catch (Throwable t) {
            Log.w(TAG, "Could not decode non-drm file. error = " + t);
            return null;
        }
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static Bitmap getBitmap(String path) {
        return getBitmap(path, null);
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static Bitmap getBitmap(String path, BitmapFactory.Options options) {
        if (!isDrmFile(path)) {
            Log.e(TAG, "Could not decode non-drm file. path = " + path);
            return null;
        }
        if (options == null) {
            options = new Options();
            options.inPreferredConfig = Config.ARGB_8888;
        }
        return BitmapFactory.decodeDrmFile(path, options);
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static byte[] getDrmImageBytes(String path) {
        if (!isDrmFile(path)) {
            Log.e(TAG, "Could not decode non-drm file. path = " + path);
            return null;
        }

        try {
            return BitmapFactory.getDrmImageBytes(path);
        } catch (Throwable t) {
            Log.w(TAG, "Could not decode non-drm file. error = " + t);
            return null;
        }
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final String getFilePath(Context context, Uri uri) {
        if (isDrmFile(uri.toString())) {
            // uri is a direct drm file path, so return as it is.
            return uri.toString();
        }

        String path = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[] { MediaStore.Images.ImageColumns.DATA }, null,
                    null, null);
            if (cursor.moveToFirst()) {
                path = cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not get drm file path");
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return path;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final String getOriginalMimeType(Context context, String path) {
        String mime = "";
        DrmManagerClientWrapper client = new DrmManagerClientWrapper(context);
        try {
            if (client.canHandle(path, null)) {
                mime = client.getOriginalMimeType(path);
            }
        } finally {
            if (client != null) {
                client.release();
            }
        }
        return mime;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isDrmMimeType(String mimeType) {
        if (!TextUtils.isEmpty(mimeType)) {
            if (MIMETYPE_DRM_MESSAGE.equals(mimeType)
                    || MIMETYPE_DRM_CONTENT.equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isDrmCD(String path) {
        if (!TextUtils.isEmpty(path)) {
            if (path.endsWith(EXTENSION_DM)) {
                return true;
            }
        }
        return false;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isDrmFile(String path) {
        if (!TextUtils.isEmpty(path)) {
            if (path.endsWith(EXTENSION_FL) || path.endsWith(EXTENSION_DM)
                    || path.endsWith(EXTENSION_DCF)) {
                return true;
            }
        }
        return false;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isDrmFL(String path) {
        if (!TextUtils.isEmpty(path)) {
            if (path.endsWith(EXTENSION_FL)) {
                return true;
            }
        }
        return false;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isDrmSD(String path) {
        if (!TextUtils.isEmpty(path)) {
            if (path.endsWith(EXTENSION_DCF)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @hide Internal DRM api
     */
    public static final boolean isDrmFLBlocking(Context context, String path) {
        if (context != null && !TextUtils.isEmpty(path)) {
            DrmManagerClientWrapper client = new DrmManagerClientWrapper(
                    context);
            try {
                ContentValues metadada = client.getMetadata(path);
                if (metadada != null) {
                    int drmType = metadada.getAsInteger("DRM-TYPE");
                    if (drmType == DrmDeliveryType.FORWARD_LOCK) {
                        return true;
                    }
                }
            } finally {
                if (client != null) {
                    client.release();
                }
            }
        }
        return false;
    }

    /**
     * @hide Internal DRM api
     */
    public static final boolean isDrmCDBlocking(Context context, String path) {
        if (context != null && !TextUtils.isEmpty(path)) {
            DrmManagerClientWrapper client = new DrmManagerClientWrapper(
                    context);
            try {
                ContentValues metadada = client.getMetadata(path);
                if (metadada != null) {
                    int drmType = metadada.getAsInteger("DRM-TYPE");
                    if (drmType == DrmDeliveryType.COMBINED_DELIVERY) {
                        return true;
                    }
                }
            } finally {
                if (client != null) {
                    client.release();
                }
            }
        }
        return false;
    }

    /**
     * @hide Internal DRM api
     */
    public static final boolean isDrmSDBlocking(Context context, String path) {
        if (context != null && !TextUtils.isEmpty(path)) {
            DrmManagerClientWrapper client = new DrmManagerClientWrapper(
                    context);
            try {
                ContentValues metadada = client.getMetadata(path);
                if (metadada != null) {
                    int drmType = metadada.getAsInteger("DRM-TYPE");
                    if (drmType == DrmDeliveryType.SEPARATE_DELIVERY) {
                        return true;
                    }
                }
            } finally {
                if (client != null) {
                    client.release();
                }
            }
        }
        return false;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isLicenseableDrmFile(String path) {
        if (isDrmFile(path)
                && (path.endsWith(EXTENSION_DM) || path.endsWith(EXTENSION_DCF))) {
            return true;
        }
        return false;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean isShareableDrmFile(String path) {
        if (isDrmFile(path) && !path.endsWith(EXTENSION_DCF)) {
            return false;
        }
        return true;
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final void manageDrmLicense(Context context, String path,
            String mimeType) {
        if (DrmHelper.isDrmFile(path)) {
            if (DrmHelper.validateLicense(context, path, mimeType)) {
                DrmHelper.consumeDrmRights(path, mimeType);
            }
        }
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final void manageDrmLicense(final Context context,
            Handler handler, final String path, final String mimeType) {
        if (handler != null) {
            // Start consume right thread 1s delayed,
            // because, let animation or transition complete smoothly,
            // then start the blocking operation.
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    if (DrmHelper.isDrmFile(path)) {
                        if (DrmHelper.validateLicense(context, path, mimeType)) {
                            DrmHelper.consumeDrmRights(path, mimeType);
                        }
                    }
                }
            }, 1000);
        } else {
            manageDrmLicense(context, path, mimeType);
        }
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final void showDrmInfo(Context context, String path) {
        if (isDrmFile(path)) {
            Intent drmintent = new Intent(
                    "android.drmservice.intent.action.SHOW_PROPERTIES");
            path = path.replace("/storage/emulated/0",
                    "/storage/emulated/legacy");
            drmintent.putExtra("DRM_FILE_PATH", path);
            drmintent.putExtra("DRM_TYPE", "OMAV1");
            context.sendBroadcast(drmintent);
        }
    }

    /**
    * @hide
    * Internal DRM api
    */
    public static final boolean validateLicense(Context context, String path,
            String mimeType) {
        boolean result = true;
        DrmManagerClientWrapper drmClient = null;
        try {
            drmClient = new DrmManagerClientWrapper(context);
            int status = checkRightsStatus(drmClient, path, mimeType);
            if (RightsStatus.RIGHTS_VALID != status) {
                ContentValues values = drmClient.getMetadata(path);
                String address = values.getAsString("Rights-Issuer");
                Intent intent = new Intent(BUY_LICENSE);
                intent.putExtra("DRM_FILE_PATH", address);
                context.sendBroadcast(intent);
                Log.e(TAG, "Drm License expared! can not proceed ahead");
                result = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while valicating drm license");
        } finally {
            if (drmClient != null) {
                drmClient.release();
            }
        }
        return result;
    }
}
