/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.app;

import com.android.internal.R;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.ListFragment;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import java.util.ArrayList;

public class LocalePicker extends ListFragment {
    private static final String TAG = "LocalePicker";
    private static final boolean DEBUG = false;

    public static interface LocaleSelectionListener {
        // You can add any argument if you really need it...
        public void onLocaleSelected(Locale locale);
    }

    protected boolean isInDeveloperMode() {
        return false;
    }

    LocaleSelectionListener mListener;  // default to null

    public static class LocaleInfo implements Comparable<LocaleInfo> {
        static final Collator sCollator = Collator.getInstance();

        String label;
        Locale locale;

        public LocaleInfo(String label, Locale locale) {
            this.label = label;
            this.locale = locale;
        }

        public String getLabel() {
            return label;
        }

        public Locale getLocale() {
            return locale;
        }

        @Override
        public String toString() {
            return this.label;
        }

        @Override
        public int compareTo(LocaleInfo another) {
            return sCollator.compare(this.label, another.label);
        }
    }

    /**
     * Constructs an Adapter object containing Locale information. Content is sorted by
     * {@link LocaleInfo#label}.
     */
    public static ArrayAdapter<LocaleInfo> constructAdapter(Context context) {
        return constructAdapter(context, false /* disable pesudolocales */);
    }

    public static ArrayAdapter<LocaleInfo> constructAdapter(Context context,
                                                            final boolean isInDeveloperMode) {
        return constructAdapter(context, R.layout.locale_picker_item, R.id.locale,
                isInDeveloperMode);
    }

    public static ArrayAdapter<LocaleInfo> constructAdapter(Context context,
            final int layoutId, final int fieldId) {
        return constructAdapter(context, layoutId, fieldId, false /* disable pseudolocales */);
    }

    public static ArrayAdapter<LocaleInfo> constructAdapter(Context context,
            final int layoutId, final int fieldId, final boolean isInDeveloperMode) {
        final Resources resources = context.getResources();

        ArrayList<String> localeList = new ArrayList<String>(Arrays.asList(
                Resources.getSystem().getAssets().getLocales()));
        if (isInDeveloperMode) {
            if (!localeList.contains("zz_ZZ")) {
                localeList.add("zz_ZZ");
            }
        /** - TODO: Enable when zz_ZY Pseudolocale is complete
         *  if (!localeList.contains("zz_ZY")) {
         *      localeList.add("zz_ZY");
         *	}
         */
        }
        String[] locales = new String[localeList.size()];
        locales = localeList.toArray(locales);

        final String[] specialLocaleCodes = resources.getStringArray(R.array.special_locale_codes);
        final String[] specialLocaleNames = resources.getStringArray(R.array.special_locale_names);
        Arrays.sort(locales);
        final int origSize = locales.length;
        final LocaleInfo[] preprocess = new LocaleInfo[origSize];
        int finalSize = 0;
        for (int i = 0 ; i < origSize; i++ ) {
            final String s = locales[i];
            final int len = s.length();
            if (len == 5) {
                String language = s.substring(0, 2);
                String country = s.substring(3, 5);
                final Locale l = new Locale(language, country);

                if (finalSize == 0) {
                    if (DEBUG) {
                        Log.v(TAG, "adding initial "+ toTitleCase(l.getDisplayLanguage(l)));
                    }
                    preprocess[finalSize++] =
                            new LocaleInfo(toTitleCase(l.getDisplayLanguage(l)), l);
                } else {
                    // check previous entry:
                    //  same lang and a country -> upgrade to full name and
                    //    insert ours with full name
                    //  diff lang -> insert ours with lang-only name
                    if (preprocess[finalSize-1].locale.getLanguage().equals(
                            language) &&
                            !preprocess[finalSize-1].locale.getLanguage().equals("zz")) {
                        if (DEBUG) {
                            Log.v(TAG, "backing up and fixing "+
                                    preprocess[finalSize-1].label+" to "+
                                    getDisplayName(preprocess[finalSize-1].locale,
                                            specialLocaleCodes, specialLocaleNames));
                        }
                        preprocess[finalSize-1].label = toTitleCase(
                                getDisplayName(preprocess[finalSize-1].locale,
                                        specialLocaleCodes, specialLocaleNames));
                        if (DEBUG) {
                            Log.v(TAG, "  and adding "+ toTitleCase(
                                    getDisplayName(l, specialLocaleCodes, specialLocaleNames)));
                        }
                        preprocess[finalSize++] =
                                new LocaleInfo(toTitleCase(
                                        getDisplayName(
                                                l, specialLocaleCodes, specialLocaleNames)), l);
                    } else {
                        String displayName;
                        if (s.equals("zz_ZZ")) {
                            displayName = "[Developer] Accented English";
                        } else if (s.equals("zz_ZY")) {
                            displayName = "[Developer] Fake Bi-Directional";
                        } else {
                            displayName = toTitleCase(l.getDisplayLanguage(l));
                        }
                        if (DEBUG) {
                            Log.v(TAG, "adding "+displayName);
                        }
                        preprocess[finalSize++] = new LocaleInfo(displayName, l);
                    }
                }
            }
        }

        final LocaleInfo[] localeInfos = new LocaleInfo[finalSize];
        System.arraycopy(preprocess, 0, localeInfos, 0, finalSize);
        Arrays.sort(localeInfos);

        final LayoutInflater inflater =
                (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return new ArrayAdapter<LocaleInfo>(context, layoutId, fieldId, localeInfos) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                TextView text;
                if (convertView == null) {
                    view = inflater.inflate(layoutId, parent, false);
                    text = (TextView) view.findViewById(fieldId);
                    view.setTag(text);
                } else {
                    view = convertView;
                    text = (TextView) view.getTag();
                }
                LocaleInfo item = getItem(position);
                text.setText(item.toString());
                text.setTextLocale(item.getLocale());

                return view;
            }
        };
    }

    private static String toTitleCase(String s) {
        if (s.length() == 0) {
            return s;
        }

        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String getDisplayName(
            Locale l, String[] specialLocaleCodes, String[] specialLocaleNames) {
        String code = l.toString();

        for (int i = 0; i < specialLocaleCodes.length; i++) {
            if (specialLocaleCodes[i].equals(code)) {
                return specialLocaleNames[i];
            }
        }

        return l.getDisplayName(l);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final ArrayAdapter<LocaleInfo> adapter = constructAdapter(getActivity(),
                isInDeveloperMode());
        setListAdapter(adapter);
    }

    public void setLocaleSelectionListener(LocaleSelectionListener listener) {
        mListener = listener;
    }

    @Override
    public void onResume() {
        super.onResume();
        getListView().requestFocus();
    }

    /**
     * Each listener needs to call {@link #updateLocale(Locale)} to actually change the locale.
     *
     * We don't call {@link #updateLocale(Locale)} automatically, as it halt the system for
     * a moment and some callers won't want it.
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (mListener != null) {
            final Locale locale = ((LocaleInfo)getListAdapter().getItem(position)).locale;
            mListener.onLocaleSelected(locale);
        }
    }

    /**
     * Requests the system to update the system locale. Note that the system looks halted
     * for a while during the Locale migration, so the caller need to take care of it.
     */
    public static void updateLocale(Locale locale) {
        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            Configuration config = am.getConfiguration();

            // Will set userSetLocale to indicate this isn't some passing default - the user
            // wants this remembered
            config.setLocale(locale);

            am.updateConfiguration(config);
            // Trigger the dirty bit for the Settings Provider.
            BackupManager.dataChanged("com.android.providers.settings");
        } catch (RemoteException e) {
            // Intentionally left blank
        }
    }
}
