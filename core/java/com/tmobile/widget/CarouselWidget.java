package com.tmobile.widget;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.internal.R;

class CarouselWidget extends RelativeLayout {

	// The max label length is defined in the style guide
	private static final int MAX_LABEL_LENGTH = 16;
	private static final int SELECTED_TITLE_TEXT_FIELD_WIDTH = 110;
	//Made the variable non final as the value need to be changed for Landscape mode, for landscape it is 75
	private static int UNSELECTED_TITLE_TEXT_FIELD_WIDTH_PORTRAIT = 78;
	private static int UNSELECTED_TITLE_TEXT_FIELD_WIDTH_LANDSCAPE = 75;

	private static final int TITLE_TEXT_SIZE_SELECTED = 14;
	private static final int TITLE_TEXT_SIZE_UNSELECTED = 13;

	private static final String TITLE_TYPEFACE_NAME = "sans";
	private static final Typeface TITLE_TYPEFACE = Typeface.create(
			TITLE_TYPEFACE_NAME, Typeface.NORMAL);

	private static final Gallery.LayoutParams ImageViewLayoutParams = new Gallery.LayoutParams(
			32, 32);

	private static final Gallery.LayoutParams SelectedImageViewLayoutParams = new Gallery.LayoutParams(
			40, 40);

	private static final LinearLayout.LayoutParams TextViewLayoutParams;
	
	private static final Gallery.LayoutParams LayoutTextViewLayoutParams = new Gallery.LayoutParams(
			110,
			Gallery.LayoutParams.FILL_PARENT);

	static {
		TextViewLayoutParams = new LinearLayout.LayoutParams(
				Gallery.LayoutParams.WRAP_CONTENT,
				Gallery.LayoutParams.WRAP_CONTENT);
		TextViewLayoutParams.gravity = Gravity.CENTER;
	}

	 class CarouselWidgetItem extends Object {

		private Context mContext;
		private String mName;
		private Intent mIntent;
		private TextView mTitleView;
		private LinearLayout mTitleViewLayout;

		private Drawable mImageDrawable;
		private ImageView mImageView;

		private CarouselWidgetItem(Context context, String name, Intent intent) {
			mContext = context;
			mIntent = intent;
			mTitleView = null;

			if (name.length() > MAX_LABEL_LENGTH) {
				StringBuffer sb = new StringBuffer(name.substring(0,
						MAX_LABEL_LENGTH));
				sb.append("...");
				mName = sb.toString();

			} else {
				mName = name;
			}

		}

		private CarouselWidgetItem(Context context, Drawable imageResource) {

			mContext = context;

			mImageDrawable = imageResource;

		}

		public String getName() {
			return mName;
		}

		public Intent getIntent() {
			return mIntent;
		}

		public View getView(int position) {

			if (null == mImageDrawable) {
				if (null == mTitleView) {
					mTitleViewLayout = new LinearLayout(mContext);
					mTitleViewLayout.setLayoutParams(LayoutTextViewLayoutParams);
					
					mTitleView = new TextView(mContext);
					mTitleView.setTypeface(TITLE_TYPEFACE);
					mTitleView.setText(mName);
					mTitleView.setTextSize(TITLE_TEXT_SIZE_UNSELECTED);
					mTitleView.setId(position);
					mTitleView.setTextColor(Color.WHITE);
					mTitleView.setGravity(Gravity.CENTER_HORIZONTAL);
					if(isPortrait) {
						mTitleView.setWidth(UNSELECTED_TITLE_TEXT_FIELD_WIDTH_PORTRAIT);
					} else {
						mTitleView.setWidth(UNSELECTED_TITLE_TEXT_FIELD_WIDTH_LANDSCAPE);	
					}
//					mTitleView.setPadding(10, 5, 10, 5);
					mTitleView.setSingleLine(true);
					mTitleView.setEllipsize(TextUtils.TruncateAt.END);
					
					mTitleViewLayout.addView(mTitleView, TextViewLayoutParams);
				}
				return mTitleViewLayout;
			} else {
				if (null == mImageView) {
					mImageView = new ImageView(mContext);

					mImageView.setImageDrawable(mImageDrawable);
					mImageView.setScaleType(ImageView.ScaleType.FIT_XY);
					mImageView.setLayoutParams(ImageViewLayoutParams);
					mImageView.setId(position);

				}

				return mImageView;
			}
		}
	}

	private class CarouselWidgetTitleAdapter extends BaseAdapter {

		public int getCount() {
			return getCarouselWidgetSize();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = mCarouselWidgetItems.get(position).getView(position);

			if (view instanceof LinearLayout) {
				TextView textView = (TextView)(((ViewGroup)view).getChildAt(0));
				 textView
				 .setTextSize((position != mCarouselWidgetSelection) ?
				 TITLE_TEXT_SIZE_UNSELECTED
				 : TITLE_TEXT_SIZE_SELECTED);

				setBackgroundResource(position, textView);
			} else if (view instanceof ImageView) {

			}

			return view;
		}
	}

