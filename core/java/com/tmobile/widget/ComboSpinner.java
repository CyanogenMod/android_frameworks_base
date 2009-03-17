package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

public class ComboSpinner extends Spinner {
    
	// TODO: Copied from Spinner. Use base class when access changed.
    private static class DropDownAdapter implements ListAdapter, SpinnerAdapter {
        private SpinnerAdapter mAdapter;

        public DropDownAdapter(SpinnerAdapter adapter) {
            mAdapter = adapter;
        }

        public int getCount() {
            return mAdapter == null ? 0 : mAdapter.getCount();
        }

        public Object getItem(int position) {
            return mAdapter == null ? null : mAdapter.getItem(position);
        }

        public long getItemId(int position) {
            return mAdapter == null ? -1 : mAdapter.getItemId(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return getDropDownView(position, convertView, parent);
        }

        public View getDropDownView(int position, View convertView, ViewGroup parent) {
        	View view = mAdapter == null ? null :
                mAdapter.getDropDownView(position, convertView, parent);
            return view;
        }

        public boolean hasStableIds() {
            return mAdapter != null && mAdapter.hasStableIds();
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            if (mAdapter != null) {
                mAdapter.registerDataSetObserver(observer);
            }
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (mAdapter != null) {
                mAdapter.unregisterDataSetObserver(observer);
            }
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return true;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }
        
        public boolean isEmpty() {
            return getCount() == 0;
        }
    }

    private PopupWindow mWindow;
	private ListView mList;
	
	static private final int kPopupWindowPadding = 6;
	
    public ComboSpinner(Context context) {
        this(context, null);
    }

    public ComboSpinner(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.spinnerStyle);
    }

    public ComboSpinner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setClickable(true);
        
		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.ListView);
		
		Drawable listSelector = a.getDrawable(R.styleable.AbsListView_listSelector);

		mList = new ListView(context);
        mList.setCacheColorHint(0x00000000);
        mList.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        mList.setOnItemClickListener(mItemClickListener);
        mList.setBackgroundColor(Color.WHITE);
        if (listSelector != null) {
        	mList.setSelector(listSelector);
        }
        mWindow = new PopupWindow(context);
        mWindow.setFocusable(true);
		mWindow.setContentView(mList);
		
		a.recycle();
    }
    
    private OnItemClickListener mItemClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {		    
			setSelection(position);
			dismissPopup();
			requestFocus();
		}
	};
	
	@Override
	public void setAdapter(SpinnerAdapter adapter) {
		super.setAdapter(adapter);
		if (mList != null) {
			mList.setAdapter((ListAdapter)new DropDownAdapter(getAdapter()));
		}
	}
	
	private void dismissPopup() {
		if (mWindow.isShowing()) {
			mWindow.dismiss();
		}
		invalidate();
	}

	public void showPopup() {
		if (mWindow.isShowing()) {
			return;
		}
        mWindow.setWidth(getWidth() - (kPopupWindowPadding << 1));
        mWindow.setHeight(150);
		mWindow.showAsDropDown(this, kPopupWindowPadding, 0);
		invalidate();
	}

    @Override
    public boolean performClick() {
        showPopup();
        return true;
    }

}
