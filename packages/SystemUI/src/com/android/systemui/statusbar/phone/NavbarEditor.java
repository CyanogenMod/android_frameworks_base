/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.systemui.statusbar.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.DisplayInfo;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.KeyButtonView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handles the editing of the navigation bar
 * @author Danesh M
 * @hide
 */
public class NavbarEditor implements View.OnTouchListener {
    /**
     * Holds reference to all assignable button ids
     */
    private static final int[] BUTTON_IDS =
            { R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six };

    /**
     * Subset of BUTTON_IDS, to differentiate small/side buttons
     * since they can be assigned additional functionality.
     */
    private static final int[] SMALL_BUTTON_IDS = { R.id.one, R.id.six };

    // holds the button views in the order they currently appear on screen
    private final ArrayList<KeyButtonView> mButtonViews;

    private Context mContext;
    private static Boolean sIsDevicePhone = null;
    private boolean mInEditMode = false;

    // Holds reference to the parent/root of the inflated view
    private View mParent;

    // Button chooser dialog
    private AlertDialog mDialog;

    // true == we're in landscape mode
    private boolean mVertical;
    // true == we're currently checking for long press
    private boolean mLongPressed;
    // start point of the current drag operation
    private float mDragOrigin;

    // just to avoid reallocations
    private static final int[] sLocation = new int[2];

    /**
     * Longpress runnable to assign buttons in edit mode
     */
    private Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (mInEditMode) {
                mLongPressed = true;
                mParent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    };

    //Available buttons
    public static final ButtonInfo NAVBAR_EMPTY = new ButtonInfo("empty",
            R.string.navbar_empty_button, R.string.accessibility_clear_all,
            0, R.drawable.ic_sysbar_add,
            R.drawable.ic_sysbar_add_land, R.drawable.ic_sysbar_add_side);
    public static final ButtonInfo NAVBAR_HOME = new ButtonInfo("home",
            R.string.navbar_home_button, R.string.accessibility_home,
            KeyEvent.KEYCODE_HOME, R.drawable.ic_sysbar_home,
            R.drawable.ic_sysbar_home_land, R.drawable.ic_sysbar_home);
    public static final ButtonInfo NAVBAR_BACK = new ButtonInfo("back",
            R.string.navbar_back_button, R.string.accessibility_back,
            KeyEvent.KEYCODE_BACK, R.drawable.ic_sysbar_back,
            R.drawable.ic_sysbar_back_land, R.drawable.ic_sysbar_back_side);
    public static final ButtonInfo NAVBAR_SEARCH = new ButtonInfo("search",
            R.string.navbar_search_button, R.string.accessibility_back,
            KeyEvent.KEYCODE_SEARCH, R.drawable.ic_sysbar_search,
            R.drawable.ic_sysbar_search_land, R.drawable.ic_sysbar_search_side);
    public static final ButtonInfo NAVBAR_RECENT = new ButtonInfo("recent",
            R.string.navbar_recent_button, R.string.accessibility_recent,
            0, R.drawable.ic_sysbar_recent,
            R.drawable.ic_sysbar_recent_land, R.drawable.ic_sysbar_recent_side);
    public static final ButtonInfo NAVBAR_CONDITIONAL_MENU = new ButtonInfo("menu0",
            R.string.navbar_menu_conditional_button, R.string.accessibility_menu,
            KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu,
            R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu);
    public static final ButtonInfo NAVBAR_ALWAYS_MENU = new ButtonInfo("menu1",
            R.string.navbar_menu_always_button, R.string.accessibility_menu,
            KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu,
            R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu);
    public static final ButtonInfo NAVBAR_MENU_BIG = new ButtonInfo("menu2",
            R.string.navbar_menu_big_button, R.string.accessibility_menu,
            KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu_big,
            R.drawable.ic_sysbar_menu_big_land, 0);

