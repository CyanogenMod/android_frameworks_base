
package android.content.res;

import java.util.Arrays;

import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
/**
 * 
 * @author 
 * @hide
 */
public class TypedArrayComposite extends TypedArray {
    private static final String TAG = "TypedArrayComposite";
    
    private final TypedArray mDefaultTypedArray;
    private final TypedArray mCustomTypedArray;
    private final boolean mAlwaysUseDefault;
    
    /* Attribute set which determines whether the attribute index should use the themed
     * array or the packages array. */
    private final boolean[] mAttrsMap;

    private TypedArrayComposite(TypedArray ta1, TypedArray ta2, AttributeSet set, int[] attrs) {
        mDefaultTypedArray = ta1;
        mCustomTypedArray = ta2;

        /* XXX: This is a hack that should eventually be properly implemented.  The idea here is
         * that if style="..." is manually specified in the XML attributes, we abort any
         * theme processing.  For a proper implementation, we should deep examine the style
         * and determine what attributes we can still use from the theme. */
        boolean alwaysUseDefault = false;
        if (set != null) {
            if (set.getStyleAttribute() != 0) {
                alwaysUseDefault = true;
            }
        }
//        mAlwaysUseDefault = alwaysUseDefault;
        mAlwaysUseDefault = false;
        
        /* Construct a map which forces our custom theme to be overridden by
         * any attribute specified in XML. */
        if (alwaysUseDefault == false && set != null && attrs != null) {
            int n = set.getAttributeCount();
            mAttrsMap = new boolean[attrs.length];
            while (n-- > 0) {
                /* When we encounter something like andorid:layout_height="?android:attr/listPreferredItemHeight",
                 * it's important that we allow the custom theme to resolve it.
                 * XXX: Do we need to check that the theme has actually
                 * provided this attribute? */
                if (set.getAttributeDataType(n) != TypedValue.TYPE_ATTRIBUTE) {
                    int attrResId = set.getAttributeNameResource(n);
                    /* XXX: Can we binary search attrs?  It seems to be
                     * sorted, but I'm not certain that's guaranteed? */
                    int index = Arrays.binarySearch(attrs, attrResId);
                    if (index >= 0) {
                        mAttrsMap[index] = true;
                    }
                }
            }
        } else {
            mAttrsMap = null;
        }
    }
    
    public static TypedArray newInstance(TypedArray a1, TypedArray a2, AttributeSet set,
            int[] attrs) {
        return new TypedArrayComposite(a1, a2, set, attrs);
    }

    private boolean useCustomTypedArray(int index) {
        if (mCustomTypedArray == null || mAlwaysUseDefault == true) {
            return false;
        }

        /* Check that the AttributeSet didn't manually set a value for this
         * attribute.  If so, then we consider this a user-supplied value and
         * it _ALWAYS_ overrides theme. */
        if (mAttrsMap != null && mAttrsMap[index] == true) {
            return false;
        }

        return mCustomTypedArray.hasValue(index);
    }

    @Override
    public int getColor(int index, int defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getColor(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getColor(index, defValue);
    }

    @Override
    public ColorStateList getColorStateList(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getColorStateList(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getColorStateList(index);
    }

    public Drawable getDrawable(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getDrawable(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getDrawable(index);
    }

    public boolean getBoolean(int index, boolean defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getBoolean(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getBoolean(index, defValue);
    }

    public float getDimension(int index, float defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getDimension(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getDimension(index, defValue);
    }

    public int getDimensionPixelOffset(int index, int defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getDimensionPixelOffset(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getDimensionPixelOffset(index, defValue);
    }

    public int getDimensionPixelSize(int index, int defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getDimensionPixelSize(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getDimensionPixelSize(index, defValue);
    }

    public float getFloat(int index, float defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getFloat(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getFloat(index, defValue);
    }

    public float getFraction(int index, int base, int pbase, float defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getFraction(index, base, pbase, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getFraction(index, base, pbase, defValue);
    }

    public int getInt(int index, int defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getInt(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getInt(index, defValue);
    }

    public int getInteger(int index, int defValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getInteger(index, defValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getInteger(index, defValue);
    }

    public int getLayoutDimension(int index, String name) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getLayoutDimension(index, name);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getLayoutDimension(index, name);
    }

    public String getNonResourceString(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getNonResourceString(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getNonResourceString(index);
    }

    public String getString(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getString(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getString(index);
    }

    public CharSequence getText(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getText(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getText(index);
    }

    public CharSequence[] getTextArray(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getTextArray(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getTextArray(index);
    }
    
    public int getResourceId(int index, int defValue) {
//        if (useCustomTypedArray(index)) {
//            try {
//                return mCustomTypedArray.getResourceId(index, defValue);
//            } catch (Exception e) {
//                Log.e(TAG, "Failure resolving value from theme!", e);
//            }
//        }
        return mDefaultTypedArray.getResourceId(index, defValue);
    }

    public boolean getValue(int index, TypedValue outValue) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.getValue(index, outValue);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.getValue(index, outValue);
    }

    public boolean hasValue(int index) {
        if (useCustomTypedArray(index)) {
            return true;
        }
        return mDefaultTypedArray.hasValue(index);
    }

    public TypedValue peekValue(int index) {
        if (useCustomTypedArray(index)) {
            try {
                return mCustomTypedArray.peekValue(index);
            } catch (Exception e) {
                Log.e(TAG, "Failure resolving value from theme!", e);
            }
        }
        return mDefaultTypedArray.peekValue(index);
    }
    
    public int getIndex(int at) {
        return mDefaultTypedArray.getIndex(at);
    }

    public int getIndexCount() {
        return mDefaultTypedArray.getIndexCount();
    }

    public String getPositionDescription() {
        return mDefaultTypedArray.getPositionDescription();
    }

    public Resources getResources() {
        return mDefaultTypedArray.getResources();
    }

    public int length() {
        return mDefaultTypedArray.length();
    }

    public String toString() {
        return mDefaultTypedArray.toString();
    }

    public void recycle() {
        mDefaultTypedArray.recycle();
        if (mCustomTypedArray != null) {
            mCustomTypedArray.recycle();
        }
    }
    
    private void printAttributeSet(AttributeSet set){
        if(set != null){
            String s = "AttributeSet:";
            for (int i=0; i< set.getAttributeCount(); i++) {
                s = s + " " + set.getAttributeName(i);
                int id = set.getAttributeNameResource(i);
                if (id != 0) {
                    s = s + "(0x" + Integer.toHexString(id) + ")";
                }
                s = s + "=" + set.getAttributeValue(i);
            }
            
            Log.d("TypedArrayComposite", "Attribute Set:"+ s);
        }
    }

}
