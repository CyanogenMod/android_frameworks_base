package com.tmobile.widget;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.internal.R;

public class Filmstrip extends RelativeLayout implements OnFocusChangeListener {

	// The max label length is defined in the style guide
	private static final int MAX_LABEL_LENGTH = 16;
	private static final int TITLE_TEXT_FIELD_WIDTH = 156;

	private static final int TITLE_TEXT_SIZE_SELECTED = 22;
	private static final int TITLE_TEXT_SIZE_UNSELECTED = 20;

	private static final String TITLE_TYPEFACE_NAME = "sans";
	private static final Typeface TITLE_TYPEFACE = Typeface.create(
			TITLE_TYPEFACE_NAME, Typeface.NORMAL);

	private static final Gallery.LayoutParams ImageViewLayoutParams = new Gallery.LayoutParams(
			32, 32);

	private static final Gallery.LayoutParams SelectedImageViewLayoutParams = new Gallery.LayoutParams(
			40, 40);

	private static final Gallery.LayoutParams TextViewLayoutParams = new Gallery.LayoutParams(
			Gallery.LayoutParams.WRAP_CONTENT,
			Gallery.LayoutParams.FILL_PARENT);

	 class FilmstripItem extends Object {

		private Context mContext;
		private String mName;
		private Intent mIntent;
		private TextView mTitleView;

		private Drawable mImageDrawable;
		private ImageView mImageView;

		private FilmstripItem(Context context, String name, Intent intent) {
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

		private FilmstripItem(Context context, Drawable imageResource) {

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
					mTitleView = new TextView(mContext);
					mTitleView.setTypeface(TITLE_TYPEFACE);
					mTitleView.setText(mName);
					mTitleView.setLayoutParams(TextViewLayoutParams);
					mTitleView.setTextSize(TITLE_TEXT_SIZE_UNSELECTED);
					mTitleView.setId(position);
					mTitleView.setTextColor(Color.WHITE);
					mTitleView.setGravity(Gravity.CENTER);
					mTitleView.setWidth(TITLE_TEXT_FIELD_WIDTH);
					mTitleView.setPadding(10, 5, 10, 5);
				}
				return mTitleView;
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

	private class FilmstripTitleAdapter extends BaseAdapter {

		public int getCount() {
			return getFilmstripSize();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = mFilmstripItems.get(position).getView(position);

			if (view instanceof TextView) {
				TextView textView = (TextView) view;
				 textView
				 .setTextSize((position != mFilmstripSelection) ?
				 TITLE_TEXT_SIZE_UNSELECTED
				 : TITLE_TEXT_SIZE_SELECTED);

				setBackgroundResource(position, textView);
			} else if (view instanceof ImageView) {

			}

			return view;
		}
	}

	public class FilmstripOnItemSelectedListener implements
			OnItemSelectedListener {

		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			if ((position < 0) || (mFilmstripItems.size() <= position))
				return;

			if ((position < 0) || (mFilmstripItems.size() <= position))
				return;
			
			if (mFilmstripSelection != -1) {
				View oldView = parent
						.findViewById(mFilmstripSelection);
				
				if (oldView instanceof TextView) {
					TextView oldTextView = (TextView) oldView;
				
					if (null != oldTextView) {
						// make the unselected button look unselected
						// oldText.setBackgroundDrawable(null);
						oldTextView.setWidth(TITLE_TEXT_FIELD_WIDTH);
						oldTextView.setTextSize(TITLE_TEXT_SIZE_UNSELECTED);
						oldTextView.setTypeface(TITLE_TYPEFACE, Typeface.NORMAL);
						oldTextView.setGravity(Gravity.CENTER);
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
			
			mFilmstripSelection = position;
			
			if (view instanceof TextView) {
				TextView newTextView = (TextView) view;
			
				// make the selected button look selected
				// newText.setBackgroundDrawable(mCarouselSelectionTitleBackground);
				// newText.setBackgroundResource(R.drawable.element_carousel_highlight_dark);
				newTextView.setWidth(TITLE_TEXT_FIELD_WIDTH);
				newTextView.setTextSize(TITLE_TEXT_SIZE_SELECTED);
				newTextView.setTypeface(TITLE_TYPEFACE, Typeface.BOLD);
				newTextView.setGravity(Gravity.CENTER);
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


	private ArrayList<FilmstripItem> mFilmstripItems;
	private FilmstripTitleAdapter mFilmstripTitleAdapter;
	private FilmstripOnItemSelectedListener mFilmstripOnItemSelectedListener;
	private int mFilmstripSelection = -1;
	
	private Drawable mFrameBackground;
	private Drawable mFrameSelectedBackground;
	private Gallery mFilmstripSelector;
	private ImageView mLeftArrow;
	private ImageView mRightArrow;
	
	public Filmstrip(Context context) {
		this(context, null, 0);
	}
	
	public Filmstrip(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.filmstripStyle);
	}
	
	public Filmstrip(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setFocusable(true);
		setFocusableInTouchMode(true);
		
		TypedArray a = 
            context.obtainStyledAttributes(attrs, R.styleable.Filmstrip, defStyle, 0);
				
		mFilmstripSelector = new Gallery(context, attrs, defStyle);
		mLeftArrow = new ImageView(context, attrs, defStyle);
		mRightArrow = new ImageView(context, attrs, defStyle);
		mFilmstripItems = new ArrayList<FilmstripItem>();
		mFilmstripTitleAdapter = new FilmstripTitleAdapter();
		mFilmstripOnItemSelectedListener = new FilmstripOnItemSelectedListener();
		
		mFrameBackground = a.getDrawable(R.styleable.Filmstrip_frameBackground);		
		mFrameSelectedBackground = a.getDrawable(R.styleable.Filmstrip_frameSelectedBackground);
		
		initWidget(a);
		
		a.recycle();
	}

	public void setOnItemSelectedListener(OnItemSelectedListener l){
		mFilmstripSelector.setOnItemSelectedListener(l);
		
	}
	
	
	private void initWidget(TypedArray a) {
		setOnFocusChangeListener(this);
		
		setOnItemSelectedListener(new FilmstripOnItemSelectedListener());
		
		RelativeLayout.LayoutParams rowLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 50);
		rowLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		this.setGravity(Gravity.LEFT);
		this.setLayoutParams(rowLayoutParams);
		
		
		if (mFilmstripSelector != null) {
			mFilmstripSelector.setAdapter(getFilmstripTitleAdapter());
			
			mFilmstripSelector.setLayoutParams(new Gallery.LayoutParams(Gallery.LayoutParams.FILL_PARENT, Gallery.LayoutParams.FILL_PARENT));
		}
		
		mLeftArrow.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				int index = mFilmstripSelector.getSelectedItemPosition();
				
				index --;
				
				if (index < 0) {
					index = 0;
				}
				
				mFilmstripSelector.setSelection(index);
				
			
			}
		});

		mRightArrow.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				int index = mFilmstripSelector.getSelectedItemPosition();
				
				index ++;
				
				int size = mFilmstripSelector.getCount();
				
				if (index >= size) {
					index = size -1 ;
				}
				
				mFilmstripSelector.setSelection(index);
				
			}
		});

