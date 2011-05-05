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

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.internal.util.XmlUtils;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.BridgeConstants;
import com.android.layoutlib.bridge.impl.ResourceHelper;
import com.android.resources.ResourceType;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ViewGroup.LayoutParams;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Map;

/**
 * Custom implementation of TypedArray to handle non compiled resources.
 */
public final class BridgeTypedArray extends TypedArray {

    private BridgeResources mBridgeResources;
    private BridgeContext mContext;
    private ResourceValue[] mResourceData;
    private String[] mNames;
    private final boolean mPlatformFile;

    public BridgeTypedArray(BridgeResources resources, BridgeContext context, int len,
            boolean platformFile) {
        super(null, null, null, 0);
        mBridgeResources = resources;
        mContext = context;
        mPlatformFile = platformFile;
        mResourceData = new ResourceValue[len];
        mNames = new String[len];
    }

    /** A bridge-specific method that sets a value in the type array */
    public void bridgeSetValue(int index, String name, ResourceValue value) {
        mResourceData[index] = value;
        mNames[index] = name;
    }

    /**
     * Seals the array after all calls to {@link #bridgeSetValue(int, String, ResourceValue)} have
     * been done.
     * <p/>This allows to compute the list of non default values, permitting
     * {@link #getIndexCount()} to return the proper value.
     */
    public void sealArray() {
        // fills TypedArray.mIndices which is used to implement getIndexCount/getIndexAt
        // first count the array size
        int count = 0;
        for (ResourceValue data : mResourceData) {
            if (data != null) {
                count++;
            }
        }

        // allocate the table with an extra to store the size
        mIndices = new int[count+1];
        mIndices[0] = count;

        // fill the array with the indices.
        int index = 1;
        for (int i = 0 ; i < mResourceData.length ; i++) {
            if (mResourceData[i] != null) {
                mIndices[index++] = i;
            }
        }
    }

    /**
     * Return the number of values in this array.
     */
    @Override
    public int length() {
        return mResourceData.length;
    }

    /**
     * Return the Resources object this array was loaded from.
     */
    @Override
    public Resources getResources() {
        return mBridgeResources;
    }

    /**
     * Retrieve the styled string value for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return CharSequence holding string data.  May be styled.  Returns
     *         null if the attribute is not defined.
     */
    @Override
    public CharSequence getText(int index) {
        if (mResourceData[index] != null) {
            // FIXME: handle styled strings!
            return mResourceData[index].getValue();
        }

        return null;
    }

    /**
     * Retrieve the string value for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return String holding string data.  Any styling information is
     * removed.  Returns null if the attribute is not defined.
     */
    @Override
    public String getString(int index) {
        if (mResourceData[index] != null) {
            return mResourceData[index].getValue();
        }

        return null;
    }

    /**
     * Retrieve the boolean value for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined.
     *
     * @return Attribute boolean value, or defValue if not defined.
     */
    @Override
    public boolean getBoolean(int index, boolean defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String s = mResourceData[index].getValue();
        if (s != null) {
            return XmlUtils.convertValueToBoolean(s, defValue);
        }

        return defValue;
    }

    /**
     * Retrieve the integer value for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined.
     *
     * @return Attribute int value, or defValue if not defined.
     */
    @Override
    public int getInt(int index, int defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String s = mResourceData[index].getValue();

        if (RenderResources.REFERENCE_NULL.equals(s)) {
            return defValue;
        }

        try {
            return (s == null) ? defValue : XmlUtils.convertValueToInt(s, defValue);
        } catch (NumberFormatException e) {
            // pass
        }

        // Field is not null and is not an integer.
        // Check for possible constants and try to find them.
        // Get the map of attribute-constant -> IntegerValue
        Map<String, Integer> map = Bridge.getEnumValues(mNames[index]);

        if (map != null) {
            // accumulator to store the value of the 1+ constants.
            int result = 0;

            // split the value in case this is a mix of several flags.
            String[] keywords = s.split("\\|");
            for (String keyword : keywords) {
                Integer i = map.get(keyword.trim());
                if (i != null) {
                    result |= i.intValue();
                } else {
                    Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                            String.format(
                                "\"%s\" in attribute \"%2$s\" is not a valid value",
                                keyword, mNames[index]), null /*data*/);
                }
            }
            return result;
        }

