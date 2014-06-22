/*
* Modification Copyright (C) 2014 AOSB Project
* Author Simon Lightfoot @slightfoot
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.systemui.recent;

import com.android.systemui.recent.model.Person;
import com.android.systemui.recent.model.PersonUtils;
import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import com.android.systemui.R;

public class PersonBubbleActivity extends Activity
{
	public  static final String TAG = PersonBubbleActivity.class.getSimpleName();
	private static final String EXTRA_ORIGIN_X = "extraOriginX";
	private static final String EXTRA_ORIGIN_Y = "extraOriginY";
	private static final String EXTRA_ORIGIN_W = "extraOriginW";
	private static final String EXTRA_ORIGIN_H = "extraOriginH";
	private static final String EXTRA_PERSON   = "extraPerson";
	
	private static final TimeInterpolator sDecelerator = new DecelerateInterpolator();
	private static final TimeInterpolator sAccelerator = new AccelerateInterpolator();
	
	private int    mOriginX, mOriginY, mOriginW, mOriginH;
	private Person mPerson;
	
	private PopupLayout mPopupLayout;
	private int mDuration;
	
	
	public static Intent createIntent(Context context, View view, Person person)
	{
		int[] screenLocation = new int[2];
		view.getLocationOnScreen(screenLocation);
		return new Intent(context.getApplicationContext(), PersonBubbleActivity.class)
			.putExtra(EXTRA_ORIGIN_X, screenLocation[0])
			.putExtra(EXTRA_ORIGIN_Y, screenLocation[1])
			.putExtra(EXTRA_ORIGIN_W, view.getWidth())
			.putExtra(EXTRA_ORIGIN_H, view.getHeight())
			.putExtra(EXTRA_PERSON,   person);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		overridePendingTransition(0, 0);
		super.onCreate(savedInstanceState);
		
		Intent intent = getIntent();
		if(intent == null || intent.getExtras() == null){
			Log.e(TAG, "Must supply extras");
			finish();
			return;
		}
		
		mOriginX = intent.getIntExtra(EXTRA_ORIGIN_X, 0);
		mOriginY = intent.getIntExtra(EXTRA_ORIGIN_Y, 0);
		mOriginW = intent.getIntExtra(EXTRA_ORIGIN_W, 0);
		mOriginH = intent.getIntExtra(EXTRA_ORIGIN_H, 0);
		mPerson  = intent.getParcelableExtra(EXTRA_PERSON);
		
		mPopupLayout = new PopupLayout(this);
		setContentView(getLayoutInflater()
			.inflate(R.layout.person_bubble, mPopupLayout));
		
		View personBubble = mPopupLayout.findViewById(R.id.person_bubble);
		personBubble.setBackgroundDrawable(new PopupBackground(this));
		
		TextView personName = (TextView)mPopupLayout.findViewById(R.id.person_name);
		TextView personStatus = (TextView)mPopupLayout.findViewById(R.id.person_status);

		personName.setText(mPerson.getName());
		//personName.setTextAppearance(this, android.R.style.TextAppearance_Small);

		personStatus.setText(mPerson.getStatus());
		personStatus.setTextAppearance(this, android.R.style.TextAppearance_Small);
		
		mDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
		
		if(savedInstanceState == null){
			ViewTreeObserver observer = mPopupLayout.getViewTreeObserver();
			observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
			{
				@Override
				public boolean onPreDraw()
				{
					mPopupLayout.getViewTreeObserver().removeOnPreDrawListener(this);
					mPopupLayout.setAlpha(0.0f);
					mPopupLayout.animate()
						.setDuration(mDuration)
						.setInterpolator(sDecelerator)
						.alpha(1.0f);
					return true;
				}
			});
		}
	}
	
	@Override
	public void onBackPressed()
	{
		finishAndAnimate();
	}
	
	@Override
	public void finish()
	{
		super.finish();
		overridePendingTransition(0, 0);
	}
	
	private void finishAndAnimate()
	{
		mPopupLayout.animate()
			.setDuration(mDuration / 2)
			.setInterpolator(sAccelerator)
			.alpha(0.0f)
			.setListener(new SimpleAnimatorListener()
			{
				@Override
				public void onAnimationEnd(Animator animation)
				{
					finish();
				}
			});
	}
	
	private class PopupLayout extends ViewGroup
		implements View.OnClickListener
	{
		private Rect mPos = new Rect();
		
		public PopupLayout(Context context)
		{
			super(context);
			setOnClickListener(this);
		}
		
		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
		{
			measureChildren(widthMeasureSpec, heightMeasureSpec);
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
		
		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b)
		{
			if(getChildCount() == 1){
				final View child = getChildAt(0);
				int width  = child.getMeasuredWidth();
				int height = child.getMeasuredHeight();
				
				int[] screenLocation = new int[2];
				getLocationOnScreen(screenLocation);
				
				mPos.left   = (mOriginX - screenLocation[0]) + ((mOriginW - width) / 2);
				mPos.top    = (mOriginY - screenLocation[1] + mOriginH);
				mPos.right  = mPos.left + width;
				mPos.bottom = mPos.top + height;
				
				if(mPos.right > r){
					mPos.offset((r - mPos.right), 0);
				}else if(mPos.left < 0){
					mPos.offset((l - mPos.left), 0);
				}
				
				child.layout(mPos.left, mPos.top, mPos.right, mPos.bottom);
			}
			else{
				Log.w(TAG, "PopupLayout should only have 1 view");
			}
		}
		
		@Override
		public void onClick(View v)
		{
			finishAndAnimate();
		}
	}
	
	private class PopupBackground extends Drawable
	{
		private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private RectF mRectF = new RectF(); 
		private Path  mPath  = new Path();
		private float mCorner;
		
		
		public PopupBackground(Context context)
		{
			Resources res = context.getResources();
			mCorner = res.getDimensionPixelSize(R.dimen.person_bubble_corner);
			mPaint.setColor(res.getColor(R.color.person_bubble_bg));
			mPaint.setStyle(Paint.Style.FILL);
		}
		
		@Override
		protected void onBoundsChange(Rect bounds)
		{
			super.onBoundsChange(bounds);
			mRectF.set(bounds);
			mRectF.top += mCorner;
			mPath.reset();
			mPath.moveTo(mRectF.centerX() - mCorner, mRectF.top);
			mPath.lineTo(mRectF.centerX(), mRectF.top - mCorner);
			mPath.lineTo(mRectF.centerX() + mCorner, mRectF.top);
			mPath.addRoundRect(mRectF, mCorner, mCorner, Path.Direction.CW);
		}
		
		@Override
		public void draw(Canvas canvas)
		{
			canvas.drawPath(mPath, mPaint);
		}
		
		@Override
		public int getOpacity()
		{
			return PixelFormat.TRANSLUCENT;
		}
		
		@Override
		public void setAlpha(int alpha)
		{
			// 
		}
		
		@Override
		public void setColorFilter(ColorFilter cf)
		{
			//
		}
	}
}
