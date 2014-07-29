/*
* Copyright (C) 2014 AOSB Project
* Author Hany alsamman @codex-corp
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
import com.android.systemui.recent.model.SlidingLayer;
import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import com.android.systemui.R;

public class PersonSlideActivity extends Activity
{
	public  static final String TAG = PersonSlideActivity.class.getSimpleName();
	private static final String EXTRA_PERSON   = "extraPerson";

	private Person mPerson;
	
	private SlidingLayer mSlidingLayer;
	private int mDuration;
	
	public static Intent createIntent(Context context, View view, Person person)
	{
		return new Intent(context.getApplicationContext(), PersonSlideActivity.class).putExtra(EXTRA_PERSON, person)
		                    .setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
	super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if(intent == null || intent.getExtras() == null){
			Log.e(TAG, "Must supply extras");
			finish();
			return;
		}

		mPerson = intent.getParcelableExtra(EXTRA_PERSON);		

		setContentView(getLayoutInflater()
			.inflate(R.layout.person_bubble, null));

		TextView personName = (TextView) findViewById(R.id.person_name);
		TextView personStatus = (TextView) findViewById(R.id.person_status);

		personName.setText(mPerson.getName());

		personStatus.setText(mPerson.getStatus());
		personStatus.setTextAppearance(this, android.R.style.TextAppearance_Small);

		mSlidingLayer = (SlidingLayer) findViewById(R.id.sliding_layer);
		LayoutParams rlp = (LayoutParams) mSlidingLayer.getLayoutParams();
		//rlp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

		rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP);

		rlp.width = LayoutParams.MATCH_PARENT;
		rlp.height = getResources().getDimensionPixelSize(R.dimen.layer_width);

		mSlidingLayer.setShadowWidthRes(R.dimen.shadow_width);
		mSlidingLayer.setOffsetWidth(35);
		mSlidingLayer.setShadowDrawable(R.drawable.sidebar_shadow);
		mSlidingLayer.setStickTo(SlidingLayer.STICK_TO_TOP);
		mSlidingLayer.setOpenOnTapEnabled(true);
		mSlidingLayer.setCloseOnTapEnabled(true);
		mSlidingLayer.setLayoutParams(rlp);	
	}

	@Override
	protected void onStop() 
	{
	    super.onStop();
	    if (mSlidingLayer.isOpened()) {
		mSlidingLayer.closeLayer(true);
	    }
	    finish();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
		    if (mSlidingLayer.isOpened()) {
			mSlidingLayer.closeLayer(true);

			try {
		          ((SlidingLayer) mSlidingLayer.getParent()).removeView(mSlidingLayer);
			} catch (Exception e) {

			}

			return true;
		    }
		default:
		    return super.onKeyDown(keyCode, event);
		}
	}
}
