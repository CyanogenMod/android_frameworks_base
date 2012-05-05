package com.android.server;

import com.android.internal.app.IAssetRedirectionManager;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ThemeInfo;
import android.content.res.AssetManager;
import android.content.res.PackageRedirectionMap;
import android.content.res.Resources;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class AssetRedirectionManagerService extends IAssetRedirectionManager.Stub {
    private static final String TAG = "AssetRedirectionManager";

    private final Context mContext;

    /*
     * TODO: This data structure should have some way to expire very old cache
     * entries. Would be nice to optimize for the removal path as well.
     */
    private final HashMap<RedirectionKey, PackageRedirectionMap> mRedirections =
            new HashMap<RedirectionKey, PackageRedirectionMap>();

    public AssetRedirectionManagerService(Context context) {
        mContext = context;
    }

    @Override
    public void clearRedirectionMapsByTheme(String themePackageName, String themeId)
            throws RemoteException {
        synchronized (mRedirections) {
            Set<RedirectionKey> keys = mRedirections.keySet();
            Iterator<RedirectionKey> iter = keys.iterator();
            while (iter.hasNext()) {
                RedirectionKey key = iter.next();
                if (themePackageName.equals(key.themePackageName) &&
                        (themeId == null || themeId.equals(key.themeId))) {
                    iter.remove();
                }
            }
        }
    }

    @Override
    public void clearPackageRedirectionMap(String targetPackageName) throws RemoteException {
        synchronized (mRedirections) {
            Set<RedirectionKey> keys = mRedirections.keySet();
            Iterator<RedirectionKey> iter = keys.iterator();
            while (iter.hasNext()) {
                RedirectionKey key = iter.next();
                if (targetPackageName.equals(key.targetPackageName)) {
                    iter.remove();
                }
            }
        }
    }

    @Override
    public PackageRedirectionMap getPackageRedirectionMap(String themePackageName,
            String themeId, String targetPackageName) throws RemoteException {
        synchronized (mRedirections) {
            RedirectionKey key = new RedirectionKey();
            key.themePackageName = themePackageName;
            key.themeId = themeId;
            key.targetPackageName = targetPackageName;

            PackageRedirectionMap map = mRedirections.get(key);
            if (map != null) {
                return map;
            } else {
                map = generatePackageRedirectionMap(key);
                if (map != null) {
                    mRedirections.put(key, map);
                }
                return map;
            }
        }
    }

    private PackageRedirectionMap generatePackageRedirectionMap(RedirectionKey key) {
        AssetManager assets = new AssetManager();

        boolean frameworkAssets = key.targetPackageName.equals("android");

        if (!frameworkAssets) {
            PackageInfo pi = getPackageInfo(mContext, key.targetPackageName);
            if (pi == null || pi.applicationInfo == null ||
                    assets.addAssetPath(pi.applicationInfo.publicSourceDir) == 0) {
                Log.w(TAG, "Unable to attach target package assets for " + key.targetPackageName);
                return null;
            }
        }

        PackageInfo pi = getPackageInfo(mContext, key.themePackageName);
        if (pi == null || pi.applicationInfo == null || pi.themeInfos == null ||
                assets.addAssetPath(pi.applicationInfo.publicSourceDir) == 0) {
            Log.w(TAG, "Unable to attach theme package assets from " + key.themePackageName);
            return null;
        }

        PackageRedirectionMap resMap = new PackageRedirectionMap();

        /*
         * Apply a special redirection hack for the highest level <style>
         * replacing @android:style/Theme.
         */
        if (frameworkAssets) {
            int themeResourceId = findThemeResourceId(pi.themeInfos, key.themeId);
            assets.generateStyleRedirections(resMap.getNativePointer(), android.R.style.Theme,
                    themeResourceId);
        }

        Resources res = new Resources(assets, null, null);
        generateExplicitRedirections(resMap, res, key.themePackageName, key.targetPackageName);

        return resMap;
    }

    private void generateExplicitRedirections(PackageRedirectionMap resMap, Resources res,
            String themePackageName, String targetPackageName) {
        /*
         * XXX: We should be parsing the <theme> tag's <meta-data>! Instead,
         * we're just assuming that res/xml/<package>.xml exists and describes
         * the redirects we want!
         */
        String redirectXmlName = targetPackageName.replace('.', '_');
        int redirectXmlResId = res.getIdentifier(redirectXmlName, "xml", themePackageName);
        if (redirectXmlResId == 0) {
            return;
        }

        ResourceRedirectionsProcessor processor = new ResourceRedirectionsProcessor(res,
                redirectXmlResId, themePackageName, targetPackageName, resMap);
        processor.process();
    }

    private static PackageInfo getPackageInfo(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Searches for the high-level theme resource id for the specific
     * &lt;theme&gt; tag being applied.
     * <p>
     * An individual theme package can contain multiple &lt;theme&gt; tags, each
     * representing a separate theme choice from the user's perspective, even
     * though the most common case is for there to be only 1.
     *
     * @return The style resource id or 0 if no match was found.
     */
    private static int findThemeResourceId(ThemeInfo[] themeInfos, String needle) {
        if (themeInfos != null && !TextUtils.isEmpty(needle)) {
            int n = themeInfos.length;
            for (int i = 0; i < n; i++) {
                ThemeInfo info = themeInfos[i];
                if (needle.equals(info.themeId)) {
                    return info.styleResourceId;
                }
            }
        }
        return 0;
    }

    private static Resources getUnredirectedResourcesForPackage(Context context, String packageName) {
        AssetManager assets = new AssetManager();

        if (!packageName.equals("android")) {
            PackageInfo pi = getPackageInfo(context, packageName);
            if (pi == null || pi.applicationInfo == null ||
                    assets.addAssetPath(pi.applicationInfo.publicSourceDir) == 0) {
                Log.w(TAG, "Unable to get resources for package " + packageName);
                return null;
            }
        }

        return new Resources(assets, null, null);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mRedirections) {
            final ArrayList<RedirectionKey> filteredKeySet = new ArrayList<RedirectionKey>();
            for (Map.Entry<RedirectionKey, PackageRedirectionMap> entry: mRedirections.entrySet()) {
                PackageRedirectionMap map = entry.getValue();
                if (map != null && map.getPackageId() != -1) {
                    filteredKeySet.add(entry.getKey());
                }
            }
            Collections.sort(filteredKeySet, new Comparator<RedirectionKey>() {
                @Override
                public int compare(RedirectionKey a, RedirectionKey b) {
                    int comp = a.themePackageName.compareTo(b.themePackageName);
                    if (comp != 0) {
                        return comp;
                    }
                    comp = a.themeId.compareTo(b.themeId);
                    if (comp != 0) {
                        return comp;
                    }
                    return a.targetPackageName.compareTo(b.targetPackageName);
                }
            });

            pw.println("Theme asset redirections:");
            String lastPackageName = null;
            String lastId = null;
            Resources themeRes = null;
            for (RedirectionKey key: filteredKeySet) {
                if (lastPackageName == null || !lastPackageName.equals(key.themePackageName)) {
                    pw.println("* Theme package " + key.themePackageName + ":");
                    lastPackageName = key.themePackageName;
                    themeRes = getUnredirectedResourcesForPackage(mContext, key.themePackageName);
                }
                if (lastId == null || !lastId.equals(key.themeId)) {
                    pw.println("  theme id #" + key.themeId + ":");
                    lastId = key.themeId;
                }
                pw.println("    " + key.targetPackageName + ":");
                Resources targetRes = getUnredirectedResourcesForPackage(mContext, key.targetPackageName);
                PackageRedirectionMap resMap = mRedirections.get(key);
                int[] fromIdents = resMap.getRedirectionKeys();
                int N = fromIdents.length;
                for (int i = 0; i < N; i++) {
                    int fromIdent = fromIdents[i];
                    int toIdent = resMap.lookupRedirection(fromIdent);
                    String fromName = targetRes != null ? targetRes.getResourceName(fromIdent) : null;
                    String toName = themeRes != null ? themeRes.getResourceName(toIdent) : null;
                    pw.println(String.format("      %s (0x%08x) => %s (0x%08x)", fromName, fromIdent,
                            toName, toIdent));
                }
            }
        }
    }

    private static class RedirectionKey {
        public String themePackageName;
        public String themeId;
        public String targetPackageName;

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof RedirectionKey)) return false;
            final RedirectionKey oo = (RedirectionKey)o;
            if (!nullSafeEquals(themePackageName, oo.themePackageName)) {
                return false;
            }
            if (!nullSafeEquals(themeId, oo.themeId)) {
                return false;
            }
            if (!nullSafeEquals(targetPackageName, oo.targetPackageName)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return themePackageName.hashCode() +
                    themeId.hashCode() +
                    targetPackageName.hashCode();
        }

        private static boolean nullSafeEquals(Object a, Object b) {
            if (a == null) {
                return b == a;
            } else if (b == null) {
                return false;
            } else {
                return a.equals(b);
            }
        }
    }

    /**
     * Parses and processes explicit redirection XML files.
     */
    private static class ResourceRedirectionsProcessor {
        private final Resources mResources;
        private final XmlPullParser mParser;
        private final int mResourceId;
        private final String mThemePackageName;
        private final String mTargetPackageName;
        private final PackageRedirectionMap mResMap;

        public ResourceRedirectionsProcessor(Resources res, int resourceId,
                String themePackageName, String targetPackageName,
                PackageRedirectionMap outMap) {
            mResources = res;
            mParser = res.getXml(resourceId);
            mResourceId = resourceId;
            mThemePackageName = themePackageName;
            mTargetPackageName = targetPackageName;
            mResMap = outMap;
        }

        public void process() {
            XmlPullParser parser = mParser;
            int type;
            try {
                while ((type = parser.next()) != XmlPullParser.START_TAG
                           && type != XmlPullParser.END_DOCUMENT) {
                    // just loop...
                }

                String tagName = parser.getName();
                if (parser.getName().equals("resource-redirections")) {
                    processResourceRedirectionsTag();
                } else {
                    Log.w(TAG, "Unknown root element: " + tagName + " at " + getResourceLabel() + " " +
                            parser.getPositionDescription());
                }
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Malformed theme redirection meta at " + getResourceLabel());
            } catch (IOException e) {
                Log.w(TAG, "Unknown error reading redirection meta at " + getResourceLabel());
            }
        }

        private void processResourceRedirectionsTag() throws XmlPullParserException, IOException {
            XmlPullParser parser = mParser;
            int type;
            final int innerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT &&
                    (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (tagName.equals("item")) {
                    processItemTag();
                } else {
                    Log.w(TAG, "Unknown element under <resource-redirections>: " + tagName
                            + " at " + getResourceLabel() + " "
                            + parser.getPositionDescription());
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }
            }
        }

        private void processItemTag() throws XmlPullParserException, IOException {
            XmlPullParser parser = mParser;
            String fromName = parser.getAttributeValue(null, "name");
            if (TextUtils.isEmpty(fromName)) {
                Log.w(TAG, "Missing android:name attribute on <item> tag at " + getResourceLabel() + " " +
                        parser.getPositionDescription());
                return;
            }
            String toName = parser.nextText();
            if (TextUtils.isEmpty(toName)) {
                Log.w(TAG, "Missing <item> text at " + getResourceLabel() + " " +
                        parser.getPositionDescription());
                return;
            }
            int fromIdent = mResources.getIdentifier(fromName, null, mTargetPackageName);
            if (fromIdent == 0) {
                Log.w(TAG, "No such resource found for " + mTargetPackageName + ":" + fromName);
                return;
            }
            int toIdent = mResources.getIdentifier(toName, null, mThemePackageName);
            if (toIdent == 0) {
                Log.w(TAG, "No such resource found for " + mThemePackageName + ":" + toName);
                return;
            }
            mResMap.addRedirection(fromIdent, toIdent);
        }

        private String getResourceLabel() {
            return "resource #0x" + Integer.toHexString(mResourceId);
        }
    }
}
