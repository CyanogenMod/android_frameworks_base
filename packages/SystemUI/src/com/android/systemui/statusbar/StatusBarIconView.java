/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.PorterDuff.Mode;
import android.os.UserHandle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.slim.ImageHelper;
import com.android.systemui.R;

import java.text.NumberFormat;

public class StatusBarIconView extends AnimatedImageView {
    private static final String TAG = "StatusBarIconView";

    private StatusBarIcon mIcon;
    @ViewDebug.ExportedProperty private String mSlot;
    private Drawable mNumberBackground;
    private Paint mNumberPaint;
    private int mNumberX;
    private int mNumberY;
    private String mNumberText;
    private Notification mNotification;
    private boolean mColorizeNotifIcons;
    private boolean mShowNotifCount;
    private int mIconColor;
    private int mNotifCountColor;
    private int mNotifCountTextColor;

    public StatusBarIconView(Context context, String slot, Notification notification) {
        super(context);
        final Resources res = context.getResources();
        final float densityMultiplier = res.getDisplayMetrics().density;
        final float scaledPx = 8 * densityMultiplier;
        mSlot = slot;
        mNumberPaint = new Paint();
        mNumberPaint.setTextAlign(Paint.Align.CENTER);
        mNumberPaint.setColor(res.getColor(R.drawable.notification_number_text_color));
        mNumberPaint.setAntiAlias(true);
        mNumberPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mNumberPaint.setTextSize(scaledPx);
        mNotification = notification;
        setContentDescription(notification);
        updateNotifCount();

        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
        // We do not resize and scale system icons (on the right), only notification icons (on the
        // left).
        if (notification != null) {
            final int outerBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_size);
            final int imageBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_drawing_size);
            final float scale = (float)imageBounds / (float)outerBounds;
            setScaleX(scale);
            setScaleY(scale);
        }

        setScaleType(ImageView.ScaleType.CENTER);
    }

    public StatusBarIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final Resources res = context.getResources();
        final int outerBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_size);
        final int imageBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_drawing_size);
        final float scale = (float)imageBounds / (float)outerBounds;
        setScaleX(scale);
        setScaleY(scale);
    }

    private static boolean streq(String a, String b) {
        if (a == b) {
            return true;
        }
        if (a == null && b != null) {
            return false;
        }
        if (a != null && b == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * Returns whether the set succeeded.
     */
    public boolean set(StatusBarIcon icon) {
        return set(icon, false);
    }

    private boolean set(StatusBarIcon icon, boolean force) {
        final boolean iconEquals = mIcon != null
                && streq(mIcon.iconPackage, icon.iconPackage)
                && mIcon.iconId == icon.iconId;
        final boolean levelEquals = iconEquals
                && mIcon.iconLevel == icon.iconLevel;
        final boolean visibilityEquals = mIcon != null
                && mIcon.visible == icon.visible;
        final boolean numberEquals = mIcon != null
                && mIcon.number == icon.number;
        mIcon = icon.clone();
        setContentDescription(icon.contentDescription);
        if (!iconEquals || force) {
            if (!updateDrawable(false /* no clear */)) return false;
        }
        if (!levelEquals || force) {
            setImageLevel(icon.iconLevel);
        }

        if (!numberEquals || force) {
            if (icon.number > 1 && mShowNotifCount) {
                if (mNumberBackground == null) {
                    mNumberBackground = getContext().getResources().getDrawable(
                            R.drawable.ic_notification_overlay);
                }
                mNumberBackground.setColorFilter(null);
                mNumberBackground.setColorFilter(mNotifCountColor,
                        Mode.MULTIPLY);
                placeNumber();
            } else {
                mNumberBackground = null;
                mNumberText = null;
            }
            invalidate();
        }
        if (!visibilityEquals || force) {
            setVisibility(icon.visible ? VISIBLE : GONE);
        }
        return true;
    }

    public void updateDrawable() {
        updateDrawable(true /* with clear */);
    }

    private boolean updateDrawable(boolean withClear) {
        Drawable drawable = getIcon(mIcon);
        if (drawable == null) {
            Log.w(TAG, "No icon for slot " + mSlot);
            return false;
        }
        if (withClear) {
            setImageDrawable(null);
        }
        if (mNotification == null) {
            drawable.setColorFilter(null);
            drawable.setColorFilter(mIconColor,
                        Mode.MULTIPLY);
            setImageDrawable(drawable);
        } else if (mNotification != null && mColorizeNotifIcons) {
            if (drawable instanceof AnimationDrawable) {
                ((DrawableContainer)drawable).setColorFilter(mIconColor,
                        Mode.MULTIPLY);
                setImageDrawable(drawable);
            } else {
                setImageBitmap(ImageHelper.getColoredBitmap(drawable, mIconColor));
            }
        } else {
            setImageDrawable(drawable);
        }
        return true;
    }

    private Drawable getIcon(StatusBarIcon icon) {
        return getIcon(getContext(), icon);
    }

    /**
     * Returns the right icon to use for this item, respecting the iconId and
     * iconPackage (if set)
     *
     * @param context Context to use to get resources if iconPackage is not set
     * @return Drawable for this item, or null if the package or item could not
     *         be found
     */
    public static Drawable getIcon(Context context, StatusBarIcon icon) {
        Resources r = null;

        if (icon.iconPackage != null) {
            try {
                int userId = icon.user.getIdentifier();
                if (userId == UserHandle.USER_ALL) {
                    userId = UserHandle.USER_OWNER;
                }
                r = context.getPackageManager()
                        .getResourcesForApplicationAsUser(icon.iconPackage, userId);
            } catch (PackageManager.NameNotFoundException ex) {
                Log.e(TAG, "Icon package not found: " + icon.iconPackage);
                return null;
            }
        } else {
            r = context.getResources();
        }

        if (icon.iconId == 0) {
            return null;
        }

        try {
            return r.getDrawable(icon.iconId);
        } catch (RuntimeException e) {
            Log.w(TAG, "Icon not found in "
                  + (icon.iconPackage != null ? icon.iconId : "<system>")
                  + ": " + Integer.toHexString(icon.iconId));
        }

        return null;
    }

    public StatusBarIcon getStatusBarIcon() {
        return mIcon;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (mNotification != null) {
            event.setParcelableData(mNotification);
        }
    }

    public String getStatusBarSlot() {
        return mSlot;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mNumberBackground != null) {
            placeNumber();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mNumberBackground != null) {
            mNumberBackground.draw(canvas);
            canvas.drawText(mNumberText, mNumberX, mNumberY, mNumberPaint);
        }
    }

    @Override
    protected void debug(int depth) {
        super.debug(depth);
        Log.d("View", debugIndent(depth) + "slot=" + mSlot);
        Log.d("View", debugIndent(depth) + "icon=" + mIcon);
    }

    void placeNumber() {
        final String str;
        final int tooBig = mContext.getResources().getInteger(
                android.R.integer.status_bar_notification_info_maxnum);
        if (mIcon.number > tooBig) {
            str = mContext.getResources().getString(
                        android.R.string.status_bar_notification_info_overflow);
        } else {
            NumberFormat f = NumberFormat.getIntegerInstance();
            str = f.format(mIcon.number);
        }
        mNumberText = str;

        final int w = getWidth();
        final int h = getHeight();
        final Rect r = new Rect();
        mNumberPaint.getTextBounds(str, 0, str.length(), r);
        final int tw = r.right - r.left;
        final int th = r.bottom - r.top;
        mNumberBackground.getPadding(r);
        int dw = r.left + tw + r.right;
        if (dw < mNumberBackground.getMinimumWidth()) {
            dw = mNumberBackground.getMinimumWidth();
        }
        mNumberX = w-r.right-((dw-r.right-r.left)/2);
        int dh = r.top + th + r.bottom;
        if (dh < mNumberBackground.getMinimumWidth()) {
            dh = mNumberBackground.getMinimumWidth();
        }
        mNumberY = h-r.bottom-((dh-r.top-th-r.bottom)/2);
        mNumberBackground.setBounds(w-dw, h-dh, w, h);
    }

    private void setContentDescription(Notification notification) {
        if (notification != null) {
            CharSequence tickerText = notification.tickerText;
            if (!TextUtils.isEmpty(tickerText)) {
                setContentDescription(tickerText);
            }
        }
    }

    public String toString() {
        return "StatusBarIconView(slot=" + mSlot + " icon=" + mIcon
            + " notification=" + mNotification + ")";
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_COLORIZE_NOTIF_ICONS),
                    false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_NOTIF_COUNT),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_SYSTEM_ICON_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_COUNT_ICON_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_COUNT_TEXT_COLOR),
                    false, this);
        }
        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
        @Override
        public void onChange(boolean selfChange) {
            updateNotifCount();
            set(mIcon, true);
        }
    }

    public void updateNotifCount() {
        ContentResolver resolver = mContext.getContentResolver();

        mColorizeNotifIcons = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_COLORIZE_NOTIF_ICONS, 0) == 1;
        mShowNotifCount = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_COUNT,
                    mContext.getResources().getBoolean(
                    R.bool.config_statusBarShowNumber) ? 1 : 0) == 1;
        mIconColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_SYSTEM_ICON_COLOR,
                0xffffffff);
        mNotifCountColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_COUNT_ICON_COLOR,
                0xffE5350D);

        mNotifCountTextColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_COUNT_TEXT_COLOR,
                0xffffffff);

        mNumberPaint.setColor(mNotifCountTextColor);
    }
}
