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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.PaintDrawable;
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
    private static final String ICON_MASK_TAG = "iconmask";
    private static final String ICON_BACK_TAG = "iconback";
    private static final String ICON_UPON_TAG = "iconupon";
    private static final String ICON_SCALE_TAG = "scale";
    private static final String ICON_BACK_FORMAT = "iconback%d";

    private static final ComponentName ICON_BACK_COMPONENT;
    private static final ComponentName ICON_MASK_COMPONENT;
    private static final ComponentName ICON_UPON_COMPONENT;

    private static final float DEFAULT_SCALE = 1.0f;

    private final Context mContext;
    private Map<ComponentName, String> mIconPackResourceMap;
    private String mLoadedIconPackName;
    private Resources mLoadedIconPackResource;
    private BitmapDrawable mIconUpon, mIconMask;
    private BitmapDrawable[] mIconBack;
    private float mIconScale;
    private int mIconDensity;
    private int mIconSize;
    private int mIconBackCount = 0;

    static {
        ICON_BACK_COMPONENT = new ComponentName(ICON_BACK_TAG, "");
        ICON_MASK_COMPONENT = new ComponentName(ICON_MASK_TAG, "");
        ICON_UPON_COMPONENT = new ComponentName(ICON_UPON_TAG, "");
    }

    public IconPackHelper(Context context) {
        mContext = context;
        mIconPackResourceMap = new HashMap<ComponentName, String>();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mIconSize = am.getLauncherLargeIconSize();
        mIconDensity = am.getLauncherLargeIconDensity();
    }

    private void loadResourcesFromXmlParser(XmlPullParser parser,
            Map<ComponentName, String> iconPackResources) throws XmlPullParserException, IOException {
        mIconBackCount = 0;
        int eventType = parser.getEventType();
        do {

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }

            String tag = parser.getName();
            if (tag.equalsIgnoreCase(ICON_MASK_TAG) ||
                    tag.equalsIgnoreCase(ICON_BACK_TAG) ||
                    tag.equalsIgnoreCase(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() >= 1) {
                        if (tag.equalsIgnoreCase(ICON_BACK_TAG)) {
                            mIconBackCount = parser.getAttributeCount();
                            for (int i = 0; i < mIconBackCount; i++) {
                                tag = String.format(ICON_BACK_FORMAT, i);
                                icon = parser.getAttributeValue(i);
                                iconPackResources.put(new ComponentName(tag, ""),
                                        icon);
                            }
                            continue;
                        } else {
                            icon = parser.getAttributeValue(0);
                        }
                    }
                }
                iconPackResources.put(new ComponentName(parser.getName().toLowerCase(), ""), icon);
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

    public void loadIconPack(String packageName) throws NameNotFoundException {
        if (packageName == null) {
            mLoadedIconPackResource = null;
            mLoadedIconPackName = null;
            mIconBack = null;
            mIconMask = mIconUpon = null;
            mIconScale = 0;
        } else {
            mIconBackCount = 0;
            Resources res = createIconResource(mContext, packageName);
            mIconPackResourceMap = getIconResMapFromXml(res, packageName);
            mLoadedIconPackResource = res;
            mLoadedIconPackName = packageName;
            loadComposedIconComponents();
        }
    }

    public Drawable[] getIconBackDrawable() {
        return mIconBack;
    }

    public Drawable getIconMaskDrawable() {
        return mIconMask;
    }

    public Drawable getIconUponDrawable() {
        return mIconUpon;
    }

    public Bitmap[] getIconBackBitmap() {
        Bitmap[] bmps = new Bitmap[mIconBackCount];
        for (int i = 0; i < mIconBackCount; i++) {
            bmps[i] = getDrawableBitmap(mIconBack[i]);
        }
        return bmps;
    }

    public Bitmap getIconMaskBitmap() {
        return getDrawableBitmap(mIconMask);
    }

    public Bitmap getIconUponBitmap() {
        return getDrawableBitmap(mIconUpon);
    }

    public float getIconScale() {
        return mIconScale;
    }

    public int getIconSize() {
        return mIconSize;
    }

    public int getIconDensity() {
        return mIconDensity;
    }

    private Bitmap getDrawableBitmap(BitmapDrawable drawable) {
        return drawable != null ? drawable.getBitmap() : null;
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

    private void loadComposedIconComponents() {
        mIconMask = (BitmapDrawable) getDrawableForName(ICON_MASK_COMPONENT);
        mIconUpon = (BitmapDrawable) getDrawableForName(ICON_UPON_COMPONENT);
        if (mIconPackResourceMap.containsKey(ICON_BACK_COMPONENT)) {
            mIconBack = new BitmapDrawable[1];
            mIconBack[0] = (BitmapDrawable) getDrawableForName(ICON_BACK_COMPONENT);
            mIconBackCount = 1;
        } else if (mIconBackCount > 0) {
            mIconBack = new BitmapDrawable[mIconBackCount];
            for (int i = 0; i < mIconBackCount; i++) {
                mIconBack[i] = (BitmapDrawable) getDrawableForName(new ComponentName(
                        String.format(ICON_BACK_FORMAT, i), ""));
            }
        }

        String scale = mIconPackResourceMap.get(ICON_SCALE_TAG);
        if (scale != null) {
            try {
                mIconScale = Float.valueOf(scale);
            } catch (NumberFormatException e) {
                mIconScale = DEFAULT_SCALE;
            }
        } else {
            mIconScale = DEFAULT_SCALE;
        }
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
        return mLoadedIconPackResource.getDrawable(id);
    }

    public Drawable getDrawableForActivityWithDensity(ActivityInfo info, int density) {
        int id = getResourceIdForActivityIcon(info);
        if (id == 0) return null;
        return mLoadedIconPackResource.getDrawableForDensity(id, density);
    }

    public static class IconCustomizer {
        private static Bitmap createIconBitmap(Drawable icon, Resources res, Drawable iconBack,
                                       Drawable iconMask, Drawable iconUpon, float scale,
                                       int iconSize) {
            if (iconSize <= 0) return null;

            int width = iconSize;
            int height = iconSize;
            final Canvas canvas = new Canvas();

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(res.getDisplayMetrics());
                }
                canvas.setDensity(bitmap.getDensity());
            }

            // no intrinsic size --> use default size
            int textureWidth = width;
            int textureHeight = height;

            Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;

            Rect oldBounds = new Rect();
            oldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            canvas.save();
            canvas.scale(scale, scale, width / 2, height/2);
            icon.draw(canvas);
            canvas.restore();
            if (iconMask != null) {
                iconMask.setBounds(icon.getBounds());
                ((BitmapDrawable) iconMask).getPaint().setXfermode(
                        new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                iconMask.draw(canvas);
            }
            if (iconBack != null) {
                canvas.setBitmap(null);
                Bitmap finalBitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                        Bitmap.Config.ARGB_8888);
                canvas.setBitmap(finalBitmap);
                iconBack.setBounds(icon.getBounds());
                iconBack.draw(canvas);
                canvas.drawBitmap(bitmap, null, icon.getBounds(), null);
                bitmap = finalBitmap;
            }
            if (iconUpon != null) {
                iconUpon.draw(canvas);
            }
            icon.setBounds(oldBounds);
            bitmap.setDensity(canvas.getDensity());
            canvas.setBitmap(null);

            return bitmap;
        }

        public static Drawable getComposedIconDrawable(Drawable icon, Context context,
                                                       Drawable[] iconBack, Drawable iconMask,
                                                       Drawable iconUpon, float scale,
                                                       int iconSize) {
            final Resources res = context.getResources();
            return getComposedIconDrawable(icon, res, iconBack, iconMask, iconUpon, scale,
                    iconSize);
        }

        public static Drawable getComposedIconDrawable(Drawable icon, Resources res,
                                                       Drawable[] iconBack, Drawable iconMask,
                                                       Drawable iconUpon, float scale,
                                                       int iconSize) {
            Drawable back = null;
            Random r = new Random();
            if (iconBack != null && iconBack.length > 0) {
                back = iconBack[r.nextInt(iconBack.length)];
            }
            Bitmap bmp = createIconBitmap(icon, res, back, iconMask, iconUpon, scale,
                    iconSize);
            return bmp != null ? new BitmapDrawable(res, bmp): null;
        }
    }
}
