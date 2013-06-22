package com.android.systemui.statusbar.phone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.DisplayInfo;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.NavigationButtons;
import com.android.systemui.statusbar.NavigationButtons.ButtonInfo;
import com.android.systemui.statusbar.policy.KeyButtonView;

/**
 * Handles the editing of the navigation bar
 * @author Danesh M
 * @hide
 */
public class NavbarEditor implements OnTouchListener {

    /**
     * Holds reference to all assignable button ids.
     * Hold this in sync with {@link NavigationButtons#BUTTON_COUNT}
     */
    static final ArrayList<Integer> BUTTON_IDS = new ArrayList<Integer>(
            Arrays.asList(R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six));

    private final ArrayList<Integer> mIds = new ArrayList<Integer>(BUTTON_IDS);

    /**
     * Subset of mIds, to differentiate small/side buttons
     * since they can be assigned additional functionality.
     * Hold this in sync with {@link NavigationButtons#BUTTON_IS_SMALL}
     */
    public static final int[] smallButtonIds = {R.id.one, R.id.six};

    private static Boolean sIsDevicePhone = null;
    protected int mVisibleCount = 4;
    private boolean mInEditMode = false;

    /**
     * Holds reference to the parent/root of the inflated view
     */
    private ViewGroup mParent;

    /**
     * Button chooser dialog
     */
    AlertDialog mDialog;

    /**
     * mVertical = Whether we're in landscape mode
     * mLongPressed = Whether the longpress runnable was activated.
     */
    boolean mVertical,mLongPressed;

    private Context mContext;

    public NavbarEditor (ViewGroup parent, Boolean orientation) {
        mParent = parent;
        mVertical = orientation;
        mContext = parent.getContext();
    }

    /**
     * Find intersecting views in mIds
     * @param pos - pointer location
     * @param v - view being dragged
     * @return array index in mIds of view intersecting
     */
    private int findInterceptingViewIndex(float pos, View v) {
        int location[] = new int[2];
        for (int cc = 0; cc < mIds.size(); cc++) {
            if (!ArrayUtils.contains(smallButtonIds,mIds.get(cc))) {
                View tmpV = mParent.findViewById(mIds.get(cc));
                tmpV.getLocationOnScreen(location);
                if (tmpV == v) {
                    continue;
                } else if (!mVertical && (pos > (location[0]+v.getWidth()/4) && pos < location[0]+v.getWidth())) {
                    return cc;
                } else if (mVertical && (pos > (location[1]+v.getHeight()/4) && pos < location[1]+v.getHeight())) {
                    return cc;
                }
            }
        }
        return -1;
    }