		mFilmstripSelector.setPadding(13, 0, 13, 0);
		mFilmstripSelector.setSpacing(4);
		
		mFilmstripSelector.setAnimationDuration(500);
		
		this.addView(mFilmstripSelector, -1);
		
		RelativeLayout.LayoutParams leftLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT);
		leftLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		leftLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
		
		Drawable leftImageDrawable = a.getDrawable(R.styleable.Filmstrip_previousButton);
		mLeftArrow.setLayoutParams(leftLayoutParams);
		mLeftArrow.setImageDrawable(leftImageDrawable);
		
		this.addView(mLeftArrow, -1);
		
		RelativeLayout.LayoutParams rightLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		rightLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		rightLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
		
		Drawable rightImageDrawable = a.getDrawable(R.styleable.Filmstrip_nextButton);
		mRightArrow.setLayoutParams(rightLayoutParams);
		mRightArrow.setImageDrawable(rightImageDrawable);
		
		this.addView(mRightArrow, -1);
	}
	
	public FilmstripTitleAdapter getFilmstripTitleAdapter() {
		return mFilmstripTitleAdapter;
	}
	
	public FilmstripOnItemSelectedListener getFilmstripOnItemSelectedListener() {
		return mFilmstripOnItemSelectedListener;
	}

	public void add(Context context, String name, Intent intent) {
			
		mFilmstripItems.add(new FilmstripItem(context, name, intent));
		mFilmstripTitleAdapter.notifyDataSetChanged();
		
		updateArrowStates();
		
	}

	public void add(Context context, Drawable imageResource) {
		
		mFilmstripItems.add(new FilmstripItem(context, imageResource));
		mFilmstripTitleAdapter.notifyDataSetChanged();
		
		updateArrowStates();
	}

	

	private void setBackgroundResource(int position, View view) {
		Drawable drawable = mFrameBackground;

		if (position == mFilmstripSelection) {
			drawable = mFrameSelectedBackground;
		}
		
		view.setBackgroundDrawable(drawable);
	}
	
	
	public void setFilmstripSelection(int itemIndex) {
		mFilmstripSelector.setSelection(itemIndex);
		updateArrowStates();
	}

	private void updateArrowStates() {
		mLeftArrow
		.setVisibility((mFilmstripSelector.getSelectedItemPosition() > 0) ? View.VISIBLE
				: View.INVISIBLE);
		mRightArrow
		.setVisibility((mFilmstripSelector.getSelectedItemPosition() < (mFilmstripSelector
				.getCount() - 1)) ? View.VISIBLE : View.INVISIBLE);
	}

	public int getFilmstripSize() {
		return mFilmstripItems.size();
	}

	public int getFilmstripSelection() {
		return mFilmstripSelection;
	}

	public FilmstripItem getFilmstripItem(int position) {
		return mFilmstripItems.get(position);
	}
	
public void onFocusChange(View v, boolean hasFocus) {
		
		if (hasFocus && v == this) {
			System.out.println ( " v is:" + v.getClass().toString());
			
			
		}		
	}

	// Dispatch LEFT/RIGTH key to gallery to move gallery items
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		return mFilmstripSelector.dispatchKeyEvent(event);
	}
	

}
