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
import com.android.internal.util.XmlUtils;
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

    public ColorMatrix loadColorFilters(Resources res, String packageName) {
        XmlPullParser parser = null;
        InputStream inputStream = null;

        try {
            inputStream = res.getAssets().open("icon_filters.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");
        } catch (Exception e) {
            // Catch any exception since we want to fall back to parsing the xml/
            // resource in all cases
            int resId = res.getIdentifier("icon_filters", "xml", packageName);
            if (resId != 0) {
                parser = res.getXml(resId);
            }
        }

        if (parser != null) {
            try {
                ColorMatrix cm = ColorFilterUtils.loadFilters(parser);
                return cm;
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
        return null;
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
            mComposedIconInfo.colorFilter = null;
        } else {
            mIconBackCount = 0;
            Resources res = createIconResource(mContext, packageName);
            mIconPackResourceMap = getIconResMapFromXml(res, packageName);
            mLoadedIconPackResource = res;
            mLoadedIconPackName = packageName;
            loadComposedIconComponents();
            ColorMatrix cm = loadColorFilters(res, packageName);
            if (cm != null) {
                mComposedIconInfo.colorFilter = cm.getArray().clone();
            }
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
                Drawable back = null;
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

        private static Bitmap createIconBitmap(Drawable icon, Resources res, Drawable iconBack,
                                               Drawable iconMask, Drawable iconUpon, float scale,
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
            if (iconMask != null) {
                iconMask.setBounds(icon.getBounds());
                ((BitmapDrawable) iconMask).getPaint().setXfermode(
                        new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                iconMask.draw(canvas);
            }
            // Draw the iconBacks if not null and then the original (scaled and masked) icon on top
            if (iconBack != null) {
                iconBack.setBounds(icon.getBounds());
                Paint p = ((BitmapDrawable) iconBack).getPaint();
                if (colorFilter != null) {
                    p.setColorFilter(new ColorMatrixColorFilter(colorFilter));
                } else {
                    p.setColorFilter(null);
                }
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
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

    public static class ColorFilterUtils {
        private static final String TAG_FILTER = "filter";

        public static ColorMatrix loadFilters(XmlPullParser xpp)
                throws IOException, XmlPullParserException {
            int eventType;
            int intValue;
            String tag = null;
            String attrName = null;
            String attr = null;

            Builder builder = new Builder();
            eventType = xpp.next();
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT)
                eventType = xpp.next();

            if (eventType != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found!");
            }
            while ((eventType = xpp.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    tag = xpp.getName().trim();
                    int attrCount = xpp.getAttributeCount();
                    while (attrCount-- > 0) {
                        attrName = xpp.getAttributeName(attrCount);
                        if (attrName.equals("name"))
                            attr = xpp.getAttributeValue(attrCount);
                    }
                    String content = xpp.nextText();
                    if (attr != null && content != null && content.length() > 0) {
                        content = content.trim();
                        if (TAG_FILTER.equals(tag)) {
                            if ("hue".equals(attr)) {
                                intValue = getInt(content, 0);
                                builder.hue(intValue);
                            } else if ("saturation".equals(attr)) {
                                intValue = getInt(content, 100);
                                builder.saturate(intValue);
                            } else if ("invert".equals(attr)) {
                                if ("true".equals(content)) {
                                    builder.invertColors();
                                }
                            } else if ("brightness".equals(attr)) {
                                intValue = getInt(content, 100);
                                builder.brightness(intValue);
                            } else if ("contrast".equals(attr)) {
                                intValue = getInt(content, 0);
                                builder.contrast(intValue);
                            } else if ("alpha".equals(attr)) {
                                intValue = getInt(content, 100);
                                builder.alpha(intValue);
                            } else if ("tint".equals(attr)) {
                                intValue = XmlUtils.convertValueToUnsignedInt(content, 0);
                                builder.tint(intValue);
                            }
                        }
                    }
                }
            }
            return builder.build();
        }

        private static int getInt(String value, int defaultValue) {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        public static ColorMatrix adjustHue(float value) {
            ColorMatrix cm = new ColorMatrix();
            adjustHue(cm, value);
            return cm;
        }

        /**
         * See the following links for reference
         * http://groups.google.com/group/android-developers/browse_thread/thread/9e215c83c3819953
         * http://gskinner.com/blog/archives/2007/12/colormatrix_cla.html
         * @param cm
         * @param value
         */
        public static void adjustHue(ColorMatrix cm, float value) {
            value = clampValue(value, -180f, 180f) / 180f * (float) Math.PI;
            if (value == 0) {
                return;
            }
            float cosVal = (float) Math.cos(value);
            float sinVal = (float) Math.sin(value);
            float lumR = 0.213f;
            float lumG = 0.715f;
            float lumB = 0.072f;
            float[] mat = new float[] {
                    lumR + cosVal * (1 - lumR) + sinVal * (-lumR),
                    lumG + cosVal * (-lumG) + sinVal * (-lumG),
                    lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0, 0,
                    lumR + cosVal * (-lumR) + sinVal * (0.143f),
                    lumG + cosVal * (1 - lumG) + sinVal * (0.140f),
                    lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0, 0,
                    lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)),
                    lumG + cosVal * (-lumG) + sinVal * (lumG),
                    lumB + cosVal * (1 - lumB) + sinVal * (lumB), 0, 0,
                    0f, 0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 0f, 1f };
            cm.postConcat(new ColorMatrix(mat));
        }

        private static float clampValue(float value, float min, float max) {
            return Math.min(max, Math.max(min, value));
        }

        public static ColorMatrix adjustSaturation(float saturation) {
            saturation = Math.min(Math.max(saturation / 100f, 0f), 2f);
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(saturation);

            return cm;
        }

        public static ColorMatrix invertColors() {
            float[] colorMatrix_Negative = {
                    -1.0f, 0, 0, 0, 255, //red
                    0, -1.0f, 0, 0, 255, //green
                    0, 0, -1.0f, 0, 255, //blue
                    0, 0, 0, 1.0f, 0 //alpha
            };

            return new ColorMatrix(colorMatrix_Negative);
        }

        public static ColorMatrix adjustBrightness(float brightness) {
            brightness = Math.min(Math.max(brightness / 100f, 0f), 1f);
            ColorMatrix cm = new ColorMatrix();
            cm.setScale(brightness, brightness, brightness, 1f);

            return cm;
        }

        public static ColorMatrix adjustContrast(float contrast) {
            contrast = Math.min(Math.max(contrast / 100f, 0f), 1f) + 1f;
            float o = (-0.5f * contrast + 0.5f) * 255f;
            float[] colorMatrix_Contrast = {
                    contrast, 0, 0, 0, o, //red
                    0, contrast, 0, 0, o, //green
                    0, 0, contrast, 0, o, //blue
                    0, 0, 0, 1.0f, 0 //alpha
            };

            return new ColorMatrix(colorMatrix_Contrast);
        }

        public static ColorMatrix adjustAlpha(float alpha) {
            alpha = Math.min(Math.max(alpha / 100f, 0f), 1f);
            ColorMatrix cm = new ColorMatrix();
            cm.setScale(1f, 1f, 1f, alpha);

            return cm;
        }

        public static ColorMatrix applyTint(int color) {
            float alpha = ((color >> 24) & 0xff) / 255f;
            float red = ((color >> 16) & 0xff) * alpha;
            float green = ((color >> 8) & 0xff) * alpha;
            float blue = (color & 0xff) * alpha;
            float rscale = red / 255f;
            float gscale = green / 255f;
            float bscale = blue / 255f;

            float[] colorMatrix_Tint = {
                    1f + rscale, 0, 0, 0, red, //red
                    0, 1f + gscale, 0, 0, green, //green
                    0, 0, 1f + bscale, 0, blue, //blue
                    0, 0, 0, 1.0f, 0 //alpha
            };

            return new ColorMatrix(colorMatrix_Tint);
        }

        public static class Builder {
            private ColorMatrix mColorMatrix;

            public Builder() {
                mColorMatrix = new ColorMatrix();
            }

            public Builder hue(float value) {
                mColorMatrix.postConcat(adjustHue(value));
                return this;
            }

            public Builder saturate(float saturation) {
                mColorMatrix.postConcat(adjustSaturation(saturation));
                return this;
            }

            public Builder brightness(float brightness) {
                mColorMatrix.postConcat(adjustBrightness(brightness));
                return this;
            }

            public Builder contrast(float contrast) {
                mColorMatrix.postConcat(adjustContrast(contrast));
                return this;
            }

            public Builder alpha(float alpha) {
                mColorMatrix.postConcat(adjustAlpha(alpha));
                return this;
            }

            public Builder invertColors() {
                mColorMatrix.postConcat(ColorFilterUtils.invertColors());
                return this;
            }

            public Builder tint(int color) {
                mColorMatrix.postConcat(applyTint(color));
                return this;
            }

            public ColorMatrix build() {
                return mColorMatrix;
            }
        }
    }
}
