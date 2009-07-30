package com.tmobile.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.android.internal.R;

public class RadioButtonPreference extends Preference implements OnCheckedChangeListener{
    private CharSequence[] mEntries;
	private int mValue;
	private Context mContext;
	
	public RadioButtonPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.RadioButtonPreference, 0, 0);
        mEntries = a.getTextArray(com.android.internal.R.styleable.RadioButtonPreference_entries);
        a.recycle();
        setSelectable(false);
	}

	public RadioButtonPreference(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.radioButtonPreferenceStyle);

	}

	public RadioButtonPreference(Context context) {
		this(context, null);
		mContext = context;
	}
	

    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
    }
    

    public void setEntries(int entriesResId) {
        setEntries(getContext().getResources().getTextArray(entriesResId));
    }
    
    public CharSequence[] getEntries() {
        return mEntries;
    }

    public void setValue(int value) {
        mValue = value;
        
        persistInt(value);
    }

    public int getValue() {
        return mValue; 
    }
    
    public CharSequence getSelectedEntry() {
    	return mEntries[mValue];
    }

	@Override
	protected void onBindView(View view) {
		super.onBindView(view);//view.setBackgroundDrawable(null);
		RadioGroup radioGroup = (RadioGroup)view.findViewById(R.id.radioButton_preference);
		radioGroup.setBackgroundDrawable(view.getBackground());
		radioGroup.bringToFront();
		if((radioGroup != null) && (radioGroup.getChildCount() == 0)) {
			RadioButton radioButton = null;
			for(int i = 0; i < mEntries.length; i++){
				radioButton = new RadioButton(mContext);
				radioButton.setId(i);
				radioButton.setText(mEntries[i]);
				radioGroup.addView(radioButton);
			}
		}
		radioGroup.check(mValue);
		radioGroup.setOnCheckedChangeListener(this);
	}
	
	
	@Override
    public boolean shouldDisableDependents() {
        return super.shouldDisableDependents();
    }

	@Override
	protected Object onGetDefaultValue(TypedArray a, int id) {
		return a.getInt(id, 0);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setValue(restoreValue ? getPersistedInt(mValue) : (Integer) defaultValue);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			// No need to save instance state since it's persistent
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.value = getValue();
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
		setValue(myState.value);
	}
	
	private static class SavedState extends BaseSavedState {
		int value;

		public SavedState(Parcel source) {
			super(source);
			value = source.readInt();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(value);
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
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		if (!callChangeListener(checkedId)) {
			return;
		}
		setValue(checkedId);
	}
}