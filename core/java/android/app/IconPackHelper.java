/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package android.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import android.content.pm.PackageInfo;
import android.content.res.IThemeService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.PaintDrawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.TypedValue;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;

/** @hide */
public class IconPackHelper {
    private static final String TAG = IconPackHelper.class.getSimpleName();
    private static final String ICON_MASK_TAG = "iconmask";
    private static final String ICON_BACK_TAG = "iconback";
    private static final String ICON_UPON_TAG = "iconupon";
    private static final String ICON_SCALE_TAG = "scale";
    private static final String ICON_BACK_FORMAT = "iconback%d";

    private static final ComponentName ICON_BACK_COMPONENT;
    private static final ComponentName ICON_MASK_COMPONENT;
    private static final ComponentName ICON_UPON_COMPONENT;
    private static final ComponentName ICON_SCALE_COMPONENT;

    private static final float DEFAULT_SCALE = 1.0f;
    private static final int COMPOSED_ICON_COOKIE = 128;

    private final Context mContext;
    private Map<ComponentName, String> mIconPackResourceMap;
    private String mLoadedIconPackName;
    private Resources mLoadedIconPackResource;
    private ComposedIconInfo mComposedIconInfo;
    private int mIconBackCount = 0;

    static {
        ICON_BACK_COMPONENT = new ComponentName(ICON_BACK_TAG, "");
        ICON_MASK_COMPONENT = new ComponentName(ICON_MASK_TAG, "");
        ICON_UPON_COMPONENT = new ComponentName(ICON_UPON_TAG, "");
        ICON_SCALE_COMPONENT = new ComponentName(ICON_SCALE_TAG, "");
    }

    public IconPackHelper(Context context) {
        mContext = context;
        mIconPackResourceMap = new HashMap<ComponentName, String>();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mComposedIconInfo = new ComposedIconInfo();
        mComposedIconInfo.iconSize = am.getLauncherLargeIconSize();
        mComposedIconInfo.iconDensity = am.getLauncherLargeIconDensity();
    }

