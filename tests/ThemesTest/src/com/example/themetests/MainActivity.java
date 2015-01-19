/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.themetests;

import android.app.Activity;
import android.app.ComposedIconInfo;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.ThemeConfig;
import android.database.Cursor;
import android.os.Bundle;

import android.os.ServiceManager;
import android.provider.ThemesContract.ThemesColumns;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private ListView mThemeList;
    private Button mDetachButton;
    private ImageView mImage;
    private ImageView mIcon;

    private Resources mResources;
    private AssetManager mAssets;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mThemeList = (ListView) findViewById(R.id.theme_list);
        mDetachButton = (Button) findViewById(R.id.detach_assets);
        mDetachButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detachThemeAssets(mResources, mAssets);
                mThemeList.setEnabled(true);
                mDetachButton.setEnabled(false);
                updateImages();
            }
        });
        mImage = (ImageView) findViewById(R.id.image);
        mIcon = (ImageView) findViewById(R.id.icon);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PackageManager pm = getPackageManager();
        Context ctx = null;
        try {
            ctx = createPackageContext("com.android.systemui", 0);
            mAssets = ctx.getAssets();
            mResources = ctx.getResources();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        updateImages();
        loadThemes();
    }

    private void loadThemes() {
        String[] columns = {ThemesColumns._ID, ThemesColumns.TITLE, ThemesColumns.PKG_NAME};
        String selection = ThemesColumns.PRESENT_AS_THEME + "=? AND " +
                ThemesColumns.PKG_NAME + "<>?";
        String[] selectionArgs = {"1", ThemeConfig.SYSTEM_DEFAULT};
        Cursor c = getContentResolver().query(ThemesColumns.CONTENT_URI, columns, selection,
                selectionArgs, null);
        if (c != null) {
            ThemeAdapter adapter = new ThemeAdapter(this, c, 0);
            mThemeList.setAdapter(adapter);
            mThemeList.setOnItemClickListener(mThemeClicked);
        }
    }

    private boolean attachThemeAssets(Resources res, AssetManager assets, String pkgName) {
        final PackageManager pm = getPackageManager();
        PackageInfo piTheme = null;
        PackageInfo piAndroid = null;
        PackageInfo piTarget = null;
        PackageInfo piIcon = null;

        String basePackageName = null;
        int count = assets.getBasePackageCount();
        if (count > 1) {
            basePackageName  = assets.getBasePackageName(1);
        } else if (count == 1) {
            basePackageName  = assets.getBasePackageName(0);
        } else {
            return false;
        }

        try {
            piTheme = pm.getPackageInfo(pkgName, 0);
            piAndroid = getPackageManager().getPackageInfo("android", 0);
            piTarget = getPackageManager().getPackageInfo(basePackageName, 0);
            piIcon = getPackageManager().getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (piTheme == null || piTheme.applicationInfo == null ||
                piAndroid == null || piAndroid.applicationInfo == null ||
                piTheme.mOverlayTargets == null) {
            return false;
        }

        String themePackageName = pkgName;
        String themePath = piTheme.applicationInfo.publicSourceDir;
        if (!piTarget.isThemeApk && piTheme.mOverlayTargets.contains(basePackageName)) {
            String targetPackagePath = piTarget.applicationInfo.sourceDir;
            String prefixPath = ThemeUtils.getOverlayPathToTarget(basePackageName);

            String resCachePath = ThemeUtils.getTargetCacheDir(basePackageName, piTheme);
            String resTablePath = resCachePath + "/resources.arsc";
            String resApkPath = resCachePath + "/resources.apk";
            int cookie = assets.addOverlayPath(themePath, resTablePath, resApkPath,
                    targetPackagePath, prefixPath);

            if (cookie != 0) {
                assets.setThemePackageName(themePackageName);
                assets.addThemeCookie(cookie);
            }
        }

        if (!piTarget.isThemeApk && piTheme.mOverlayTargets.contains("android")) {
            String resCachePath= ThemeUtils.getTargetCacheDir(piAndroid.packageName, piTheme);
            String prefixPath = ThemeUtils.getOverlayPathToTarget(piAndroid.packageName);
            String targetPackagePath = piAndroid.applicationInfo.publicSourceDir;
            String resTablePath = resCachePath + "/resources.arsc";
            String resApkPath = resCachePath + "/resources.apk";
            int cookie = assets.addOverlayPath(themePath, resTablePath,
                    resApkPath, targetPackagePath, prefixPath);
            if (cookie != 0 && !assets.getThemeCookies().contains(cookie)) {
                assets.setThemePackageName(themePackageName);
                assets.addThemeCookie(cookie);
            }
        }

        if (piIcon != null) {
            String themeIconPath =  piIcon.applicationInfo.publicSourceDir;
            String prefixPath = ThemeUtils.ICONS_PATH;
            String iconDir = ThemeUtils.getIconPackDir(pkgName);
            String resTablePath = iconDir + "/resources.arsc";
            String resApkPath = iconDir + "/resources.apk";

            // Legacy Icon packs have everything in their APK
            if (piIcon.isLegacyIconPackApk) {
                prefixPath = "";
                resApkPath = "";
                resTablePath = "";
            }

            int cookie = assets.addIconPath(themeIconPath, resTablePath, resApkPath, prefixPath,
                    Resources.THEME_ICON_PKG_ID);
            if (cookie != 0) {
                assets.setIconPackCookie(cookie);
                assets.setIconPackageName(pkgName);
                setActivityIcons(res);
            }
        }

        res.updateStringCache();
        return true;
    }

    private void detachThemeAssets(Resources res, AssetManager assets) {
        String themePackageName = assets.getThemePackageName();
        String iconPackageName = assets.getIconPackageName();
        String commonResPackageName = assets.getCommonResPackageName();

        //Remove Icon pack if it exists
        if (!TextUtils.isEmpty(iconPackageName) && assets.getIconPackCookie() > 0) {
            assets.removeOverlayPath(iconPackageName, assets.getIconPackCookie());
            assets.setIconPackageName(null);
            assets.setIconPackCookie(0);
        }
        //Remove common resources if it exists
        if (!TextUtils.isEmpty(commonResPackageName) && assets.getCommonResCookie() > 0) {
            assets.removeOverlayPath(commonResPackageName, assets.getCommonResCookie());
            assets.setCommonResPackageName(null);
            assets.setCommonResCookie(0);
        }
        final List<Integer> themeCookies = assets.getThemeCookies();
        if (!TextUtils.isEmpty(themePackageName) && !themeCookies.isEmpty()) {
            // remove overlays in reverse order
            for (int i = themeCookies.size() - 1; i >= 0; i--) {
                assets.removeOverlayPath(themePackageName, themeCookies.get(i));
            }
        }
        assets.getThemeCookies().clear();
        assets.setThemePackageName(null);
        //res.updateStringCache();
    }

    private void setActivityIcons(Resources r, String themePkgName) {
        SparseArray<PackageItemInfo> iconResources = new SparseArray<PackageItemInfo>();
        String pkgName = r.getAssets().getAppName();
        PackageInfo pkgInfo = null;
        ApplicationInfo appInfo = null;

        try {
            pkgInfo = getPackageManager().getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        final ThemeConfig themeConfig = r.getConfiguration().themeConfig;
        if (pkgName != null && themeConfig != null &&
                pkgName.equals(themeConfig.getIconPackPkgName())) {
            return;
        }

        //Map application icon
        if (pkgInfo != null && pkgInfo.applicationInfo != null) {
            appInfo = pkgInfo.applicationInfo;
            if (appInfo.themedIcon != 0 || iconResources.get(appInfo.icon) == null) {
                iconResources.put(appInfo.icon, appInfo);
            }
        }

        //Map activity icons.
        if (pkgInfo != null && pkgInfo.activities != null) {
            for (ActivityInfo ai : pkgInfo.activities) {
                if (ai.icon != 0 && (ai.themedIcon != 0 || iconResources.get(ai.icon) == null)) {
                    iconResources.put(ai.icon, ai);
                } else if (appInfo != null && appInfo.icon != 0 &&
                        (ai.themedIcon != 0 || iconResources.get(appInfo.icon) == null)) {
                    iconResources.put(appInfo.icon, ai);
                }
            }
        }

        r.setIconResources(iconResources);
        final IPackageManager pm = IPackageManager.Stub.asInterface(
                ServiceManager.getService("package"));
        try {
            ComposedIconInfo iconInfo = pm.getComposedIconInfo();
            r.setComposedIconInfo(iconInfo);
        } catch (Exception e) {
        }
    }

    AdapterView.OnItemClickListener mThemeClicked = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String pkgName = (String) view.getTag();
            if (attachThemeAssets(mResources, mAssets, pkgName)) {
                mThemeList.setEnabled(false);
                mDetachButton.setEnabled(true);
                updateImages();
            }
        }
    };

    private void updateImages() {
        int resId = mResources.getIdentifier("ic_sysbar_home", "drawable", "com.android.systemui");
        if (resId != 0) {
            mImage.setImageDrawable(mResources.getDrawable(resId));
        }
        resId = mResources.getIdentifier("icon", "drawable", "com.android.systemui");
        if (resId != 0) {
            mIcon.setImageDrawable(mResources.getDrawable(resId));
        }
    }

    class ThemeAdapter extends CursorAdapter {
        public ThemeAdapter(Context context, Cursor c, int flags) {
            super(context, c, flags);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = getLayoutInflater();
            View v = inflater.inflate(R.layout.theme_list_item, parent, false);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            int titleIdx = cursor.getColumnIndex(ThemesColumns.TITLE);
            int pkgIdx = cursor.getColumnIndex(ThemesColumns.PKG_NAME);
            String title = cursor.getString(titleIdx);
            String pkgName = cursor.getString(pkgIdx);
            TextView tv = (TextView) view.findViewById(R.id.theme_title);
            tv.setText(title);
            view.setTag(pkgName);
        }
    }
}
