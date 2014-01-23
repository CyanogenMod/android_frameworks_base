package android.graphics.drawable;

import com.android.internal.R;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

public class CalendarDrawable extends Drawable {
	Context mContext;
	Drawable mCalendar;
	Drawable mNum;
	int size;

	public CalendarDrawable(Context context,int size,int day) {
		mContext = context;
		this.size = size;
		Resources r = mContext.getResources();
		mCalendar = r.getDrawable(R.drawable.launcher_calendar);
		mCalendar.setBounds(0, 0, size, size);
		mNum = r.getDrawable(r.getIdentifier("launcher_calendar_num"+day, "drawable", "android"));
		mNum.setBounds(0, 0, size, size);
	}

	@Override
	public void draw(Canvas canvas) {
		mCalendar.draw(canvas);
		mNum.draw(canvas);
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		// TODO Auto-generated method stub

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
	public int getIntrinsicHeight() {
		return size;
	}
	@Override
	public int getIntrinsicWidth() {
		return size;
	}
}
