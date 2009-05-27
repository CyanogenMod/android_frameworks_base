package com.tmobile.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SpinnerAdapter;

import com.tmobile.widget.ComboSpinner;

public class SpinnerPreference extends Preference implements
		ComboSpinner.IDataBinder {

	private int mSelected;
	private SpinnerAdapter mAdapter;

	public SpinnerPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public SpinnerPreference(Context context, AttributeSet attrs) {
		this(context, attrs, com.android.internal.R.attr.spinnerPreferenceStyle);
	}

	public SpinnerPreference(Context context) {
		this(context, null); 
	}

	private int getSelectedIndex() {
		return mSelected;
	}

	private void setSelectedIndex(int pos) {
		mSelected = pos;
		persistInt(pos);
		notifyDependencyChange(shouldDisableDependents());
		notifyChanged();
	}

	public void setAdapter(SpinnerAdapter adapter) {
		mAdapter = adapter;
	}

	public SpinnerAdapter getAdapter() {
		return mAdapter;
	}

	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		ComboSpinner spinner = (ComboSpinner) view
				.findViewById(com.android.internal.R.id.spinner_preferences);
		if (spinner != null) {
			spinner.setAdapter(getAdapter());
			spinner.setSelection(mSelected);
			spinner.setDataBinder(this);
		}
	}
	
	@Override
    public boolean shouldDisableDependents() {
     
        return super.shouldDisableDependents();
    }

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, 0);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setSelectedIndex(restoreValue ? getPersistedInt(mSelected)
				: (Integer) defaultValue);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			// No need to save instance state since it's persistent
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.value = getSelectedIndex();
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
		setSelectedIndex(myState.value);
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

	public void onSelectionChange(int value) {
		if (!callChangeListener(value)) {
			return;
		}
		setSelectedIndex(value);
	}

}
