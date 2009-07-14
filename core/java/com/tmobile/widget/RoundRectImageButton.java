
package com.tmobile.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

public class RoundRectImageButton extends ImageButton {
    private static final String TAG = "RoundRectImageButton";

    private Path mPath;
    
    float[] mCropRadii = {4,4,4,4,4,4,4,4};
    
    public RoundRectImageButton(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
        init(context);
        Log.e(TAG, "constructor RoundRectImageButton(Context, AttributeSet, int) - attr count = " + attr.getAttributeCount() + " defStyle = " + defStyle);
    }

    public RoundRectImageButton(Context context, AttributeSet attr) {
        super(context, attr);
        init(context);
        Log.e(TAG, "constructor RoundRectImageButton(Context, AttributeSet) - attr count = " + attr.getAttributeCount());
    }

    public RoundRectImageButton(Context context) {
        super(context);
        init(context);
        Log.e(TAG, "constructor RoundRectImageButton(Context)");
    }
    
    private void init(Context context) {
        mPath = new Path();
        mPath.reset();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            drawImage(canvas);
            Log.e(TAG, "onDraw");
        } catch (Exception e) {
            Log.e(TAG, "onDraw", e);
        }
    }
    
    
    /*
     * This methos scales, translates and draws the Button's
     * image to fit. Also makes the corners rounded.
     */
    private void drawImage(Canvas canvas) {
        Drawable drawable = this.getDrawable();
        
        if (drawable == null) {
            return; // no drawable set
        }
        
        if (drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0) {
            return; // drawable is effectively empty
        }
        
        // save drawing environment state
        int saveCount = canvas.getSaveCount();
        canvas.save();

        // do our customized drawing
        Bitmap bm = convertDrawableToBitMap(drawable);
        RectF clipRect = getClipRectF();
        setupClipping(canvas, clipRect);
        canvas.drawBitmap(bm, null, clipRect, null);
        
        // restore drawing environment state
        canvas.restoreToCount(saveCount);
        
        // uncomment to see the view's drawing area and the clipRect
        // framed in red and blue, respectively -- for debugging
//        frameViewAreaAndPassedRectF(canvas, clipRect);
    }
    
    /*
     * Given a RectF, set up clipping using a RoundRect with the clipRect size
     * and using mCropRadii to determine corner roundness
     */
    private void setupClipping(Canvas canvas, RectF clipRect) {
        mPath.reset();
        mPath.addRoundRect(clipRect, mCropRadii, Path.Direction.CCW);
        canvas.clipPath(mPath, Region.Op.REPLACE);
    }
    
    /*
     * HACK: This method is used to set up a RectF that will fit
     * centered into the current background drawn by the theme (???)
     */
    private RectF getClipRectF() {
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        int viewWidth = this.getWidth();
        int viewHeight = this.getHeight();
        
        RectF clipRect = new RectF(scrollX + mPaddingLeft, scrollY + mPaddingTop,
                scrollX + viewWidth - mPaddingRight - mPaddingLeft,
                scrollY + viewHeight - mPaddingBottom - mPaddingTop);
        clipRect.inset(2.0f, 2.0f);
        clipRect.right++;
        
        clipRect.offset(1.0f, -1.0f);
        clipRect.bottom++;
        return clipRect;
    }
    
    /*
     * This method simply takes a Drawable and converts it to a Bitmap
     * by creating a Bitmap, wrapping it with a Canvas and drawing the
     * Drawable into the Canvas/Bitmap and returning the Bitmap.
     */
    private Bitmap convertDrawableToBitMap(Drawable d) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); 
        Canvas canvas = new Canvas(bitmap); 
        d.setBounds(0, 0, w, h); 
        d.draw(canvas);
        return bitmap;
    }
    
    /*
     * This is a "debugging" routine that will frame the entire view's drawing area in red
     * and also frame a passed in RectF in blue. The passed in RectF may be null.
     */
    @SuppressWarnings("unused")
    private void frameViewAreaAndPassedRectF(Canvas canvas, RectF rectF) {

        Rect savedClipBounds = canvas.getClipBounds();
        
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        
        Rect r = new Rect();
        getDrawingRect(r);
        canvas.clipRect(r, Region.Op.REPLACE);
        
        float[] pts = {
                r.left,r.top,r.right-1,r.top,
                r.right-1,r.top,r.right-1,r.bottom-1,
                r.right-1,r.bottom-1,r.left,r.bottom-1,
                r.left,r.bottom-1,r.left,r.top
                };
        canvas.drawLines(pts, paint);
        
        if ( rectF != null ) {
            float[] pts2 = {
                    rectF.left,rectF.top,rectF.right-1,rectF.top,
                    rectF.right-1,rectF.top,rectF.right-1,rectF.bottom-1,
                    rectF.right-1,rectF.bottom-1,rectF.left,rectF.bottom-1,
                    rectF.left,rectF.bottom-1,rectF.left,rectF.top
                    };
            paint.setColor(Color.BLUE);
            canvas.drawLines(pts2, paint);
        }
        
        canvas.clipRect(savedClipBounds);
    }
}
