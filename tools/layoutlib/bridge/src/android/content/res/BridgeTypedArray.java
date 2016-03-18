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

package android.content.res;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.internal.util.XmlUtils;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser;
import com.android.layoutlib.bridge.impl.ParserFactory;
import com.android.layoutlib.bridge.impl.ResourceHelper;
import com.android.resources.ResourceType;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.Nullable;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater_Delegate;
import android.view.ViewGroup.LayoutParams;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static android.util.TypedValue.TYPE_ATTRIBUTE;
import static android.util.TypedValue.TYPE_DIMENSION;
import static android.util.TypedValue.TYPE_FLOAT;
import static android.util.TypedValue.TYPE_INT_BOOLEAN;
import static android.util.TypedValue.TYPE_INT_COLOR_ARGB4;
import static android.util.TypedValue.TYPE_INT_COLOR_ARGB8;
import static android.util.TypedValue.TYPE_INT_COLOR_RGB4;
import static android.util.TypedValue.TYPE_INT_COLOR_RGB8;
import static android.util.TypedValue.TYPE_INT_DEC;
import static android.util.TypedValue.TYPE_INT_HEX;
import static android.util.TypedValue.TYPE_NULL;
import static android.util.TypedValue.TYPE_REFERENCE;
import static android.util.TypedValue.TYPE_STRING;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.ide.common.rendering.api.RenderResources.REFERENCE_EMPTY;
import static com.android.ide.common.rendering.api.RenderResources.REFERENCE_NULL;
import static com.android.ide.common.rendering.api.RenderResources.REFERENCE_UNDEFINED;

/**
 * Custom implementation of TypedArray to handle non compiled resources.
 */
public final class BridgeTypedArray extends TypedArray {

    private final BridgeResources mBridgeResources;
    private final BridgeContext mContext;
    private final boolean mPlatformFile;

    private final ResourceValue[] mResourceData;
    private final String[] mNames;
    private final boolean[] mIsFramework;

    // Contains ids that are @empty. We still store null in mResourceData for that index, since we
    // want to save on the check against empty, each time a resource value is requested.
    @Nullable
    private int[] mEmptyIds;

    public BridgeTypedArray(BridgeResources resources, BridgeContext context, int len,
            boolean platformFile) {
        super(resources, null, null, 0);
        mBridgeResources = resources;
        mContext = context;
        mPlatformFile = platformFile;
        mResourceData = new ResourceValue[len];
        mNames = new String[len];
        mIsFramework = new boolean[len];
    }

    /**
     * A bridge-specific method that sets a value in the type array
     * @param index the index of the value in the TypedArray
     * @param name the name of the attribute
     * @param isFramework whether the attribute is in the android namespace.
     * @param value the value of the attribute
     */
    public void bridgeSetValue(int index, String name, boolean isFramework, ResourceValue value) {
        mResourceData[index] = value;
        mNames[index] = name;
        mIsFramework[index] = isFramework;
    }

