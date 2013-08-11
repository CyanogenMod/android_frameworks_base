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
import android.graphics.drawable.StateListDrawable;
import android.util.Log;

import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;

public final class LockscreenTargetUtils {
    private static final String TAG = "LockscreenTargetUtils";

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
            return GlowPadView.MAX_TABLET_TARGETS;
        }

        return GlowPadView.MAX_PHONE_TARGETS;
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

        inactivelayer[0] = new InsetDrawable(res.getDrawable(
                    com.android.internal.R.drawable.ic_lockscreen_lock_pressed), 0, 0, 0, 0);
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

    public static Drawable getDrawableFromResources(Context context,
            String packageName, String identifier, boolean activated) {
        Resources res;

        if (packageName != null) {
            try {
                Context packageContext = context.createPackageContext(packageName, 0);
                res = packageContext.getResources();
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Could not fetch icons from package " + packageName);
                return null;
            }
        } else {
            res = context.getResources();
            packageName = "android";
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
}
