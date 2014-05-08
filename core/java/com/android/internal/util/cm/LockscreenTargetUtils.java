/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.cm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;

public final class LockscreenTargetUtils {
    private static final String TAG = "LockscreenTargetUtils";

    /**
     * @hide
     */
    public final static String ICON_RESOURCE = "icon_resource";

    /**
     * @hide
     */
    public final static String ICON_PACKAGE = "icon_package";

    /**
     * @hide
     */
    public final static String ICON_FILE = "icon_file";

    /**
     * Number of customizable lockscreen targets for tablets
     * @hide
     */
    public final static int MAX_TABLET_TARGETS = 7;

    /**
     * Number of customizable lockscreen targets for phones
     * @hide
     */
    public final static int MAX_PHONE_TARGETS = 4;

    /**
     * Empty target used to reference unused lockscreen targets
     * @hide
     */
    public final static String EMPTY_TARGET = "empty";

    private LockscreenTargetUtils() {
    }

    public static boolean isScreenLarge(Context context) {
        final int screenSize = context.getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        boolean isScreenLarge = screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE ||
                screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE;
        return isScreenLarge;
    }

    public static int getMaxTargets(Context context) {
        if (isScreenLarge(context)) {
            return MAX_TABLET_TARGETS;
        }

        return MAX_PHONE_TARGETS;
    }

    public static int getTargetOffset(Context context) {
        boolean isLandscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        return isLandscape && !isScreenLarge(context) ? 2 : 0;
    }

    /**
     * Create a layered drawable
     * @param back - Background image to use when target is active
     * @param front - Front image to use for target
     * @param inset - Target inset padding
     * @param frontBlank - Whether the front image for active target should be blank
     * @return StateListDrawable
     */
    public static StateListDrawable getLayeredDrawable(Context context,
            Drawable back, Drawable front, int inset, boolean frontBlank) {
        final Resources res = context.getResources();
        InsetDrawable[] inactivelayer = new InsetDrawable[2];
        InsetDrawable[] activelayer = new InsetDrawable[2];

        final String keyguardPackage = context.getString(
                com.android.internal.R.string.config_keyguardPackage);

        inactivelayer[0] = new InsetDrawable(getDrawableFromResources(context,
                keyguardPackage, "ic_lockscreen_lock_pressed", false), 0, 0, 0, 0);
        inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);

        activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
        activelayer[1] = new InsetDrawable(
                frontBlank ? res.getDrawable(android.R.color.transparent) : front,
                inset, inset, inset, inset);

        LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
        inactiveLayerDrawable.setId(0, 0);
        inactiveLayerDrawable.setId(1, 1);

        LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
        activeLayerDrawable.setId(0, 0);
        activeLayerDrawable.setId(1, 1);

        StateListDrawable states = new StateListDrawable();
        states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
        states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
        states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);

        return states;
    }

    private static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 24;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public static Drawable getDrawableFromFile(Context context, String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            return null;
        }

        return new BitmapDrawable(context.getResources(),
                LockscreenTargetUtils.getRoundedCornerBitmap(BitmapFactory.decodeFile(fileName)));
    }

    public static int getInsetForIconType(Context context, String type) {
        if (TextUtils.equals(type, ICON_RESOURCE)) {
            return 0;
        }

        int inset = 0;

        final String keyguardPackage = context.getString(
                com.android.internal.R.string.config_keyguardPackage);

        try {
            Context packageContext = context.createPackageContext(keyguardPackage, 0);
            Resources res = packageContext.getResources();
            int targetInsetIdentifier = res.getIdentifier("lockscreen_target_inset", "dimen", keyguardPackage);
            inset = res.getDimensionPixelSize(targetInsetIdentifier);
            if (TextUtils.equals(type, ICON_FILE)) {
                int targetIconFileInsetIdentifier = res.getIdentifier(
                    "lockscreen_target_icon_file_inset", "dimen", keyguardPackage);
                inset += res.getDimensionPixelSize(targetIconFileInsetIdentifier);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Could not fetch icons from " + keyguardPackage);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Could not resolve lockscreen_target_inset", e);
        }

        return inset;
    }

    public static Drawable getDrawableFromResources(Context context,
            String packageName, String identifier, boolean activated) {
        Resources res;

        if (TextUtils.isEmpty(packageName)) {
            final String keyguardPackage = context.getString(
                    com.android.internal.R.string.config_keyguardPackage);
            packageName = keyguardPackage;
        }

        try {
            Context packageContext = context.createPackageContext(packageName, 0);
            res = packageContext.getResources();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Could not fetch icons from package " + packageName);
            return null;
        }

        if (activated) {
            identifier = identifier.replaceAll("_normal", "_activated");
        }

        try {
            int id = res.getIdentifier(identifier, "drawable", packageName);
            return res.getDrawable(id);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Could not resolve icon " + identifier + " in " + packageName, e);
        }

        return null;
    }

    public static Drawable getDrawableFromIntent(Context context, Intent intent) {
        final Resources res = context.getResources();
        final PackageManager pm = context.getPackageManager();
        ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

        if (info == null) {
            return res.getDrawable(android.R.drawable.sym_def_app_icon);
        }

        Drawable icon = info.loadIcon(pm);
        return new BitmapDrawable(res, resizeIconTarget(context, icon));
    }

    private static Bitmap resizeIconTarget(Context context, Drawable icon) {
        Resources res = context.getResources();
        int size = (int) res.getDimension(android.R.dimen.app_icon_size);

        int width = size;
        int height = size;

        if (icon instanceof PaintDrawable) {
            PaintDrawable painter = (PaintDrawable) icon;
            painter.setIntrinsicWidth(width);
            painter.setIntrinsicHeight(height);
        } else if (icon instanceof BitmapDrawable) {
            // Ensure the bitmap has a density.
            BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
            Bitmap bitmap = bitmapDrawable.getBitmap();
            if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
            }
        }
        int sourceWidth = icon.getIntrinsicWidth();
        int sourceHeight = icon.getIntrinsicHeight();
        if (sourceWidth > 0 && sourceHeight > 0) {
            // There are intrinsic sizes.
            if (width < sourceWidth || height < sourceHeight) {
                // It's too big, scale it down.
                final float ratio = (float) sourceWidth / sourceHeight;
                if (sourceWidth > sourceHeight) {
                    height = (int) (width / ratio);
                } else if (sourceHeight > sourceWidth) {
                    width = (int) (height * ratio);
                }
            } else if (sourceWidth < width && sourceHeight < height) {
                // Don't scale up the icon
                width = sourceWidth;
                height = sourceHeight;
            }
        }

        final Bitmap bitmap = Bitmap.createBitmap(size, size,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas();
        canvas.setBitmap(bitmap);

        final int left = (size - width) / 2;
        final int top = (size - height) / 2;

        Rect oldBounds = new Rect();
        oldBounds.set(icon.getBounds());
        icon.setBounds(left, top, left + width, top + height);
        icon.draw(canvas);
        icon.setBounds(oldBounds);
        canvas.setBitmap(null);

        return bitmap;
    }
}
