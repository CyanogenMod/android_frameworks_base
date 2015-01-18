package com.android.internal.util.cm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class LockscreenShortcutsHelpers {

    public static final int LEFT_INDEX = 0;
    public static final int RIGHT_INDEX = 1;
    private static final int NUM_SHORTCUTS = 2;

    private static final String DELIMITER = "|";
    private static final String SYSTEM_UI_PKGNAME = "com.android.systemui";
    private static final String PHONE_DEFAULT_ICON = "ic_phone_24dp";
    private static final String CAMERA_DEFAULT_ICON = "ic_camera_24dp";

    private LockscreenShortcutsHelpers() {}

    public static class TargetInfo {
        public Drawable icon;
        public ColorFilter colorFilter;

        public TargetInfo(Drawable icon, ColorFilter colorFilter) {
            this.icon = icon;
            this.colorFilter = colorFilter;
        }
    }

    public static List<TargetInfo> getDrawablesForTargets(Context context) {
        List<TargetInfo> result = new ArrayList<TargetInfo>();

        List<String> targetActivities = Settings.Secure.getDelimitedStringAsList(context.getContentResolver(),
                Settings.Secure.LOCKSCREEN_TARGETS, DELIMITER);

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        ColorFilter filerToSet = null;

        for (int i = 0; i < NUM_SHORTCUTS; i++) {
            String activity = targetActivities.get(i);
            Drawable drawable = null;

            if (TextUtils.isEmpty(activity) || activity.equals(NavigationRingConstants.ACTION_NONE)) {
                drawable = getDrawableFromSystemUI(context, i == 0 ? PHONE_DEFAULT_ICON : CAMERA_DEFAULT_ICON);
                filerToSet = null;
            } else {
                // No pre-defined action, try to resolve URI
                try {
                    Intent intent = Intent.parseUri(activity, 0);
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                    if (info != null) {
                        drawable = info.loadIcon(pm);
                        filerToSet = filter;
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    // Treat as empty
                }
            }
            result.add(new TargetInfo(drawable, filerToSet));
        }
        return result;
    }

    public static Drawable getDrawableFromSystemUI(Context context, String name) {
        Resources res = null;
        if (context.getPackageName().equals(SYSTEM_UI_PKGNAME)) {
            res = context.getResources();
        } else {
            try {
                context = context.createPackageContext(SYSTEM_UI_PKGNAME,
                        Context.CONTEXT_IGNORE_SECURITY);
                res = context.getResources();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (res == null) {
            return null;
        }
        int id = res.getIdentifier(name, "drawable", SYSTEM_UI_PKGNAME);
        if (id > 0) {
            return res.getDrawable(id);
        }
        return null;
    }

    private static String getFriendlyActivityName(Context context, Intent intent, boolean labelOnly) {
        PackageManager packageManager = context.getPackageManager();
        ActivityInfo ai = intent.resolveActivityInfo(packageManager, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;
        if (ai != null) {
            friendlyName = ai.loadLabel(packageManager).toString();
            if (friendlyName == null && !labelOnly) {
                friendlyName = ai.name;
            }
        }
        return friendlyName != null || labelOnly ? friendlyName : intent.toUri(0);
    }

    private static String getFriendlyShortcutName(Context context, Intent intent) {
        String activityName = getFriendlyActivityName(context, intent, true);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    public static String getFriendlyNameForUri(Context context, String uri) {
        if (uri == null) {
            return null;
        }

        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                return getFriendlyActivityName(context, intent, false);
            }
            return getFriendlyShortcutName(context, intent);
        } catch (URISyntaxException e) {
        }

        return uri;
    }

}
