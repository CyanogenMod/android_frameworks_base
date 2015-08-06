/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.layoutlib.bridge.android;

import android.os.IBinder;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.IProjectCallback;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.BridgeConstants;
import com.android.layoutlib.bridge.android.view.WindowManagerImpl;
import com.android.layoutlib.bridge.impl.ParserFactory;
import com.android.layoutlib.bridge.impl.Stack;
import com.android.resources.ResourceType;
import com.android.util.Pair;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.BridgeAssetManager;
import android.content.res.BridgeResources;
import android.content.res.BridgeTypedArray;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.BridgeInflater;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.textservice.TextServicesManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom implementation of Context/Activity to handle non compiled resources.
 */
public final class BridgeContext extends Context {

    /** The map adds cookies to each view so that IDE can link xml tags to views. */
    private final HashMap<View, Object> mViewKeyMap = new HashMap<View, Object>();
    /**
     * In some cases, when inflating an xml, some objects are created. Then later, the objects are
     * converted to views. This map stores the mapping from objects to cookies which can then be
     * used to populate the mViewKeyMap.
     */
    private final HashMap<Object, Object> mViewKeyHelpMap = new HashMap<Object, Object>();
    private final BridgeAssetManager mAssets;
    private Resources mSystemResources;
    private final Object mProjectKey;
    private final DisplayMetrics mMetrics;
    private final RenderResources mRenderResources;
    private final Configuration mConfig;
    private final ApplicationInfo mApplicationInfo;
    private final IProjectCallback mProjectCallback;
    private final WindowManager mWindowManager;
    private final DisplayManager mDisplayManager;

    private Resources.Theme mTheme;

    private final Map<Object, Map<String, String>> mDefaultPropMaps =
        new IdentityHashMap<Object, Map<String,String>>();

    // maps for dynamically generated id representing style objects (StyleResourceValue)
    private Map<Integer, StyleResourceValue> mDynamicIdToStyleMap;
    private Map<StyleResourceValue, Integer> mStyleToDynamicIdMap;
    private int mDynamicIdGenerator = 0x02030000; // Base id for R.style in custom namespace

    // cache for TypedArray generated from IStyleResourceValue object
    private Map<int[], Map<Integer, BridgeTypedArray>> mTypedArrayCache;
    private BridgeInflater mBridgeInflater;

    private BridgeContentResolver mContentResolver;

    private final Stack<BridgeXmlBlockParser> mParserStack = new Stack<BridgeXmlBlockParser>();
    private SharedPreferences mSharedPreferences;

  /**
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param metrics the {@link DisplayMetrics}.
     * @param renderResources the configured resources (both framework and projects) for this
     * render.
     * @param config the Configuration object for this render.
     * @param targetSdkVersion the targetSdkVersion of the application.
     */
    public BridgeContext(Object projectKey, DisplayMetrics metrics,
            RenderResources renderResources,
            AssetRepository assets,
            IProjectCallback projectCallback,
            Configuration config,
            int targetSdkVersion,
            boolean hasRtlSupport) {
        mProjectKey = projectKey;
        mMetrics = metrics;
        mProjectCallback = projectCallback;

        mRenderResources = renderResources;
        mConfig = config;
        mAssets = new BridgeAssetManager();
        mAssets.setAssetRepository(assets);

        mApplicationInfo = new ApplicationInfo();
        mApplicationInfo.targetSdkVersion = targetSdkVersion;
        if (hasRtlSupport) {
            mApplicationInfo.flags = mApplicationInfo.flags | ApplicationInfo.FLAG_SUPPORTS_RTL;
        }

        mWindowManager = new WindowManagerImpl(mMetrics);
        mDisplayManager = new DisplayManager(this);
    }

    /**
     * Initializes the {@link Resources} singleton to be linked to this {@link Context}, its
     * {@link DisplayMetrics}, {@link Configuration}, and {@link IProjectCallback}.
     *
     * @see #disposeResources()
     */
    public void initResources() {
        AssetManager assetManager = AssetManager.getSystem();

        mSystemResources = BridgeResources.initSystem(
                this,
                assetManager,
                mMetrics,
                mConfig,
                mProjectCallback);
        mTheme = mSystemResources.newTheme();
    }

    /**
     * Disposes the {@link Resources} singleton.
     */
    public void disposeResources() {
        BridgeResources.disposeSystem();
    }

    public void setBridgeInflater(BridgeInflater inflater) {
        mBridgeInflater = inflater;
    }

    public void addViewKey(View view, Object viewKey) {
        mViewKeyMap.put(view, viewKey);
    }

