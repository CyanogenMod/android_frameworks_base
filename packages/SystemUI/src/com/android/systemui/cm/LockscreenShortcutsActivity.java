package com.android.systemui.cm;

import com.android.settingslib.cm.ShortcutPickHelper;
import com.android.systemui.R;
import com.android.systemui.cm.LockscreenShortcutsHelper.Shortcuts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;

public class LockscreenShortcutsActivity extends Activity implements View.OnClickListener,
        ShortcutPickHelper.OnPickListener, View.OnTouchListener, LockscreenShortcutsHelper.OnChangeListener {

    private static final int[] sIconIds = new int[]{R.id.left_button, R.id.right_button};
    private static final String ACTION_APP = "action_app";

    private ActionHolder mActions;
    private ShortcutPickHelper mPicker;
    private LockscreenShortcutsHelper mShortcutHelper;
    private View mSelectedView;
    private ColorMatrixColorFilter mFilter;
    private ColorStateList mDefaultTintList;

    @Override
    public void shortcutPicked(String uri, String friendlyName, boolean isApplication) {
        onTargetChange(uri);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                GlowBackground background = (GlowBackground) v.getBackground();
                background.showGlow();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                background = (GlowBackground) v.getBackground();
                background.hideGlow();
                break;
        }
        return false;
    }

    @Override
    public void onChange() {
        updateDrawables();
    }

    private class ActionHolder {
        private ArrayList<CharSequence> mAvailableEntries = new ArrayList<CharSequence>();
        private ArrayList<String> mAvailableValues = new ArrayList<String>();

        public void addAction(String action, int entryResId) {
            mAvailableEntries.add(getString(entryResId));
            mAvailableValues.add(action);
        }

        public int getActionIndex(String action) {
            int count = mAvailableValues.size();
            for (int i = 0; i < count; i++) {
                if (TextUtils.equals(mAvailableValues.get(i), action)) {
                    return i;
                }
            }

            return -1;
        }

        public String getAction(int index) {
            if (index > mAvailableValues.size()) {
                return null;
            }

            return mAvailableValues.get(index);
        }

        public CharSequence[] getEntries() {
            return mAvailableEntries.toArray(new CharSequence[mAvailableEntries.size()]);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lockscreen_shortcuts);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mPicker = new ShortcutPickHelper(this, this);
        mShortcutHelper = new LockscreenShortcutsHelper(this, this);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        mFilter = new ColorMatrixColorFilter(cm);
        ImageView unlockButton = (ImageView) findViewById(R.id.middle_button);
        mDefaultTintList = unlockButton.getImageTintList();
        createActionList();
        initiateViews();
        updateDrawables();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initiateViews() {
        int size = getResources().getDimensionPixelSize(R.dimen.lockscreen_icon_size);
        for (int id : sIconIds) {
            View v = findViewById(id);
            v.setOnClickListener(this);
            v.setOnTouchListener(this);
            GlowBackground background = new GlowBackground(Color.WHITE);
            background.setBounds(0, 0, size, size);
            v.setBackground(background);
        }
    }

    private void updateDrawables() {
        for (int i = 0; i < sIconIds.length; i++) {
            int id = sIconIds[i];
            ImageView v = (ImageView) findViewById(id);
            v.setImageTintList(null);
            v.setColorFilter(null);
            Shortcuts shortcut = Shortcuts.values()[i];
            v.setTag(mShortcutHelper.getUriForTarget(shortcut));
            Drawable drawable;
            if (mShortcutHelper.isTargetEmpty(shortcut)) {
                drawable = getResources().getDrawable(R.drawable.ic_lockscreen_shortcuts_blank);
            } else {
                drawable = mShortcutHelper.getDrawableForTarget(shortcut);
                if (drawable == null) {
                    drawable = getResources().getDrawable(shortcut == Shortcuts.LEFT_SHORTCUT
                            ? R.drawable.ic_phone_24dp : R.drawable.ic_camera_alt_24dp);
                    v.setImageTintList(mDefaultTintList);
                } else {
                    v.setColorFilter(mFilter);
                }
            }
            v.setImageDrawable(drawable);
        }
    }

    private void createActionList() {
        mActions = new ActionHolder();
        mActions.addAction(LockscreenShortcutsHelper.NONE, R.string.lockscreen_none_target);
        mActions.addAction(LockscreenShortcutsHelper.DEFAULT, R.string.lockscreen_default_target);
        mActions.addAction(ACTION_APP, R.string.select_application);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mPicker.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View v) {
        mSelectedView = v;

        final GlowBackground background = (GlowBackground) mSelectedView.getBackground();

        mSelectedView.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                background.showGlow();
            }
        });

        final DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                onTargetChange(mActions.getAction(item));
                dialog.dismiss();
            }
        };

        final DialogInterface.OnCancelListener cancel = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onTargetChange(null);
            }
        };

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lockscreen_choose_action_title)
                .setItems(mActions.getEntries(), l)
                .setOnCancelListener(cancel)
                .create();

        dialog.show();
    }

    private void onTargetChange(String uri) {
        if (uri == null) {
            final GlowBackground background = (GlowBackground) mSelectedView.getBackground();
            background.hideGlow();
            return;
        }

        if (uri.equals(ACTION_APP)) {
            mPicker.pickShortcut(null, null, 0);
        } else {
            mSelectedView.setTag(uri);
            saveCustomActions();
            GlowBackground background = (GlowBackground) mSelectedView.getBackground();
            background.hideGlow();
        }
    }

    private void saveCustomActions() {
        ArrayList<String> targets = new ArrayList<String>();
        for (int id : sIconIds) {
            View v = findViewById(id);
            String uri = (String) v.getTag();
            targets.add(uri);
        }
        mShortcutHelper.saveTargets(targets);
    }

}
