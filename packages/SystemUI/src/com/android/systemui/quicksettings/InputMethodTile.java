package com.android.systemui.quicksettings;

import java.util.List;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class InputMethodTile extends QuickSettingsTile {

    private InputMethodManager mImm;
    private boolean showTile = false;
    private static final String TAG_TRY_SUPPRESSING_IME_SWITCHER = "TrySuppressingImeSwitcher";

    public InputMethodTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mImm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    mQsc.mBar.collapseAllPanels(true);
                    Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                    pendingIntent.send();
                } catch (Exception e) {}
            }
        };

    }

    private static String getCurrentInputMethodName(Context context, ContentResolver resolver,
            InputMethodManager imm, List<InputMethodInfo> imis, PackageManager pm) {
        if (resolver == null || imis == null) return null;
        final String currentInputMethodId = Settings.Secure.getString(resolver,
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (TextUtils.isEmpty(currentInputMethodId)) return null;
        for (InputMethodInfo imi : imis) {
            if (currentInputMethodId.equals(imi.getId())) {
                final InputMethodSubtype subtype = imm.getCurrentInputMethodSubtype();
                final CharSequence summary = subtype != null
                        ? subtype.getDisplayName(context, imi.getPackageName(),
                                imi.getServiceInfo().applicationInfo)
                        : context.getString(R.string.quick_settings_ime_label);
                return summary.toString();
            }
        }
        return null;
    }

    private boolean needsToShowImeSwitchOngoingNotification(InputMethodManager imm) {
        List<InputMethodInfo> imis = imm.getEnabledInputMethodList();
        final int N = imis.size();
        if (N > 2) return true;
        if (N < 1) return false;
        int nonAuxCount = 0;
        int auxCount = 0;
        InputMethodSubtype nonAuxSubtype = null;
        InputMethodSubtype auxSubtype = null;
        for(int i = 0; i < N; ++i) {
            final InputMethodInfo imi = imis.get(i);
            final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(imi,
                    true);
            final int subtypeCount = subtypes.size();
            if (subtypeCount == 0) {
                ++nonAuxCount;
            } else {
                for (int j = 0; j < subtypeCount; ++j) {
                    final InputMethodSubtype subtype = subtypes.get(j);
                    if (!subtype.isAuxiliary()) {
                        ++nonAuxCount;
                        nonAuxSubtype = subtype;
                    } else {
                        ++auxCount;
                        auxSubtype = subtype;
                    }
                }
            }
        }
        if (nonAuxCount > 1 || auxCount > 1) {
            return true;
        } else if (nonAuxCount == 1 && auxCount == 1) {
            if (nonAuxSubtype != null && auxSubtype != null
                    && (nonAuxSubtype.getLocale().equals(auxSubtype.getLocale())
                            || auxSubtype.overridesImplicitlyEnabledSubtype()
                            || nonAuxSubtype.overridesImplicitlyEnabledSubtype())
                    && nonAuxSubtype.containsExtraValueKey(TAG_TRY_SUPPRESSING_IME_SWITCHER)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(showTile ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    private synchronized void updateTile() {
        List<InputMethodInfo> imis = mImm.getInputMethodList();
        mLabel = getCurrentInputMethodName(mContext, mContext.getContentResolver(),
                mImm, imis, mContext.getPackageManager());
        mDrawable = R.drawable.ic_qs_ime;
    }

    public void toggleVisibility(boolean show) {
        showTile = (show && needsToShowImeSwitchOngoingNotification(mImm));
        updateResources();
    }

}
