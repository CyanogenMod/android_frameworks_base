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

import android.content.pm.PackageInfo;
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

    private final Context mContext;
    private Map<ComponentName, String> mIconPackResourceMap;
    private String mLoadedIconPackName;
    private Resources mLoadedIconPackResource;
    private float mIconScale;

    public IconPackHelper(Context context) {
        mContext = context;
        mIconPackResourceMap = new HashMap<ComponentName, String>();
    }

    private static void loadResourcesFromXmlParser(XmlPullParser parser,
            Map<ComponentName, String> iconPackResources) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {

            if (eventType != XmlPullParser.START_TAG) {
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
        } else {
            Resources res = createIconResource(mContext, packageName);
            mIconPackResourceMap = getIconResMapFromXml(res, packageName);
            mLoadedIconPackResource = res;
            mLoadedIconPackName = packageName;
            String scale = mIconPackResourceMap.get(ICON_SCALE_TAG);
            if (scale != null) {
                try {
                    mIconScale = Float.valueOf(scale);
                } catch (NumberFormatException e) {
                }
            }

        }
    }

    public float getIconScale() {
        return mIconScale;
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

    public static Map<ComponentName, String> getIconResMapFromXml(Resources res, String packageName) {
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
        ComponentName compName = new ComponentName(info.packageName.toLowerCase(), info.name.toLowerCase());
        String drawable = mIconPackResourceMap.get(compName);
        if (drawable == null) {
            // Icon pack doesn't have an icon for the activity, fallback to package icon
            compName = new ComponentName(info.packageName.toLowerCase(), "");
            drawable = mIconPackResourceMap.get(compName);
            if (drawable == null) {
                return 0;
            }
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
}
