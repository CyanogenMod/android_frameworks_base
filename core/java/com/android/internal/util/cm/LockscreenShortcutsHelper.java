package com.android.internal.util.cm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class LockscreenShortcutsHelper {

    public enum Shortcuts {
        LEFT_SHORTCUT(0),
        RIGHT_SHORTCUT(1);

        private final int index;

        private Shortcuts(int index) {
            this.index = index;
        }
    }

    public static final String DEFAULT = "default";
    public static final String NONE = "none";
    private static final String DELIMITER = "|";
    private static final String SYSTEM_UI_PKGNAME = "com.android.systemui";
    private static final String PHONE_DEFAULT_ICON = "ic_phone_24dp";
    private static final String CAMERA_DEFAULT_ICON = "ic_camera_alt_24dp";

    private final Context mContext;
    private Resources mSystemUiResources;
    private OnChangeListener mListener;
    private List<String> mTargetActivities;

    public interface OnChangeListener {
        public void onChange();
    }

    public LockscreenShortcutsHelper(Context context, OnChangeListener listener) {
        mContext = context;
        if (listener != null) {
            mListener = listener;
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.LOCKSCREEN_TARGETS), false, mObserver);
        }
        fetchTargets();
    }

    private ContentObserver mObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            fetchTargets();
            if (mListener != null) {
                mListener.onChange();
            }
        }
    };

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
        mTargetActivities = Settings.Secure.getDelimitedStringAsList(mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_TARGETS, DELIMITER);
        int itemsToPad = Shortcuts.values().length - mTargetActivities.size();
        if (itemsToPad > 0) {
            for (int i = 0; i < itemsToPad; i++) {
                mTargetActivities.add(DEFAULT);
            }
        }
    }

    public List<TargetInfo> getDrawablesForTargets() {
        fetchTargets();
        List<TargetInfo> result = new ArrayList<TargetInfo>();

        final ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        final ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);

        for (int i = 0; i < Shortcuts.values().length; i++) {
            String activity = mTargetActivities.get(i);
            Drawable drawable = null;
            ColorFilter filerToSet = null;

            if (!TextUtils.isEmpty(activity) && !activity.equals(NONE)) {
                // No pre-defined action, try to resolve URI
                try {
                    Intent intent = Intent.parseUri(activity, 0);
                    PackageManager pm = mContext.getPackageManager();
                    ActivityInfo info = intent.resolveActivityInfo(pm,
                            PackageManager.GET_ACTIVITIES);

                    if (info != null) {
                        drawable = info.loadIcon(pm);
                        filerToSet = filter;
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    // Treat as empty
                }
            }

            if (drawable == null) {
                drawable = getDrawableFromSystemUI(i == Shortcuts.LEFT_SHORTCUT.index
                        ? PHONE_DEFAULT_ICON : CAMERA_DEFAULT_ICON);
                filerToSet = null;
            }

            result.add(new TargetInfo(drawable, filerToSet, activity));
        }
        return result;
    }

    public Drawable getDrawableFromSystemUI(String name) {
        Resources res;
        if (mContext.getPackageName().equals(SYSTEM_UI_PKGNAME)) {
            res = mContext.getResources();
        } else {
            if (mSystemUiResources == null) {
                try {
                    PackageManager pm = mContext.getPackageManager();
                    mSystemUiResources = pm.getResourcesForApplication(SYSTEM_UI_PKGNAME);
                } catch (PackageManager.NameNotFoundException e) {
                    // ignore
                }
            }
            res = mSystemUiResources;
        }
        if (res == null) {
            return null;
        }
        int id = res.getIdentifier(name, "drawable", SYSTEM_UI_PKGNAME);
        return id > 0 ? res.getDrawable(id) : null;
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
        Settings.Secure.putListAsDelimitedString(mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_TARGETS, DELIMITER, targets);
    }
}
