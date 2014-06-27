package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import com.android.internal.R;

/**
 * Created by castorflex on 11/10/13.
 */
public class SmoothProgressBar extends ProgressBar {

    private static final int INTERPOLATOR_ACCELERATE = 0;
    private static final int INTERPOLATOR_LINEAR = 1;
    private static final int INTERPOLATOR_ACCELERATEDECELERATE = 2;
    private static final int INTERPOLATOR_DECELERATE = 3;

    public SmoothProgressBar(Context context) {
        this(context, null);
    }

    public SmoothProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.spbStyle);
    }

    public SmoothProgressBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = context.getResources();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SmoothProgressBar, defStyle, 0);


        final int color = a.getColor(R.styleable.SmoothProgressBar_spb_color, res.getColor(R.color.spb_default_color));
        final int sectionsCount = a.getInteger(R.styleable.SmoothProgressBar_spb_sections_count, res.getInteger(R.integer.spb_default_sections_count));
        final int separatorLength = a.getDimensionPixelSize(R.styleable.SmoothProgressBar_spb_stroke_separator_length, res.getDimensionPixelSize(R.dimen.spb_default_stroke_separator_length));
        final float strokeWidth = a.getDimension(R.styleable.SmoothProgressBar_spb_stroke_width, res.getDimension(R.dimen.spb_default_stroke_width));
        final float speed = a.getFloat(R.styleable.SmoothProgressBar_spb_speed, Float.parseFloat(res.getString(R.string.spb_default_speed)));
        final int iInterpolator = a.getInteger(R.styleable.SmoothProgressBar_spb_interpolator, res.getInteger(R.integer.spb_default_interpolator));
        final boolean reversed = a.getBoolean(R.styleable.SmoothProgressBar_spb_reversed, res.getBoolean(R.bool.spb_default_reversed));
        final boolean mirrorMode = a.getBoolean(R.styleable.SmoothProgressBar_spb_mirror_mode, res.getBoolean(R.bool.spb_default_mirror_mode));
        final int colorsId = a.getResourceId(R.styleable.SmoothProgressBar_spb_colors, 0);
        a.recycle();

        //interpolator
        Interpolator interpolator;
        switch (iInterpolator) {
            case INTERPOLATOR_ACCELERATEDECELERATE:
                interpolator = new AccelerateDecelerateInterpolator();
                break;
            case INTERPOLATOR_DECELERATE:
                interpolator = new DecelerateInterpolator();
                break;
            case INTERPOLATOR_LINEAR:
                interpolator = new LinearInterpolator();
                break;
            case INTERPOLATOR_ACCELERATE:
            default:
                interpolator = new AccelerateInterpolator();
        }

        int[] colors = null;
        //colors
        if (colorsId != 0) {
            colors = res.getIntArray(colorsId);
        }

        SmoothProgressDrawable.Builder builder = new SmoothProgressDrawable.Builder(context)
                .speed(speed)
                .interpolator(interpolator)
                .sectionsCount(sectionsCount)
                .separatorLength(separatorLength)
                .strokeWidth(strokeWidth)
                .reversed(reversed)
                .mirrorMode(mirrorMode);

        if(colors != null && colors.length > 0)
            builder.colors(colors);
        else
            builder.color(color);

        setIndeterminateDrawable(builder.build());
    }

    public void applyStyle(int styleResId){
        TypedArray a = getContext().obtainStyledAttributes(null, R.styleable.SmoothProgressBar, 0, styleResId);

        if(a.hasValue(R.styleable.SmoothProgressBar_spb_color)){
            setSmoothProgressDrawableColor(a.getColor(R.styleable.SmoothProgressBar_spb_color, 0));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_colors)){
            int colorsId = a.getResourceId(R.styleable.SmoothProgressBar_spb_colors, 0);
            if(colorsId != 0){
                int[] colors = getResources().getIntArray(colorsId);
                if(colors != null && colors.length > 0)
                    setSmoothProgressDrawableColors(colors);
            }
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_sections_count)){
            setSmoothProgressDrawableSectionsCount(a.getInteger(R.styleable.SmoothProgressBar_spb_sections_count, 0));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_stroke_separator_length)){
            setSmoothProgressDrawableSeparatorLength(a.getDimensionPixelSize(R.styleable.SmoothProgressBar_spb_stroke_separator_length, 0));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_stroke_width)){
            setSmoothProgressDrawableStrokeWidth(a.getDimension(R.styleable.SmoothProgressBar_spb_stroke_width, 0));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_speed)){
            setSmoothProgressDrawableSpeed(a.getFloat(R.styleable.SmoothProgressBar_spb_speed, 0));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_reversed)){
            setSmoothProgressDrawableReversed(a.getBoolean(R.styleable.SmoothProgressBar_spb_reversed, false));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_mirror_mode)){
            setSmoothProgressDrawableMirrorMode(a.getBoolean(R.styleable.SmoothProgressBar_spb_mirror_mode, false));
        }
        if(a.hasValue(R.styleable.SmoothProgressBar_spb_interpolator)){
            int iInterpolator = a.getInteger(R.styleable.SmoothProgressBar_spb_interpolator, -1);
            Interpolator interpolator;
            switch (iInterpolator) {
                case INTERPOLATOR_ACCELERATEDECELERATE:
                    interpolator = new AccelerateDecelerateInterpolator();
                    break;
                case INTERPOLATOR_DECELERATE:
                    interpolator = new DecelerateInterpolator();
                    break;
                case INTERPOLATOR_LINEAR:
                    interpolator = new LinearInterpolator();
                    break;
                case INTERPOLATOR_ACCELERATE:
                    interpolator = new AccelerateInterpolator();
                    break;
                default:
                    interpolator = null;
            }
            if(interpolator != null){
                setInterpolator(interpolator);
            }
        }
        a.recycle();
    }

    private SmoothProgressDrawable checkIndeterminateDrawable(){
        Drawable ret = getIndeterminateDrawable();
        if(ret == null || !(ret instanceof SmoothProgressDrawable))
            throw new RuntimeException("The drawable is not a SmoothProgressDrawable");
        return (SmoothProgressDrawable) ret;
    }

    @Override
    public void setInterpolator(Interpolator interpolator) {
        super.setInterpolator(interpolator);
        Drawable ret = getIndeterminateDrawable();
        if(ret != null && (ret instanceof SmoothProgressDrawable))
            ((SmoothProgressDrawable) ret).setInterpolator(interpolator);
    }

    public void setSmoothProgressDrawableInterpolator(Interpolator interpolator) {
        checkIndeterminateDrawable().setInterpolator(interpolator);
    }

    public void setSmoothProgressDrawableColors(int[] colors) {
        checkIndeterminateDrawable().setColors(colors);
    }

    public void setSmoothProgressDrawableColor(int color) {
        checkIndeterminateDrawable().setColor(color);
    }

    public void setSmoothProgressDrawableSpeed(float speed){
        checkIndeterminateDrawable().setSpeed(speed);
    }

    public void setSmoothProgressDrawableSectionsCount(int sectionsCount){
        checkIndeterminateDrawable().setSectionsCount(sectionsCount);
    }

    public void setSmoothProgressDrawableSeparatorLength(int separatorLength){
        checkIndeterminateDrawable().setSeparatorLength(separatorLength);
    }

    public void setSmoothProgressDrawableStrokeWidth(float strokeWidth){
        checkIndeterminateDrawable().setStrokeWidth(strokeWidth);
    }

    public void setSmoothProgressDrawableReversed(boolean reversed){
        checkIndeterminateDrawable().setReversed(reversed);
    }

    public void setSmoothProgressDrawableMirrorMode(boolean mirrorMode){
        checkIndeterminateDrawable().setMirrorMode(mirrorMode);
    }
}
