package com.android.systemui.statusbar.phone;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Display;
import android.view.WindowManager;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.content.ContentResolver;

import com.android.systemui.R;


class NotificationWallpaper extends FrameLayout {

    private final String TAG = "NotificationWallpaperUpdater";

    private final String NOTIF_WALLPAPER_IMAGE_PATH = "/data/data/com.android.settings/files/notification_wallpaper.jpg";
    private final String NOTIF_WALLPAPER_IMAGE_PATH_LANDSCAPE = "/data/data/com.android.settings/files/notification_wallpaper_landscape.jpg";

    private ImageView mNotificationWallpaperImage;
    private float wallpaperAlpha;
    private int mCreationOrientation = 2;

    Context mContext;

    Bitmap bitmapWallpaper;

    public NotificationWallpaper(Context context, AttributeSet attrs) {
        super(context);
        mContext = context;
        setNotificationWallpaper();
        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
    }

    public void setNotificationWallpaper() {
        boolean isLandscape = false;
        File file = new File(NOTIF_WALLPAPER_IMAGE_PATH);
        File fileLandscape = new File(NOTIF_WALLPAPER_IMAGE_PATH_LANDSCAPE);
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = display.getRotation();
        switch(orientation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                isLandscape = false;
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                isLandscape = true;
                break;
        }

        if (file.exists()) {
            if (mNotificationWallpaperImage != null) {
                removeView(mNotificationWallpaperImage);
            }
            wallpaperAlpha = Settings.System.getFloat(getContext()
                .getContentResolver(), Settings.System.NOTIF_WALLPAPER_ALPHA, 0.1f);

            mNotificationWallpaperImage = new ImageView(getContext());
            if (isLandscape && !fileLandscape.exists()) {
                 mNotificationWallpaperImage.setScaleType(ScaleType.CENTER_CROP);
            }else {
                 mNotificationWallpaperImage.setScaleType(ScaleType.CENTER);
            }
            addView(mNotificationWallpaperImage, -1, -1);
            if (isLandscape && fileLandscape.exists()) {
                bitmapWallpaper = BitmapFactory.decodeFile(NOTIF_WALLPAPER_IMAGE_PATH_LANDSCAPE);
            }else {
                bitmapWallpaper = BitmapFactory.decodeFile(NOTIF_WALLPAPER_IMAGE_PATH);
            }
            Drawable d = new BitmapDrawable(getResources(), bitmapWallpaper);
            d.setAlpha((int) ((1-wallpaperAlpha) * 255));
            mNotificationWallpaperImage.setImageDrawable(d);
        } else {
            if (mNotificationWallpaperImage != null) {
                removeView(mNotificationWallpaperImage);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (bitmapWallpaper != null)
            bitmapWallpaper.recycle();

        System.gc();
        super.onDetachedFromWindow();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIF_WALLPAPER_ALPHA), false, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            setNotificationWallpaper();
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
            setNotificationWallpaper();
    }
}
