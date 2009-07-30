/**
 * 
 */
package com.tmobile.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

/**
 * @author gaurav
 * 
 */
public class SeekBarPreference extends Preference implements
		SeekBar.OnSeekBarChangeListener {

	private int mProgress;
	private Drawable mThumb;
	private int mThumbOffset;
	private int mMinWidth;
	private int mMax;

	public SeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public SeekBarPreference(Context context, AttributeSet attrs) {
		this(context, attrs, com.android.internal.R.attr.seekBarPreferenceStyle);

	}

	public SeekBarPreference(Context context) {
		this(context, null);
	}

	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		SeekBar seekbar = (SeekBar) view
				.findViewById(com.android.internal.R.id.seekbar_preference);
		if (seekbar != null) {
			if (mMax > 0)
				seekbar.setMax(mMax);
			if (mThumb != null)
				seekbar.setThumb(mThumb);
			if (mThumbOffset > 0)
				seekbar.setThumbOffset(mThumbOffset);
			if (mMinWidth > 0)
				seekbar.setMinimumWidth(mMinWidth);
			if (mProgress > 0)
				seekbar.setProgress(mProgress);
			seekbar.setOnSeekBarChangeListener(this);
		}
	}

	private int getProgress() {
		return mProgress;
	}

	public void setProgress(int progress) {
		mProgress = progress;

		persistInt(progress);

		notifyDependencyChange(shouldDisableDependents());

		notifyChanged();
	}

	public Drawable getThumb() {
		return mThumb;
	}

	public void setThumb(Drawable thumb) {
		mThumb = thumb;
	}

	public int getThumbOffset() {
		return mThumbOffset;
	}

	public void setThumbOffset(int thumbOffset) {
		mThumbOffset = thumbOffset;
	}

	public void setMinWidth(int minWidth) {
		mMinWidth = minWidth;
	}

	public int getMinWidth() {
		return mMinWidth;
	}

	public void setMax(int max) {
		mMax = max;
	}

	public int getMax() {
		return mMax;
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, 0);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setProgress(restoreValue ? getPersistedInt(mProgress)
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
		myState.value = getProgress();
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
		setProgress(myState.value);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.widget.SeekBar.OnSeekBarChangeListener#onProgressChanged(android
	 * .widget.SeekBar, int, boolean)
	 */
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {

		if (!callChangeListener(progress)) {
			return;
		}
		setProgress(progress);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.widget.SeekBar.OnSeekBarChangeListener#onStartTrackingTouch(android
	 * .widget.SeekBar)
	 */
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.widget.SeekBar.OnSeekBarChangeListener#onStopTrackingTouch(android
	 * .widget.SeekBar)
	 */
	public void onStopTrackingTouch(SeekBar seekBar) {

	}

}
