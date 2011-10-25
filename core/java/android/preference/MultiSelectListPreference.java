/*
 * Copyright (C) 2011 The CyanogenMod Project
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

package android.preference;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.AttributeSet;

public class MultiSelectListPreference extends ListPreference {

    private static final String SEPARATOR = "OV=I=XseparatorX=I=VO";

    private boolean[] mClickedDialogEntryIndices;

    public MultiSelectListPreference(Context context) {
        super(context);
    }

    public MultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();

        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            throw new IllegalStateException(
                    this.getClass().getSimpleName()
                            + " requires an entries array and an entryValues array which are both the same length");
        }

        mClickedDialogEntryIndices = new boolean[entryValues.length];
        restoreCheckedEntries();
        builder.setMultiChoiceItems(entries, mClickedDialogEntryIndices, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                mClickedDialogEntryIndices[which] = isChecked;
            }
        });
    }

    @Override
    public void setValue(String value) {
        CharSequence[] entryValues = getEntryValues();
        if (value == null && entryValues != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entryValues.length; i++) {
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append(entryValues[i]);
            }
            value = sb.toString();
        }
        super.setValue(value);
    }

    public static String[] parseStoredValue(CharSequence val) {
        if (TextUtils.isEmpty(val)) {
            return null;
        } else {
            return val.toString().split(SEPARATOR);
        }
    }

    private void restoreCheckedEntries() {
        CharSequence[] entryValues = getEntryValues();

        String[] vals = parseStoredValue(getValue());
        if (vals != null) {
            for (String val : vals) {
                for (int i = 0; i < entryValues.length; i++) {
                    CharSequence entry = entryValues[i];
                    if (entry.equals(val)) {
                        mClickedDialogEntryIndices[i] = true;
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        CharSequence[] entryValues = getEntryValues();

        if (positiveResult && entryValues != null) {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < entryValues.length; i++) {
                if (mClickedDialogEntryIndices[i]) {
                    if (value.length() > 0) {
                        value.append(SEPARATOR);
                    }
                    value.append(entryValues[i]);
                }
            }

            String val = value.toString();
            if (callChangeListener(val)) {
                setValue(val);
            }
        }
    }
}
