
package android.content.res;

import java.util.List;

import android.graphics.drawable.Drawable;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
/**
 * 
 * @author 
 * @hide
 */
public class TypedArrayComposite extends TypedArray {
    private TypedArray mDefaultTypedArray = null;

    private TypedArray mCustomTypedArray = null;

    private AttributeSet mSet = null;

    private TypedArrayComposite(TypedArray ta1, TypedArray ta2, AttributeSet set) {
        // super(ta1.getResources(), ta1.mData, ta1.mIndices, ta1.mLength);
        // // TODO create another constructor with mXml and mRsrcs
        // mDefaultTypedArray.mXml = ta1.mXml;
        // mDefaultTypedArray.mRsrcs = ta1.mRsrcs;

        mDefaultTypedArray = ta1;
        mCustomTypedArray = ta2;
        mSet = set;
    }// end of constructor

    public static TypedArray newInstance(TypedArray a1, TypedArray a2, AttributeSet set) {
        return new TypedArrayComposite(a1, a2, set);
    }

    @Override
    public int getColor(int index, int defValue) {
        int retValue = 0;
        try {
            if (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0) {
                // Log.d("TypedArrayComposite: ",
                // "Attribute is defined with value:"+
                // mSet.getAttributeResourceValue(index, 0));
                return mDefaultTypedArray.getColor(index, defValue);
            }
            if (mCustomTypedArray != null) {
                retValue = mCustomTypedArray.getColor(index, defValue);
                if (retValue > 0) {
                    return retValue;
                }
            }
        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getColor", ex);
        }

        return mDefaultTypedArray.getColor(index, defValue);
    }

