package android.graphics.drawable;

import com.android.internal.R;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.text.format.Time;

public class ClockDrawable extends Drawable {

	private Drawable mHourHand;
	private Drawable mMinuteHand;
	private Drawable mSecondHand;
	private Drawable mDial;
	private Resources mResources;
	private int size;
	private int mHour;
	private int mMinute;
	private int mSecond;
	Context mContext;

	public ClockDrawable(Context mContext,int size,int hour,int minute,int second) {
		this.size = size;
		mResources = mContext.getResources();
		mDial = mResources.getDrawable(R.drawable.launcher_clock);
		mDial.setBounds(0, 0, size, size);
		mHourHand = mResources.getDrawable(R.drawable.launcher_clock_hour);
		mHourHand.setBounds(0, 0, size, size);
		mMinuteHand = mResources.getDrawable(R.drawable.launcher_clock_min);
		mMinuteHand.setBounds(0, 0, size, size);
		mSecondHand = mResources.getDrawable(R.drawable.launcher_clock_sec);
		mSecondHand.setBounds(0, 0, size, size);
		mHour = hour;
		mMinute = minute;
		mSecond = second; 
	}

	@Override
	public void draw(Canvas canvas) {
		mDial.draw(canvas);
		canvas.save();
		canvas.rotate(mSecond * 6.0f, size/2, size/2);
		mSecondHand.draw(canvas);
		canvas.restore();
		canvas.save();
		canvas.rotate(mHour * 30.0f+ mMinute % 60 * 0.5f, size/2, size/2);
		mHourHand.draw(canvas);
		canvas.restore();
		canvas.save();
		canvas.rotate(mMinute * 6.0f + mSecond % 60 * 0.1f, size/2, size/2);
		mMinuteHand.draw(canvas);
		canvas.restore();
	}

	@Override
	public int getIntrinsicHeight() {
		// TODO Auto-generated method stub
		return size;
	}

	@Override
	public int getIntrinsicWidth() {
		// TODO Auto-generated method stub
		return size;
	}

	@Override
	public int getOpacity() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setAlpha(int alpha) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		// TODO Auto-generated method stub

	}
}