	public class CarouselWidgetOnItemSelectedListener implements
			OnItemSelectedListener {

		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			if ((position < 0) || (mCarouselWidgetItems.size() <= position))
				return;

			if ((position < 0) || (mCarouselWidgetItems.size() <= position))
				return;
			
			if (mCarouselWidgetSelection != -1) {
				View oldView = parent
						.findViewById(mCarouselWidgetSelection);
				
				if (oldView instanceof TextView) {
					TextView oldTextView = (TextView) oldView;
				
					if (null != oldTextView) {
						// make the unselected button look unselected
						// oldText.setBackgroundDrawable(null);
						if(isPortrait) {
							oldTextView.setWidth(UNSELECTED_TITLE_TEXT_FIELD_WIDTH_PORTRAIT);
						} else {
							oldTextView.setWidth(UNSELECTED_TITLE_TEXT_FIELD_WIDTH_LANDSCAPE);	
						}
						oldTextView.setTextSize(TITLE_TEXT_SIZE_UNSELECTED);
						oldTextView.setTypeface(TITLE_TYPEFACE, Typeface.NORMAL);
//                        oldTextView.setGravity(Gravity.CENTER);
						setBackgroundResource(position, oldTextView);
					}
				} else if (oldView instanceof ImageView) {
					ImageView oldImageView = (ImageView) oldView;
					
					if (null != oldImageView) {
						oldImageView.setScaleType(ImageView.ScaleType.FIT_XY);
						oldImageView.setLayoutParams(ImageViewLayoutParams);
					}
				}
			}
			
			mCarouselWidgetSelection = position;
			
			if (view instanceof LinearLayout) {
				TextView newTextView = (TextView)(((ViewGroup)view).getChildAt(0));
			
				// make the selected button look selected
				// newText.setBackgroundDrawable(mCarouselSelectionTitleBackground);
				// newText.setBackgroundResource(R.drawable.element_carousel_highlight_dark);
				newTextView.setWidth(SELECTED_TITLE_TEXT_FIELD_WIDTH);
				newTextView.setTextSize(TITLE_TEXT_SIZE_SELECTED);
				newTextView.setTypeface(TITLE_TYPEFACE, Typeface.BOLD);
//                newTextView.setPadding(10, 5, 10, 5);
//                newTextView.setGravity(Gravity.CENTER);
				setBackgroundResource(position, newTextView);
			} else if (view instanceof ImageView) {
				ImageView newImageView = (ImageView) view;
				
				if (null != newImageView) {
					newImageView.setScaleType(ImageView.ScaleType.FIT_XY);
					newImageView.setLayoutParams(SelectedImageViewLayoutParams);
				}
			}
			
			updateArrowStates();
		}