    @Override
    public ColorStateList getColorStateList(int index) {
        try {
            if (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0) {
                return mDefaultTypedArray.getColorStateList(index);
            }
            if (mCustomTypedArray != null) {
                // mCustomTypedArraay
                // Debug.waitForDebugger();
                // mCustomTypedArray.dump();
                ColorStateList colorList = mCustomTypedArray.getColorStateList(index);
                if (colorList != null) {
                    return colorList;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getColorStateList Exception ", ex);
        }

        return mDefaultTypedArray.getColorStateList(index);
    }

    public Drawable getDrawable(int index) {
        try {
            if (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0) {
                return mDefaultTypedArray.getDrawable(index);
            }
            if (mCustomTypedArray != null) {
                Drawable drawable = mCustomTypedArray.getDrawable(index);
                if (drawable != null) {
                    return drawable;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getDrawable Exception ");
        }

        return mDefaultTypedArray.getDrawable(index);
    }

    public boolean getBoolean(int index, boolean defValue) {
        boolean retValue = false;
        try {
            if (mCustomTypedArray == null) {
                return mDefaultTypedArray.getBoolean(index, defValue);
            }
            if (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0) {
                return mDefaultTypedArray.getBoolean(index, defValue);
            }

            if (mCustomTypedArray != null) {
                retValue = mCustomTypedArray.getBoolean(index, defValue);
            }
        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getBoolean", ex);
        }
        if (retValue != defValue) {
            return retValue;
        }

        return mDefaultTypedArray.getBoolean(index, defValue);
    }

    public float getDimension(int index, float defValue) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getDimension(index, defValue);
            }
            if (mCustomTypedArray != null) {
                float dimension = mCustomTypedArray.getDimension(index, defValue);
                if (dimension != defValue) {
                    return dimension;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getDimension Exception ");
        }

        return mDefaultTypedArray.getDimension(index, defValue);
    }

    public int getDimensionPixelOffset(int index, int defValue) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getDimensionPixelOffset(index, defValue);
            }
            if (mCustomTypedArray != null) {
                int dimensionPixelOffest = mCustomTypedArray.getDimensionPixelOffset(index,
                        defValue);
                if (dimensionPixelOffest != defValue) {
                    return dimensionPixelOffest;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getDimensionPixelOffset Exception ");
        }

        return mDefaultTypedArray.getDimensionPixelOffset(index, defValue);
    }

    public int getDimensionPixelSize(int index, int defValue) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {                
                return mDefaultTypedArray.getDimensionPixelSize(index, defValue);
            }
  
            if (mCustomTypedArray != null) {
                //mCustomTypedArray.dump();
                //mDefaultTypedArray.dump();
                //Debug.waitForDebugger();
                int dimensionPixelSize = mCustomTypedArray.getDimensionPixelSize(index, defValue);
                if (dimensionPixelSize != defValue) {
                    return dimensionPixelSize;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getDimensionPixelSize Exception ", ex);
        }

        return mDefaultTypedArray.getDimensionPixelSize(index, defValue);
    }

    public float getFloat(int index, float defValue) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getFloat(index, defValue);
            }
            if (mCustomTypedArray != null) {
                float floatValue = mCustomTypedArray.getFloat(index, defValue);
                if (floatValue != defValue) {
                    return floatValue;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getFloat Exception ");
        }

        return mDefaultTypedArray.getFloat(index, defValue);
    }

    public float getFraction(int index, int base, int pbase, float defValue) {
        try {
            if ((mCustomTypedArray == null)
                    | (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getFraction(index, base, pbase, defValue);
            }
            if (mCustomTypedArray != null) {
                float floatValue = mCustomTypedArray.getFraction(index, base, pbase, defValue);
                if (floatValue != defValue) {
                    return floatValue;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getFraction Exception ");
        }

        return mDefaultTypedArray.getFraction(index, base, pbase, defValue);
    }

    public int getInt(int index, int defValue) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getInt(index, defValue);
            }
            if (mCustomTypedArray != null) {
                int intValue = mCustomTypedArray.getInt(index, defValue);
                if (intValue != defValue) {
                    return intValue;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getInt Exception ", ex);
        }

        return mDefaultTypedArray.getInt(index, defValue);
    }

    public int getInteger(int index, int defValue) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getInteger(index, defValue);
            }
            if (mCustomTypedArray != null) {
                int intValue = mCustomTypedArray.getInteger(index, defValue);
                if (intValue != defValue) {
                    return intValue;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getInteger Exception ");
        }

        return mDefaultTypedArray.getInteger(index, defValue);
    }

    public int getLayoutDimension(int index, String name) {
        try {
            // Log.d("TypedArrayComposite:", "getLayoutDimension : attrname:"+
            // name);
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getLayoutDimension(index, name);
            }

            if (mCustomTypedArray != null) {
                int dimensionPixelSize = mCustomTypedArray.getLayoutDimension(index, name);
                if (dimensionPixelSize > 0) {
                    return dimensionPixelSize;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getLayoutDimension Exception");
        }

        return mDefaultTypedArray.getLayoutDimension(index, name);
    }

    public String getNonResourceString(int index) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getNonResourceString(index);
            }
            if (mCustomTypedArray != null) {
                String nonResource = mCustomTypedArray.getNonResourceString(index);
                if (nonResource != null) {
                    return nonResource;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getNonResourceString Exception ");
        }

        return mDefaultTypedArray.getNonResourceString(index);
    }

    public String getString(int index) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getString(index);
            }
            if (mCustomTypedArray != null) {
                String strValue = mCustomTypedArray.getString(index);
                if (strValue != null) {
                    return strValue;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getString Exception ");
        }

        return mDefaultTypedArray.getString(index);
    }

    public CharSequence getText(int index) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getText(index);
            }
            if (mCustomTypedArray != null) {
                CharSequence strValue = mCustomTypedArray.getText(index);
                if (strValue != null) {
                    return strValue;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getText Exception ");
        }

        return mDefaultTypedArray.getText(index);
    }

    public CharSequence[] getTextArray(int index) {
        try {
            if ((mCustomTypedArray == null)
                    || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
                return mDefaultTypedArray.getTextArray(index);
            }

            if (mCustomTypedArray != null) {
                CharSequence[] cSeq = mCustomTypedArray.getTextArray(index);
                if (cSeq != null) {
                    return cSeq;
                }
            }

        } catch (Exception ex) {
            Log.d("TypedArrayComposite", "getTextArray Exception ");
        }

        return mDefaultTypedArray.getTextArray(index);
    }

    public boolean getValue(int index, TypedValue outValue) {
        if ((mCustomTypedArray == null)
                || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
            return mDefaultTypedArray.getValue(index, outValue);
        }
        return mDefaultTypedArray.getValue(index, outValue);
    }

    public boolean hasValue(int index) {
        if ((mCustomTypedArray == null)
                || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
            return mDefaultTypedArray.hasValue(index);
        }
        return mDefaultTypedArray.hasValue(index);
    }

    public TypedValue peekValue(int index) {
        if ((mCustomTypedArray == null)
                || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)) {
            return mDefaultTypedArray.peekValue(index);
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

    public int getResourceId(int index, int defValue) {
        return mDefaultTypedArray.getResourceId(index, defValue);
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