    public Object getViewKey(View view) {
        return mViewKeyMap.get(view);
    }

    public void addCookie(Object o, Object cookie) {
        mViewKeyHelpMap.put(o, cookie);
    }

    public Object getCookie(Object o) {
        return mViewKeyHelpMap.get(o);
    }

    public Object getProjectKey() {
        return mProjectKey;
    }

    public DisplayMetrics getMetrics() {
        return mMetrics;
    }

    public IProjectCallback getProjectCallback() {
        return mProjectCallback;
    }

    public RenderResources getRenderResources() {
        return mRenderResources;
    }

    public Map<String, String> getDefaultPropMap(Object key) {
        return mDefaultPropMaps.get(key);
    }

    public Configuration getConfiguration() {
        return mConfig;
    }

    /**
     * Adds a parser to the stack.
     * @param parser the parser to add.
     */
    public void pushParser(BridgeXmlBlockParser parser) {
        if (ParserFactory.LOG_PARSER) {
            System.out.println("PUSH " + parser.getParser().toString());
        }
        mParserStack.push(parser);
    }

    /**
     * Removes the parser at the top of the stack
     */
    public void popParser() {
        BridgeXmlBlockParser parser = mParserStack.pop();
        if (ParserFactory.LOG_PARSER) {
            System.out.println("POPD " + parser.getParser().toString());
        }
    }

    /**
     * Returns the current parser at the top the of the stack.
     * @return a parser or null.
     */
    public BridgeXmlBlockParser getCurrentParser() {
        return mParserStack.peek();
    }

    /**
     * Returns the previous parser.
     * @return a parser or null if there isn't any previous parser
     */
    public BridgeXmlBlockParser getPreviousParser() {
        if (mParserStack.size() < 2) {
            return null;
        }
        return mParserStack.get(mParserStack.size() - 2);
    }

    public boolean resolveThemeAttribute(int resid, TypedValue outValue, boolean resolveRefs) {
        Pair<ResourceType, String> resourceInfo = Bridge.resolveResourceId(resid);
        boolean isFrameworkRes = true;
        if (resourceInfo == null) {
            resourceInfo = mProjectCallback.resolveResourceId(resid);
            isFrameworkRes = false;
        }

        if (resourceInfo == null) {
            return false;
        }

        ResourceValue value = mRenderResources.findItemInTheme(resourceInfo.getSecond(),
                isFrameworkRes);
        if (resolveRefs) {
            value = mRenderResources.resolveResValue(value);
        }

        if (value == null) {
            // unable to find the attribute.
            return false;
        }

        // check if this is a style resource
        if (value instanceof StyleResourceValue) {
            // get the id that will represent this style.
            outValue.resourceId = getDynamicIdByStyle((StyleResourceValue)value);
            return true;
        }

        int a;
        // if this is a framework value.
        if (value.isFramework()) {
            // look for idName in the android R classes.
            // use 0 a default res value as it's not a valid id value.
            a = getFrameworkResourceValue(value.getResourceType(), value.getName(), 0 /*defValue*/);
        } else {
            // look for idName in the project R class.
            // use 0 a default res value as it's not a valid id value.
            a = getProjectResourceValue(value.getResourceType(), value.getName(), 0 /*defValue*/);
        }

        if (a != 0) {
            outValue.resourceId = a;
            return true;
        }

        return false;
    }


    public ResourceReference resolveId(int id) {
        // first get the String related to this id in the framework
        Pair<ResourceType, String> resourceInfo = Bridge.resolveResourceId(id);

        if (resourceInfo != null) {
            return new ResourceReference(resourceInfo.getSecond(), true);
        }

        // didn't find a match in the framework? look in the project.
        if (mProjectCallback != null) {
            resourceInfo = mProjectCallback.resolveResourceId(id);

            if (resourceInfo != null) {
                return new ResourceReference(resourceInfo.getSecond(), false);
            }
        }

        // The base value for R.style is 0x01030000 and the custom style is 0x02030000.
        // So, if the second byte is 03, it's probably a style.
        if ((id >> 16 & 0xFF) == 0x03) {
            return getStyleByDynamicId(id);
        }
        return null;
    }

