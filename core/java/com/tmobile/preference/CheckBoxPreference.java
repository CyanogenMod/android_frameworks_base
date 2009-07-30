package com.tmobile.preference;

import android.content.Context;


public class CheckBoxPreference extends android.preference.CheckBoxPreference {
	/**
	 * Need to change the layout only, everything else will remain same as of android.preference.CheckBoxPreference
	 * @param context
	 */
	public CheckBoxPreference(Context context) {
		super(context);
		setLayoutResource(com.android.internal.R.layout.tmobile_preference_widget_left);
	}
}