        return defValue;
    }

    /**
     * Retrieve the float value for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return Attribute float value, or defValue if not defined..
     */
    @Override
    public float getFloat(int index, float defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String s = mResourceData[index].getValue();

        if (s != null) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException e) {
                Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                        String.format(
                            "\"%s\" in attribute \"%2$s\" cannot be converted to float.",
                            s, mNames[index]), null /*data*/);

                // we'll return the default value below.
            }
        }
        return defValue;
    }

    /**
     * Retrieve the color value for the attribute at <var>index</var>.  If
     * the attribute references a color resource holding a complex
     * {@link android.content.res.ColorStateList}, then the default color from
     * the set is returned.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute color value, or defValue if not defined.
     */
    @Override
    public int getColor(int index, int defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        ColorStateList colorStateList = ResourceHelper.getColorStateList(
                mResourceData[index], mContext);
        if (colorStateList != null) {
            return colorStateList.getDefaultColor();
        }

        return defValue;
    }

    /**
     * Retrieve the ColorStateList for the attribute at <var>index</var>.
     * The value may be either a single solid color or a reference to
     * a color or complex {@link android.content.res.ColorStateList} description.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return ColorStateList for the attribute, or null if not defined.
     */
    @Override
    public ColorStateList getColorStateList(int index) {
        if (mResourceData[index] == null) {
            return null;
        }

        ResourceValue resValue = mResourceData[index];
        String value = resValue.getValue();

        if (value == null) {
            return null;
        }

        if (RenderResources.REFERENCE_NULL.equals(value)) {
            return null;
        }

        // let the framework inflate the ColorStateList from the XML file.
        File f = new File(value);
        if (f.isFile()) {
            try {
                KXmlParser parser = new KXmlParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                parser.setInput(new FileReader(f));

                BridgeXmlBlockParser blockParser = new BridgeXmlBlockParser(
                        parser, mContext, resValue.isFramework());
                try {
                    return ColorStateList.createFromXml(mContext.getResources(), blockParser);
                } finally {
                    blockParser.ensurePopped();
                }
            } catch (XmlPullParserException e) {
                Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                        "Failed to configure parser for " + value, e, null /*data*/);
                return null;
            } catch (Exception e) {
                // this is an error and not warning since the file existence is checked before
                // attempting to parse it.
                Bridge.getLog().error(LayoutLog.TAG_RESOURCES_READ,
                        "Failed to parse file " + value, e, null /*data*/);

                return null;
            }
        }

        try {
            int color = ResourceHelper.getColor(value);
            return ColorStateList.valueOf(color);
        } catch (NumberFormatException e) {
            Bridge.getLog().error(LayoutLog.TAG_RESOURCES_FORMAT, e.getMessage(), e, null /*data*/);
        }

        return null;
    }

    /**
     * Retrieve the integer value for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute integer value, or defValue if not defined.
     */
    @Override
    public int getInteger(int index, int defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String s = mResourceData[index].getValue();

        if (s != null) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                        String.format(
                            "\"%s\" in attribute \"%2$s\" cannont be converted to an integer.",
                            s, mNames[index]), null /*data*/);

                // The default value is returned below.
            }
        }

        return defValue;
    }

    /**
     * Retrieve a dimensional unit attribute at <var>index</var>.  Unit
     * conversions are based on the current {@link DisplayMetrics}
     * associated with the resources this {@link TypedArray} object
     * came from.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute dimension value multiplied by the appropriate
     * metric, or defValue if not defined.
     *
     * @see #getDimensionPixelOffset
     * @see #getDimensionPixelSize
     */
    @Override
    public float getDimension(int index, float defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String s = mResourceData[index].getValue();

        if (s == null) {
            return defValue;
        } else if (s.equals(BridgeConstants.MATCH_PARENT) ||
                s.equals(BridgeConstants.FILL_PARENT)) {
            return LayoutParams.MATCH_PARENT;
        } else if (s.equals(BridgeConstants.WRAP_CONTENT)) {
            return LayoutParams.WRAP_CONTENT;
        } else if (RenderResources.REFERENCE_NULL.equals(s)) {
            return defValue;
        }

        if (ResourceHelper.stringToFloat(s, mValue)) {
            return mValue.getDimension(mBridgeResources.mMetrics);
        }

        // looks like we were unable to resolve the dimension value
        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                String.format(
                    "\"%1$s\" in attribute \"%2$s\" is not a valid format.",
                    s, mNames[index]), null /*data*/);

        return defValue;
    }

    /**
     * Retrieve a dimensional unit attribute at <var>index</var> for use
     * as an offset in raw pixels.  This is the same as
     * {@link #getDimension}, except the returned value is converted to
     * integer pixels for you.  An offset conversion involves simply
     * truncating the base value to an integer.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute dimension value multiplied by the appropriate
     * metric and truncated to integer pixels, or defValue if not defined.
     *
     * @see #getDimension
     * @see #getDimensionPixelSize
     */
    @Override
    public int getDimensionPixelOffset(int index, int defValue) {
        return (int) getDimension(index, defValue);
    }

    /**
     * Retrieve a dimensional unit attribute at <var>index</var> for use
     * as a size in raw pixels.  This is the same as
     * {@link #getDimension}, except the returned value is converted to
     * integer pixels for use as a size.  A size conversion involves
     * rounding the base value, and ensuring that a non-zero base value
     * is at least one pixel in size.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute dimension value multiplied by the appropriate
     * metric and truncated to integer pixels, or defValue if not defined.
     *
     * @see #getDimension
     * @see #getDimensionPixelOffset
     */
    @Override
    public int getDimensionPixelSize(int index, int defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String s = mResourceData[index].getValue();

        if (s == null) {
            return defValue;
        } else if (s.equals(BridgeConstants.MATCH_PARENT) ||
                s.equals(BridgeConstants.FILL_PARENT)) {
            return LayoutParams.MATCH_PARENT;
        } else if (s.equals(BridgeConstants.WRAP_CONTENT)) {
            return LayoutParams.WRAP_CONTENT;
        } else if (RenderResources.REFERENCE_NULL.equals(s)) {
            return defValue;
        }

        if (ResourceHelper.stringToFloat(s, mValue)) {
            float f = mValue.getDimension(mBridgeResources.mMetrics);

            final int res = (int)(f+0.5f);
            if (res != 0) return res;
            if (f == 0) return 0;
            if (f > 0) return 1;
            return defValue; // this is basically unreachable.
        }

        // looks like we were unable to resolve the dimension value
        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                String.format(
                    "\"%1$s\" in attribute \"%2$s\" is not a valid format.",
                    s, mNames[index]), null /*data*/);

        return defValue;
    }

    /**
     * Special version of {@link #getDimensionPixelSize} for retrieving
     * {@link android.view.ViewGroup}'s layout_width and layout_height
     * attributes.  This is only here for performance reasons; applications
     * should use {@link #getDimensionPixelSize}.
     *
     * @param index Index of the attribute to retrieve.
     * @param name Textual name of attribute for error reporting.
     *
     * @return Attribute dimension value multiplied by the appropriate
     * metric and truncated to integer pixels.
     */
    @Override
    public int getLayoutDimension(int index, String name) {
        return getDimensionPixelSize(index, 0);
    }

    @Override
    public int getLayoutDimension(int index, int defValue) {
        return getDimensionPixelSize(index, defValue);
    }

    /**
     * Retrieve a fractional unit attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     * @param base The base value of this fraction.  In other words, a
     *             standard fraction is multiplied by this value.
     * @param pbase The parent base value of this fraction.  In other
     *             words, a parent fraction (nn%p) is multiplied by this
     *             value.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute fractional value multiplied by the appropriate
     * base value, or defValue if not defined.
     */
    @Override
    public float getFraction(int index, int base, int pbase, float defValue) {
        if (mResourceData[index] == null) {
            return defValue;
        }

        String value = mResourceData[index].getValue();
        if (value == null) {
            return defValue;
        }

        if (ResourceHelper.stringToFloat(value, mValue)) {
            return mValue.getFraction(base, pbase);
        }

        // looks like we were unable to resolve the fraction value
        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                String.format(
                    "\"%1$s\" in attribute \"%2$s\" cannont be converted to a fraction.",
                    value, mNames[index]), null /*data*/);

        return defValue;
    }

    /**
     * Retrieve the resource identifier for the attribute at
     * <var>index</var>.  Note that attribute resource as resolved when
     * the overall {@link TypedArray} object is retrieved.  As a
     * result, this function will return the resource identifier of the
     * final resource value that was found, <em>not</em> necessarily the
     * original resource that was specified by the attribute.
     *
     * @param index Index of attribute to retrieve.
     * @param defValue Value to return if the attribute is not defined or
     *                 not a resource.
     *
     * @return Attribute resource identifier, or defValue if not defined.
     */
    @Override
    public int getResourceId(int index, int defValue) {
        // get the Resource for this index
        ResourceValue resValue = mResourceData[index];

        // no data, return the default value.
        if (resValue == null) {
            return defValue;
        }

        // check if this is a style resource
        if (resValue instanceof StyleResourceValue) {
            // get the id that will represent this style.
            return mContext.getDynamicIdByStyle((StyleResourceValue)resValue);
        }

        if (RenderResources.REFERENCE_NULL.equals(resValue.getValue())) {
            return defValue;
        }

        // if the attribute was a reference to a resource, and not a declaration of an id (@+id),
        // then the xml attribute value was "resolved" which leads us to a ResourceValue with a
        // valid getType() and getName() returning a resource name.
        // (and getValue() returning null!). We need to handle this!
        if (resValue.getResourceType() != null) {
            // if this is a framework id
            if (mPlatformFile || resValue.isFramework()) {
                // look for idName in the android R classes
                return mContext.getFrameworkResourceValue(
                        resValue.getResourceType(), resValue.getName(), defValue);
            }

            // look for idName in the project R class.
            return mContext.getProjectResourceValue(
                    resValue.getResourceType(), resValue.getName(), defValue);
        }

        // else, try to get the value, and resolve it somehow.
        String value = resValue.getValue();
        if (value == null) {
            return defValue;
        }

        // if the value is just an integer, return it.
        try {
            int i = Integer.parseInt(value);
            if (Integer.toString(i).equals(value)) {
                return i;
            }
        } catch (NumberFormatException e) {
            // pass
        }

        // Handle the @id/<name>, @+id/<name> and @android:id/<name>
        // We need to return the exact value that was compiled (from the various R classes),
        // as these values can be reused internally with calls to findViewById().
        // There's a trick with platform layouts that not use "android:" but their IDs are in
        // fact in the android.R and com.android.internal.R classes.
        // The field mPlatformFile will indicate that all IDs are to be looked up in the android R
        // classes exclusively.

        // if this is a reference to an id, find it.
        if (value.startsWith("@id/") || value.startsWith("@+") ||
                value.startsWith("@android:id/")) {

            int pos = value.indexOf('/');
            String idName = value.substring(pos + 1);

            // if this is a framework id
            if (mPlatformFile || value.startsWith("@android") || value.startsWith("@+android")) {
                // look for idName in the android R classes
                return mContext.getFrameworkResourceValue(ResourceType.ID, idName, defValue);
            }

            // look for idName in the project R class.
            return mContext.getProjectResourceValue(ResourceType.ID, idName, defValue);
        }

        // not a direct id valid reference? resolve it
        Integer idValue = null;

        if (resValue.isFramework()) {
            idValue = Bridge.getResourceId(resValue.getResourceType(),
                    resValue.getName());
        } else {
            idValue = mContext.getProjectCallback().getResourceId(
                    resValue.getResourceType(), resValue.getName());
        }

        if (idValue != null) {
            return idValue.intValue();
        }

        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_RESOLVE,
                String.format(
                    "Unable to resolve id \"%1$s\" for attribute \"%2$s\"", value, mNames[index]),
                    resValue);

        return defValue;
    }

    /**
     * Retrieve the Drawable for the attribute at <var>index</var>.  This
     * gets the resource ID of the selected attribute, and uses
     * {@link Resources#getDrawable Resources.getDrawable} of the owning
     * Resources object to retrieve its Drawable.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return Drawable for the attribute, or null if not defined.
     */
    @Override
    public Drawable getDrawable(int index) {
        if (mResourceData[index] == null) {
            return null;
        }

        ResourceValue value = mResourceData[index];
        String stringValue = value.getValue();
        if (stringValue == null || RenderResources.REFERENCE_NULL.equals(stringValue)) {
            return null;
        }

        return ResourceHelper.getDrawable(value, mContext);
    }


    /**
     * Retrieve the CharSequence[] for the attribute at <var>index</var>.
     * This gets the resource ID of the selected attribute, and uses
     * {@link Resources#getTextArray Resources.getTextArray} of the owning
     * Resources object to retrieve its String[].
     *
     * @param index Index of attribute to retrieve.
     *
     * @return CharSequence[] for the attribute, or null if not defined.
     */
    @Override
    public CharSequence[] getTextArray(int index) {
        if (mResourceData[index] == null) {
            return null;
        }

        String value = mResourceData[index].getValue();
        if (value != null) {
            if (RenderResources.REFERENCE_NULL.equals(value)) {
                return null;
            }

            return new CharSequence[] { value };
        }

        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                String.format(
                    String.format("Unknown value for getTextArray(%d) => %s", //DEBUG
                    index, mResourceData[index].getName())), null /*data*/);

        return null;
    }

    /**
     * Retrieve the raw TypedValue for the attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     * @param outValue TypedValue object in which to place the attribute's
     *                 data.
     *
     * @return Returns true if the value was retrieved, else false.
     */
    @Override
    public boolean getValue(int index, TypedValue outValue) {
        if (mResourceData[index] == null) {
            return false;
        }

        String s = mResourceData[index].getValue();

        return ResourceHelper.stringToFloat(s, outValue);
    }

    /**
     * Determines whether there is an attribute at <var>index</var>.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return True if the attribute has a value, false otherwise.
     */
    @Override
    public boolean hasValue(int index) {
        return mResourceData[index] != null;
    }

    /**
     * Retrieve the raw TypedValue for the attribute at <var>index</var>
     * and return a temporary object holding its data.  This object is only
     * valid until the next call on to {@link TypedArray}.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return Returns a TypedValue object if the attribute is defined,
     *         containing its data; otherwise returns null.  (You will not
     *         receive a TypedValue whose type is TYPE_NULL.)
     */
    @Override
    public TypedValue peekValue(int index) {
        if (getValue(index, mValue)) {
            return mValue;
        }

        return null;
    }

    /**
     * Returns a message about the parser state suitable for printing error messages.
     */
    @Override
    public String getPositionDescription() {
        return "<internal -- stub if needed>";
    }

    /**
     * Give back a previously retrieved StyledAttributes, for later re-use.
     */
    @Override
    public void recycle() {
        // pass
    }

    @Override
    public boolean getValueAt(int index, TypedValue outValue) {
        // pass
        return false;
    }

    @Override
    public String toString() {
        return Arrays.toString(mResourceData);
    }
 }
