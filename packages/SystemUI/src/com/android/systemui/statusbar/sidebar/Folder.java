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

import java.util.ArrayList;

import android.content.Context;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.sidebar.FolderInfo.FolderListener;

public class Folder extends LinearLayout implements FolderListener,
        View.OnFocusChangeListener, TextView.OnEditorActionListener {
    protected FolderInfo mInfo;
    
    private FolderIcon mIcon;
    private Context mContext;
    private ArrayList<View> mContents = new ArrayList<View>();
    private boolean mIsEditingName = false;
    FolderEditText mFolderName;
    
    private static int sTextSize;
    
    private GridView mContent;
    private FrameLayout mFolderFooter;
    private int mFolderNameHeight;

    private InputMethodManager mInputMethodManager;

    public Folder(Context context) {
        this(context, null);
    }

    public Folder(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Folder(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mInputMethodManager = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        sTextSize = context.getResources().getDimensionPixelSize(R.dimen.item_title_text_size);
    }
    
    /**
     * Creates a new UserFolder, inflated from R.layout.setup_user_folder.
     *
     * @param context The application's context.
     *
     * @return A new UserFolder.
     */
    static Folder fromXml(Context context, boolean isSidebar) {
        return (Folder) LayoutInflater.from(context).inflate(
                isSidebar ? R.layout.sidebar_user_folder : R.layout.setup_user_folder,
                null);
    }

    public void setFolderIcon(FolderIcon icon) {
        mIcon = icon;
    }

    public ArrayList<View> getItemsInReadingOrder() {
        return mContents;
    }

    void bind(FolderInfo info) {
        mInfo = info;
        ArrayList<AppItemInfo> children = info.contents;
        ArrayList<AppItemInfo> overflow = new ArrayList<AppItemInfo>();
        int count = 0;
        for (AppItemInfo child : children) {
            if (!createAndAddShortcut(child)) {
                overflow.add(child);
            } else {
                count++;
            }
        }
        mFolderName.setText(mInfo.title);

        mInfo.addListener(this);
    }

    protected boolean createAndAddShortcut(AppItemInfo item) {
        final TextView textView = new TextView(mContext);

        textView.setCompoundDrawables(null,
                item.icon, null, null);
        textView.setText(item.title);
        textView.setTag(item);
        textView.setSingleLine(true);
        textView.setEllipsize(TruncateAt.END);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sTextSize);

        GridView.LayoutParams lp =
            new GridView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mContents.add(textView);
        return true;
    }
    
    public int getItemCount() {
        return mContents.size();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = (GridView) findViewById(R.id.folder_content);
        mContent.setAdapter(new ContentsAdapter());
        mFolderFooter = (FrameLayout) findViewById(R.id.folder_footer);
        mFolderName = (FolderEditText) findViewById(R.id.folder_name);
        mFolderName.setFolder(this);
        mFolderName.setOnFocusChangeListener(this);

        // We find out how tall the text view wants to be (it is set to wrap_content), so that
        // we can allocate the appropriate amount of space for it.
        int measureSpec = MeasureSpec.UNSPECIFIED;
        mFolderFooter.measure(measureSpec, measureSpec);
        mFolderNameHeight = mFolderFooter.getMeasuredHeight();

        // We disable action mode for now since it messes up the view on phones
        mFolderName.setCustomSelectionActionModeCallback(mActionModeCallback);
        mFolderName.setOnEditorActionListener(this);
        mFolderName.setSelectAllOnFocus(true);
        mFolderName.setInputType(mFolderName.getInputType() |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

    }

    /**
     * @return the FolderInfo object associated with this folder
     */
    FolderInfo getInfo() {
        return mInfo;
    }

    @Override
    public void onAdd(AppItemInfo item) {
        // make sure this is not a duplicate
        for (View v : mContents) {
            AppItemInfo ai = (AppItemInfo) v.getTag();
            if (ai.packageName.equals(item.packageName) && ai.className.equals(item.className))
                return;
        }

        createAndAddShortcut(item);
        ContentsAdapter adapter = (ContentsAdapter)mContent.getAdapter();
        adapter.notifyDataSetChanged();
        mContent.setAdapter(adapter);
    }

    @Override
    public void onRemove(AppItemInfo item) {
        mContents.remove(item);
        View v = mContent.findViewWithTag(item);
        if (v != null)
            removeView(v);
    }
    
    public void removeView(View v) {
        mContents.remove(v);
        ContentsAdapter adapter = (ContentsAdapter)mContent.getAdapter();
        adapter.notifyDataSetChanged();
        mContent.setAdapter(adapter);
    }

    @Override
    public void onTitleChanged(CharSequence title) {
    }

    @Override
    public void onItemsChanged() {
    }

    public void startEditingFolderName() {
        mFolderName.setHint("");
        mIsEditingName = true;
    }

    public void doneEditingFolderName(boolean commit) {
        // Convert to a string here to ensure that no other state associated with the text field
        // gets saved.
        String newTitle = mFolderName.getText().toString();
        mInfo.setTitle(newTitle);
        // In order to clear the focus from the text field, we set the focus on ourself. This
        // ensures that every time the field is clicked, focus is gained, giving reliable behavior.
        requestFocus();

        Selection.setSelection((Spannable) mFolderName.getText(), 0, 0);
        mIsEditingName = false;
    }

    public void dismissEditingName() {
        mInputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
        doneEditingFolderName(true);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mFolderName && hasFocus) {
            startEditingFolderName();
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            dismissEditingName();
            return true;
        }
        return false;
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
    };
    
    private class ContentsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mContents.size();
        }

        @Override
        public Object getItem(int position) {
            return mContents.get(position).getTag();
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return mContents.get(position);
        }
        
    }
}