    /**
     * Longpress runnable to assign buttons in edit mode
     */
    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (mInEditMode) {
                mLongPressed = true;
                mParent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }
        }
    };

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

    protected static boolean isDevicePhone(Context con) {
        if (sIsDevicePhone == null) {
            WindowManager wm = (WindowManager)con.getSystemService(Context.WINDOW_SERVICE);
            DisplayInfo outDisplayInfo = new DisplayInfo();
            wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);
            int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
            int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;
            if (shortSizeDp < 600) {
                // 0-599dp: "phone" UI with a separate status & navigation bar
                sIsDevicePhone = true;
            } else {
                sIsDevicePhone = false;
            }
        }
        return sIsDevicePhone;
    }

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!mInEditMode || (mDialog != null && mDialog.isShowing())) {
            return false;
        }
        float curPos = 0;
        if (!mVertical) {
            curPos = event.getRawX();
        } else {
            curPos = event.getRawY();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int screenLoc[] = new int[2];
            view.setPressed(true);
            view.getLocationOnScreen(screenLoc);
            // Store the starting view position in the parent's tag
            mParent.setTag(Float.valueOf(screenLoc[mVertical ? 1 : 0]));
            view.postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            view.setPressed(false);
            if (!mLongPressed || ArrayUtils.contains(smallButtonIds, view.getId())) {
                return false;
            }
            view.bringToFront();
            ViewGroup viewParent = (ViewGroup) view.getParent();
            float buttonSize = mVertical ? view.getHeight() : view.getWidth();
            float min = mVertical ? viewParent.getTop() : (viewParent.getLeft() - buttonSize / 2);
            float max = mVertical ? (viewParent.getTop() + viewParent.getHeight())
                    : (viewParent.getLeft() + viewParent.getWidth());
            // Prevents user from dragging view outside of bounds
            if (curPos < min || curPos > max) {
                return false;
            }
            if (!mVertical) {
                view.setX(curPos - viewParent.getLeft() - buttonSize / 2);
            } else {
                view.setY(curPos - viewParent.getTop() - buttonSize / 2);
            }
            int affectedViewPosition = findInterceptingViewIndex(curPos, view);
            if (affectedViewPosition == -1) {
                return false;
            }
            switchId(mIds.indexOf(view.getId()), affectedViewPosition, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
            view.removeCallbacks(mCheckLongPress);
            if (!mLongPressed && !view.getTag().equals(NavigationButtons.HOME)) {
                final ButtonAdapter list =
                        new ButtonAdapter(ArrayUtils.contains(smallButtonIds, view.getId()));
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                        .setTitle(mContext.getString(R.string.navbar_dialog_title))
                        .setAdapter(list, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                KeyButtonView button = (KeyButtonView) view;
                                ButtonInfo info = (ButtonInfo) list.getItem(which);

                                button.setInfo(info, mVertical);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

                mDialog = builder.create();
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.show();
                mLongPressed = false;

                return true;
            }
            mLongPressed = false;
            // Reset the dragged view to its original location
            ViewGroup vParent = (ViewGroup) view.getParent();
            if (!mVertical) {
                view.setX((Float) mParent.getTag() - vParent.getLeft());
            } else {
                view.setY((Float) mParent.getTag() - vParent.getTop());
            }
        }
        return true;
    }

    /**
     * Switches positions of two views and
     * updates their mIds entry
     * @param to - index for new position in mIds
     * @param from - index for old position in mIds
     * @param view - view being dragged
     */
    private void switchId(int to, int from, View view) {
        View tView = mParent.findViewById(mIds.get(from));
        int screenLoc[] = new int[2];
        tView.getLocationOnScreen(screenLoc);
        ViewGroup a = (ViewGroup) view.getParent();
        if (!mVertical) {
            tView.setX((Float) mParent.getTag() - a.getLeft());
            mParent.setTag(Float.valueOf(screenLoc[0]));
        } else {
            tView.setY((Float) mParent.getTag() - a.getTop());
            mParent.setTag(Float.valueOf(screenLoc[1]));
        }
        Collections.swap(mIds, to, from);
    }

    /**
     * Saves the current key arrangement
     * to the settings provider
     */
    protected void saveKeys() {
        ButtonInfo[] buttons = new ButtonInfo[NavigationButtons.SLOT_COUNT];
        for (int i = 0; i < NavigationButtons.SLOT_COUNT; i++) {
            int idIndex = mVertical ? NavigationButtons.SLOT_COUNT - i : i;
            buttons[i] = (ButtonInfo) mParent.findViewById(mIds.get(idIndex)).getTag();
        }
        NavigationButtons.storeButtonMap(mContext, buttons);
    }

    /**
     * Updates the buttons according to the
     * key arrangement stored in settings provider
     */
    protected void updateKeys() {
        ButtonInfo[] buttons = NavigationButtons.loadButtonMap(mContext);

        mVisibleCount = 0;

        for (int i = 0; i < buttons.length; i++) {
            int id = BUTTON_IDS.get(i);
            ButtonInfo info = buttons[i];
            KeyButtonView button = (KeyButtonView) mParent.findViewById(id);
            boolean isSmallButton = NavigationButtons.IS_SLOT_SMALL[i];

            button.setInfo(info, mVertical);
            if (!info.equals(NavigationButtons.EMPTY) && !isSmallButton) {
                mVisibleCount++;
            }

            button.setTranslationX(0);
        }

        if (isDevicePhone(mContext)) {
            adjustPadding();
        }
    }

    /**
     * Accommodates the padding between keys based on
     * number of keys in use.
     */
    private void adjustPadding() {
        ViewGroup viewParent = (ViewGroup) mParent.findViewById(R.id.mid_nav_buttons);
        int sCount = mVisibleCount;
        for (int v = 0; v < viewParent.getChildCount();v++) {
            View cView = viewParent.getChildAt(v);
            if (cView instanceof KeyButtonView) {
                View nextPadding = viewParent.getChildAt(v+1);
                if (nextPadding != null) {
                    View nextKey = viewParent.getChildAt(v+2);
                    ButtonInfo nextBi = NavigationButtons.EMPTY;
                    if (nextKey != null) {
                        nextBi = (ButtonInfo) nextKey.getTag();
                    }
                    ButtonInfo curBi = (ButtonInfo) cView.getTag();
                    if (nextKey != null && nextBi != null
                            && curBi != null && curBi != NavigationButtons.EMPTY) {
                        if (nextBi != NavigationButtons.EMPTY){
                            nextPadding.setVisibility(View.VISIBLE);
                        } else {
                            if (sCount > 1) {
                                nextPadding.setVisibility(View.VISIBLE);
                            } else {
                                nextPadding.setVisibility(View.GONE);
                            }
                        }
                        sCount--;
                    } else {
                        nextPadding.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    protected void updateLowLights(View current) {
        ViewGroup lowLights = (ViewGroup) current.findViewById(R.id.lights_out);
        int totalViews = lowLights.getChildCount();
        int visibleCount = mVisibleCount;
        for (int v = 0;v < totalViews; v++) {
            if (lowLights.getChildAt(v) instanceof ImageView) {
                View blank = lowLights.getChildAt(v+1);
                if (visibleCount <= 0) {
                    lowLights.getChildAt(v).setVisibility(View.GONE);
                    if (blank != null) {
                        blank.setVisibility(View.GONE);
                    }
                } else {
                    lowLights.getChildAt(v).setVisibility(View.VISIBLE);
                    visibleCount--;
                    if (visibleCount > 0 && blank != null) {
                        blank.setVisibility(View.VISIBLE);
                    } else if (blank != null) {
                        blank.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    private class ButtonAdapter implements ListAdapter {
        /**
         * Already assigned items
         */
        ArrayList<ButtonInfo> takenItems;
        ArrayList<ButtonInfo> items;
        LayoutInflater inflater;

        ButtonAdapter (boolean smallButtons) {
            inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            takenItems = new ArrayList<ButtonInfo>();
            for (int id : mIds) {
                ButtonInfo vTag = (ButtonInfo) mParent.findViewById(id).getTag();
                if (vTag == null || vTag == NavigationButtons.EMPTY) {
                    continue;
                }
                takenItems.add(vTag);
            }
            items = new ArrayList<ButtonInfo>(NavigationButtons.BUTTON_MAP.values());
            // home button is not assignable
            items.remove(NavigationButtons.HOME);
            // menu buttons can only be assigned to side buttons
            if (!smallButtons) {
                items.remove(NavigationButtons.CONDITIONAL_MENU);
                items.remove(NavigationButtons.ALWAYS_MENU);
            } else {
                items.remove(NavigationButtons.MENU_BIG);
            }
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int arg0) {
            return items.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return 0;
        }

        @Override
        public int getItemViewType(int arg0) {
            return 0;
        }

        @Override
        public View getView(int arg0, View convertView, ViewGroup parent) {
            if(convertView == null) {
                convertView = inflater.inflate(android.R.layout.select_dialog_item, parent,false);
            }
            TextView text = (TextView) convertView.findViewById(android.R.id.text1);
            if (takenItems.contains(items.get(arg0))) {
                text.setBackgroundColor(Color.parseColor("#181818"));
            } else {
                text.setBackground(null);
            }
            text.setText(mParent.getResources().getString(items.get(arg0).displayId));
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver arg0) {
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver arg0) {
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int arg0) {
            if (takenItems.contains(items.get(arg0))) {
                return false;
            }
            return true;
        }
    }
}
