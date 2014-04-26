/*
 * Copyright (C) 2013 The ChameleonOS Project
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

package com.android.systemui.statusbar.sidebar;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.systemui.R;
import com.android.systemui.statusbar.AppSidebar;

public class SidebarConfigurationActivity extends Activity {
    private static final String TAG = "SidebarConfiguration";

    private List<TextView> mInstalledPackages;
    private static final Collator sCollator = Collator.getInstance();
    private AscendingComparator mAscendingComparator;
    private GridView mAppGridView;
    private AppContainer mSidebarContents;
    private FrameLayout mMainLayout;
    private ScrollView mSideBar;
    private Activity mContext;
    private Rect mIconBounds;
    private int mDragItemSize;
    private float mItemTextSize;
    private Folder mFolder;
    private FolderIcon mFolderIcon;
    private ItemDragListener mDragListener;
    private ItemLongClickListener mLongClickListener;
    private int mFolderWidth;
    private int mSidebarWidth;
    private PackageManager mPm;
    
    private static LinearLayout.LayoutParams DUMMY_VIEW_PARAMS = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.sidebar_configuration_layout);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setProgressBarIndeterminateVisibility(true);
        setProgressBarIndeterminate(true);

        mDragListener = new ItemDragListener();
        mLongClickListener = new ItemLongClickListener();
        mAscendingComparator = new AscendingComparator();
        mPm = getPackageManager();

        mMainLayout = (FrameLayout)findViewById(R.id.frame_layout);
        mAppGridView = (GridView) findViewById(R.id.available_apps);
        mAppGridView.setOnDragListener(mDragListener);
        mAppGridView.setOnTouchListener(mTouchOutsideListener);
        mSidebarContents = (AppContainer) findViewById(R.id.contents);
        mSidebarContents.setOnTouchListener(mTouchOutsideListener);
        mSideBar = (ScrollView) findViewById(R.id.sidebar);
        mSideBar.setOnDragListener(mDragListener);
        mSideBar.setOnTouchListener(mTouchOutsideListener);
        mContext = this;
        mFolderWidth = getResources().getDimensionPixelSize(R.dimen.folder_width);
        mSidebarWidth = getResources().getDimensionPixelSize(R.dimen.setup_sidebar_width);

        Resources res = getResources();
        int size = (int) res.getDimensionPixelSize(R.dimen.icon_size);
        mIconBounds = new Rect(0, 0, size, size);
        mDragItemSize = (int) res.getDimensionPixelSize(R.dimen.drag_item_size);
        mItemTextSize = res.getDimensionPixelSize(R.dimen.item_title_text_size);
        DUMMY_VIEW_PARAMS.height = mDragItemSize;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean firstRun = prefs.getBoolean("first_run", true);
        if (!firstRun) {
            findViewById(R.id.first_use).setVisibility(View.GONE);
        } else {
            prefs.edit().putBoolean("first_run", false).commit();
            findViewById(R.id.dismiss).setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    findViewById(R.id.first_use).setVisibility(View.GONE);
                }
            });
        }

        mRefreshAppsTask.execute(null, null, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.sidebar_configuration, menu);
        return true;
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_save:
            SidebarSQLiteHelper helper = new SidebarSQLiteHelper(this);
            SQLiteDatabase db = helper.getWritableDatabase();
            helper.resetDatabase(db);
            db.close();
            helper.close();
            List<ItemInfo> items = getSidebarItems();
            for (ItemInfo i : items) {
                if (i.container >= ItemInfo.CONTAINER_SIDEBAR) {
                    ContentValues values = new ContentValues();
                    values.put(SidebarTable.COLUMN_ITEM_ID, i.id);
                    values.put(SidebarTable.COLUMN_ITEM_TYPE, i.itemType);
                    values.put(SidebarTable.COLUMN_CONTAINER, i.container);
                    values.put(SidebarTable.COLUMN_TITLE, i.title.toString());
                    if (i instanceof AppItemInfo) {
                        ComponentName cn = new ComponentName(((AppItemInfo)i).packageName,
                                ((AppItemInfo)i).className);
                        values.put(SidebarTable.COLUMN_COMPONENT, cn.flattenToString());
                    }
                    getContentResolver().insert(SidebarContentProvider.CONTENT_URI, values);
                }
            }
            sendBroadcast(new Intent(AppSidebar.ACTION_SIDEBAR_ITEMS_CHANGED));
            Toast.makeText(this, R.string.toast_items_saved, Toast.LENGTH_SHORT).show();
            mSidebarContents.removeAllViews();
            populateSidebar();
            return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void getInstalledAppsList() {
        Intent localIntent = new Intent("android.intent.action.MAIN",
                null);
        localIntent.addCategory("android.intent.category.LAUNCHER");
        List<ResolveInfo> apps = mPm.queryIntentActivities(localIntent,
                0);
        mInstalledPackages = new ArrayList<TextView>();
        ResolveInfo ri;
        AppItemInfo ai;
        ai = new AppItemInfo();
        ai.packageName = "com.android.systemui";
        ai.className = "com.android.systemui.statusbar.sidebar.SidebarConfigurationActivity";
        try {
            ActivityInfo info = mPm.getActivityInfo(new ComponentName(ai.packageName, ai.className), 0);
            ai.title = info.loadLabel(mPm);
            ai.icon = info.loadIcon(mPm);
        } catch (NameNotFoundException e) {
        }
        TextView tv = createAppItem(ai);
        tv.setOnLongClickListener(mLongClickListener);
        mInstalledPackages.add(tv);
        for (int i = 0; i < apps.size(); i++) {
            ri = apps.get(i);
            ai = new AppItemInfo();
            ai.className = ri.activityInfo.name;
            ai.packageName = ri.activityInfo.packageName;
            ai.title = ri.activityInfo.loadLabel(mPm).toString();
            tv = new TextView(mContext);
            ai.setIcon(ri.activityInfo.loadIcon(mPm));
            ai.icon.setBounds(mIconBounds);
            tv.setCompoundDrawables(null, ai.icon, null, null);
            tv.setTag(ai);
            tv.setText(ai.title);
            tv.setSingleLine(true);
            tv.setEllipsize(TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mItemTextSize);
            mInstalledPackages.add(tv);
            tv.setOnLongClickListener(mLongClickListener);
        }
        Collections.sort(mInstalledPackages, mAscendingComparator);
    }

    AsyncTask<Void, Void, Void> mRefreshAppsTask = new AsyncTask<Void, Void, Void>(){

        @Override
        protected void onPostExecute(Void result) {
            AppItemAdapter adapter = new AppItemAdapter(mInstalledPackages);
            mAppGridView.setAdapter(adapter);
            setProgressBarIndeterminateVisibility(false);
            setProgressBarIndeterminate(false);
            populateSidebar();
            super.onPostExecute(result);
        }

        @Override
        protected Void doInBackground(Void... params) {
            getInstalledAppsList();
            return null;
        }
    };

    public static class AscendingComparator implements Comparator<TextView> {
        public final int compare(TextView a, TextView b) {
            String alabel = ((AppItemInfo) a.getTag()).title.toString();
            String blabel = ((AppItemInfo) b.getTag()).title.toString();
            return sCollator.compare(alabel, blabel);
        }
    }
    
    private OnTouchListener mTouchOutsideListener = new OnTouchListener() {
        
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN)
                dismissFolderView();
            return false;
        }
    };
    
    private void dismissFolderView() {
        if (mFolder != null) {
            mMainLayout.removeView(mFolder);
            mFolder = null;
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void onBackPressed() {
        if (mFolder != null) {
            dismissFolderView();
        } else
            super.onBackPressed();
    }

    private final class ItemLongClickListener implements OnLongClickListener {

        @Override
        public boolean onLongClick(View view) {
            if (view.getParent() == mSidebarContents)
                view.setVisibility(View.INVISIBLE);
            ClipData data = ClipData.newPlainText("", "");
            DragShadowBuilder shadowBuilder = new IconShadowBuilder(view);
            view.startDrag(data, shadowBuilder, view, 0);
            if (mFolder != null) {
                mFolderIcon.invalidate();
                mFolder.setVisibility(View.GONE);
                ItemInfo ai = (ItemInfo) view.getTag();
                if (ai.container == ItemInfo.CONTAINER_FOLDER) {
                    mFolder.getInfo().remove((AppItemInfo) ai);
                    ai.container = ItemInfo.CONTAINER_SIDEBAR;
                    if (mFolder.getItemCount() == 1) {
                        int folderPos = mSidebarContents.indexOfChild(mFolderIcon);
                        AppItemInfo finalItem = (AppItemInfo)mFolder.getItemsInReadingOrder().get(0).getTag();
                        mFolder.getInfo().remove(finalItem);
                        mSidebarContents.removeViewAt(folderPos);
                        finalItem.container = ItemInfo.CONTAINER_SIDEBAR;
                        mSidebarContents.addView(createAppItem(finalItem), folderPos);
                    }
                }
                mSidebarContents.invalidate();
            }
            return true;
        }

    }
    
    private final class FolderClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            if (mFolder != null) {
                dismissFolderView();
                return;
            }
            mFolderIcon = (FolderIcon) v;
            final Folder folder = mFolder = ((FolderIcon)v).getFolder();
            mFolder.setOnDragListener(mDragListener);
            mMainLayout.addView(mFolder, getFolderLayoutParams());
            mFolder.setVisibility(View.VISIBLE);
            ArrayList<View> items = folder.getItemsInReadingOrder();
            for (View item : items)
                item.setOnLongClickListener(mLongClickListener);
            folder.setOnTouchListener(new OnTouchListener() {
                
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                        dismissFolderView();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    class ItemDragListener implements OnDragListener {
        private View mDummyView;
        @Override
        public boolean onDrag(View v, DragEvent event) {
            int action = event.getAction();
            View view = (View) event.getLocalState();
            ViewGroup owner = (ViewGroup) view.getParent();
            Object tag = view.getTag();
            if (mDummyView == null) {
                mDummyView = new View(mContext);
                DUMMY_VIEW_PARAMS.height = view.getHeight();
            }
            switch (action) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (v == mSideBar) {
                    int pos = mSidebarContents.indexOfChild(view);
                    if (pos >= 0) {
                        mSidebarContents.addView(mDummyView, pos);
                        mSideBar.setBackgroundResource(R.drawable.sidebar_drag_enter);
                    }
                    mSidebarContents.removeView(view);
                }
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (v == mSideBar) {
                    v.setBackgroundResource(R.drawable.sidebar_drag_enter);
                    mDummyView.setBackgroundResource(R.drawable.item_placeholder);
                    if (mDummyView.getParent() == null)
                        mSidebarContents.addView(mDummyView, DUMMY_VIEW_PARAMS);
                }
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                if (v == mSideBar) {
                    float x = event.getX();
                    float y = event.getY();
                    mSidebarContents.repositionView(mDummyView, x, y + mSideBar.getScrollY(), tag instanceof FolderInfo);
                    if (y < mDummyView.getHeight() / 2)
                        mSideBar.scrollBy((int)x, (int)-5);
                    else if (y > mSideBar.getHeight() - mDummyView.getHeight()/2)
                        mSideBar.scrollBy((int)x, 5);
                    if (mSidebarContents.indexOfChild(mDummyView) == mSidebarContents.getChildCount()-1)
                        mSideBar.scrollTo((int)x, mDummyView.getBottom());
                }                
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                if (v == mSideBar) {
                    v.setBackgroundResource(R.drawable.sidebar_drag_exit);
                    if (mDummyView.getParent() == mSidebarContents)
                        mSidebarContents.removeView(mDummyView);
                    for (int i = 0; i < mSidebarContents.getChildCount(); i++)
                        mSidebarContents.getChildAt(i).setBackgroundResource(0);
                }
                break;
            case DragEvent.ACTION_DROP:
                // Dropped, reassign View to ViewGroup
                if (v == mSideBar) {
                    if (mSidebarContents.findViewWithTag(view.getTag()) != null) {
                        mSidebarContents.removeView(mDummyView);
                        break;
                    }
                    ItemInfo addTo = mSidebarContents.getAddToItem();
                    View addToView = mSidebarContents.findViewWithTag(addTo);
                    View tv = cloneItem(view);
                    tv.setOnLongClickListener(mLongClickListener);
                    tv.setBackgroundResource(0);
                    tv.setVisibility(View.VISIBLE);
                    if (addTo != null && addToView != null) {
                        addToView.setBackgroundResource(0);
                        FolderInfo info;
                        FolderIcon icon;
                        if (addTo instanceof FolderInfo) {
                            info = (FolderInfo)addTo;
                            icon = (FolderIcon)addToView;
                        } else {
                            info = new FolderInfo();
                            info.title = getString(R.string.default_folder_text);
                            icon = FolderIcon.fromXml(R.layout.sidebar_folder_icon,
                                    mSidebarContents, null, info, mContext, false);
                            info.add((AppItemInfo)addToView.getTag());
                            icon.setOnLongClickListener(mLongClickListener);
                            icon.setOnClickListener(new FolderClickListener());
                        }
                        
                        info.add((AppItemInfo)tv.getTag());
                        int pos = mSidebarContents.indexOfChild(addToView);
                        mSidebarContents.removeView(addToView);
                        mSidebarContents.addView(icon, pos);
                    } else {
                        int pos = mSidebarContents.indexOfChild(mDummyView);
                        mSidebarContents.addView(tv, pos);
                    }
                    mSidebarContents.removeView(mDummyView);
                } else {
                    if (owner != null && owner != v) {
                        owner.removeView(view);
                    } else
                        view.setVisibility(View.VISIBLE);
                }
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                if (v == mSideBar)
                    v.setBackgroundResource(R.drawable.sidebar_drag_exit);
                dismissFolderView();
            default:
                break;
            }
            return true;
        }
        
        private View cloneItem(View original) {
            ItemInfo ai = (ItemInfo) original.getTag();
            View v;
            if (ai instanceof AppItemInfo) {
                v = new TextView(mContext);
                TextView tv = (TextView)v;
                ((AppItemInfo)ai).icon.setBounds(mIconBounds);
                tv.setCompoundDrawables(null, ((AppItemInfo)ai).icon, null, null);
                tv.setTag(ai);
                tv.setSingleLine(true);
                tv.setEllipsize(TruncateAt.END);
                tv.setText(ai.title);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mItemTextSize);
                tv.setGravity(Gravity.CENTER);
            } else {
                v = original;
            }
            
            return v;
        }
    }
    
    private class IconShadowBuilder extends DragShadowBuilder {
        private Drawable shadow;
        private int mShadowWidth;
        private int mShadowHeight;
        
        @SuppressWarnings("deprecation")
        public IconShadowBuilder(View v) {
            super(v);
            mShadowWidth = (int)(v.getWidth() * 1.5f);
            mShadowHeight = (int)(v.getHeight() * 1.5f);
            v.setDrawingCacheEnabled(true);
            v.buildDrawingCache();
            shadow = new BitmapDrawable(v.getDrawingCache(true));
        }

        /* (non-Javadoc)
         * @see android.view.View.DragShadowBuilder#onDrawShadow(android.graphics.Canvas)
         */
        @Override
        public void onDrawShadow(Canvas canvas) {
            shadow.draw(canvas);
        }

        /* (non-Javadoc)
         * @see android.view.View.DragShadowBuilder#onProvideShadowMetrics(android.graphics.Point, android.graphics.Point)
         */
        @Override
        public void onProvideShadowMetrics(Point size,
                Point touch) {
            // The drag shadow is a ColorDrawable. This sets its dimensions to be the same as the
            // Canvas that the system will provide. As a result, the drag shadow will fill the
            // Canvas.
            shadow.setBounds(0, 0, mShadowWidth, mShadowHeight);

            // Sets the size parameter's width and height values. These get back to the system
            // through the size parameter.
            size.set(mShadowWidth, mShadowHeight);

            // Sets the touch point's position to be in the middle of the drag shadow
            touch.set(mShadowWidth / 2, mShadowHeight / 2);
        }
    }

    private LayoutParams getFolderLayoutParams() {
        LayoutParams lp = new LayoutParams(
                mFolderWidth,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = mSidebarWidth;
        return lp;
    }
    
    private List<ItemInfo> getSidebarItems() {
        int id = 0;
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        for (int i = 0; i < mSidebarContents.getChildCount(); i++) {
            ItemInfo info = (ItemInfo) mSidebarContents.getChildAt(i).getTag();
            info.id = id++;
            items.add(info);
            if (info instanceof FolderInfo) {
                List<AppItemInfo> contents = ((FolderInfo)info).contents;
                for (AppItemInfo item : contents) {
                    item.container = info.id;
                    item.id = id++;
                    items.add(item);
                }
            }
        }
        
        return items;
    }
    
    private void populateSidebar() {
        String[] projection = {
                SidebarTable.COLUMN_ITEM_ID,
                SidebarTable.COLUMN_ITEM_TYPE,
                SidebarTable.COLUMN_CONTAINER,
                SidebarTable.COLUMN_TITLE,
                SidebarTable.COLUMN_COMPONENT
        };
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        Cursor cursor = getContentResolver().query(SidebarContentProvider.CONTENT_URI, 
                projection, null, null, null);
        while (cursor.moveToNext()) {
            ItemInfo item;
            int type = cursor.getInt(cursor.getColumnIndex(SidebarTable.COLUMN_ITEM_TYPE));
            if (type == ItemInfo.TYPE_APPLICATION) {
                item = new AppItemInfo();
                String component = cursor.getString(4);
                ComponentName cn = ComponentName.unflattenFromString(component);
                ((AppItemInfo)item).packageName = cn.getPackageName();
                ((AppItemInfo)item).className = cn.getClassName();
            } else {
                item = new FolderInfo();
            }
            item.id = cursor.getInt(0);
            item.itemType = type;
            item.container = cursor.getInt(2);
            item.title = cursor.getString(3);
            if (item.container == ItemInfo.CONTAINER_SIDEBAR) {
                if (item instanceof AppItemInfo) {
                    TextView tv = createAppItem((AppItemInfo) item);
                    mSidebarContents.addView(tv);
                    tv.setOnLongClickListener(mLongClickListener);
                } else {
                    FolderIcon icon = FolderIcon.fromXml(R.layout.sidebar_folder_icon,
                            mSidebarContents, null, (FolderInfo)item, mContext, false);
                    icon.setOnLongClickListener(mLongClickListener);
                    icon.setOnClickListener(new FolderClickListener());
                    mSidebarContents.addView(icon);
                }
            } else {
                try {
                    ((AppItemInfo)item).setIcon(mPm.getActivityIcon(
                            new ComponentName(((AppItemInfo)item).packageName, ((AppItemInfo)item).className)));
                } catch (NameNotFoundException e) {
                    ((AppItemInfo)item).setIcon(mPm.getDefaultActivityIcon());
                }
                ((AppItemInfo)item).icon.setBounds(mIconBounds);
                FolderInfo info = (FolderInfo) items.get(item.container);
                info.add((AppItemInfo) item);
            }
            items.add(item);
        }
    }
    
    private TextView createAppItem(AppItemInfo info) {
        TextView tv = new TextView(mContext);
        try {
            info.setIcon(mPm.getActivityIcon(new ComponentName(info.packageName, info.className)));
        } catch (NameNotFoundException e) {
            info.setIcon(mPm.getDefaultActivityIcon());
        }
        info.icon.setBounds(mIconBounds);
        tv.setCompoundDrawables(null, info.icon, null, null);
        tv.setTag(info);
        tv.setText(info.title);
        tv.setSingleLine(true);
        tv.setEllipsize(TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mItemTextSize);
        
        return tv;
    }
}