    public Pair<View, Boolean> inflateView(ResourceReference resource, ViewGroup parent,
            boolean attachToRoot, boolean skipCallbackParser) {
        boolean isPlatformLayout = resource.isFramework();

        if (!isPlatformLayout && !skipCallbackParser) {
            // check if the project callback can provide us with a custom parser.
            ILayoutPullParser parser = getParser(resource);

            if (parser != null) {
                BridgeXmlBlockParser blockParser = new BridgeXmlBlockParser(parser,
                        this, resource.isFramework());
                try {
                    pushParser(blockParser);
                    return Pair.of(
                            mBridgeInflater.inflate(blockParser, parent, attachToRoot),
                            true);
                } finally {
                    popParser();
                }
            }
        }

        ResourceValue resValue;
        if (resource instanceof ResourceValue) {
            resValue = (ResourceValue) resource;
        } else {
            if (isPlatformLayout) {
                resValue = mRenderResources.getFrameworkResource(ResourceType.LAYOUT,
                        resource.getName());
            } else {
                resValue = mRenderResources.getProjectResource(ResourceType.LAYOUT,
                        resource.getName());
            }
        }

        if (resValue != null) {

            File xml = new File(resValue.getValue());
            if (xml.isFile()) {
                // we need to create a pull parser around the layout XML file, and then
                // give that to our XmlBlockParser
                try {
                    XmlPullParser parser = ParserFactory.create(xml);

                    // set the resource ref to have correct view cookies
                    mBridgeInflater.setResourceReference(resource);

                    BridgeXmlBlockParser blockParser = new BridgeXmlBlockParser(parser,
                            this, resource.isFramework());
                    try {
                        pushParser(blockParser);
                        return Pair.of(
                                mBridgeInflater.inflate(blockParser, parent, attachToRoot),
                                false);
                    } finally {
                        popParser();
                    }
                } catch (XmlPullParserException e) {
                    Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                            "Failed to configure parser for " + xml, e, null /*data*/);
                    // we'll return null below.
                } catch (FileNotFoundException e) {
                    // this shouldn't happen since we check above.
                } finally {
                    mBridgeInflater.setResourceReference(null);
                }
            } else {
                Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                        String.format("File %s is missing!", xml), null);
            }
        } else {
            Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                    String.format("Layout %s%s does not exist.", isPlatformLayout ? "android:" : "",
                            resource.getName()), null);
        }

        return Pair.of(null, false);
    }

    @SuppressWarnings("deprecation")
    private ILayoutPullParser getParser(ResourceReference resource) {
        ILayoutPullParser parser;
        if (resource instanceof ResourceValue) {
            parser = mProjectCallback.getParser((ResourceValue) resource);
        } else {
            parser = mProjectCallback.getParser(resource.getName());
        }
        return parser;
    }

    // ------------ Context methods

    @Override
    public Resources getResources() {
        return mSystemResources;
    }

    @Override
    public Theme getTheme() {
        return mTheme;
    }

    @Override
    public void recreateTheme() {
        throw new UnsupportedOperationException("recreateTheme is unsupported");
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    @Override
    public Object getSystemService(String service) {
        if (LAYOUT_INFLATER_SERVICE.equals(service)) {
            return mBridgeInflater;
        }

        if (TEXT_SERVICES_MANAGER_SERVICE.equals(service)) {
            // we need to return a valid service to avoid NPE
            return TextServicesManager.getInstance();
        }

        if (WINDOW_SERVICE.equals(service)) {
            return mWindowManager;
        }

        // needed by SearchView
        if (INPUT_METHOD_SERVICE.equals(service)) {
            return null;
        }

        if (POWER_SERVICE.equals(service)) {
            return new PowerManager(this, new BridgePowerManager(), new Handler());
        }

        if (DISPLAY_SERVICE.equals(service)) {
            return mDisplayManager;
        }

        if (ACCESSIBILITY_SERVICE.equals(service)) {
            return AccessibilityManager.getInstance(this);
        }

        throw new UnsupportedOperationException("Unsupported Service: " + service);
    }


    @Override
    public final BridgeTypedArray obtainStyledAttributes(int[] attrs) {
        // No style is specified here, so create the typed array based on the default theme
        // and the styles already applied to it. A null value of style indicates that the default
        // theme should be used.
        return createStyleBasedTypedArray(null, attrs);
    }

    @Override
    public final BridgeTypedArray obtainStyledAttributes(int resid, int[] attrs)
            throws Resources.NotFoundException {
        StyleResourceValue style = null;
        // get the StyleResourceValue based on the resId;
        if (resid != 0) {
            style = getStyleByDynamicId(resid);

            if (style == null) {
                // In some cases, style may not be a dynamic id, so we do a full search.
                ResourceReference ref = resolveId(resid);
                if (ref != null) {
                    style = mRenderResources.getStyle(ref.getName(), ref.isFramework());
                }
            }

            if (style == null) {
                throw new Resources.NotFoundException();
            }
        }

        if (mTypedArrayCache == null) {
            mTypedArrayCache = new HashMap<int[], Map<Integer,BridgeTypedArray>>();

            Map<Integer, BridgeTypedArray> map = new HashMap<Integer, BridgeTypedArray>();
            mTypedArrayCache.put(attrs, map);

            BridgeTypedArray ta = createStyleBasedTypedArray(style, attrs);
            map.put(resid, ta);

            return ta;
        }

        // get the 2nd map
        Map<Integer, BridgeTypedArray> map = mTypedArrayCache.get(attrs);
        if (map == null) {
            map = new HashMap<Integer, BridgeTypedArray>();
            mTypedArrayCache.put(attrs, map);
        }

        // get the array from the 2nd map
        BridgeTypedArray ta = map.get(resid);

        if (ta == null) {
            ta = createStyleBasedTypedArray(style, attrs);
            map.put(resid, ta);
        }

        return ta;
    }

    @Override
    public final BridgeTypedArray obtainStyledAttributes(AttributeSet set, int[] attrs) {
        return obtainStyledAttributes(set, attrs, 0, 0);
    }

    @Override
    public BridgeTypedArray obtainStyledAttributes(AttributeSet set, int[] attrs,
            int defStyleAttr, int defStyleRes) {

        Map<String, String> defaultPropMap = null;
        boolean isPlatformFile = true;

        // Hint: for XmlPullParser, attach source //DEVICE_SRC/dalvik/libcore/xml/src/java
        if (set instanceof BridgeXmlBlockParser) {
            BridgeXmlBlockParser parser = null;
            parser = (BridgeXmlBlockParser)set;

            isPlatformFile = parser.isPlatformFile();

            Object key = parser.getViewCookie();
            if (key != null) {
                defaultPropMap = mDefaultPropMaps.get(key);
                if (defaultPropMap == null) {
                    defaultPropMap = new HashMap<String, String>();
                    mDefaultPropMaps.put(key, defaultPropMap);
                }
            }

        } else if (set instanceof BridgeLayoutParamsMapAttributes) {
            // this is only for temp layout params generated dynamically, so this is never
            // platform content.
            isPlatformFile = false;
        } else if (set != null) { // null parser is ok
            // really this should not be happening since its instantiated in Bridge
            Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                    "Parser is not a BridgeXmlBlockParser!", null /*data*/);
            return null;
        }

        List<Pair<String, Boolean>> attributeList = searchAttrs(attrs);

        BridgeTypedArray ta = ((BridgeResources) mSystemResources).newTypeArray(attrs.length,
                isPlatformFile);

        // look for a custom style.
        String customStyle = null;
        if (set != null) {
            customStyle = set.getAttributeValue(null /* namespace*/, "style");
        }

        StyleResourceValue customStyleValues = null;
        if (customStyle != null) {
            ResourceValue item = mRenderResources.findResValue(customStyle,
                    isPlatformFile /*forceFrameworkOnly*/);

            // resolve it in case it links to something else
            item = mRenderResources.resolveResValue(item);

            if (item instanceof StyleResourceValue) {
                customStyleValues = (StyleResourceValue)item;
            }
        }

        // resolve the defStyleAttr value into a IStyleResourceValue
        StyleResourceValue defStyleValues = null;

        if (defStyleAttr != 0) {
            // get the name from the int.
            Pair<String, Boolean> defStyleAttribute = searchAttr(defStyleAttr);

            if (defaultPropMap != null) {
                String defStyleName = defStyleAttribute.getFirst();
                if (defStyleAttribute.getSecond()) {
                    defStyleName = "android:" + defStyleName;
                }
                defaultPropMap.put("style", defStyleName);
            }

            // look for the style in the current theme, and its parent:
            ResourceValue item = mRenderResources.findItemInTheme(defStyleAttribute.getFirst(),
                    defStyleAttribute.getSecond());

            if (item != null) {
                // item is a reference to a style entry. Search for it.
                item = mRenderResources.findResValue(item.getValue(), item.isFramework());

                if (item instanceof StyleResourceValue) {
                    defStyleValues = (StyleResourceValue)item;
                }
            } else {
                Bridge.getLog().error(LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR,
                        String.format(
                                "Failed to find style '%s' in current theme",
                                defStyleAttribute.getFirst()),
                        null /*data*/);
            }
        } else if (defStyleRes != 0) {
            boolean isFrameworkRes = true;
            Pair<ResourceType, String> value = Bridge.resolveResourceId(defStyleRes);
            if (value == null) {
                value = mProjectCallback.resolveResourceId(defStyleRes);
                isFrameworkRes = false;
            }

            if (value != null) {
                if ((value.getFirst() == ResourceType.STYLE)) {
                    // look for the style in all resources:
                    StyleResourceValue item = mRenderResources.getStyle(value.getSecond(),
                            isFrameworkRes);
                    if (item != null) {
                        if (defaultPropMap != null) {
                            defaultPropMap.put("style", item.getName());
                        }

                        defStyleValues = item;
                    } else {
                        Bridge.getLog().error(null,
                                String.format(
                                        "Style with id 0x%x (resolved to '%s') does not exist.",
                                        defStyleRes, value.getSecond()),
                                null);
                    }
                } else {
                    Bridge.getLog().error(null,
                            String.format(
                                    "Resource id 0x%x is not of type STYLE (instead %s)",
                                    defStyleRes, value.getFirst().toString()),
                            null);
                }
            } else {
                Bridge.getLog().error(null,
                        String.format(
                                "Failed to find style with id 0x%x in current theme",
                                defStyleRes),
                        null);
            }
        }

        String appNamespace = mProjectCallback.getNamespace();

        if (attributeList != null) {
            for (int index = 0 ; index < attributeList.size() ; index++) {
                Pair<String, Boolean> attribute = attributeList.get(index);

                if (attribute == null) {
                    continue;
                }

                String attrName = attribute.getFirst();
                boolean frameworkAttr = attribute.getSecond();
                String value = null;
                if (set != null) {
                    value = set.getAttributeValue(
                            frameworkAttr ? BridgeConstants.NS_RESOURCES : appNamespace,
                                    attrName);

                    // if this is an app attribute, and the first get fails, try with the
                    // new res-auto namespace as well
                    if (!frameworkAttr && value == null) {
                        value = set.getAttributeValue(BridgeConstants.NS_APP_RES_AUTO, attrName);
                    }
                }

                // if there's no direct value for this attribute in the XML, we look for default
                // values in the widget defStyle, and then in the theme.
                if (value == null) {
                    ResourceValue resValue = null;

                    // look for the value in the custom style first (and its parent if needed)
                    if (customStyleValues != null) {
                        resValue = mRenderResources.findItemInStyle(customStyleValues,
                                attrName, frameworkAttr);
                    }

                    // then look for the value in the default Style (and its parent if needed)
                    if (resValue == null && defStyleValues != null) {
                        resValue = mRenderResources.findItemInStyle(defStyleValues,
                                attrName, frameworkAttr);
                    }

                    // if the item is not present in the defStyle, we look in the main theme (and
                    // its parent themes)
                    if (resValue == null) {
                        resValue = mRenderResources.findItemInTheme(attrName, frameworkAttr);
                    }

                    // if we found a value, we make sure this doesn't reference another value.
                    // So we resolve it.
                    if (resValue != null) {
                        // put the first default value, before the resolution.
                        if (defaultPropMap != null) {
                            defaultPropMap.put(attrName, resValue.getValue());
                        }

                        resValue = mRenderResources.resolveResValue(resValue);
                    }

                    ta.bridgeSetValue(index, attrName, frameworkAttr, resValue);
                } else {
                    // there is a value in the XML, but we need to resolve it in case it's
                    // referencing another resource or a theme value.
                    ta.bridgeSetValue(index, attrName, frameworkAttr,
                            mRenderResources.resolveValue(null, attrName, value, isPlatformFile));
                }
            }
        }

        ta.sealArray();

        return ta;
    }

    @Override
    public Looper getMainLooper() {
        return Looper.myLooper();
    }


    // ------------- private new methods

    /**
     * Creates a {@link BridgeTypedArray} by filling the values defined by the int[] with the
     * values found in the given style. If no style is specified, the default theme, along with the
     * styles applied to it are used.
     *
     * @see #obtainStyledAttributes(int, int[])
     */
    private BridgeTypedArray createStyleBasedTypedArray(@Nullable StyleResourceValue style,
            int[] attrs) throws Resources.NotFoundException {

        List<Pair<String, Boolean>> attributes = searchAttrs(attrs);

        BridgeTypedArray ta = ((BridgeResources) mSystemResources).newTypeArray(attrs.length,
                false);

        // for each attribute, get its name so that we can search it in the style
        for (int i = 0 ; i < attrs.length ; i++) {
            Pair<String, Boolean> attribute = attributes.get(i);

            if (attribute != null) {
                // look for the value in the given style
                ResourceValue resValue;
                if (style != null) {
                    resValue = mRenderResources.findItemInStyle(style, attribute.getFirst(),
                            attribute.getSecond());
                } else {
                    resValue = mRenderResources.findItemInTheme(attribute.getFirst(),
                            attribute.getSecond());
                }

                if (resValue != null) {
                    // resolve it to make sure there are no references left.
                    ta.bridgeSetValue(i, attribute.getFirst(), attribute.getSecond(),
                            mRenderResources.resolveResValue(resValue));
                }
            }
        }

        ta.sealArray();

        return ta;
    }

    /**
     * The input int[] attrs is a list of attributes. The returns a list of information about
     * each attributes. The information is (name, isFramework)
     * <p/>
     *
     * @param attrs An attribute array reference given to obtainStyledAttributes.
     * @return List of attribute information.
     */
    private List<Pair<String, Boolean>> searchAttrs(int[] attrs) {
        List<Pair<String, Boolean>> results = new ArrayList<Pair<String, Boolean>>(attrs.length);

        // for each attribute, get its name so that we can search it in the style
        for (int attr : attrs) {
            Pair<ResourceType, String> resolvedResource = Bridge.resolveResourceId(attr);
            boolean isFramework = false;
            if (resolvedResource != null) {
                isFramework = true;
            } else {
                resolvedResource = mProjectCallback.resolveResourceId(attr);
            }

            if (resolvedResource != null) {
                results.add(Pair.of(resolvedResource.getSecond(), isFramework));
            } else {
                results.add(null);
            }
        }

        return results;
    }

    /**
     * Searches for the attribute referenced by its internal id.
     *
     * @param attr An attribute reference given to obtainStyledAttributes such as defStyle.
     * @return A (name, isFramework) pair describing the attribute if found. Returns null
     *         if nothing is found.
     */
    public Pair<String, Boolean> searchAttr(int attr) {
        Pair<ResourceType, String> info = Bridge.resolveResourceId(attr);
        if (info != null) {
            return Pair.of(info.getSecond(), Boolean.TRUE);
        }

        info = mProjectCallback.resolveResourceId(attr);
        if (info != null) {
            return Pair.of(info.getSecond(), Boolean.FALSE);
        }

        return null;
    }

    public int getDynamicIdByStyle(StyleResourceValue resValue) {
        if (mDynamicIdToStyleMap == null) {
            // create the maps.
            mDynamicIdToStyleMap = new HashMap<Integer, StyleResourceValue>();
            mStyleToDynamicIdMap = new HashMap<StyleResourceValue, Integer>();
        }

        // look for an existing id
        Integer id = mStyleToDynamicIdMap.get(resValue);

        if (id == null) {
            // generate a new id
            id = ++mDynamicIdGenerator;

            // and add it to the maps.
            mDynamicIdToStyleMap.put(id, resValue);
            mStyleToDynamicIdMap.put(resValue, id);
        }

        return id;
    }

    private StyleResourceValue getStyleByDynamicId(int i) {
        if (mDynamicIdToStyleMap != null) {
            return mDynamicIdToStyleMap.get(i);
        }

        return null;
    }

    public int getFrameworkResourceValue(ResourceType resType, String resName, int defValue) {
        if (getRenderResources().getFrameworkResource(resType, resName) != null) {
            // Bridge.getResourceId creates a new resource id if an existing one isn't found. So,
            // we check for the existence of the resource before calling it.
            return Bridge.getResourceId(resType, resName);
        }

        return defValue;
    }

    public int getProjectResourceValue(ResourceType resType, String resName, int defValue) {
        // getResourceId creates a new resource id if an existing resource id isn't found. So, we
        // check for the existence of the resource before calling it.
        if (getRenderResources().getProjectResource(resType, resName) != null) {
            if (mProjectCallback != null) {
                Integer value = mProjectCallback.getResourceId(resType, resName);
                if (value != null) {
                    return value;
                }
            }
        }

        return defValue;
    }

    //------------ NOT OVERRIDEN --------------------

    @Override
    public boolean bindService(Intent arg0, ServiceConnection arg1, int arg2) {
        // pass
        return false;
    }

    @Override
    public int checkCallingOrSelfPermission(String arg0) {
        // pass
        return 0;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri arg0, int arg1) {
        // pass
        return 0;
    }

    @Override
    public int checkCallingPermission(String arg0) {
        // pass
        return 0;
    }

    @Override
    public int checkCallingUriPermission(Uri arg0, int arg1) {
        // pass
        return 0;
    }

    @Override
    public int checkPermission(String arg0, int arg1, int arg2) {
        // pass
        return 0;
    }

    @Override
    public int checkPermission(String arg0, int arg1, int arg2, IBinder arg3) {
        // pass
        return 0;
    }

    @Override
    public int checkUriPermission(Uri arg0, int arg1, int arg2, int arg3) {
        // pass
        return 0;
    }

    @Override
    public int checkUriPermission(Uri arg0, int arg1, int arg2, int arg3, IBinder arg4) {
        // pass
        return 0;
    }

    @Override
    public int checkUriPermission(Uri arg0, String arg1, String arg2, int arg3,
            int arg4, int arg5) {
        // pass
        return 0;
    }

    @Override
    public void clearWallpaper() {
        // pass

    }

    @Override
    public Context createPackageContext(String arg0, int arg1) {
        // pass
        return null;
    }

    @Override
    public Context createPackageContextAsUser(String arg0, int arg1, UserHandle user) {
        // pass
        return null;
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        // pass
        return null;
    }

    @Override
    public Context createDisplayContext(Display display) {
        // pass
        return null;
    }

    @Override
    public String[] databaseList() {
        // pass
        return null;
    }

    @Override
    public Context createApplicationContext(ApplicationInfo application, int flags)
            throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public boolean deleteDatabase(String arg0) {
        // pass
        return false;
    }

    @Override
    public boolean deleteFile(String arg0) {
        // pass
        return false;
    }

    @Override
    public void enforceCallingOrSelfPermission(String arg0, String arg1) {
        // pass

    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri arg0, int arg1,
            String arg2) {
        // pass

    }

    @Override
    public void enforceCallingPermission(String arg0, String arg1) {
        // pass

    }

    @Override
    public void enforceCallingUriPermission(Uri arg0, int arg1, String arg2) {
        // pass

    }

    @Override
    public void enforcePermission(String arg0, int arg1, int arg2, String arg3) {
        // pass

    }

    @Override
    public void enforceUriPermission(Uri arg0, int arg1, int arg2, int arg3,
            String arg4) {
        // pass

    }

    @Override
    public void enforceUriPermission(Uri arg0, String arg1, String arg2,
            int arg3, int arg4, int arg5, String arg6) {
        // pass

    }

    @Override
    public String[] fileList() {
        // pass
        return null;
    }

    @Override
    public BridgeAssetManager getAssets() {
        return mAssets;
    }

    @Override
    public File getCacheDir() {
        // pass
        return null;
    }

    @Override
    public File getCodeCacheDir() {
        // pass
        return null;
    }

    @Override
    public File getExternalCacheDir() {
        // pass
        return null;
    }

    @Override
    public ContentResolver getContentResolver() {
        if (mContentResolver == null) {
            mContentResolver = new BridgeContentResolver(this);
        }
        return mContentResolver;
    }

    @Override
    public File getDatabasePath(String arg0) {
        // pass
        return null;
    }

    @Override
    public File getDir(String arg0, int arg1) {
        // pass
        return null;
    }

    @Override
    public File getFileStreamPath(String arg0) {
        // pass
        return null;
    }

    @Override
    public File getFilesDir() {
        // pass
        return null;
    }

    @Override
    public File getNoBackupFilesDir() {
        // pass
        return null;
    }

    @Override
    public File getExternalFilesDir(String type) {
        // pass
        return null;
    }

    @Override
    public String getPackageCodePath() {
        // pass
        return null;
    }

    @Override
    public PackageManager getPackageManager() {
        // pass
        return null;
    }

    @Override
    public String getPackageName() {
        // pass
        return null;
    }

    @Override
    public String getBasePackageName() {
        // pass
        return null;
    }

    @Override
    public String getOpPackageName() {
        // pass
        return null;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return mApplicationInfo;
    }

    @Override
    public String getPackageResourcePath() {
        // pass
        return null;
    }

    @Override
    public File getSharedPrefsFile(String name) {
        // pass
        return null;
    }

    @Override
    public SharedPreferences getSharedPreferences(String arg0, int arg1) {
        if (mSharedPreferences == null) {
            mSharedPreferences = new BridgeSharedPreferences();
        }
        return mSharedPreferences;
    }

    @Override
    public Drawable getWallpaper() {
        // pass
        return null;
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        return -1;
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        return -1;
    }

    @Override
    public void grantUriPermission(String arg0, Uri arg1, int arg2) {
        // pass

    }

    @Override
    public FileInputStream openFileInput(String arg0) throws FileNotFoundException {
        // pass
        return null;
    }

    @Override
    public FileOutputStream openFileOutput(String arg0, int arg1) throws FileNotFoundException {
        // pass
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String arg0, int arg1, CursorFactory arg2) {
        // pass
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String arg0, int arg1,
            CursorFactory arg2, DatabaseErrorHandler arg3) {
        // pass
        return null;
    }

    @Override
    public Drawable peekWallpaper() {
        // pass
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver arg0, IntentFilter arg1) {
        // pass
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver arg0, IntentFilter arg1,
            String arg2, Handler arg3) {
        // pass
        return null;
    }

    @Override
    public Intent registerReceiverAsUser(BroadcastReceiver arg0, UserHandle arg0p5,
            IntentFilter arg1, String arg2, Handler arg3) {
        // pass
        return null;
    }

    @Override
    public void removeStickyBroadcast(Intent arg0) {
        // pass

    }

    @Override
    public void revokeUriPermission(Uri arg0, int arg1) {
        // pass

    }

    @Override
    public void sendBroadcast(Intent arg0) {
        // pass

    }

    @Override
    public void sendBroadcast(Intent arg0, String arg1) {
        // pass

    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission, int appOp) {
        // pass
    }

    @Override
    public void sendOrderedBroadcast(Intent arg0, String arg1) {
        // pass

    }

    @Override
    public void sendOrderedBroadcast(Intent arg0, String arg1,
            BroadcastReceiver arg2, Handler arg3, int arg4, String arg5,
            Bundle arg6) {
        // pass

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, int appOp,
            BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
            String initialData, Bundle initialExtras) {
        // pass
    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user) {
        // pass
    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user,
            String receiverPermission) {
        // pass
    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
            String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler,
            int initialCode, String initialData, Bundle initialExtras) {
        // pass
    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
            String receiverPermission, int appOp, BroadcastReceiver resultReceiver,
            Handler scheduler,
            int initialCode, String initialData, Bundle initialExtras) {
        // pass
    }

    @Override
    public void sendStickyBroadcast(Intent arg0) {
        // pass

    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent,
            BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData,
           Bundle initialExtras) {
        // pass
    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {
        // pass
    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent,
            UserHandle user, BroadcastReceiver resultReceiver,
            Handler scheduler, int initialCode, String initialData,
            Bundle initialExtras) {
        // pass
    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {
        // pass
    }

    @Override
    public void setTheme(int arg0) {
        // pass

    }

    @Override
    public void setWallpaper(Bitmap arg0) throws IOException {
        // pass

    }

    @Override
    public void setWallpaper(InputStream arg0) throws IOException {
        // pass

    }

    @Override
    public void startActivity(Intent arg0) {
        // pass
    }

    @Override
    public void startActivity(Intent arg0, Bundle arg1) {
        // pass
    }

    @Override
    public void startIntentSender(IntentSender intent,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags)
            throws IntentSender.SendIntentException {
        // pass
    }

    @Override
    public void startIntentSender(IntentSender intent,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags,
            Bundle options) throws IntentSender.SendIntentException {
        // pass
    }

    @Override
    public boolean startInstrumentation(ComponentName arg0, String arg1,
            Bundle arg2) {
        // pass
        return false;
    }

    @Override
    public ComponentName startService(Intent arg0) {
        // pass
        return null;
    }

    @Override
    public boolean stopService(Intent arg0) {
        // pass
        return false;
    }

    @Override
    public ComponentName startServiceAsUser(Intent arg0, UserHandle arg1) {
        // pass
        return null;
    }

    @Override
    public boolean stopServiceAsUser(Intent arg0, UserHandle arg1) {
        // pass
        return false;
    }

    @Override
    public void unbindService(ServiceConnection arg0) {
        // pass

    }

    @Override
    public void unregisterReceiver(BroadcastReceiver arg0) {
        // pass

    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public void startActivities(Intent[] arg0) {
        // pass

    }

    @Override
    public void startActivities(Intent[] arg0, Bundle arg1) {
        // pass

    }

    @Override
    public boolean isRestricted() {
        return false;
    }

    @Override
    public File getObbDir() {
        Bridge.getLog().error(LayoutLog.TAG_UNSUPPORTED, "OBB not supported", null);
        return null;
    }

    @Override
    public DisplayAdjustments getDisplayAdjustments(int displayId) {
        // pass
        return null;
    }

    @Override
    public int getUserId() {
        return 0; // not used
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        // pass
        return new File[0];
    }

    @Override
    public File[] getObbDirs() {
        // pass
        return new File[0];
    }

    @Override
    public File[] getExternalCacheDirs() {
        // pass
        return new File[0];
    }

    @Override
    public File[] getExternalMediaDirs() {
        // pass
        return new File[0];
    }
}
