package android.content.res;

import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;

public class TypedArrayComposite extends TypedArray {
	//private TypedArray mDefaultTypedArray = null;
	private TypedArray mCustomTypedArray = null;
	private AttributeSet mSet = null;

	public TypedArrayComposite(TypedArray ta1, TypedArray ta2, AttributeSet set) {
		super(ta1.getResources(), ta1.mData, ta1.mIndices, ta1.mLength);
		// TODO create another constructor with mXml and mRsrcs
		super.mXml = ta1.mXml;
		super.mRsrcs = ta1.mRsrcs;

		//mDefaultTypedArray = ta1;
		mCustomTypedArray = ta2;		
		mSet = set;

	}// end of constructor
	
	@Override
	public int getColor(int index, int defValue) {
		int retValue = 0;
		try {
			if(mSet != null && mSet.getAttributeResourceValue(index, 0) != 0){
				//Log.d("TypedArrayComposite: ", "Attribute is defined with value:"+ mSet.getAttributeResourceValue(index, 0));
				return super.getColor(index, defValue);
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

		return super.getColor(index, defValue);
	}

	@Override
	public ColorStateList getColorStateList(int index) {
		try {
			if(mSet != null && mSet.getAttributeResourceValue(index, 0) != 0){
				return super.getColorStateList(index);
			}
			if (mCustomTypedArray != null) {
				// mCustomTypedArraay
				ColorStateList colorList = mCustomTypedArray.getColorStateList(index);
				if (colorList != null) {
					return colorList;
				}
			}

		} catch (Exception ex) {
			Log.d("TypedArrayComposite", "getColorStateList Exception ", ex);
		}

		return super.getColorStateList(index);
	}

	
	 public Drawable getDrawable(int index) {
			try {
				if(mSet != null && mSet.getAttributeResourceValue(index, 0) != 0){					
					return super.getDrawable(index);
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
			
			return super.getDrawable(index);
	 }
	
	 
	
	public boolean getBoolean(int index, boolean defValue) {
		boolean retValue = false;
		try {
			if(mCustomTypedArray == null){
				return super.getBoolean(index, defValue);
			}
			if(mSet != null && mSet.getAttributeResourceValue(index, 0) != 0){
				return super.getBoolean(index, defValue);
			}
			
			if (mCustomTypedArray != null) {
				retValue = mCustomTypedArray.getBoolean(index, defValue);
			}
		} catch (Exception ex) {
			Log.d("TypedArrayComposite", "getBoolean", ex);
		}
		if (retValue !=  defValue) {
			return retValue;
		}

		return  super.getBoolean(index, defValue);
	}

	
	 public float getDimension(int index, float defValue) {
		try {
			if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
				return super.getDimension(index, defValue);
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

		return super.getDimension(index, defValue);
	 }
	 
	 public int getDimensionPixelOffset(int index, int defValue) {
			try {
				if((mCustomTypedArray == null) ||(mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getDimensionPixelOffset(index, defValue);
				}
				if (mCustomTypedArray != null) {
					int dimensionPixelOffest = mCustomTypedArray.getDimensionPixelOffset(index, defValue);
					if (dimensionPixelOffest != defValue) {
						return dimensionPixelOffest;
					}
				}

			} catch (Exception ex) {
				Log.d("TypedArrayComposite", "getDimensionPixelOffset Exception ");
			}

			return super.getDimensionPixelOffset(index, defValue);
	 }
	
	 
	 public int getDimensionPixelSize(int index, int defValue) {
		try {
			if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
				return super.getDimensionPixelSize(index, defValue);
			}
			
			if (mCustomTypedArray != null) {
				int dimensionPixelSize = mCustomTypedArray.getDimensionPixelSize(index, defValue);
				if (dimensionPixelSize != defValue) {
					return dimensionPixelSize;
				}
			}

		} catch (Exception ex) {
			Log.d("TypedArrayComposite", "getDimensionPixelSize Exception ");
		}

		return super.getDimensionPixelSize(index, defValue);		 
	 }
	 
	 
	 public float getFloat(int index, float defValue) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getFloat(index, defValue);
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

			return super.getFloat(index, defValue);		 
	 }
	 
	 public float getFraction(int index, int base, int pbase, float defValue) {
			try {
				if((mCustomTypedArray == null) | (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getFraction(index, base, pbase, defValue);
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

			return super.getFraction(index, base, pbase, defValue);			 
	 }
	 
	 public int getInt(int index, int defValue) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getInt(index, defValue);
				}
				if (mCustomTypedArray != null) {
					int intValue = mCustomTypedArray.getInt(index, defValue);
					if (intValue != defValue) {
						return intValue;
					}
				}

			} catch (Exception ex) {
				Log.d("TypedArrayComposite", "getInt Exception ");
			}

			return super.getInt(index, defValue);					 
	 }
	 
	 public int getInteger(int index, int defValue) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getInteger(index, defValue);
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

			return super.getInteger(index, defValue);		
	 }

	 public int getLayoutDimension(int index, String name) {
			try {
				//Log.d("TypedArrayComposite:", "getLayoutDimension : attrname:"+ name);
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getLayoutDimension(index, name);
				}
				
				if (mCustomTypedArray != null && mCustomTypedArray.mXml != null) {
					int dimensionPixelSize = mCustomTypedArray.getLayoutDimension(index, name);
					if (dimensionPixelSize > 0 ) {
						return dimensionPixelSize;
					}
				}

			} catch (Resources.NotFoundException ex) {
				Log.d("TypedArrayComposite", "getLayoutDimension Exception");
			}

			return super.getLayoutDimension(index, name); 
	 }
	 
	 public String getNonResourceString(int index) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getNonResourceString(index);
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

			return super.getNonResourceString(index);					 
	 }
	 
	 public String getString(int index) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getString(index);
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

			return super.getString(index);				 
	 }
	 
	 public CharSequence getText(int index) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getText(index);
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

			return super.getText(index);				 
	 }
	 
	 public CharSequence[] getTextArray(int index) {
			try {
				if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
					return super.getTextArray(index);
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

			return super.getTextArray(index);				 
	 }
	 
	 public boolean getValue(int index, TypedValue outValue) {
		if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
			return super.getValue(index, outValue);
		}
		return super.getValue(index, outValue);
	 }
	 
	 public boolean hasValue(int index) {
		if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
			return super.hasValue(index);
		}
		 return super.hasValue(index); 
	 }
	 
	 public TypedValue peekValue(int index) {
		if((mCustomTypedArray == null) || (mSet != null && mSet.getAttributeResourceValue(index, 0) != 0)){
			return super.peekValue(index);
		}
		 return super.peekValue(index);
	 }
	 
	
	 
	 public void recycle() {
		 super.recycle();
		 if(mCustomTypedArray != null){
			 mCustomTypedArray.recycle();		
		 }
	 }
	 
}