    private void loadResourcesFromXmlParser(XmlPullParser parser,
            Map<ComponentName, String> iconPackResources) throws XmlPullParserException, IOException {
        mIconBackCount = 0;
        int eventType = parser.getEventType();
        do {

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }

            if (parseComposedIconComponent(parser, iconPackResources)) {
                continue;
            }

            if (parser.getName().equalsIgnoreCase(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() == 1) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(ICON_SCALE_COMPONENT, factor);
                continue;
            }

            if (!parser.getName().equalsIgnoreCase("item")) {
                continue;
            }

            String component = parser.getAttributeValue(null, "component");
            String drawable = parser.getAttributeValue(null, "drawable");

            // Validate component/drawable exist
            if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawable)) {
                continue;
            }

            // Validate format/length of component
            if (!component.startsWith("ComponentInfo{") || !component.endsWith("}")
                    || component.length() < 16 || drawable.length() == 0) {
                continue;
            }

            // Sanitize stored value
            component = component.substring(14, component.length() - 1).toLowerCase();

            ComponentName name = null;
            if (!component.contains("/")) {
                // Package icon reference
                name = new ComponentName(component.toLowerCase(), "");
            } else {
                name = ComponentName.unflattenFromString(component);
            }

            if (name != null) {
                iconPackResources.put(name, drawable);
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
    }

    private boolean isComposedIconComponent(String tag) {
        return tag.equalsIgnoreCase(ICON_MASK_TAG) ||
                tag.equalsIgnoreCase(ICON_BACK_TAG) ||
                tag.equalsIgnoreCase(ICON_UPON_TAG);
    }

    private boolean parseComposedIconComponent(XmlPullParser parser,
                                            Map<ComponentName, String> iconPackResources) {
        String icon;
        String tag = parser.getName();
        if (!isComposedIconComponent(tag)) {
            return false;
        }

        if (parser.getAttributeCount() >= 1) {
            if (tag.equalsIgnoreCase(ICON_BACK_TAG)) {
                mIconBackCount = parser.getAttributeCount();
                for (int i = 0; i < mIconBackCount; i++) {
                    tag = String.format(ICON_BACK_FORMAT, i);
                    icon = parser.getAttributeValue(i);
                    iconPackResources.put(new ComponentName(tag, ""), icon);
                }
            } else {
                icon = parser.getAttributeValue(0);
                iconPackResources.put(new ComponentName(tag, ""),
                        icon);
            }
            return true;
        }

        return false;
    }

    public void loadIconPack(String packageName) throws NameNotFoundException {
        if (packageName == null) {
            mLoadedIconPackResource = null;
            mLoadedIconPackName = null;
            mComposedIconInfo.iconBacks = null;
            mComposedIconInfo.iconMask = mComposedIconInfo.iconUpon = null;
            mComposedIconInfo.iconScale = 0;
        } else {
            mIconBackCount = 0;
            Resources res = createIconResource(mContext, packageName);
            mIconPackResourceMap = getIconResMapFromXml(res, packageName);
            mLoadedIconPackResource = res;
            mLoadedIconPackName = packageName;
            loadComposedIconComponents();
        }
    }

    public ComposedIconInfo getComposedIconInfo() {
        return mComposedIconInfo;
    }

    private void loadComposedIconComponents() {
        mComposedIconInfo.iconMask = (BitmapDrawable) getDrawableForName(ICON_MASK_COMPONENT);
        mComposedIconInfo.iconUpon = (BitmapDrawable) getDrawableForName(ICON_UPON_COMPONENT);

        // Take care of loading iconback which can have multiple images
        if (mIconBackCount > 0) {
            mComposedIconInfo.iconBacks = new BitmapDrawable[mIconBackCount];
            for (int i = 0; i < mIconBackCount; i++) {
                mComposedIconInfo.iconBacks[i] =
                        (BitmapDrawable) getDrawableForName(
                                new ComponentName(String.format(ICON_BACK_FORMAT, i), ""));
            }
        }

        // Get the icon scale from this pack
        String scale = mIconPackResourceMap.get(ICON_SCALE_COMPONENT);
        if (scale != null) {
            try {
                mComposedIconInfo.iconScale = Float.valueOf(scale);
            } catch (NumberFormatException e) {
                mComposedIconInfo.iconScale = DEFAULT_SCALE;
            }
        } else {
            mComposedIconInfo.iconScale = DEFAULT_SCALE;
        }
    }

    private Drawable getDrawableForName(ComponentName component) {
        if (isIconPackLoaded()) {
            String item = mIconPackResourceMap.get(component);
            if (!TextUtils.isEmpty(item)) {
                int id = getResourceIdForDrawable(item);
                if (id != 0) {
                    return mLoadedIconPackResource.getDrawable(id);
                }
            }
        }
        return null;
    }

    public static Resources createIconResource(Context context, String packageName) throws NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
        String themeApk = info.applicationInfo.publicSourceDir;

        String prefixPath;
        String iconApkPath;
        String iconResPath;
        if (info.isLegacyIconPackApk) {
            iconResPath = "";
            iconApkPath = "";
            prefixPath = "";
        } else {
            prefixPath = ThemeUtils.ICONS_PATH; //path inside APK
            iconApkPath = ThemeUtils.getIconPackApkPath(packageName);
            iconResPath = ThemeUtils.getIconPackResPath(packageName);
        }

        AssetManager assets = new AssetManager();
        assets.addIconPath(themeApk, iconResPath, iconApkPath,
                prefixPath, Resources.THEME_ICON_PKG_ID);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        Configuration config = context.getResources().getConfiguration();
        Resources res = new Resources(assets, dm, config);
        return res;
    }

    public Map<ComponentName, String> getIconResMapFromXml(Resources res, String packageName) {
        XmlPullParser parser = null;
        InputStream inputStream = null;
        Map<ComponentName, String> iconPackResources = new HashMap<ComponentName, String>();

        try {
            inputStream = res.getAssets().open("appfilter.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");
        } catch (Exception e) {
            // Catch any exception since we want to fall back to parsing the xml/
            // resource in all cases
            int resId = res.getIdentifier("appfilter", "xml", packageName);
            if (resId != 0) {
                parser = res.getXml(resId);
            }
        }

        if (parser != null) {
            try {
                  loadResourcesFromXmlParser(parser, iconPackResources);
                  return iconPackResources;
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Cleanup resources
                if (parser instanceof XmlResourceParser) {
                    ((XmlResourceParser) parser).close();
                } else if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        // Application uses a different theme format (most likely launcher pro)
        int arrayId = res.getIdentifier("theme_iconpack", "array", packageName);
        if (arrayId == 0) {
            arrayId = res.getIdentifier("icon_pack", "array", packageName);
        }

        if (arrayId != 0) {
            String[] iconPack = res.getStringArray(arrayId);
            ComponentName compName = null;
            for (String entry : iconPack) {

                if (TextUtils.isEmpty(entry)) {
                    continue;
                }

                String icon = entry;
                entry = entry.replaceAll("_", ".");

                compName = new ComponentName(entry.toLowerCase(), "");
                iconPackResources.put(compName, icon);

                int activityIndex = entry.lastIndexOf(".");
                if (activityIndex <= 0 || activityIndex == entry.length() - 1) {
                    continue;
                }

                String iconPackage = entry.substring(0, activityIndex);
                if (TextUtils.isEmpty(iconPackage)) {
                    continue;
                }

                String iconActivity = entry.substring(activityIndex + 1);
                if (TextUtils.isEmpty(iconActivity)) {
                    continue;
                }

                // Store entries as lower case to ensure match
                iconPackage = iconPackage.toLowerCase();
                iconActivity = iconActivity.toLowerCase();

                iconActivity = iconPackage + "." + iconActivity;
                compName = new ComponentName(iconPackage, iconActivity);
                iconPackResources.put(compName, icon);
            }
        }
        return iconPackResources;
    }

    boolean isIconPackLoaded() {
        return mLoadedIconPackResource != null &&
                mLoadedIconPackName != null &&
                mIconPackResourceMap != null;
    }

    private int getResourceIdForDrawable(String resource) {
        int resId = mLoadedIconPackResource.getIdentifier(resource, "drawable", mLoadedIconPackName);
        return resId;
    }

    public int getResourceIdForActivityIcon(ActivityInfo info) {
        if (!isIconPackLoaded()) {
            return 0;
        }
        ComponentName compName = new ComponentName(info.packageName.toLowerCase(),
                info.name.toLowerCase());
        String drawable = mIconPackResourceMap.get(compName);
        if (drawable != null) {
            int resId = getResourceIdForDrawable(drawable);
            if (resId != 0) return resId;
        }

        // Icon pack doesn't have an icon for the activity, fallback to package icon
        compName = new ComponentName(info.packageName.toLowerCase(), "");
        drawable = mIconPackResourceMap.get(compName);
        if (drawable == null) {
            return 0;
        }
        return getResourceIdForDrawable(drawable);
    }

    public int getResourceIdForApp(String pkgName) {
        ActivityInfo info = new ActivityInfo();
        info.packageName = pkgName;
        info.name = "";
        return getResourceIdForActivityIcon(info);
    }

    public Drawable getDrawableForActivity(ActivityInfo info) {
        int id = getResourceIdForActivityIcon(info);
        if (id == 0) return null;
        return mLoadedIconPackResource.getDrawable(id, false);
    }

    public Drawable getDrawableForActivityWithDensity(ActivityInfo info, int density) {
        int id = getResourceIdForActivityIcon(info);
        if (id == 0) return null;
        return mLoadedIconPackResource.getDrawableForDensity(id, density, false);
    }

    public static class IconCustomizer {
        private static final Random sRandom = new Random();
        private static final IThemeService sThemeService;

        static {
            sThemeService = IThemeService.Stub.asInterface(
                    ServiceManager.getService(Context.THEME_SERVICE));
        }

        public static Drawable getComposedIconDrawable(Drawable icon, Context context,
                                                       ComposedIconInfo iconInfo) {
            final Resources res = context.getResources();
            return getComposedIconDrawable(icon, res, iconInfo);
        }

        public static Drawable getComposedIconDrawable(Drawable icon, Resources res,
                                                       ComposedIconInfo iconInfo) {
            if (iconInfo == null) return icon;
            Drawable back = null;
            if (iconInfo.iconBacks != null && iconInfo.iconBacks.length > 0) {
                back = iconInfo.iconBacks[sRandom.nextInt(iconInfo.iconBacks.length)];
            }
            Bitmap bmp = createIconBitmap(icon, res, back, iconInfo.iconMask, iconInfo.iconUpon,
                    iconInfo.iconScale, iconInfo.iconSize);
            return bmp != null ? new BitmapDrawable(res, bmp): null;
        }

        public static void getValue(Resources res, int resId, TypedValue outValue,
                                    Drawable baseIcon) {
            if (!(baseIcon instanceof BitmapDrawable)) return;

            final String pkgName = res.getAssets().getAppName();
            TypedValue tempValue = new TypedValue();
            tempValue.setTo(outValue);
            outValue.assetCookie = COMPOSED_ICON_COOKIE;
            outValue.data = resId & (COMPOSED_ICON_COOKIE << 24 | 0x00ffffff);
            outValue.string = getCachedIconPath(pkgName, resId, outValue.density);

            if (!(new File(outValue.string.toString()).exists())) {
                // compose the icon and cache it
                final ComposedIconInfo iconInfo = res.getComposedIconInfo();
                Drawable back = null;
                if (iconInfo.iconBacks != null && iconInfo.iconBacks.length > 0) {
                    back = iconInfo.iconBacks[(outValue.string.hashCode() & 0x7fffffff)
                            % iconInfo.iconBacks.length];
                }
                Bitmap bmp = createIconBitmap(baseIcon, res, back, iconInfo.iconMask,
                        iconInfo.iconUpon, iconInfo.iconScale, iconInfo.iconSize);
                if (!cacheComposedIcon(bmp, getCachedIconName(pkgName, resId, outValue.density))) {
                    Log.w(TAG, "Unable to cache icon " + outValue.string);
                    // restore the original TypedValue
                    outValue.setTo(tempValue);
                }
            }
        }

        private static Bitmap createIconBitmap(Drawable icon, Resources res, Drawable iconBack,
                                               Drawable iconMask, Drawable iconUpon, float scale,
                                               int iconSize) {
            if (iconSize <= 0) return null;

            final Canvas canvas = new Canvas();
            canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                    Paint.FILTER_BITMAP_FLAG));

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(iconSize);
                painter.setIntrinsicHeight(iconSize);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(res.getDisplayMetrics());
                }
                canvas.setDensity(bitmap.getDensity());
            }

            Bitmap bitmap = Bitmap.createBitmap(iconSize, iconSize,
                    Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);

            // Scale the original
            Rect oldBounds = new Rect();
            oldBounds.set(icon.getBounds());
            icon.setBounds(0, 0, iconSize, iconSize);
            canvas.save();
            canvas.scale(scale, scale, iconSize / 2, iconSize / 2);
            icon.draw(canvas);
            canvas.restore();

            // Mask off the original if iconMask is not null
            if (iconMask != null) {
                iconMask.setBounds(icon.getBounds());
                ((BitmapDrawable) iconMask).getPaint().setXfermode(
                        new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                iconMask.draw(canvas);
            }
            // Draw the iconBacks if not null and then the original (scaled and masked) icon on top
            if (iconBack != null) {
                iconBack.setBounds(icon.getBounds());
                ((BitmapDrawable) iconBack).getPaint().setXfermode(
                        new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
                iconBack.draw(canvas);
            }
            // Finally draw the foreground if one was supplied
            if (iconUpon != null) {
                iconUpon.setBounds(icon.getBounds());
                iconUpon.draw(canvas);
            }
            icon.setBounds(oldBounds);
            bitmap.setDensity(canvas.getDensity());

            return bitmap;
        }

        private static boolean cacheComposedIcon(Bitmap bmp, String path) {
            try {
                return sThemeService.cacheComposedIcon(bmp, path);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to cache icon.", e);
            }

            return false;
        }

        private static String getCachedIconPath(String pkgName, int resId, int density) {
            return String.format("%s/%s", ThemeUtils.SYSTEM_THEME_ICON_CACHE_DIR,
                    getCachedIconName(pkgName, resId, density));
        }

        private static String getCachedIconName(String pkgName, int resId, int density) {
            return String.format("%s_%08x_%d.png", pkgName, resId, density);
        }
    }
}
