package com.tmobile.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.EditText;

public class EditTextPreference extends Preference {
    
    class FocusClass implements OnFocusChangeListener{
        public void onFocusChange(View v, boolean hasFocus) {
            // TODO Auto-generated method stub
            if(v == mEditTextBox && !hasFocus){
                persistString(mEditTextBox.getText().toString());
            }
        }
    }
    
    public static final String TAG = "EditTextPreference"; 
    
	private String mDefault="";
	EditText mEditTextBox;

	public EditTextPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public EditTextPreference(Context context, AttributeSet attrs) {
		this(context, attrs, com.android.internal.R.attr.tmobileEditTextPreferenceStyle);
	}

	public EditTextPreference(Context context) {
		this(context, null); 
	}

	public void setText(String value) {
	    
	    mDefault = value;
	   Log.e(TAG, "value >>> "+value);
	}

	public String getText() {
		return mEditTextBox.getText().toString();
	}

	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		mEditTextBox = (EditText) view
				.findViewById(com.android.internal.R.id.tmobile_editbox_preferences);
		mEditTextBox.setText( mDefault);
		mEditTextBox.setFocusableInTouchMode(true);
		mEditTextBox.setOnFocusChangeListener(new FocusClass());
	}
	
	@Override
    public boolean shouldDisableDependents() {
	    return super.shouldDisableDependents();
    }

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
	    return a.getString(index);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setText(restoreValue ? this.getPersistedString(mDefault)
				: (String) defaultValue);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			// No need to save instance state since it's persistent
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.value = getText();
		return myState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			// Didn't save state for us in onSaveInstanceState
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setText(myState.value);
	}

	private static class SavedState extends BaseSavedState {
		String value;

		public SavedState(Parcel source) {
			super(source);
			value = source.readString();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(value);
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
	
	@Override
    protected void onClick() {
        super.onClick();
        mEditTextBox.setFocusableInTouchMode(true);
        mEditTextBox.setEnabled(true);
        // Data has changed, notify so UI can be refreshed!
        notifyChanged();
	}

}
