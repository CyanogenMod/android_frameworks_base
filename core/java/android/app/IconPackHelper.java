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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import android.content.pm.PackageInfo;
import android.content.res.IThemeService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
    private ColorFilterUtils.Builder mFilterBuilder;

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
        mFilterBuilder = new ColorFilterUtils.Builder();
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

            if (ColorFilterUtils.parseIconFilter(parser, mFilterBuilder)) {
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
            mComposedIconInfo.iconMask = mComposedIconInfo.iconUpon = 0;
            mComposedIconInfo.iconScale = 0;
            mComposedIconInfo.colorFilter = null;
        } else {
            mIconBackCount = 0;
            Resources res = createIconResource(mContext, packageName);
            mIconPackResourceMap = getIconResMapFromXml(res, packageName);
            mLoadedIconPackResource = res;
            mLoadedIconPackName = packageName;
            loadComposedIconComponents();
            ColorMatrix cm = mFilterBuilder.build();
            if (cm != null) {
                mComposedIconInfo.colorFilter = cm.getArray().clone();
            }
        }
    }

    public ComposedIconInfo getComposedIconInfo() {
        return mComposedIconInfo;
    }

    private void loadComposedIconComponents() {
        mComposedIconInfo.iconMask = getResourceIdForName(ICON_MASK_COMPONENT);
        mComposedIconInfo.iconUpon = getResourceIdForName(ICON_UPON_COMPONENT);

        // Take care of loading iconback which can have multiple images
        if (mIconBackCount > 0) {
            mComposedIconInfo.iconBacks = new int[mIconBackCount];
            for (int i = 0; i < mIconBackCount; i++) {
                mComposedIconInfo.iconBacks[i] =
                        getResourceIdForName(
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

    private int getResourceIdForName(ComponentName component) {
        String item = mIconPackResourceMap.get(component);
        if (!TextUtils.isEmpty(item)) {
            return getResourceIdForDrawable(item);
        }
        return 0;
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

    public static boolean shouldComposeIcon(ComposedIconInfo iconInfo) {
        return iconInfo != null &&
                (iconInfo.iconBacks != null || iconInfo.iconMask != 0 ||
                        iconInfo.iconUpon != 0 || iconInfo.colorFilter != null);
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
            int back = 0;
            if (iconInfo.iconBacks != null && iconInfo.iconBacks.length > 0) {
                back = iconInfo.iconBacks[sRandom.nextInt(iconInfo.iconBacks.length)];
            }
            Bitmap bmp = createIconBitmap(icon, res, back, iconInfo.iconMask, iconInfo.iconUpon,
                    iconInfo.iconScale, iconInfo.iconSize, iconInfo.colorFilter);
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
                int back = 0;
                if (iconInfo.iconBacks != null && iconInfo.iconBacks.length > 0) {
                    back = iconInfo.iconBacks[(outValue.string.hashCode() & 0x7fffffff)
                            % iconInfo.iconBacks.length];
                }
                Bitmap bmp = createIconBitmap(baseIcon, res, back, iconInfo.iconMask,
                        iconInfo.iconUpon, iconInfo.iconScale, iconInfo.iconSize,
                        iconInfo.colorFilter);
                if (!cacheComposedIcon(bmp, getCachedIconName(pkgName, resId, outValue.density))) {
                    Log.w(TAG, "Unable to cache icon " + outValue.string);
                    // restore the original TypedValue
                    outValue.setTo(tempValue);
                }
            }
        }

        private static Bitmap createIconBitmap(Drawable icon, Resources res, int iconBack,
                                               int iconMask, int iconUpon, float scale,
                                               int iconSize, float[] colorFilter) {
            if (iconSize <= 0) return null;

            final Canvas canvas = new Canvas();
            canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                    Paint.FILTER_BITMAP_FLAG));

            int width = 0, height = 0;
            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(iconSize);
                painter.setIntrinsicHeight(iconSize);

                // A PaintDrawable does not have an exact size
                width = iconSize;
                height = iconSize;
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(res.getDisplayMetrics());
                }
                canvas.setDensity(bitmap.getDensity());

                // If the original size of the icon isn't greater
                // than twice the size of recommended large icons
                // respect the original size of the icon
                // otherwise enormous icons can easily create
                // OOM situations.
                if ((bitmap.getWidth() < (iconSize * 2))
                        && (bitmap.getHeight() < (iconSize * 2))) {
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                } else {
                    width = iconSize;
                    height = iconSize;
                }
            }

            if (width <= 0 || height <= 0) return null;

            Bitmap bitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);

            // Scale the original
            Rect oldBounds = new Rect();
            oldBounds.set(icon.getBounds());
            icon.setBounds(0, 0, width, height);
            canvas.save();
            canvas.scale(scale, scale, width / 2, height / 2);
            if (colorFilter != null) {
                Paint p = null;
                if (icon instanceof BitmapDrawable) {
                    p = ((BitmapDrawable) icon).getPaint();
                } else if (icon instanceof PaintDrawable) {
                    p = ((PaintDrawable) icon).getPaint();
                }
                p.setColorFilter(new ColorMatrixColorFilter(colorFilter));
            }
            icon.draw(canvas);
            canvas.restore();

            // Mask off the original if iconMask is not null
            if (iconMask != 0) {
                Drawable mask = res.getDrawable(iconMask);
                if (mask != null) {
                    mask.setBounds(icon.getBounds());
                    ((BitmapDrawable) mask).getPaint().setXfermode(
                            new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                    mask.draw(canvas);
                }
            }
            // Draw the iconBacks if not null and then the original (scaled and masked) icon on top
            if (iconBack != 0) {
                Drawable back = res.getDrawable(iconBack);
                if (back != null) {
                    back.setBounds(icon.getBounds());
                    ((BitmapDrawable) back).getPaint().setXfermode(
                            new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
                    back.draw(canvas);
                }
            }
            // Finally draw the foreground if one was supplied
            if (iconUpon != 0) {
                Drawable upon = res.getDrawable(iconUpon);
                if (upon != null) {
                    upon.setBounds(icon.getBounds());
                    upon.draw(canvas);
                }
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


        // Returns paths for all densities
        public static String[] getCachedIconPaths(String pkgName) {
            File iconCache = new File(ThemeUtils.SYSTEM_THEME_ICON_CACHE_DIR);
            final String prefix = String.format("%s", pkgName);

            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.startsWith(prefix);
                }
            };

            String[] validPaths = iconCache.list(filter);
            return validPaths != null ? validPaths : new String[0];
        }

        private static String getCachedIconName(String pkgName, int resId, int density) {
            return String.format("%s_%08x_%d.png", pkgName, resId, density);
        }
    }

    public static class ColorFilterUtils {
        private static final String TAG_FILTER = "filter";
        private static final String FILTER_HUE = "hue";
        private static final String FILTER_SATURATION = "saturation";
        private static final String FILTER_INVERT = "invert";
        private static final String FILTER_BRIGHTNESS = "brightness";
        private static final String FILTER_CONTRAST = "contrast";
        private static final String FILTER_ALPHA = "alpha";
        private static final String FILTER_TINT = "tint";

        private static final int MIN_HUE = -180;
        private static final int MAX_HUE = 180;
        private static final int MIN_SATURATION = 0;
        private static final int MAX_SATURATION = 200;
        private static final int MIN_BRIGHTNESS = 0;
        private static final int MAX_BRIGHTNESS = 200;
        private static final int MIN_CONTRAST = -100;
        private static final int MAX_CONTRAST = 100;
        private static final int MIN_ALPHA = 0;
        private static final int MAX_ALPHA = 100;

        public static boolean parseIconFilter(XmlPullParser parser, Builder builder)
                throws IOException, XmlPullParserException {
            String tag = parser.getName();
            if (!TAG_FILTER.equals(tag)) return false;

            int attrCount = parser.getAttributeCount();
            String attrName;
            String attr = null;
            int intValue;
            while (attrCount-- > 0) {
                attrName = parser.getAttributeName(attrCount);
                if (attrName.equals("name")) {
                    attr = parser.getAttributeValue(attrCount);
                }
            }
            String content = parser.nextText();
            if (attr != null && content != null && content.length() > 0) {
                content = content.trim();
                if (FILTER_HUE.equalsIgnoreCase(attr)) {
                    intValue = clampValue(getInt(content, 0),MIN_HUE, MAX_HUE);
                    builder.hue(intValue);
                } else if (FILTER_SATURATION.equalsIgnoreCase(attr)) {
                    intValue = clampValue(getInt(content, 100),
                            MIN_SATURATION, MAX_SATURATION);
                    builder.saturate(intValue);
                } else if (FILTER_INVERT.equalsIgnoreCase(attr)) {
                    if ("true".equalsIgnoreCase(content)) {
                        builder.invertColors();
                    }
                } else if (FILTER_BRIGHTNESS.equalsIgnoreCase(attr)) {
                    intValue = clampValue(getInt(content, 100),
                            MIN_BRIGHTNESS, MAX_BRIGHTNESS);
                    builder.brightness(intValue);
                } else if (FILTER_CONTRAST.equalsIgnoreCase(attr)) {
                    intValue = clampValue(getInt(content, 0),
                            MIN_CONTRAST, MAX_CONTRAST);
                    builder.contrast(intValue);
                } else if (FILTER_ALPHA.equalsIgnoreCase(attr)) {
                    intValue = clampValue(getInt(content, 100), MIN_ALPHA, MAX_ALPHA);
                    builder.alpha(intValue);
                } else if (FILTER_TINT.equalsIgnoreCase(attr)) {
                    try {
                        intValue = Color.parseColor(content);
                        builder.tint(intValue);
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "Cannot apply tint, invalid argument: " + content);
                    }
                }
            }
            return true;
        }

        private static int getInt(String value, int defaultValue) {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private static int clampValue(int value, int min, int max) {
            return Math.min(max, Math.max(min, value));
        }

        /**
         * See the following links for reference
         * http://groups.google.com/group/android-developers/browse_thread/thread/9e215c83c3819953
         * http://gskinner.com/blog/archives/2007/12/colormatrix_cla.html
         * @param value
         */
        public static ColorMatrix adjustHue(float value) {
            ColorMatrix cm = new ColorMatrix();
            value = value / 180 * (float) Math.PI;
            if (value != 0) {
                float cosVal = (float) Math.cos(value);
                float sinVal = (float) Math.sin(value);
                float lumR = 0.213f;
                float lumG = 0.715f;
                float lumB = 0.072f;
                float[] mat = new float[]{
                        lumR + cosVal * (1 - lumR) + sinVal * (-lumR),
                        lumG + cosVal * (-lumG) + sinVal * (-lumG),
                        lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0, 0,
                        lumR + cosVal * (-lumR) + sinVal * (0.143f),
                        lumG + cosVal * (1 - lumG) + sinVal * (0.140f),
                        lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0, 0,
                        lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)),
                        lumG + cosVal * (-lumG) + sinVal * (lumG),
                        lumB + cosVal * (1 - lumB) + sinVal * (lumB), 0, 0,
                        0, 0, 0, 1, 0,
                        0, 0, 0, 0, 1};
                cm.set(mat);
            }
            return cm;
        }

        public static ColorMatrix adjustSaturation(float saturation) {
            saturation = saturation / 100;
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(saturation);

            return cm;
        }

        public static ColorMatrix invertColors() {
            float[] matrix = {
                    -1, 0, 0, 0, 255, //red
                    0, -1, 0, 0, 255, //green
                    0, 0, -1, 0, 255, //blue
                    0, 0, 0, 1, 0 //alpha
            };

            return new ColorMatrix(matrix);
        }

        public static ColorMatrix adjustBrightness(float brightness) {
            brightness = brightness / 100;
            ColorMatrix cm = new ColorMatrix();
            cm.setScale(brightness, brightness, brightness, 1);

            return cm;
        }

        public static ColorMatrix adjustContrast(float contrast) {
            contrast = contrast / 100 + 1;
            float o = (-0.5f * contrast + 0.5f) * 255;
            float[] matrix = {
                    contrast, 0, 0, 0, o, //red
                    0, contrast, 0, 0, o, //green
                    0, 0, contrast, 0, o, //blue
                    0, 0, 0, 1, 0 //alpha
            };

            return new ColorMatrix(matrix);
        }

        public static ColorMatrix adjustAlpha(float alpha) {
            alpha = alpha / 100;
            ColorMatrix cm = new ColorMatrix();
            cm.setScale(1, 1, 1, alpha);

            return cm;
        }

        public static ColorMatrix applyTint(int color) {
            float alpha = Color.alpha(color) / 255f;
            float red = Color.red(color) * alpha;
            float green = Color.green(color) * alpha;
            float blue = Color.blue(color) * alpha;

            float[] matrix = {
                    1, 0, 0, 0, red, //red
                    0, 1, 0, 0, green, //green
                    0, 0, 1, 0, blue, //blue
                    0, 0, 0, 1, 0 //alpha
            };

            return new ColorMatrix(matrix);
        }

        public static class Builder {
            private List<ColorMatrix> mMatrixList;

            public Builder() {
                mMatrixList = new ArrayList<ColorMatrix>();
            }

            public Builder hue(float value) {
                mMatrixList.add(adjustHue(value));
                return this;
            }

            public Builder saturate(float saturation) {
                mMatrixList.add(adjustSaturation(saturation));
                return this;
            }

            public Builder brightness(float brightness) {
                mMatrixList.add(adjustBrightness(brightness));
                return this;
            }

            public Builder contrast(float contrast) {
                mMatrixList.add(adjustContrast(contrast));
                return this;
            }

            public Builder alpha(float alpha) {
                mMatrixList.add(adjustAlpha(alpha));
                return this;
            }

            public Builder invertColors() {
                mMatrixList.add(ColorFilterUtils.invertColors());
                return this;
            }

            public Builder tint(int color) {
                mMatrixList.add(applyTint(color));
                return this;
            }

            public ColorMatrix build() {
                if (mMatrixList == null || mMatrixList.size() == 0) return null;

                ColorMatrix colorMatrix = new ColorMatrix();
                for (ColorMatrix cm : mMatrixList) {
                    colorMatrix.postConcat(cm);
                }
                return colorMatrix;
            }
        }
    }
}
