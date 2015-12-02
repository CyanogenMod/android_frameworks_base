package com.android.systemui.cm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.android.systemui.R;
import cyanogenmod.providers.CMSettings;

public class LockscreenShortcutsHelper {

    private Handler mHandler;

    public enum Shortcuts {
        LEFT_SHORTCUT(0),
        RIGHT_SHORTCUT(1);

        private final int index;

        Shortcuts(int index) {
            this.index = index;
        }
    }

    public static final String DEFAULT = "default";
    public static final String NONE = "none";
    private static final String DELIMITER = "|";

    private final Context mContext;
    private OnChangeListener mListener;
    private List<String> mTargetActivities;

    public interface OnChangeListener {
        void onChange();
    }

    public LockscreenShortcutsHelper(Context context, OnChangeListener listener) {
        mContext = context;
        if (listener != null) {
            mListener = listener;
            mHandler = new Handler(Looper.getMainLooper());
            mContext.getContentResolver().registerContentObserver(
                    CMSettings.Secure.getUriFor(CMSettings.Secure.LOCKSCREEN_TARGETS), false, mObserver);
        }
        fetchTargets();
    }

    private ContentObserver mObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            fetchTargets();
            if (mListener != null && mHandler != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onChange();
                    }
                });
            }
        }
    };

    public void cleanup() {
        mContext.getContentResolver().unregisterContentObserver(mObserver);
        mListener = null;
    }

    public static class TargetInfo {
        public Drawable icon;
        public ColorFilter colorFilter;
        public String uri;
        public TargetInfo(Drawable icon, ColorFilter colorFilter, String uri) {
            this.icon = icon;
            this.colorFilter = colorFilter;
            this.uri = uri;
        }
    }

    private void fetchTargets() {
        mTargetActivities = CMSettings.Secure.getDelimitedStringAsList(mContext.getContentResolver(),
                CMSettings.Secure.LOCKSCREEN_TARGETS, DELIMITER);
        int itemsToPad = Shortcuts.values().length - mTargetActivities.size();
        if (itemsToPad > 0) {
            for (int i = 0; i < itemsToPad; i++) {
                mTargetActivities.add(DEFAULT);
            }
        }
    }

    public Drawable getDrawableForTarget(Shortcuts shortcut) {
        Intent intent = getIntent(shortcut);
        if (intent != null) {
            PackageManager pm = mContext.getPackageManager();
            ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
            if (info != null) {
                return getScaledDrawable(info.loadIcon(pm));
            }
        }
        return null;
    }

    public String getUriForTarget(Shortcuts shortcuts) {
        return mTargetActivities.get(shortcuts.index);
    }

    private Drawable getScaledDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Resources res = mContext.getResources();
            int width = res.getDimensionPixelSize(R.dimen.keyguard_affordance_icon_width);
            int height = res.getDimensionPixelSize(R.dimen.keyguard_affordance_icon_height);
            return new BitmapDrawable(mContext.getResources(),
                    Bitmap.createScaledBitmap(((BitmapDrawable) drawable).getBitmap(),
                            width, height, true));
        } else {
            return drawable;
        }
    }

    private String getFriendlyActivityName(Intent intent, boolean labelOnly) {
        PackageManager pm = mContext.getPackageManager();
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;
        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null && !labelOnly) {
                friendlyName = ai.name;
            }
        }
        return friendlyName != null || labelOnly ? friendlyName : intent.toUri(0);
    }

    private String getFriendlyShortcutName(Intent intent) {
        String activityName = getFriendlyActivityName(intent, true);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    public String getFriendlyNameForUri(Shortcuts shortcut) {
        Intent intent = getIntent(shortcut);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            return getFriendlyActivityName(intent, false);
        }
        return getFriendlyShortcutName(intent);
    }

    public boolean isTargetCustom(Shortcuts shortcut) {
        if (mTargetActivities == null || mTargetActivities.isEmpty()) {
            return false;
        }
        String action = mTargetActivities.get(shortcut.index);
        if (DEFAULT.equals(action)) {
            return false;
        }

        return NONE.equals(action) || getIntent(shortcut) != null;
    }

    public boolean isTargetEmpty(Shortcuts shortcut) {
        return mTargetActivities != null &&
                !mTargetActivities.isEmpty() &&
                mTargetActivities.get(shortcut.index).equals(NONE);
    }

    public Intent getIntent(Shortcuts shortcut) {
        Intent intent = null;
        try {
            intent = Intent.parseUri(mTargetActivities.get(shortcut.index), 0);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return intent;
    }

    public void saveTargets(ArrayList<String> targets) {
        CMSettings.Secure.putListAsDelimitedString(mContext.getContentResolver(),
                CMSettings.Secure.LOCKSCREEN_TARGETS, DELIMITER, targets);
    }
}