    /**
     * Seals the array after all calls to
     * {@link #bridgeSetValue(int, String, boolean, ResourceValue)} have been done.
     * <p/>This allows to compute the list of non default values, permitting
     * {@link #getIndexCount()} to return the proper value.
     */
    public void sealArray() {
        // fills TypedArray.mIndices which is used to implement getIndexCount/getIndexAt
        // first count the array size
        int count = 0;
        ArrayList<Integer> emptyIds = null;
        for (int i = 0; i < mResourceData.length; i++) {
            ResourceValue data = mResourceData[i];
            if (data != null) {
                String dataValue = data.getValue();
                if (REFERENCE_NULL.equals(dataValue) || REFERENCE_UNDEFINED.equals(dataValue)) {
                    mResourceData[i] = null;
                } else if (REFERENCE_EMPTY.equals(dataValue)) {
                    mResourceData[i] = null;
                    if (emptyIds == null) {
                        emptyIds = new ArrayList<Integer>(4);
                    }
                    emptyIds.add(i);
                } else {
                    count++;
                }
            }
        }

        if (emptyIds != null) {
            mEmptyIds = new int[emptyIds.size()];
            for (int i = 0; i < emptyIds.size(); i++) {
                mEmptyIds[i] = emptyIds.get(i);
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
     * Set the theme to be used for inflating drawables.
     */
    public void setTheme(Theme theme) {
        mTheme = theme;
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
        // FIXME: handle styled strings!
        return getString(index);
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
        if (!hasValue(index)) {
            return null;
        }
        // As unfortunate as it is, it's possible to use enums with all attribute formats,
        // not just integers/enums. So, we need to search the enums always. In case
        // enums are used, the returned value is an integer.
        Integer v = resolveEnumAttribute(index);
        return v == null ? mResourceData[index].getValue() : String.valueOf((int) v);
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
        String s = getString(index);
        return s == null ? defValue : XmlUtils.convertValueToBoolean(s, defValue);

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
        String s = getString(index);
        try {
            return convertValueToInt(s, defValue);
        } catch (NumberFormatException e) {
            Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                    String.format("\"%1$s\" in attribute \"%2$s\" is not a valid integer",
                            s, mNames[index]),
                    null);
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
        String s = getString(index);
        try {
            if (s != null) {
                    return Float.parseFloat(s);
            }
        } catch (NumberFormatException e) {
            Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                    String.format("\"%1$s\" in attribute \"%2$s\" cannot be converted to float.",
                            s, mNames[index]),
                    null);
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
        if (index < 0 || index >= mResourceData.length) {
            return defValue;
        }

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
        if (!hasValue(index)) {
            return null;
        }

        ResourceValue resValue = mResourceData[index];
        String value = resValue.getValue();

        if (value == null) {
            return null;
        }

        // let the framework inflate the ColorStateList from the XML file.
        File f = new File(value);
        if (f.isFile()) {
            try {
                XmlPullParser parser = ParserFactory.create(f);

                BridgeXmlBlockParser blockParser = new BridgeXmlBlockParser(
                        parser, mContext, resValue.isFramework());
                try {
                    return ColorStateList.createFromXml(mContext.getResources(), blockParser,
                            mContext.getTheme());
                } finally {
                    blockParser.ensurePopped();
                }
            } catch (XmlPullParserException e) {
                Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                        "Failed to configure parser for " + value, e, null);
                return null;
            } catch (Exception e) {
                // this is an error and not warning since the file existence is checked before
                // attempting to parse it.
                Bridge.getLog().error(LayoutLog.TAG_RESOURCES_READ,
                        "Failed to parse file " + value, e, null);

                return null;
            }
        }

        try {
            int color = ResourceHelper.getColor(value);
            return ColorStateList.valueOf(color);
        } catch (NumberFormatException e) {
            Bridge.getLog().error(LayoutLog.TAG_RESOURCES_FORMAT, e.getMessage(), e, null);
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
        return getInt(index, defValue);
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
        String s = getString(index);
        if (s == null) {
            return defValue;
        }
        // Check if the value is a magic constant that doesn't require a unit.
        try {
            int i = Integer.parseInt(s);
            if (i == LayoutParams.MATCH_PARENT || i == LayoutParams.WRAP_CONTENT) {
                return i;
            }
        } catch (NumberFormatException ignored) {
            // pass
        }

        if (ResourceHelper.parseFloatAttribute(mNames[index], s, mValue, true)) {
            return mValue.getDimension(mBridgeResources.getDisplayMetrics());
        }

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
        try {
            return getDimension(index, null);
        } catch (RuntimeException e) {
            String s = getString(index);

            if (s != null) {
                // looks like we were unable to resolve the dimension value
                Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                        String.format("\"%1$s\" in attribute \"%2$s\" is not a valid format.",
                                s, mNames[index]), null);
            }

            return defValue;
        }
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
        try {
            // this will throw an exception if not found.
            return getDimension(index, name);
        } catch (RuntimeException e) {

            if (LayoutInflater_Delegate.sIsInInclude) {
                throw new RuntimeException("Layout Dimension '" + name + "' not found.");
            }

            Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                    "You must supply a " + name + " attribute.", null);

            return 0;
        }
    }

    @Override
    public int getLayoutDimension(int index, int defValue) {
        return getDimensionPixelSize(index, defValue);
    }

    /** @param name attribute name, used for error reporting. */
    private int getDimension(int index, @Nullable String name) {
        String s = getString(index);
        if (s == null) {
            if (name != null) {
                throw new RuntimeException("Attribute '" + name + "' not found");
            }
            throw new RuntimeException();
        }
        // Check if the value is a magic constant that doesn't require a unit.
        try {
            int i = Integer.parseInt(s);
            if (i == LayoutParams.MATCH_PARENT || i == LayoutParams.WRAP_CONTENT) {
                return i;
            }
        } catch (NumberFormatException ignored) {
            // pass
        }
        if (ResourceHelper.parseFloatAttribute(mNames[index], s, mValue, true)) {
            float f = mValue.getDimension(mBridgeResources.getDisplayMetrics());

            final int res = (int)(f+0.5f);
            if (res != 0) return res;
            if (f == 0) return 0;
            if (f > 0) return 1;
        }

        throw new RuntimeException();
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
        String value = getString(index);
        if (value == null) {
            return defValue;
        }

        if (ResourceHelper.parseFloatAttribute(mNames[index], value, mValue, false)) {
            return mValue.getFraction(base, pbase);
        }

        // looks like we were unable to resolve the fraction value
        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_FORMAT,
                String.format(
                        "\"%1$s\" in attribute \"%2$s\" cannot be converted to a fraction.",
                        value, mNames[index]), null);

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
        if (index < 0 || index >= mResourceData.length) {
            return defValue;
        }

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
            boolean create = value.startsWith("@+");
            boolean isFrameworkId =
                    mPlatformFile || value.startsWith("@android") || value.startsWith("@+android");

            // Look for the idName in project or android R class depending on isPlatform.
            if (create) {
                Integer idValue;
                if (isFrameworkId) {
                    idValue = Bridge.getResourceId(ResourceType.ID, idName);
                } else {
                    idValue = mContext.getLayoutlibCallback().getResourceId(ResourceType.ID, idName);
                }
                return idValue == null ? defValue : idValue;
            }
            // This calls the same method as in if(create), but doesn't create a dynamic id, if
            // one is not found.
            if (isFrameworkId) {
                return mContext.getFrameworkResourceValue(ResourceType.ID, idName, defValue);
            } else {
                return mContext.getProjectResourceValue(ResourceType.ID, idName, defValue);
            }
        }

