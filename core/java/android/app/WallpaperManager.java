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

package android.app;

import android.annotation.IntDef;
import android.annotation.RawRes;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.DeadSystemException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManagerGlobal;

import libcore.io.IoUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Provides access to the system wallpaper. With WallpaperManager, you can
 * get the current wallpaper, get the desired dimensions for the wallpaper, set
 * the wallpaper, and more. Get an instance of WallpaperManager with
 * {@link #getInstance(android.content.Context) getInstance()}.
 *
 * <p> An app can check whether wallpapers are supported for the current user, by calling
 * {@link #isWallpaperSupported()}, and whether setting of wallpapers is allowed, by calling
 * {@link #isSetWallpaperAllowed()}.
 */
public class WallpaperManager {
    private static String TAG = "WallpaperManager";
    private static boolean DEBUG = false;
    private float mWallpaperXStep = -1;
    private float mWallpaperYStep = -1;

    /** {@hide} */
    private static final String PROP_WALLPAPER = "ro.config.wallpaper";
    /** {@hide} */
    private static final String PROP_LOCK_WALLPAPER = "ro.config.lock_wallpaper";
    /** {@hide} */
    private static final String PROP_WALLPAPER_COMPONENT = "ro.config.wallpaper_component";

    /**
     * Activity Action: Show settings for choosing wallpaper. Do not use directly to construct
     * an intent; instead, use {@link #getCropAndSetWallpaperIntent}.
     * <p>Input:  {@link Intent#getData} is the URI of the image to crop and set as wallpaper.
     * <p>Output: RESULT_OK if user decided to crop/set the wallpaper, RESULT_CANCEL otherwise
     * Activities that support this intent should specify a MIME filter of "image/*"
     */
    public static final String ACTION_CROP_AND_SET_WALLPAPER =
            "android.service.wallpaper.CROP_AND_SET_WALLPAPER";

    /**
     * Launch an activity for the user to pick the current global live
     * wallpaper.
     */
    public static final String ACTION_LIVE_WALLPAPER_CHOOSER
            = "android.service.wallpaper.LIVE_WALLPAPER_CHOOSER";

    /**
     * Directly launch live wallpaper preview, allowing the user to immediately
     * confirm to switch to a specific live wallpaper.  You must specify
     * {@link #EXTRA_LIVE_WALLPAPER_COMPONENT} with the ComponentName of
     * a live wallpaper component that is to be shown.
     */
    public static final String ACTION_CHANGE_LIVE_WALLPAPER
            = "android.service.wallpaper.CHANGE_LIVE_WALLPAPER";

    /**
     * Extra in {@link #ACTION_CHANGE_LIVE_WALLPAPER} that specifies the
     * ComponentName of a live wallpaper that should be shown as a preview,
     * for the user to confirm.
     */
    public static final String EXTRA_LIVE_WALLPAPER_COMPONENT
            = "android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT";

    /**
     * Manifest entry for activities that respond to {@link Intent#ACTION_SET_WALLPAPER}
     * which allows them to provide a custom large icon associated with this action.
     */
    public static final String WALLPAPER_PREVIEW_META_DATA = "android.wallpaper.preview";

    /**
     * Command for {@link #sendWallpaperCommand}: reported by the wallpaper
     * host when the user taps on an empty area (not performing an action
     * in the host).  The x and y arguments are the location of the tap in
     * screen coordinates.
     */
    public static final String COMMAND_TAP = "android.wallpaper.tap";

    /**
     * Command for {@link #sendWallpaperCommand}: reported by the wallpaper
     * host when the user releases a secondary pointer on an empty area
     * (not performing an action in the host).  The x and y arguments are
     * the location of the secondary tap in screen coordinates.
     */
    public static final String COMMAND_SECONDARY_TAP = "android.wallpaper.secondaryTap";

    /**
     * Command for {@link #sendWallpaperCommand}: reported by the wallpaper
     * host when the user drops an object into an area of the host.  The x
     * and y arguments are the location of the drop.
     */
    public static final String COMMAND_DROP = "android.home.drop";

    /**
     * Extra passed back from setWallpaper() giving the new wallpaper's assigned ID.
     * @hide
     */
    public static final String EXTRA_NEW_WALLPAPER_ID = "android.service.wallpaper.extra.ID";

    // flags for which kind of wallpaper to act on

    /** @hide */
    @IntDef(flag = true, value = {
            FLAG_SYSTEM,
            FLAG_LOCK
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SetWallpaperFlags {}

    /**
     * Flag: set or retrieve the general system wallpaper.
     */
    public static final int FLAG_SYSTEM = 1 << 0;

    /**
     * Flag: set or retrieve the lock-screen-specific wallpaper.
     */
    public static final int FLAG_LOCK = 1 << 1;

    private final Context mContext;

    /**
     * Special drawable that draws a wallpaper as fast as possible.  Assumes
     * no scaling or placement off (0,0) of the wallpaper (this should be done
     * at the time the bitmap is loaded).
     */
    static class FastBitmapDrawable extends Drawable {
        private final Bitmap mBitmap;
        private final int mWidth;
        private final int mHeight;
        private int mDrawLeft;
        private int mDrawTop;
        private final Paint mPaint;

        private FastBitmapDrawable(Bitmap bitmap) {
            mBitmap = bitmap;
            mWidth = bitmap.getWidth();
            mHeight = bitmap.getHeight();

            setBounds(0, 0, mWidth, mHeight);

            mPaint = new Paint();
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, mDrawLeft, mDrawTop, mPaint);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            mDrawLeft = left + (right-left - mWidth) / 2;
            mDrawTop = top + (bottom-top - mHeight) / 2;
        }

        @Override
        public void setAlpha(int alpha) {
            throw new UnsupportedOperationException("Not supported with this drawable");
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            throw new UnsupportedOperationException("Not supported with this drawable");
        }

        @Override
        public void setDither(boolean dither) {
            throw new UnsupportedOperationException("Not supported with this drawable");
        }

        @Override
        public void setFilterBitmap(boolean filter) {
            throw new UnsupportedOperationException("Not supported with this drawable");
        }

        @Override
        public int getIntrinsicWidth() {
            return mWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mHeight;
        }

        @Override
        public int getMinimumWidth() {
            return mWidth;
        }

        @Override
        public int getMinimumHeight() {
            return mHeight;
        }
    }

    static class Globals extends IWallpaperManagerCallback.Stub {
        private final IWallpaperManager mService;
        private Bitmap mCachedWallpaper;
        private int mCachedWallpaperUserId;
        private Bitmap mDefaultWallpaper;

        Globals(Looper looper) {
            IBinder b = ServiceManager.getService(Context.WALLPAPER_SERVICE);
            mService = IWallpaperManager.Stub.asInterface(b);
            forgetLoadedWallpaper();
        }

        public void onWallpaperChanged() {
            /* The wallpaper has changed but we shouldn't eagerly load the
             * wallpaper as that would be inefficient. Reset the cached wallpaper
             * to null so if the user requests the wallpaper again then we'll
             * fetch it.
             */
            forgetLoadedWallpaper();
        }

        public Bitmap peekWallpaperBitmap(Context context, boolean returnDefault,
                @SetWallpaperFlags int which) {
            return peekWallpaperBitmap(context, returnDefault, which, context.getUserId());
        }

        public Bitmap peekWallpaperBitmap(Context context, boolean returnDefault,
                @SetWallpaperFlags int which, int userId) {
            if (mService != null) {
                try {
                    if (!mService.isWallpaperSupported(context.getOpPackageName())) {
                        return null;
                    }
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            synchronized (this) {
                if (mCachedWallpaper != null && mCachedWallpaperUserId == userId) {
                    return mCachedWallpaper;
                }
                mCachedWallpaper = null;
                mCachedWallpaperUserId = 0;
                try {
                    mCachedWallpaper = getCurrentWallpaperLocked(userId);
                    mCachedWallpaperUserId = userId;
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, "No memory load current wallpaper", e);
                }
                if (mCachedWallpaper != null) {
                    return mCachedWallpaper;
                }
            }
            if (returnDefault) {
                Bitmap defaultWallpaper = mDefaultWallpaper;
                if (defaultWallpaper == null) {
                    defaultWallpaper = getDefaultWallpaper(context, which);
                    synchronized (this) {
                        mDefaultWallpaper = defaultWallpaper;
                    }
                }
                return defaultWallpaper;
            }
            return null;
        }

        void forgetLoadedWallpaper() {
            synchronized (this) {
                mCachedWallpaper = null;
                mCachedWallpaperUserId = 0;
                mDefaultWallpaper = null;
            }
        }

        private Bitmap getCurrentWallpaperLocked(int userId) {
            if (mService == null) {
                Log.w(TAG, "WallpaperService not running");
                return null;
            }

            try {
                Bundle params = new Bundle();
                ParcelFileDescriptor fd = mService.getWallpaper(this, FLAG_SYSTEM,
                        params, userId);
                if (fd != null) {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        return BitmapFactory.decodeFileDescriptor(
                                fd.getFileDescriptor(), null, options);
                    } catch (OutOfMemoryError e) {
                        Log.w(TAG, "Can't decode file", e);
                    } finally {
                        IoUtils.closeQuietly(fd);
                    }
                }
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
            return null;
        }

        private Bitmap getDefaultWallpaper(Context context, @SetWallpaperFlags int which) {
            InputStream is = openDefaultWallpaper(context, which);
            if (is != null) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    return BitmapFactory.decodeStream(is, null, options);
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, "Can't decode stream", e);
                } finally {
                    IoUtils.closeQuietly(is);
                }
            }
            return null;
        }
    }

    private static final Object sSync = new Object[0];
    private static Globals sGlobals;

    static void initGlobals(Looper looper) {
        synchronized (sSync) {
            if (sGlobals == null) {
                sGlobals = new Globals(looper);
            }
        }
    }

    /*package*/ WallpaperManager(Context context, Handler handler) {
        mContext = context;
        initGlobals(context.getMainLooper());
    }

    /**
     * Retrieve a WallpaperManager associated with the given Context.
     */
    public static WallpaperManager getInstance(Context context) {
        return (WallpaperManager)context.getSystemService(
                Context.WALLPAPER_SERVICE);
    }

    /** @hide */
    public IWallpaperManager getIWallpaperManager() {
        return sGlobals.mService;
    }

    /**
     * Retrieve the current system wallpaper; if
     * no wallpaper is set, the system built-in static wallpaper is returned.
     * This is returned as an
     * abstract Drawable that you can install in a View to display whatever
     * wallpaper the user has currently set.
     * <p>
     * This method can return null if there is no system wallpaper available, if
     * wallpapers are not supported in the current user, or if the calling app is not
     * permitted to access the system wallpaper.
     *
     * @return Returns a Drawable object that will draw the system wallpaper,
     *     or {@code null} if no system wallpaper exists or if the calling application
     *     is not able to access the wallpaper.
     */
    public Drawable getDrawable() {
        Bitmap bm = sGlobals.peekWallpaperBitmap(mContext, true, FLAG_SYSTEM);
        if (bm != null) {
            Drawable dr = new BitmapDrawable(mContext.getResources(), bm);
            dr.setDither(false);
            return dr;
        }
        return null;
    }

    /**
     * Obtain a drawable for the built-in static system wallpaper.
     */
    public Drawable getBuiltInDrawable() {
        return getBuiltInDrawable(0, 0, false, 0, 0, FLAG_SYSTEM);
    }

    /**
     * Obtain a drawable for the specified built-in static system wallpaper.
     *
     * @param which The {@code FLAG_*} identifier of a valid wallpaper type.  Throws
     *     IllegalArgumentException if an invalid wallpaper is requested.
     * @return A Drawable presenting the specified wallpaper image, or {@code null}
     *     if no built-in default image for that wallpaper type exists.
     */
    public Drawable getBuiltInDrawable(@SetWallpaperFlags int which) {
        return getBuiltInDrawable(0, 0, false, 0, 0, which);
    }

    /**
     * Returns a drawable for the system built-in static wallpaper. Based on the parameters, the
     * drawable can be cropped and scaled
     *
     * @param outWidth The width of the returned drawable
     * @param outWidth The height of the returned drawable
     * @param scaleToFit If true, scale the wallpaper down rather than just cropping it
     * @param horizontalAlignment A float value between 0 and 1 specifying where to crop the image;
     *        0 for left-aligned, 0.5 for horizontal center-aligned, and 1 for right-aligned
     * @param verticalAlignment A float value between 0 and 1 specifying where to crop the image;
     *        0 for top-aligned, 0.5 for vertical center-aligned, and 1 for bottom-aligned
     * @return A Drawable presenting the built-in default system wallpaper image,
     *        or {@code null} if no such default image is defined on this device.
     */
    public Drawable getBuiltInDrawable(int outWidth, int outHeight,
            boolean scaleToFit, float horizontalAlignment, float verticalAlignment) {
        return getBuiltInDrawable(outWidth, outHeight, scaleToFit,
                horizontalAlignment, verticalAlignment, FLAG_SYSTEM);
    }

    /**
     * Returns a drawable for the built-in static wallpaper of the specified type.  Based on the
     * parameters, the drawable can be cropped and scaled.
     *
     * @param outWidth The width of the returned drawable
     * @param outWidth The height of the returned drawable
     * @param scaleToFit If true, scale the wallpaper down rather than just cropping it
     * @param horizontalAlignment A float value between 0 and 1 specifying where to crop the image;
     *        0 for left-aligned, 0.5 for horizontal center-aligned, and 1 for right-aligned
     * @param verticalAlignment A float value between 0 and 1 specifying where to crop the image;
     *        0 for top-aligned, 0.5 for vertical center-aligned, and 1 for bottom-aligned
     * @param which The {@code FLAG_*} identifier of a valid wallpaper type.  Throws
     *     IllegalArgumentException if an invalid wallpaper is requested.
     * @return A Drawable presenting the built-in default wallpaper image of the given type,
     *        or {@code null} if no default image of that type is defined on this device.
     */
    public Drawable getBuiltInDrawable(int outWidth, int outHeight, boolean scaleToFit,
            float horizontalAlignment, float verticalAlignment, @SetWallpaperFlags int which) {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }

        if (which != FLAG_SYSTEM && which != FLAG_LOCK) {
            throw new IllegalArgumentException("Must request exactly one kind of wallpaper");
        }

        Resources resources = mContext.getResources();
        horizontalAlignment = Math.max(0, Math.min(1, horizontalAlignment));
        verticalAlignment = Math.max(0, Math.min(1, verticalAlignment));

        InputStream wpStream = openDefaultWallpaper(mContext, which);
        if (wpStream == null) {
            if (DEBUG) {
                Log.w(TAG, "default wallpaper stream " + which + " is null");
            }
            return null;
        } else {
            InputStream is = new BufferedInputStream(wpStream);
            if (outWidth <= 0 || outHeight <= 0) {
                Bitmap fullSize = BitmapFactory.decodeStream(is, null, null);
                return new BitmapDrawable(resources, fullSize);
            } else {
                int inWidth;
                int inHeight;
                // Just measure this time through...
                {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, options);
                    if (options.outWidth != 0 && options.outHeight != 0) {
                        inWidth = options.outWidth;
                        inHeight = options.outHeight;
                    } else {
                        Log.e(TAG, "default wallpaper dimensions are 0");
                        return null;
                    }
                }

                // Reopen the stream to do the full decode.  We know at this point
                // that openDefaultWallpaper() will return non-null.
                is = new BufferedInputStream(openDefaultWallpaper(mContext, which));

                RectF cropRectF;

                outWidth = Math.min(inWidth, outWidth);
                outHeight = Math.min(inHeight, outHeight);
                if (scaleToFit) {
                    cropRectF = getMaxCropRect(inWidth, inHeight, outWidth, outHeight,
                        horizontalAlignment, verticalAlignment);
                } else {
                    float left = (inWidth - outWidth) * horizontalAlignment;
                    float right = left + outWidth;
                    float top = (inHeight - outHeight) * verticalAlignment;
                    float bottom = top + outHeight;
                    cropRectF = new RectF(left, top, right, bottom);
                }
                Rect roundedTrueCrop = new Rect();
                cropRectF.roundOut(roundedTrueCrop);

                if (roundedTrueCrop.width() <= 0 || roundedTrueCrop.height() <= 0) {
                    Log.w(TAG, "crop has bad values for full size image");
                    return null;
                }

                // See how much we're reducing the size of the image
                int scaleDownSampleSize = Math.min(roundedTrueCrop.width() / outWidth,
                        roundedTrueCrop.height() / outHeight);

                // Attempt to open a region decoder
                BitmapRegionDecoder decoder = null;
                try {
                    decoder = BitmapRegionDecoder.newInstance(is, true);
                } catch (IOException e) {
                    Log.w(TAG, "cannot open region decoder for default wallpaper");
                }

                Bitmap crop = null;
                if (decoder != null) {
                    // Do region decoding to get crop bitmap
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (scaleDownSampleSize > 1) {
                        options.inSampleSize = scaleDownSampleSize;
                    }
                    crop = decoder.decodeRegion(roundedTrueCrop, options);
                    decoder.recycle();
                }

                if (crop == null) {
                    // BitmapRegionDecoder has failed, try to crop in-memory. We know at
                    // this point that openDefaultWallpaper() will return non-null.
                    is = new BufferedInputStream(openDefaultWallpaper(mContext, which));
                    Bitmap fullSize = null;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (scaleDownSampleSize > 1) {
                        options.inSampleSize = scaleDownSampleSize;
                    }
                    fullSize = BitmapFactory.decodeStream(is, null, options);
                    if (fullSize != null) {
                        crop = Bitmap.createBitmap(fullSize, roundedTrueCrop.left,
                                roundedTrueCrop.top, roundedTrueCrop.width(),
                                roundedTrueCrop.height());
                    }
                }

                if (crop == null) {
                    Log.w(TAG, "cannot decode default wallpaper");
                    return null;
                }

                // Scale down if necessary
                if (outWidth > 0 && outHeight > 0 &&
                        (crop.getWidth() != outWidth || crop.getHeight() != outHeight)) {
                    Matrix m = new Matrix();
                    RectF cropRect = new RectF(0, 0, crop.getWidth(), crop.getHeight());
                    RectF returnRect = new RectF(0, 0, outWidth, outHeight);
                    m.setRectToRect(cropRect, returnRect, Matrix.ScaleToFit.FILL);
                    Bitmap tmp = Bitmap.createBitmap((int) returnRect.width(),
                            (int) returnRect.height(), Bitmap.Config.ARGB_8888);
                    if (tmp != null) {
                        Canvas c = new Canvas(tmp);
                        Paint p = new Paint();
                        p.setFilterBitmap(true);
                        c.drawBitmap(crop, m, p);
                        crop = tmp;
                    }
                }

                return new BitmapDrawable(resources, crop);
            }
        }
    }

    private static RectF getMaxCropRect(int inWidth, int inHeight, int outWidth, int outHeight,
                float horizontalAlignment, float verticalAlignment) {
        RectF cropRect = new RectF();
        // Get a crop rect that will fit this
        if (inWidth / (float) inHeight > outWidth / (float) outHeight) {
             cropRect.top = 0;
             cropRect.bottom = inHeight;
             float cropWidth = outWidth * (inHeight / (float) outHeight);
             cropRect.left = (inWidth - cropWidth) * horizontalAlignment;
             cropRect.right = cropRect.left + cropWidth;
        } else {
            cropRect.left = 0;
            cropRect.right = inWidth;
            float cropHeight = outHeight * (inWidth / (float) outWidth);
            cropRect.top = (inHeight - cropHeight) * verticalAlignment;
            cropRect.bottom = cropRect.top + cropHeight;
        }
        return cropRect;
    }

    /**
     * Retrieve the current system wallpaper; if there is no wallpaper set,
     * a null pointer is returned. This is returned as an
     * abstract Drawable that you can install in a View to display whatever
     * wallpaper the user has currently set.
     *
     * @return Returns a Drawable object that will draw the wallpaper or a
     * null pointer if these is none.
     */
    public Drawable peekDrawable() {
        Bitmap bm = sGlobals.peekWallpaperBitmap(mContext, false, FLAG_SYSTEM);
        if (bm != null) {
            Drawable dr = new BitmapDrawable(mContext.getResources(), bm);
            dr.setDither(false);
            return dr;
        }
        return null;
    }

    /**
     * Like {@link #getDrawable()}, but the returned Drawable has a number
     * of limitations to reduce its overhead as much as possible. It will
     * never scale the wallpaper (only centering it if the requested bounds
     * do match the bitmap bounds, which should not be typical), doesn't
     * allow setting an alpha, color filter, or other attributes, etc.  The
     * bounds of the returned drawable will be initialized to the same bounds
     * as the wallpaper, so normally you will not need to touch it.  The
     * drawable also assumes that it will be used in a context running in
     * the same density as the screen (not in density compatibility mode).
     *
     * @return Returns a Drawable object that will draw the wallpaper.
     */
    public Drawable getFastDrawable() {
        Bitmap bm = sGlobals.peekWallpaperBitmap(mContext, true, FLAG_SYSTEM);
        if (bm != null) {
            return new FastBitmapDrawable(bm);
        }
        return null;
    }

    /**
     * Like {@link #getFastDrawable()}, but if there is no wallpaper set,
     * a null pointer is returned.
     *
     * @return Returns an optimized Drawable object that will draw the
     * wallpaper or a null pointer if these is none.
     */
    public Drawable peekFastDrawable() {
       Bitmap bm = sGlobals.peekWallpaperBitmap(mContext, false, FLAG_SYSTEM);
        if (bm != null) {
            return new FastBitmapDrawable(bm);
        }
        return null;
    }

    /**
     * Like {@link #getDrawable()} but returns a Bitmap.
     *
     * @hide
     */
    public Bitmap getBitmap() {
        return getBitmapAsUser(mContext.getUserId());
    }

    /**
     * Like {@link #getDrawable()} but returns a Bitmap for the provided user.
     *
     * @hide
     */
    public Bitmap getBitmapAsUser(int userId) {
        return sGlobals.peekWallpaperBitmap(mContext, true, FLAG_SYSTEM, userId);
    }

    /**
     * Get an open, readable file descriptor to the given wallpaper image file.
     * The caller is responsible for closing the file descriptor when done ingesting the file.
     *
     * <p>If no lock-specific wallpaper has been configured for the given user, then
     * this method will return {@code null} when requesting {@link #FLAG_LOCK} rather than
     * returning the system wallpaper's image file.
     *
     * @param which The wallpaper whose image file is to be retrieved.  Must be a single
     *     defined kind of wallpaper, either {@link #FLAG_SYSTEM} or
     *     {@link #FLAG_LOCK}.
     *
     * @see #FLAG_LOCK
     * @see #FLAG_SYSTEM
     */
    public ParcelFileDescriptor getWallpaperFile(@SetWallpaperFlags int which) {
        return getWallpaperFile(which, mContext.getUserId());
    }

    /**
     * Version of {@link #getWallpaperFile(int)} that can access the wallpaper data
     * for a given user.  The caller must hold the INTERACT_ACROSS_USERS_FULL
     * permission to access another user's wallpaper data.
     *
     * @param which The wallpaper whose image file is to be retrieved.  Must be a single
     *     defined kind of wallpaper, either {@link #FLAG_SYSTEM} or
     *     {@link #FLAG_LOCK}.
     * @param userId The user or profile whose imagery is to be retrieved
     *
     * @see #FLAG_LOCK
     * @see #FLAG_SYSTEM
     *
     * @hide
     */
    public ParcelFileDescriptor getWallpaperFile(@SetWallpaperFlags int which, int userId) {
        if (which != FLAG_SYSTEM && which != FLAG_LOCK) {
            throw new IllegalArgumentException("Must request exactly one kind of wallpaper");
        }

        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        } else {
            try {
                Bundle outParams = new Bundle();
                return sGlobals.mService.getWallpaper(null, which, outParams, userId);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Remove all internal references to the last loaded wallpaper.  Useful
     * for apps that want to reduce memory usage when they only temporarily
     * need to have the wallpaper.  After calling, the next request for the
     * wallpaper will require reloading it again from disk.
     */
    public void forgetLoadedWallpaper() {
        sGlobals.forgetLoadedWallpaper();
    }

    /**
     * If the current wallpaper is a live wallpaper component, return the
     * information about that wallpaper.  Otherwise, if it is a static image,
     * simply return null.
     */
    public WallpaperInfo getWallpaperInfo() {
        try {
            if (sGlobals.mService == null) {
                Log.w(TAG, "WallpaperService not running");
                throw new RuntimeException(new DeadSystemException());
            } else {
                return sGlobals.mService.getWallpaperInfo(UserHandle.myUserId());
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the ID of the current wallpaper of the given kind.  If there is no
     * such wallpaper configured, returns a negative number.
     *
     * <p>Every time the wallpaper image is set, a new ID is assigned to it.
     * This method allows the caller to determine whether the wallpaper imagery
     * has changed, regardless of how that change happened.
     *
     * @param which The wallpaper whose ID is to be returned.  Must be a single
     *     defined kind of wallpaper, either {@link #FLAG_SYSTEM} or
     *     {@link #FLAG_LOCK}.
     * @return The positive numeric ID of the current wallpaper of the given kind,
     *     or a negative value if no such wallpaper is configured.
     */
    public int getWallpaperId(@SetWallpaperFlags int which) {
        return getWallpaperIdForUser(which, mContext.getUserId());
    }

    /**
     * Get the ID of the given user's current wallpaper of the given kind.  If there
     * is no such wallpaper configured, returns a negative number.
     * @hide
     */
    public int getWallpaperIdForUser(@SetWallpaperFlags int which, int userId) {
        try {
            if (sGlobals.mService == null) {
                Log.w(TAG, "WallpaperService not running");
                throw new RuntimeException(new DeadSystemException());
            } else {
                return sGlobals.mService.getWallpaperIdForUser(which, userId);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets an Intent that will launch an activity that crops the given
     * image and sets the device's wallpaper. If there is a default HOME activity
     * that supports cropping wallpapers, it will be preferred as the default.
     * Use this method instead of directly creating a {@link #ACTION_CROP_AND_SET_WALLPAPER}
     * intent.
     *
     * @param imageUri The image URI that will be set in the intent. The must be a content
     *                 URI and its provider must resolve its type to "image/*"
     *
     * @throws IllegalArgumentException if the URI is not a content URI or its MIME type is
     *         not "image/*"
     */
    public Intent getCropAndSetWallpaperIntent(Uri imageUri) {
        if (imageUri == null) {
            throw new IllegalArgumentException("Image URI must not be null");
        }

        if (!ContentResolver.SCHEME_CONTENT.equals(imageUri.getScheme())) {
            throw new IllegalArgumentException("Image URI must be of the "
                    + ContentResolver.SCHEME_CONTENT + " scheme type");
        }

        final PackageManager packageManager = mContext.getPackageManager();
        Intent cropAndSetWallpaperIntent =
                new Intent(ACTION_CROP_AND_SET_WALLPAPER, imageUri);
        cropAndSetWallpaperIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Find out if the default HOME activity supports CROP_AND_SET_WALLPAPER
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolvedHome = packageManager.resolveActivity(homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (resolvedHome != null) {
            cropAndSetWallpaperIntent.setPackage(resolvedHome.activityInfo.packageName);

            List<ResolveInfo> cropAppList = packageManager.queryIntentActivities(
                    cropAndSetWallpaperIntent, 0);
            if (cropAppList.size() > 0) {
                return cropAndSetWallpaperIntent;
            }
        }

        // fallback crop activity
        final String cropperPackage = mContext.getString(
                com.android.internal.R.string.config_wallpaperCropperPackage);
        cropAndSetWallpaperIntent.setPackage(cropperPackage);
        List<ResolveInfo> cropAppList = packageManager.queryIntentActivities(
                cropAndSetWallpaperIntent, 0);
        if (cropAppList.size() > 0) {
            return cropAndSetWallpaperIntent;
        }
        // If the URI is not of the right type, or for some reason the system wallpaper
        // cropper doesn't exist, return null
        throw new IllegalArgumentException("Cannot use passed URI to set wallpaper; " +
            "check that the type returned by ContentProvider matches image/*");
    }

    /**
     * Change the current system wallpaper to the bitmap in the given resource.
     * The resource is opened as a raw data stream and copied into the
     * wallpaper; it must be a valid PNG or JPEG image.  On success, the intent
     * {@link Intent#ACTION_WALLPAPER_CHANGED} is broadcast.
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER}.
     *
     * @param resid The resource ID of the bitmap to be used as the wallpaper image
     *
     * @throws IOException If an error occurs reverting to the built-in
     * wallpaper.
     */
    public void setResource(@RawRes int resid) throws IOException {
        setResource(resid, FLAG_SYSTEM | FLAG_LOCK);
    }

    /**
     * Version of {@link #setResource(int)} that allows the caller to specify which
     * of the supported wallpaper categories to set.
     *
     * @param resid The resource ID of the bitmap to be used as the wallpaper image
     * @param which Flags indicating which wallpaper(s) to configure with the new imagery
     *
     * @see #FLAG_LOCK
     * @see #FLAG_SYSTEM
     *
     * @return An integer ID assigned to the newly active wallpaper; or zero on failure.
     *
     * @throws IOException
     */
    public int setResource(@RawRes int resid, @SetWallpaperFlags int which)
            throws IOException {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        final Bundle result = new Bundle();
        final WallpaperSetCompletion completion = new WallpaperSetCompletion();
        try {
            Resources resources = mContext.getResources();
            /* Set the wallpaper to the default values */
            ParcelFileDescriptor fd = sGlobals.mService.setWallpaper(
                    "res:" + resources.getResourceName(resid),
                    mContext.getOpPackageName(), null, false, result, which, completion,
                    UserHandle.myUserId());
            if (fd != null) {
                FileOutputStream fos = null;
                boolean ok = false;
                try {
                    fos = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
                    copyStreamToWallpaperFile(resources.openRawResource(resid), fos);
                    // The 'close()' is the trigger for any server-side image manipulation,
                    // so we must do that before waiting for completion.
                    fos.close();
                    completion.waitForCompletion();
                } finally {
                    // Might be redundant but completion shouldn't wait unless the write
                    // succeeded; this is a fallback if it threw past the close+wait.
                    IoUtils.closeQuietly(fos);
                }
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return result.getInt(EXTRA_NEW_WALLPAPER_ID, 0);
    }

    /**
     * Change the current system wallpaper to a bitmap.  The given bitmap is
     * converted to a PNG and stored as the wallpaper.  On success, the intent
     * {@link Intent#ACTION_WALLPAPER_CHANGED} is broadcast.
     *
     * <p>This method is equivalent to calling
     * {@link #setBitmap(Bitmap, Rect, boolean)} and passing {@code null} for the
     * {@code visibleCrop} rectangle and {@code true} for the {@code allowBackup}
     * parameter.
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER}.
     *
     * @param bitmap The bitmap to be used as the new system wallpaper.
     *
     * @throws IOException If an error occurs when attempting to set the wallpaper
     *     to the provided image.
     */
    public void setBitmap(Bitmap bitmap) throws IOException {
        setBitmap(bitmap, null, true);
    }

    /**
     * Change the current system wallpaper to a bitmap, specifying a hint about
     * which subrectangle of the full image is to be visible.  The OS will then
     * try to best present the given portion of the full image as the static system
     * wallpaper image.  On success, the intent
     * {@link Intent#ACTION_WALLPAPER_CHANGED} is broadcast.
     *
     * <p>Passing {@code null} as the {@code visibleHint} parameter is equivalent to
     * passing (0, 0, {@code fullImage.getWidth()}, {@code fullImage.getHeight()}).
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER}.
     *
     * @param fullImage A bitmap that will supply the wallpaper imagery.
     * @param visibleCropHint The rectangular subregion of {@code fullImage} that should be
     *     displayed as wallpaper.  Passing {@code null} for this parameter means that
     *     the full image should be displayed if possible given the image's and device's
     *     aspect ratios, etc.
     * @param allowBackup {@code true} if the OS is permitted to back up this wallpaper
     *     image for restore to a future device; {@code false} otherwise.
     *
     * @return An integer ID assigned to the newly active wallpaper; or zero on failure.
     *
     * @throws IOException If an error occurs when attempting to set the wallpaper
     *     to the provided image.
     * @throws IllegalArgumentException If the {@code visibleCropHint} rectangle is
     *     empty or invalid.
     */
    public int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup)
            throws IOException {
        return setBitmap(fullImage, visibleCropHint, allowBackup, FLAG_SYSTEM | FLAG_LOCK);
    }

    /**
     * Version of {@link #setBitmap(Bitmap, Rect, boolean)} that allows the caller
     * to specify which of the supported wallpaper categories to set.
     *
     * @param fullImage A bitmap that will supply the wallpaper imagery.
     * @param visibleCropHint The rectangular subregion of {@code fullImage} that should be
     *     displayed as wallpaper.  Passing {@code null} for this parameter means that
     *     the full image should be displayed if possible given the image's and device's
     *     aspect ratios, etc.
     * @param allowBackup {@code true} if the OS is permitted to back up this wallpaper
     *     image for restore to a future device; {@code false} otherwise.
     * @param which Flags indicating which wallpaper(s) to configure with the new imagery.
     *
     * @see #FLAG_LOCK
     * @see #FLAG_SYSTEM
     *
     * @return An integer ID assigned to the newly active wallpaper; or zero on failure.
     *
     * @throws IOException
     */
    public int setBitmap(Bitmap fullImage, Rect visibleCropHint,
            boolean allowBackup, @SetWallpaperFlags int which)
            throws IOException {
        return setBitmap(fullImage, visibleCropHint, allowBackup, which,
                UserHandle.myUserId());
    }

    /**
     * Like {@link #setBitmap(Bitmap, Rect, boolean, int)}, but allows to pass in an explicit user
     * id. If the user id doesn't match the user id the process is running under, calling this
     * requires permission {@link android.Manifest.permission#INTERACT_ACROSS_USERS_FULL}.
     * @hide
     */
    public int setBitmap(Bitmap fullImage, Rect visibleCropHint,
            boolean allowBackup, @SetWallpaperFlags int which, int userId)
            throws IOException {
        validateRect(visibleCropHint);
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        final Bundle result = new Bundle();
        final WallpaperSetCompletion completion = new WallpaperSetCompletion();
        try {
            ParcelFileDescriptor fd = sGlobals.mService.setWallpaper(null,
                    mContext.getOpPackageName(), visibleCropHint, allowBackup,
                    result, which, completion, userId);
            if (fd != null) {
                FileOutputStream fos = null;
                try {
                    fos = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
                    fullImage.compress(Bitmap.CompressFormat.PNG, 90, fos);
                    fos.close();
                    completion.waitForCompletion();
                } finally {
                    IoUtils.closeQuietly(fos);
                }
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return result.getInt(EXTRA_NEW_WALLPAPER_ID, 0);
    }

    private final void validateRect(Rect rect) {
        if (rect != null && rect.isEmpty()) {
            throw new IllegalArgumentException("visibleCrop rectangle must be valid and non-empty");
        }
    }

    /**
     * Change the current system wallpaper to a specific byte stream.  The
     * give InputStream is copied into persistent storage and will now be
     * used as the wallpaper.  Currently it must be either a JPEG or PNG
     * image.  On success, the intent {@link Intent#ACTION_WALLPAPER_CHANGED}
     * is broadcast.
     *
     * <p>This method is equivalent to calling
     * {@link #setStream(InputStream, Rect, boolean)} and passing {@code null} for the
     * {@code visibleCrop} rectangle and {@code true} for the {@code allowBackup}
     * parameter.
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER}.
     *
     * @param bitmapData A stream containing the raw data to install as a wallpaper.  This
     *     data can be in any format handled by {@link BitmapRegionDecoder}.
     *
     * @throws IOException If an error occurs when attempting to set the wallpaper
     *     based on the provided image data.
     */
    public void setStream(InputStream bitmapData) throws IOException {
        setStream(bitmapData, null, true);
    }

    private void copyStreamToWallpaperFile(InputStream data, FileOutputStream fos)
            throws IOException {
        byte[] buffer = new byte[32768];
        int amt;
        while ((amt=data.read(buffer)) > 0) {
            fos.write(buffer, 0, amt);
        }
    }

    /**
     * Change the current system wallpaper to a specific byte stream, specifying a
     * hint about which subrectangle of the full image is to be visible.  The OS will
     * then try to best present the given portion of the full image as the static system
     * wallpaper image.  The data from the given InputStream is copied into persistent
     * storage and will then be used as the system wallpaper.  Currently the data must
     * be either a JPEG or PNG image.  On success, the intent
     * {@link Intent#ACTION_WALLPAPER_CHANGED} is broadcast.
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER}.
     *
     * @param bitmapData A stream containing the raw data to install as a wallpaper.  This
     *     data can be in any format handled by {@link BitmapRegionDecoder}.
     * @param visibleCropHint The rectangular subregion of the streamed image that should be
     *     displayed as wallpaper.  Passing {@code null} for this parameter means that
     *     the full image should be displayed if possible given the image's and device's
     *     aspect ratios, etc.
     * @param allowBackup {@code true} if the OS is permitted to back up this wallpaper
     *     image for restore to a future device; {@code false} otherwise.
     * @return An integer ID assigned to the newly active wallpaper; or zero on failure.
     *
     * @see #getWallpaperId(int)
     *
     * @throws IOException If an error occurs when attempting to set the wallpaper
     *     based on the provided image data.
     * @throws IllegalArgumentException If the {@code visibleCropHint} rectangle is
     *     empty or invalid.
     */
    public int setStream(InputStream bitmapData, Rect visibleCropHint, boolean allowBackup)
            throws IOException {
        return setStream(bitmapData, visibleCropHint, allowBackup, FLAG_SYSTEM | FLAG_LOCK);
    }

    /**
     * Version of {@link #setStream(InputStream, Rect, boolean)} that allows the caller
     * to specify which of the supported wallpaper categories to set.
     *
     * @param bitmapData A stream containing the raw data to install as a wallpaper.  This
     *     data can be in any format handled by {@link BitmapRegionDecoder}.
     * @param visibleCropHint The rectangular subregion of the streamed image that should be
     *     displayed as wallpaper.  Passing {@code null} for this parameter means that
     *     the full image should be displayed if possible given the image's and device's
     *     aspect ratios, etc.
     * @param allowBackup {@code true} if the OS is permitted to back up this wallpaper
     *     image for restore to a future device; {@code false} otherwise.
     * @param which Flags indicating which wallpaper(s) to configure with the new imagery.
     * @return An integer ID assigned to the newly active wallpaper; or zero on failure.
     *
     * @see #getWallpaperId(int)
     * @see #FLAG_LOCK
     * @see #FLAG_SYSTEM
     *
     * @throws IOException
     */
    public int setStream(InputStream bitmapData, Rect visibleCropHint,
            boolean allowBackup, @SetWallpaperFlags int which)
                    throws IOException {
        validateRect(visibleCropHint);
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        final Bundle result = new Bundle();
        final WallpaperSetCompletion completion = new WallpaperSetCompletion();
        try {
            ParcelFileDescriptor fd = sGlobals.mService.setWallpaper(null,
                    mContext.getOpPackageName(), visibleCropHint, allowBackup,
                    result, which, completion, UserHandle.myUserId());
            if (fd != null) {
                FileOutputStream fos = null;
                try {
                    fos = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
                    copyStreamToWallpaperFile(bitmapData, fos);
                    fos.close();
                    completion.waitForCompletion();
                } finally {
                    IoUtils.closeQuietly(fos);
                }
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result.getInt(EXTRA_NEW_WALLPAPER_ID, 0);
    }

    /**
     * Return whether any users are currently set to use the wallpaper
     * with the given resource ID.  That is, their wallpaper has been
     * set through {@link #setResource(int)} with the same resource id.
     */
    public boolean hasResourceWallpaper(@RawRes int resid) {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        try {
            Resources resources = mContext.getResources();
            String name = "res:" + resources.getResourceName(resid);
            return sGlobals.mService.hasNamedWallpaper(name);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the desired minimum width for the wallpaper. Callers of
     * {@link #setBitmap(android.graphics.Bitmap)} or
     * {@link #setStream(java.io.InputStream)} should check this value
     * beforehand to make sure the supplied wallpaper respects the desired
     * minimum width.
     *
     * If the returned value is <= 0, the caller should use the width of
     * the default display instead.
     *
     * @return The desired minimum width for the wallpaper. This value should
     * be honored by applications that set the wallpaper but it is not
     * mandatory.
     */
    public int getDesiredMinimumWidth() {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        try {
            return sGlobals.mService.getWidthHint();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the desired minimum height for the wallpaper. Callers of
     * {@link #setBitmap(android.graphics.Bitmap)} or
     * {@link #setStream(java.io.InputStream)} should check this value
     * beforehand to make sure the supplied wallpaper respects the desired
     * minimum height.
     *
     * If the returned value is <= 0, the caller should use the height of
     * the default display instead.
     *
     * @return The desired minimum height for the wallpaper. This value should
     * be honored by applications that set the wallpaper but it is not
     * mandatory.
     */
    public int getDesiredMinimumHeight() {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        try {
            return sGlobals.mService.getHeightHint();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * For use only by the current home application, to specify the size of
     * wallpaper it would like to use.  This allows such applications to have
     * a virtual wallpaper that is larger than the physical screen, matching
     * the size of their workspace.
     *
     * <p>Note developers, who don't seem to be reading this.  This is
     * for <em>home apps</em> to tell what size wallpaper they would like.
     * Nobody else should be calling this!  Certainly not other non-home
     * apps that change the wallpaper.  Those apps are supposed to
     * <b>retrieve</b> the suggested size so they can construct a wallpaper
     * that matches it.
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER_HINTS}.
     *
     * @param minimumWidth Desired minimum width
     * @param minimumHeight Desired minimum height
     */
    public void suggestDesiredDimensions(int minimumWidth, int minimumHeight) {
        try {
            /**
             * The framework makes no attempt to limit the window size
             * to the maximum texture size. Any window larger than this
             * cannot be composited.
             *
             * Read maximum texture size from system property and scale down
             * minimumWidth and minimumHeight accordingly.
             */
            int maximumTextureSize;
            try {
                maximumTextureSize = SystemProperties.getInt("sys.max_texture_size", 0);
            } catch (Exception e) {
                maximumTextureSize = 0;
            }

            if (maximumTextureSize > 0) {
                if ((minimumWidth > maximumTextureSize) ||
                    (minimumHeight > maximumTextureSize)) {
                    float aspect = (float)minimumHeight / (float)minimumWidth;
                    if (minimumWidth > minimumHeight) {
                        minimumWidth = maximumTextureSize;
                        minimumHeight = (int)((minimumWidth * aspect) + 0.5);
                    } else {
                        minimumHeight = maximumTextureSize;
                        minimumWidth = (int)((minimumHeight / aspect) + 0.5);
                    }
                }
            }

            if (sGlobals.mService == null) {
                Log.w(TAG, "WallpaperService not running");
                throw new RuntimeException(new DeadSystemException());
            } else {
                sGlobals.mService.setDimensionHints(minimumWidth, minimumHeight,
                        mContext.getOpPackageName());
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Specify extra padding that the wallpaper should have outside of the display.
     * That is, the given padding supplies additional pixels the wallpaper should extend
     * outside of the display itself.
     * @param padding The number of pixels the wallpaper should extend beyond the display,
     * on its left, top, right, and bottom sides.
     * @hide
     */
    @SystemApi
    public void setDisplayPadding(Rect padding) {
        try {
            if (sGlobals.mService == null) {
                Log.w(TAG, "WallpaperService not running");
                throw new RuntimeException(new DeadSystemException());
            } else {
                sGlobals.mService.setDisplayPadding(padding, mContext.getOpPackageName());
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Apply a raw offset to the wallpaper window.  Should only be used in
     * combination with {@link #setDisplayPadding(android.graphics.Rect)} when you
     * have ensured that the wallpaper will extend outside of the display area so that
     * it can be moved without leaving part of the display uncovered.
     * @param x The offset, in pixels, to apply to the left edge.
     * @param y The offset, in pixels, to apply to the top edge.
     * @hide
     */
    @SystemApi
    public void setDisplayOffset(IBinder windowToken, int x, int y) {
        try {
            //Log.v(TAG, "Sending new wallpaper display offsets from app...");
            WindowManagerGlobal.getWindowSession().setWallpaperDisplayOffset(
                    windowToken, x, y);
            //Log.v(TAG, "...app returning after sending display offset!");
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Clear the wallpaper.
     *
     * @hide
     */
    @SystemApi
    public void clearWallpaper() {
        clearWallpaper(FLAG_LOCK, mContext.getUserId());
        clearWallpaper(FLAG_SYSTEM, mContext.getUserId());
    }

    /**
     * Clear the wallpaper for a specific user.  The caller must hold the
     * INTERACT_ACROSS_USERS_FULL permission to clear another user's
     * wallpaper.
     * @hide
     */
    @SystemApi
    public void clearWallpaper(@SetWallpaperFlags int which, int userId) {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        try {
            sGlobals.mService.clearWallpaper(mContext.getOpPackageName(), which, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set the live wallpaper.
     *
     * This can only be called by packages with android.permission.SET_WALLPAPER_COMPONENT
     * permission.
     *
     * @hide
     */
    @SystemApi
    public boolean setWallpaperComponent(ComponentName name) {
        return setWallpaperComponent(name, UserHandle.myUserId());
    }

    /**
     * Set the live wallpaper.
     *
     * This can only be called by packages with android.permission.SET_WALLPAPER_COMPONENT
     * permission. The caller must hold the INTERACT_ACROSS_USERS_FULL permission to change
     * another user's wallpaper.
     *
     * @hide
     */
    public boolean setWallpaperComponent(ComponentName name, int userId) {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        try {
            sGlobals.mService.setWallpaperComponentChecked(name, mContext.getOpPackageName(),
                    userId);
            return true;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set the display position of the current wallpaper within any larger space, when
     * that wallpaper is visible behind the given window.  The X and Y offsets
     * are floating point numbers ranging from 0 to 1, representing where the
     * wallpaper should be positioned within the screen space.  These only
     * make sense when the wallpaper is larger than the display.
     *
     * @param windowToken The window who these offsets should be associated
     * with, as returned by {@link android.view.View#getWindowToken()
     * View.getWindowToken()}.
     * @param xOffset The offset along the X dimension, from 0 to 1.
     * @param yOffset The offset along the Y dimension, from 0 to 1.
     */
    public void setWallpaperOffsets(IBinder windowToken, float xOffset, float yOffset) {
        try {
            //Log.v(TAG, "Sending new wallpaper offsets from app...");
            WindowManagerGlobal.getWindowSession().setWallpaperPosition(
                    windowToken, xOffset, yOffset, mWallpaperXStep, mWallpaperYStep);
            //Log.v(TAG, "...app returning after sending offsets!");
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * For applications that use multiple virtual screens showing a wallpaper,
     * specify the step size between virtual screens. For example, if the
     * launcher has 3 virtual screens, it would specify an xStep of 0.5,
     * since the X offset for those screens are 0.0, 0.5 and 1.0
     * @param xStep The X offset delta from one screen to the next one
     * @param yStep The Y offset delta from one screen to the next one
     */
    public void setWallpaperOffsetSteps(float xStep, float yStep) {
        mWallpaperXStep = xStep;
        mWallpaperYStep = yStep;
    }

    /**
     * Send an arbitrary command to the current active wallpaper.
     *
     * @param windowToken The window who these offsets should be associated
     * with, as returned by {@link android.view.View#getWindowToken()
     * View.getWindowToken()}.
     * @param action Name of the command to perform.  This must be a scoped
     * name to avoid collisions, such as "com.mycompany.wallpaper.DOIT".
     * @param x Arbitrary integer argument based on command.
     * @param y Arbitrary integer argument based on command.
     * @param z Arbitrary integer argument based on command.
     * @param extras Optional additional information for the command, or null.
     */
    public void sendWallpaperCommand(IBinder windowToken, String action,
            int x, int y, int z, Bundle extras) {
        try {
            //Log.v(TAG, "Sending new wallpaper offsets from app...");
            WindowManagerGlobal.getWindowSession().sendWallpaperCommand(
                    windowToken, action, x, y, z, extras, false);
            //Log.v(TAG, "...app returning after sending offsets!");
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether wallpapers are supported for the calling user. If this function returns
     * {@code false}, any attempts to changing the wallpaper will have no effect,
     * and any attempt to obtain of the wallpaper will return {@code null}.
     */
    public boolean isWallpaperSupported() {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        } else {
            try {
                return sGlobals.mService.isWallpaperSupported(mContext.getOpPackageName());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Returns whether the calling package is allowed to set the wallpaper for the calling user.
     * If this function returns {@code false}, any attempts to change the wallpaper will have
     * no effect. Always returns {@code true} for device owner and profile owner.
     *
     * @see android.os.UserManager#DISALLOW_SET_WALLPAPER
     */
    public boolean isSetWallpaperAllowed() {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        } else {
            try {
                return sGlobals.mService.isSetWallpaperAllowed(mContext.getOpPackageName());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Clear the offsets previously associated with this window through
     * {@link #setWallpaperOffsets(IBinder, float, float)}.  This reverts
     * the window to its default state, where it does not cause the wallpaper
     * to scroll from whatever its last offsets were.
     *
     * @param windowToken The window who these offsets should be associated
     * with, as returned by {@link android.view.View#getWindowToken()
     * View.getWindowToken()}.
     */
    public void clearWallpaperOffsets(IBinder windowToken) {
        try {
            WindowManagerGlobal.getWindowSession().setWallpaperPosition(
                    windowToken, -1, -1, -1, -1);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove any currently set system wallpaper, reverting to the system's built-in
     * wallpaper. On success, the intent {@link Intent#ACTION_WALLPAPER_CHANGED}
     * is broadcast.
     *
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#SET_WALLPAPER}.
     *
     * @throws IOException If an error occurs reverting to the built-in
     * wallpaper.
     */
    public void clear() throws IOException {
        setStream(openDefaultWallpaper(mContext, FLAG_SYSTEM), null, false);
    }

    /**
     * Remove one or more currently set wallpapers, reverting to the system default
     * display for each one.  If {@link #FLAG_SYSTEM} is set in the {@code which}
     * parameter, the intent {@link Intent#ACTION_WALLPAPER_CHANGED} will be broadcast
     * upon success.
     *
     * @param which A bitwise combination of {@link #FLAG_SYSTEM} or
     *   {@link #FLAG_LOCK}
     * @throws IOException If an error occurs reverting to the built-in wallpaper.
     */
    public void clear(@SetWallpaperFlags int which) throws IOException {
        if ((which & FLAG_SYSTEM) != 0) {
            clear();
        }
        if ((which & FLAG_LOCK) != 0) {
            clearWallpaper(FLAG_LOCK, mContext.getUserId());
        }
    }

    /**
     * Open stream representing the default static image wallpaper.
     *
     * If the device defines no default wallpaper of the requested kind,
     * {@code null} is returned.
     *
     * @hide
     */
    public static InputStream openDefaultWallpaper(Context context, @SetWallpaperFlags int which) {
        final String whichProp;
        final int defaultResId;
        if (which == FLAG_LOCK) {
            /* Factory-default lock wallpapers are not yet supported
            whichProp = PROP_LOCK_WALLPAPER;
            defaultResId = com.android.internal.R.drawable.default_lock_wallpaper;
            */
            return null;
        } else {
            whichProp = PROP_WALLPAPER;
            defaultResId = com.android.internal.R.drawable.default_wallpaper;
        }
        final String path = SystemProperties.get(whichProp);
        if (!TextUtils.isEmpty(path)) {
            final File file = new File(path);
            if (file.exists()) {
                try {
                    return new FileInputStream(file);
                } catch (IOException e) {
                    // Ignored, fall back to platform default below
                }
            }
        }
        try {
            return context.getResources().openRawResource(defaultResId);
        } catch (NotFoundException e) {
            // no default defined for this device; this is not a failure
        }
        return null;
    }

    /**
     * Return {@link ComponentName} of the default live wallpaper, or
     * {@code null} if none is defined.
     *
     * @hide
     */
    public static ComponentName getDefaultWallpaperComponent(Context context) {
        String flat = SystemProperties.get(PROP_WALLPAPER_COMPONENT);
        if (!TextUtils.isEmpty(flat)) {
            final ComponentName cn = ComponentName.unflattenFromString(flat);
            if (cn != null) {
                return cn;
            }
        }

        flat = context.getString(com.android.internal.R.string.default_wallpaper_component);
        if (!TextUtils.isEmpty(flat)) {
            final ComponentName cn = ComponentName.unflattenFromString(flat);
            if (cn != null) {
                return cn;
            }
        }

        return null;
    }

    /**
     * Register a callback for lock wallpaper observation. Only the OS may use this.
     *
     * @return true on success; false on error.
     * @hide
     */
    public boolean setLockWallpaperCallback(IWallpaperManagerCallback callback) {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }

        try {
            return sGlobals.mService.setLockWallpaperCallback(callback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Is the current system wallpaper eligible for backup?
     *
     * Only the OS itself may use this method.
     * @hide
     */
    public boolean isWallpaperBackupEligible(int which) {
        if (sGlobals.mService == null) {
            Log.w(TAG, "WallpaperService not running");
            throw new RuntimeException(new DeadSystemException());
        }
        try {
            return sGlobals.mService.isWallpaperBackupEligible(which, mContext.getUserId());
        } catch (RemoteException e) {
            Log.e(TAG, "Exception querying wallpaper backup eligibility: " + e.getMessage());
        }
        return false;
    }

    // Private completion callback for setWallpaper() synchronization
    private class WallpaperSetCompletion extends IWallpaperManagerCallback.Stub {
        final CountDownLatch mLatch;

        public WallpaperSetCompletion() {
            mLatch = new CountDownLatch(1);
        }

        public void waitForCompletion() {
            try {
                mLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // This might be legit: the crop may take a very long time. Don't sweat
                // it in that case; we are okay with display lagging behind in order to
                // keep the caller from locking up indeterminately.
            }
        }

        @Override
        public void onWallpaperChanged() throws RemoteException {
            mLatch.countDown();
        }
    }
}