    private static final ButtonInfo[] ALL_BUTTONS = new ButtonInfo[] {
        NAVBAR_EMPTY, NAVBAR_HOME, NAVBAR_BACK, NAVBAR_SEARCH,
        NAVBAR_RECENT, NAVBAR_CONDITIONAL_MENU, NAVBAR_ALWAYS_MENU, NAVBAR_MENU_BIG
    };

    private static final String DEFAULT_SETTING_STRING = "empty|back|home|recent|empty|menu0";

    public NavbarEditor (View parent, boolean orientation) {
        mContext = parent.getContext();
        mParent = parent;
        mVertical = orientation;

        mButtonViews = new ArrayList<KeyButtonView>();
        for (int id : BUTTON_IDS) {
            mButtonViews.add((KeyButtonView) mParent.findViewById(id));
        }
    }

    public void setEditMode(boolean editMode) {
        mInEditMode = editMode;
        for (Integer id : BUTTON_IDS) {
            KeyButtonView button = (KeyButtonView) mParent.findViewById(id);
            if (button != null) {
                button.setEditMode(editMode);
                button.setOnTouchListener(editMode ? this : null);
            }
        }
        if (!editMode && mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    public static boolean isDevicePhone(Context context) {
        if (sIsDevicePhone == null) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayInfo outDisplayInfo = new DisplayInfo();

            wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);

            int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
            int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;

            // 0-599dp: "phone" UI with a separate status & navigation bar
            sIsDevicePhone = shortSizeDp < 600;
        }

        return sIsDevicePhone;
    }