        // not a direct id valid reference? resolve it
        Integer idValue;

        if (resValue.isFramework()) {
            idValue = Bridge.getResourceId(resValue.getResourceType(),
                    resValue.getName());
        } else {
            idValue = mContext.getLayoutlibCallback().getResourceId(
                    resValue.getResourceType(), resValue.getName());
        }

        if (idValue != null) {
            return idValue;
        }

        Bridge.getLog().warning(LayoutLog.TAG_RESOURCES_RESOLVE,
                String.format(
                    "Unable to resolve id \"%1$s\" for attribute \"%2$s\"", value, mNames[index]),
                    resValue);

        return defValue;
    }

    @Override
    public int getThemeAttributeId(int index, int defValue) {
        // TODO: Get the right Theme Attribute ID to enable caching of the drawables.
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
        if (!hasValue(index)) {
            return null;
        }

        ResourceValue value = mResourceData[index];
        return ResourceHelper.getDrawable(value, mContext, mTheme);
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
        if (!hasValue(index)) {
            return null;
        }
        ResourceValue resVal = mResourceData[index];
        if (resVal instanceof ArrayResourceValue) {
            ArrayResourceValue array = (ArrayResourceValue) resVal;
            int count = array.getElementCount();
            return count >= 0 ? mBridgeResources.fillValues(array, new CharSequence[count]) : null;
        }
        int id = getResourceId(index, 0);
        String resIdMessage = id > 0 ? " (resource id 0x" + Integer.toHexString(id) + ')' : "";
        throw new NotFoundException(
                String.format("%1$s in %2$s%3$s is not a valid array resource.",
                        resVal.getValue(), mNames[index], resIdMessage));
    }

    @Override
    public int[] extractThemeAttrs() {
        // The drawables are always inflated with a Theme and we don't care about caching. So,
        // just return.
        return null;
    }

    @Override
    public int getChangingConfigurations() {
        // We don't care about caching. Any change in configuration is a fresh render. So,
        // just return.
        return 0;
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
        String s = getString(index);
        return s != null && ResourceHelper.parseFloatAttribute(mNames[index], s, outValue, false);
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public int getType(int index) {
        String value = getString(index);
        if (value == null) {
            return TYPE_NULL;
        }
        if (value.startsWith(PREFIX_RESOURCE_REF)) {
            return TYPE_REFERENCE;
        }
        if (value.startsWith(PREFIX_THEME_REF)) {
            return TYPE_ATTRIBUTE;
        }
        try {
            // Don't care about the value. Only called to check if an exception is thrown.
            convertValueToInt(value, 0);
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return TYPE_INT_HEX;
            }
            // is it a color?
            if (value.startsWith("#")) {
                int length = value.length() - 1;
                if (length == 3) {  // rgb
                    return TYPE_INT_COLOR_RGB4;
                }
                if (length == 4) {  // argb
                    return TYPE_INT_COLOR_ARGB4;
                }
                if (length == 6) {  // rrggbb
                    return TYPE_INT_COLOR_RGB8;
                }
                if (length == 8) {  // aarrggbb
                    return TYPE_INT_COLOR_ARGB8;
                }
            }
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                return TYPE_INT_BOOLEAN;
            }
            return TYPE_INT_DEC;
        } catch (NumberFormatException ignored) {
            try {
                Float.parseFloat(value);
                return TYPE_FLOAT;
            } catch (NumberFormatException ignore) {
            }
            // Might be a dimension.
            if (ResourceHelper.parseFloatAttribute(null, value, new TypedValue(), false)) {
                return TYPE_DIMENSION;
            }
        }
        // TODO: handle fractions.
        return TYPE_STRING;
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
        return index >= 0 && index < mResourceData.length && mResourceData[index] != null;
    }

    @Override
    public boolean hasValueOrEmpty(int index) {
        return hasValue(index) || index >= 0 && index < mResourceData.length &&
                mEmptyIds != null && Arrays.binarySearch(mEmptyIds, index) >= 0;
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
        if (index < 0 || index >= mResourceData.length) {
            return null;
        }

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
     * Give back a previously retrieved TypedArray, for later re-use.
     */
    @Override
    public void recycle() {
        // pass
    }

    @Override
    public String toString() {
        return Arrays.toString(mResourceData);
    }

    /**
     * Searches for the string in the attributes (flag or enums) and returns the integer.
     * If found, it will return an integer matching the value.
     *
     * @param index Index of attribute to retrieve.
     *
     * @return Attribute int value, or null if not defined.
     */
    private Integer resolveEnumAttribute(int index) {
        // Get the map of attribute-constant -> IntegerValue
        Map<String, Integer> map = null;
        if (mIsFramework[index]) {
            map = Bridge.getEnumValues(mNames[index]);
        } else {
            // get the styleable matching the resolved name
            RenderResources res = mContext.getRenderResources();
            ResourceValue attr = res.getProjectResource(ResourceType.ATTR, mNames[index]);
            if (attr instanceof AttrResourceValue) {
                map = ((AttrResourceValue) attr).getAttributeValues();
            }
        }

        if (map != null) {
            // accumulator to store the value of the 1+ constants.
            int result = 0;
            boolean found = false;

            // split the value in case this is a mix of several flags.
            String[] keywords = mResourceData[index].getValue().split("\\|");
            for (String keyword : keywords) {
                Integer i = map.get(keyword.trim());
                if (i != null) {
                    result |= i;
                    found = true;
                }
                // TODO: We should act smartly and log a warning for incorrect keywords. However,
                // this method is currently called even if the resourceValue is not an enum.
            }
            if (found) {
                return result;
            }
        }

        return null;
    }

    /**
     * Copied from {@link XmlUtils#convertValueToInt(CharSequence, int)}, but adapted to account
     * for aapt, and the fact that host Java VM's Integer.parseInt("XXXXXXXX", 16) cannot handle
     * "XXXXXXXX" > 80000000.
     */
    private static int convertValueToInt(@Nullable String charSeq, int defValue) {
        if (null == charSeq || charSeq.isEmpty())
            return defValue;

        int sign = 1;
        int index = 0;
        int len = charSeq.length();
        int base = 10;

        if ('-' == charSeq.charAt(0)) {
            sign = -1;
            index++;
        }

        if ('0' == charSeq.charAt(index)) {
            //  Quick check for a zero by itself
            if (index == (len - 1))
                return 0;

            char c = charSeq.charAt(index + 1);

            if ('x' == c || 'X' == c) {
                index += 2;
                base = 16;
            } else {
                index++;
                // Leave the base as 10. aapt removes the preceding zero, and thus when framework
                // sees the value, it only gets the decimal value.
            }
        } else if ('#' == charSeq.charAt(index)) {
            return ResourceHelper.getColor(charSeq) * sign;
        } else if ("true".equals(charSeq) || "TRUE".equals(charSeq)) {
            return -1;
        } else if ("false".equals(charSeq) || "FALSE".equals(charSeq)) {
            return 0;
        }

        // Use Long, since we want to handle hex ints > 80000000.
        return ((int)Long.parseLong(charSeq.substring(index), base)) * sign;
    }

    static TypedArray obtain(Resources res, int len) {
        return res instanceof BridgeResources ?
                new BridgeTypedArray(((BridgeResources) res), null, len, true) : null;
    }
}
