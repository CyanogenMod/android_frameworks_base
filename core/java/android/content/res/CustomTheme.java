package android.content.res;

import android.os.*;
import android.content.Context;
import android.content.pm.*;
import android.util.Log;
import android.util.DisplayMetrics;
import android.R;
import android.app.ActivityManager;
import android.view.WindowManager;
import android.text.TextUtils;

/**
 * @hide
 */
public final class CustomTheme implements Cloneable {

    private final String mThemeId;
    private final String mThemePackageName;

    private static final CustomTheme sDefaultTheme = new CustomTheme();

    private CustomTheme() {
        mThemeId = SystemProperties.get("default_theme.style_id");
        mThemePackageName = SystemProperties.get("default_theme.package_name");
    }

    public CustomTheme(String themeId, String packageName) {
        mThemeId = themeId;
        mThemePackageName = packageName;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof CustomTheme) {
            CustomTheme o = (CustomTheme) object;
            if (!mThemeId.equals(o.mThemeId)) {
                return false;
            }
            String currentPackageName = (mThemePackageName == null)? "" : mThemePackageName;
            String newPackageName = (o.mThemePackageName == null)? "" : o.mThemePackageName;
            String currentThemeId = (mThemeId == null)? "" : mThemeId;
            String newThemeId = (o.mThemeId == null)? "" : o.mThemeId;
            return (currentPackageName.trim().equalsIgnoreCase(newPackageName.trim())) &&
                    (currentThemeId.trim().equalsIgnoreCase(newThemeId.trim()));
        }
        return false;
    }

    public static boolean nullSafeEquals(CustomTheme a, CustomTheme b) {
        if (a == b) {
            return true;
        }
        if (a != null && a.equals(b)) {
            return true;
        }
        return false;
    }

    @Override
    public final String toString() {
        StringBuilder result = new StringBuilder();
        result.append(mThemeId);
        result.append("_");
        if (mThemePackageName != null && mThemePackageName.length() > 0){
            result.append(mThemePackageName);
        }

        return result.toString();
    }

    @Override
    public synchronized int hashCode() {
        return mThemeId.hashCode() + mThemePackageName.hashCode();
    }

    public String getThemeId() {
        return mThemeId;
    }

    public String getThemePackageName() {
        return mThemePackageName;
    }

    public static CustomTheme getDefault() {
        return sDefaultTheme;
    }

    public static int getStyleId(Context context, String packageName, String styleName) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(styleName)) {
            return R.style.Theme;
        }
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
            ThemeInfo[] infos = pi.themeInfos;
            if (infos != null) {
                for (ThemeInfo ti : infos) {
                    if (ti.themeId.equals(styleName)) {
                        return ti.styleResourceId;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("CustomTheme", "Unable to get style resource id", e);
        }
        return -1;
    }
}