    /**
     * Find intersecting view in mButtonViews
     * @param pos - pointer location
     * @param v - view being dragged
     * @return intersecting view or null
     */
    private View findInterceptingView(float pos, View v) {
        for (KeyButtonView otherView : mButtonViews) {
            if (otherView == v) {
                continue;
            }

            if (ArrayUtils.contains(SMALL_BUTTON_IDS, otherView.getId())) {
                continue;
            }

            otherView.getLocationOnScreen(sLocation);
            float otherPos = sLocation[mVertical ? 1 : 0];
            float otherDimension = mVertical ? v.getHeight() : v.getWidth();

            if (pos > (otherPos + otherDimension / 4) && pos < (otherPos + otherDimension)) {
                return otherView;
            }
        }
        return null;
    }

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!mInEditMode || (mDialog != null && mDialog.isShowing())) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            view.setPressed(true);
            view.getLocationOnScreen(sLocation);
            mDragOrigin = sLocation[mVertical ? 1 : 0];
            view.postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            view.setPressed(false);

            if (!mLongPressed || ArrayUtils.contains(SMALL_BUTTON_IDS, view.getId())) {
                return false;
            }

            ViewGroup viewParent = (ViewGroup) view.getParent();
            float pos = mVertical ? event.getRawY() : event.getRawX();
            float buttonSize = mVertical ? view.getHeight() : view.getWidth();
            float min = mVertical ? viewParent.getTop() : (viewParent.getLeft() - buttonSize / 2);
            float max = mVertical ? (viewParent.getTop() + viewParent.getHeight())
                    : (viewParent.getLeft() + viewParent.getWidth());

            // Prevents user from dragging view outside of bounds
            if (pos < min || pos > max) {
                return false;
            }
            if (!mVertical) {
                view.setX(pos - viewParent.getLeft() - buttonSize / 2);
            } else {
                view.setY(pos - viewParent.getTop() - buttonSize / 2);
            }
            View affectedView = findInterceptingView(pos, view);
            if (affectedView == null) {
                return false;
            }
            switchId(affectedView, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
            view.removeCallbacks(mCheckLongPress);

            if (!mLongPressed && !view.getTag().equals(NAVBAR_HOME)) {
                final boolean isSmallButton = ArrayUtils.contains(SMALL_BUTTON_IDS, view.getId());
                final ButtonAdapter list = new ButtonAdapter(mContext, mButtonViews, isSmallButton);

                AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                        .setTitle(mContext.getString(R.string.navbar_dialog_title))
                        .setAdapter(list, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                KeyButtonView button = (KeyButtonView) view;
                                ButtonInfo info = (ButtonInfo) list.getItem(which);

                                button.setInfo(info, mVertical, isSmallButton);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

                mDialog = builder.create();
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.show();
            } else {
                // Reset the dragged view to its original location
                ViewGroup parent = (ViewGroup) view.getParent();

                if (!mVertical) {
                    view.setX(mDragOrigin - parent.getLeft());
                } else {
                    view.setY(mDragOrigin - parent.getTop());
                }
            }
            mLongPressed = false;
        }
        return true;
    }

    /**
     * Switches positions of two views and
     * updates their mButtonViews entry
     * @param targetView - view to be replaced
     * @param view - view being dragged
     */
    private void switchId(View targetView, View view) {
        ViewGroup parent = (ViewGroup) view.getParent();

        targetView.getLocationOnScreen(sLocation);
        if (!mVertical) {
            targetView.setX(mDragOrigin - parent.getLeft());
            mDragOrigin = sLocation[0];
        } else {
            targetView.setY(mDragOrigin - parent.getTop());
            mDragOrigin = sLocation[1];
        }

        int targetIndex = mButtonViews.indexOf(targetView);
        int draggedIndex = mButtonViews.indexOf(view);
        Collections.swap(mButtonViews, draggedIndex, targetIndex);
    }

    /**
     * Saves the current key arrangement
     * to the settings provider
     */
    protected void saveKeys() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BUTTON_IDS.length; i++) {
            int idIndex = mVertical ? BUTTON_IDS.length - i : i;
            ButtonInfo info = (ButtonInfo) mButtonViews.get(idIndex).getTag();
            if (i != 0) sb.append("|");
            sb.append(info.key);
        }
        Settings.System.putStringForUser(mContext.getContentResolver(),
                Settings.System.NAV_BUTTONS, sb.toString(), UserHandle.USER_CURRENT);
    }

    /**
     * Updates the buttons according to the
     * key arrangement stored in settings provider
     */
    protected void updateKeys() {
        String saved = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.NAV_BUTTONS, UserHandle.USER_CURRENT);
        if (saved == null) {
            saved = DEFAULT_SETTING_STRING;
        }

        String[] buttons = saved.split("\\|");
        if (buttons.length < BUTTON_IDS.length) {
            buttons = DEFAULT_SETTING_STRING.split("\\|");
        }

        int visibleCount = 0;

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            int id = BUTTON_IDS[i];
            int index = mVertical ? BUTTON_IDS.length - i - 1 : i;
            String key = index < buttons.length ? buttons[index] : null;
            KeyButtonView buttonView = (KeyButtonView) mParent.findViewById(id);
            boolean isSmallButton = ArrayUtils.contains(SMALL_BUTTON_IDS, id);
            ButtonInfo button = NAVBAR_EMPTY;

            for (ButtonInfo info : ALL_BUTTONS) {
                if (info.key.equals(key)) {
                    button = info;
                    break;
                }
            }

            buttonView.setInfo(button, mVertical, isSmallButton);
            if (button != NAVBAR_EMPTY && !isSmallButton) {
                visibleCount++;
            }

            buttonView.setTranslationX(0);
            mButtonViews.set(i, buttonView);
        }

        if (isDevicePhone(mContext)) {
            adjustPadding(visibleCount);
        }
        updateLowLights(visibleCount);
    }

    /**
     * Accommodates the padding between keys based on
     * number of keys in use.
     */
    private void adjustPadding(int visibleCount) {
        ViewGroup viewParent = (ViewGroup) mParent.findViewById(R.id.mid_nav_buttons);
        int totalViews = viewParent.getChildCount();

        for (int v = 0; v < totalViews; v++) {
            View currentKey = viewParent.getChildAt(v);
            if (!(currentKey instanceof KeyButtonView)) {
                continue;
            }
            View nextPadding = viewParent.getChildAt(v + 1);
            if (nextPadding == null) {
                continue;
            }

            View nextKey = viewParent.getChildAt(v + 2);
            ButtonInfo nextInfo = nextKey == null ? null : (ButtonInfo) nextKey.getTag();
            ButtonInfo currentInfo = (ButtonInfo) currentKey.getTag();

            if (nextInfo != null && currentInfo != null && currentInfo != NAVBAR_EMPTY) {
                if (nextInfo != NAVBAR_EMPTY || visibleCount > 1) {
                    nextPadding.setVisibility(View.VISIBLE);
                } else {
                    nextPadding.setVisibility(View.GONE);
                }
                visibleCount--;
            } else {
                nextPadding.setVisibility(View.GONE);
            }
        }
    }

    protected void updateLowLights(int visibleCount) {
        ViewGroup lowLights = (ViewGroup) mParent.findViewById(R.id.lights_out);
        int totalViews = lowLights.getChildCount();

        for (int v = 0;v < totalViews; v++) {
            View currentView = lowLights.getChildAt(v);
            if (!(currentView instanceof ImageView)) {
                continue;
            }

            if (visibleCount <= 0) {
                currentView.setVisibility(View.GONE);
            } else {
                currentView.setVisibility(View.VISIBLE);
                visibleCount--;
            }

            View blank = lowLights.getChildAt(v + 1);
            if (blank != null) {
                blank.setVisibility(visibleCount > 0 ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * Class to store info about supported buttons
     */
    public static final class ButtonInfo {
        private final String key;
        public int displayId;
        public int contentDescription;
        public int keyCode;
        public int portResource;
        public int landResource;
        public int sideResource;
        /**
         * Constructor for new button type
         * @param key - the internal key of the button
         * @param rId - resource id of text shown to user in choose dialog
         * @param cD  - accessibility information regarding button
         * @param mC  - keyCode to execute on button press
         * @param pR  - portrait resource used to display button
         * @param lR  - landscape resource used to display button
         * @param sR  - smaller scaled resource for side buttons
         */
        ButtonInfo (String key, int rId, int cD, int mC, int pR, int lR, int sR) {
            this.key = key;
            displayId = rId;
            contentDescription = cD;
            keyCode = mC;
            portResource = pR;
            landResource = lR;
            sideResource = sR;
        }

        @Override
        public String toString() {
            return "ButtonInfo[" + key + "]";
        }
    }

    private static class ButtonAdapter extends ArrayAdapter<ButtonInfo> {
        private ArrayList<ButtonInfo> mTakenItems;

        public ButtonAdapter(Context context,
                ArrayList<KeyButtonView> buttons, boolean smallButtons) {
            super(context, R.layout.navigation_bar_edit_menu_item, R.id.key_text,
                    buildItems(smallButtons));

            mTakenItems = new ArrayList<ButtonInfo>();
            for (KeyButtonView button : buttons) {
                ButtonInfo info = (ButtonInfo) button.getTag();
                if (info != null && info != NAVBAR_EMPTY) {
                    mTakenItems.add(info);
                }
            }
        }

        private static List<ButtonInfo> buildItems(boolean smallButtons) {
            List<ButtonInfo> items = new ArrayList<ButtonInfo>(Arrays.asList(ALL_BUTTONS));

            // home button is not assignable
            items.remove(NAVBAR_HOME);
            // menu buttons can only be assigned to side buttons
            if (!smallButtons) {
                items.remove(NAVBAR_CONDITIONAL_MENU);
                items.remove(NAVBAR_ALWAYS_MENU);
            } else {
                items.remove(NAVBAR_MENU_BIG);
            }

            return items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            ButtonInfo info = getItem(position);
            boolean enabled = isEnabled(position);

            TextView text = (TextView) view.findViewById(R.id.key_text);
            text.setText(getContext().getResources().getString(info.displayId));
            text.setEnabled(enabled);

            ImageView icon = (ImageView) view.findViewById(R.id.key_icon);
            icon.setImageResource(info.portResource);
            icon.setColorFilter(new PorterDuffColorFilter(
                    text.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));

            return view;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return !mTakenItems.contains(getItem(position));
        }
    }
}