		public void onNothingSelected(AdapterView<?> parent) {
		}

	}


	private ArrayList<CarouselWidgetItem> mCarouselWidgetItems;
	private CarouselWidgetTitleAdapter mCarouselWidgetTitleAdapter;
	private CarouselWidgetOnItemSelectedListener mCarouselWidgetOnItemSelectedListener;
	private int mCarouselWidgetSelection = -1;
	
	private Drawable mFrameBackground;
	private Drawable mFrameSelectedBackground;
	private Gallery mCarouselWidgetSelector;
	private ImageView mLeftArrow;
	private ImageView mRightArrow;
	private boolean isPortrait;
	public CarouselWidget(Context context) {
		this(context, null);
	}
	
	public CarouselWidget(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.carouselWidgetStyle);
	}
	
	public CarouselWidget(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setFocusable(true);
		setFocusableInTouchMode(true);
		//Fix: To change the unselected textview width to 75 in the Landscape mode
		Configuration config = getResources().getConfiguration();
		int mOrientation = config.orientation;
		isPortrait = mOrientation == Configuration.ORIENTATION_PORTRAIT? true : false;
		//Fix End
		TypedArray a = 
			context.obtainStyledAttributes(attrs, R.styleable.CarouselWidget, defStyle, 0);
				
		mCarouselWidgetSelector = new Gallery(context);
		mLeftArrow = new ImageView(context);
		mRightArrow = new ImageView(context);
		mCarouselWidgetItems = new ArrayList<CarouselWidgetItem>();
		mCarouselWidgetTitleAdapter = new CarouselWidgetTitleAdapter();
		mCarouselWidgetOnItemSelectedListener = new CarouselWidgetOnItemSelectedListener();
		
		mFrameBackground = a.getDrawable(R.styleable.CarouselWidget_frameBackground);		
		mFrameSelectedBackground = a.getDrawable(R.styleable.CarouselWidget_frameSelectedBackground);
		
		initWidget(a);
		
		a.recycle();
	}

	public void setOnItemSelectedListener(OnItemSelectedListener l){
		mCarouselWidgetSelector.setOnItemSelectedListener(l);
		
	}
	
	
	private void initWidget(TypedArray a) {
		
		
		setOnItemSelectedListener(new CarouselWidgetOnItemSelectedListener());
		
		int height = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50,
				getResources().getDisplayMetrics());
		
		RelativeLayout.LayoutParams rowLayoutParams = 
			new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, height);
		rowLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		this.setGravity(Gravity.LEFT);
		this.setLayoutParams(rowLayoutParams);
		
		
		if (mCarouselWidgetSelector != null) {
			mCarouselWidgetSelector.setAdapter(getCarouselWidgetTitleAdapter());
			
			mCarouselWidgetSelector.setLayoutParams(new Gallery.LayoutParams(Gallery.LayoutParams.FILL_PARENT, Gallery.LayoutParams.FILL_PARENT));
		}
		
		mLeftArrow.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				int index = mCarouselWidgetSelector.getSelectedItemPosition();
				
				index --;
				
				if (index < 0) {
					index = 0;
				}
				
				mCarouselWidgetSelector.setSelection(index);
				
			
			}
		});

		mRightArrow.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				int index = mCarouselWidgetSelector.getSelectedItemPosition();
				
				index ++;
				
				int size = mCarouselWidgetSelector.getCount();
				
				if (index >= size) {
					index = size -1 ;
				}
				
				mCarouselWidgetSelector.setSelection(index);
				
			}
		});
		//According to Red Line document there should be margin of 21px from the edge 
		mCarouselWidgetSelector.setPadding(21, 0, 21, 0);
		//Space between the two filmstrip elements should be 6px
		if (isPortrait) {
			mCarouselWidgetSelector.setSpacing(6);
		} else {
			mCarouselWidgetSelector.setSpacing(8);
		}
				
		mCarouselWidgetSelector.setAnimationDuration(500);
		
		this.addView(mCarouselWidgetSelector, -1);
		
		RelativeLayout.LayoutParams leftLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT);
		leftLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		leftLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
		
		Drawable leftImageDrawable = a.getDrawable(R.styleable.CarouselWidget_previousButton);
		mLeftArrow.setLayoutParams(leftLayoutParams);
		mLeftArrow.setImageDrawable(leftImageDrawable);
		
		this.addView(mLeftArrow, -1);
		
		RelativeLayout.LayoutParams rightLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		rightLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		rightLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
		
		Drawable rightImageDrawable = a.getDrawable(R.styleable.CarouselWidget_nextButton);
		mRightArrow.setLayoutParams(rightLayoutParams);
		mRightArrow.setImageDrawable(rightImageDrawable);
		
		this.addView(mRightArrow, -1);
	}
	
	public CarouselWidgetTitleAdapter getCarouselWidgetTitleAdapter() {
		return mCarouselWidgetTitleAdapter;
	}
	
	public CarouselWidgetOnItemSelectedListener getCarouselWidgetOnItemSelectedListener() {
		return mCarouselWidgetOnItemSelectedListener;
	}

	public void add(Context context, String name, Intent intent) {
			
		mCarouselWidgetItems.add(new CarouselWidgetItem(context, name, intent));
		mCarouselWidgetTitleAdapter.notifyDataSetChanged();
		
		updateArrowStates();
		
	}

	public void add(Context context, Drawable imageResource) {
		
		mCarouselWidgetItems.add(new CarouselWidgetItem(context, imageResource));
		mCarouselWidgetTitleAdapter.notifyDataSetChanged();
		
		updateArrowStates();
	}

	

	private void setBackgroundResource(int position, View view) {
		Drawable drawable = mFrameBackground;

		if (position == mCarouselWidgetSelection) {
			drawable = mFrameSelectedBackground;
		}
		
		view.setBackgroundDrawable(drawable);
	}
	
	
	public void setCarouselWidgetSelection(int itemIndex) {
		mCarouselWidgetSelector.setSelection(itemIndex);
		updateArrowStates();
	}

	private void updateArrowStates() {
		mLeftArrow
		.setVisibility((mCarouselWidgetSelector.getSelectedItemPosition() > 0) ? View.VISIBLE
				: View.INVISIBLE);
		mRightArrow
		.setVisibility((mCarouselWidgetSelector.getSelectedItemPosition() < (mCarouselWidgetSelector
				.getCount() - 1)) ? View.VISIBLE : View.INVISIBLE);
	}

	public int getCarouselWidgetSize() {
		return mCarouselWidgetItems.size();
	}

	public int getCarouselWidgetSelection() {
		return mCarouselWidgetSelection;
	}

	public CarouselWidgetItem getCarouselWidgetItem(int position) {
		return mCarouselWidgetItems.get(position);
	}
	

	// Dispatch LEFT/RIGTH key to gallery to move gallery items
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		return mCarouselWidgetSelector.dispatchKeyEvent(event);
	}
	

}
