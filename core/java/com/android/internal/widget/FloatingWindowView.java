package com.android.internal.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;

import com.android.internal.R;

@SuppressLint("ViewConstructor")
/**
 * @hide
 */
public class FloatingWindowView extends RelativeLayout {

    private static final int ID_OVERLAY_VIEW = 1000000;

    private final int SNAP_LEFT = 1;
    private final int SNAP_TOP = 2;
    private final int SNAP_RIGHT = 3;
    private final int SNAP_BOTTOM = 4;

    private Resources mResource;
    private RelativeLayout mTitleBarHeader;
    private ImageButton mTitleBarMin;
    private ImageButton mTitleBarMax;
    private ImageButton mTitleBarClose;
    private ImageButton mTitleBarMore;
    private View mContentViews;
    private View mDividerViews;

    public FloatingWindowView(final Activity activity, int height) {
        super(activity);
        mResource = activity.getResources();

	XmlResourceParser parser = mResource.getLayout(R.layout.floating_window_layout);
        activity.getLayoutInflater().inflate(parser, this);

        setId(ID_OVERLAY_VIEW);
        setIsRootNamespace(false);

        mContentViews = findViewById(R.id.floating_window_background);
        mContentViews.bringToFront();

	setIsRootNamespace(true);

	final FrameLayout decorView =
                    (FrameLayout) activity.getWindow().peekDecorView().getRootView();

        View child = decorView.getChildAt(0);
        FrameLayout.LayoutParams params =
                                 (FrameLayout.LayoutParams) child.getLayoutParams();
        params.setMargins(0, height, 0, 0);
        child.setLayoutParams(params);

        mTitleBarHeader = (RelativeLayout) findViewByIdHelper(this, R.id.floating_window_titlebar,
                                               "floating_window_titlebar");
        mTitleBarMore = (ImageButton) findViewByIdHelper(mTitleBarHeader, R.id.floating_window_more,
                                               "floating_window_more");
        mTitleBarClose = (ImageButton) findViewByIdHelper(mTitleBarHeader, R.id.floating_window_close,
                                               "floating_window_close");
        mTitleBarMax = (ImageButton) findViewByIdHelper(mTitleBarHeader, R.id.floating_window_max,
                                               "floating_window_max");
        mTitleBarMin = (ImageButton) findViewByIdHelper(mTitleBarHeader, R.id.floating_window_min,
                                               "floating_window_min");
        mDividerViews = findViewByIdHelper(mTitleBarHeader, R.id.floating_window_line,
                                               "floating_window_line");

        if (mTitleBarHeader == null
            || mTitleBarClose == null
            || mTitleBarMore == null
            || mTitleBarMax == null
            || mTitleBarMin == null
            || mDividerViews == null) {
            return;
        }

        mTitleBarClose.setImageDrawable(mResource.getDrawable(R.drawable.ic_floating_window_close));
        mTitleBarClose.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                activity.finish();
            }
        });

        mTitleBarMax.setImageDrawable(mResource.getDrawable(R.drawable.ic_floating_window_max));
        mTitleBarMax.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                activity.setFullscreenApp();
            }
        });

        mTitleBarMin.setImageDrawable(mResource.getDrawable(R.drawable.ic_floating_window_min));
        mTitleBarMin.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                activity.restorePreviousLayoutApp();
            }
        });

        mTitleBarMore.setImageDrawable(mResource.getDrawable(R.drawable.ic_floating_window_more));

        final String menu_item1 = mResource.getString(R.string.floating_window_snap_top);
        final String menu_item2 = mResource.getString(R.string.floating_window_snap_bottom);
        final String menu_item3 = mResource.getString(R.string.floating_window_snap_left);
        final String menu_item4 = mResource.getString(R.string.floating_window_snap_right);
        final String menu_item5 = mResource.getString(R.string.floating_window_minimize);

        final PopupMenu popupMenu = new PopupMenu(mTitleBarMore.getContext(), mTitleBarMore);
        Menu menu = popupMenu.getMenu();
        menu.add(menu_item1);
        menu.add(menu_item2);
        menu.add(menu_item3);
        menu.add(menu_item4);
        menu.add(menu_item5);

        mTitleBarMore.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                      @Override
                      public boolean onMenuItemClick(MenuItem item) {
                          if (item.getTitle().equals(menu_item1)) {
                              activity.forceSnap(SNAP_TOP);
                          } else if (item.getTitle().equals(menu_item2)) {
                              activity.forceSnap(SNAP_BOTTOM);
                          } else if (item.getTitle().equals(menu_item3)) {
                              activity.forceSnap(SNAP_LEFT);
                          } else if (item.getTitle().equals(menu_item4)) {
                              activity.forceSnap(SNAP_RIGHT);
                          } else if (item.getTitle().equals(menu_item5)) {
                              activity.sendAppLaunchBroadcast();
                          }
                          return false;
                      }
                });
                popupMenu.show();
            }
        });

        RelativeLayout.LayoutParams header_param =
                              (LayoutParams) mTitleBarHeader.getLayoutParams();
        header_param.height = height;
        mTitleBarHeader.setLayoutParams(header_param);
        mTitleBarHeader.setOnTouchListener(new View.OnTouchListener() {
                 @Override
                 public boolean onTouch(View view, MotionEvent event) {
                     switch (event.getAction()) {
                          case MotionEvent.ACTION_DOWN:
                              activity.setTouchViewDown(event.getX(), event.getY());
                              activity.onUserInteraction();
                              activity.updateFocusApp();
                              if (!activity.getChangedPreviousRange()) {
                                  activity.setPreviousTouchRange(event.getRawX(), event.getRawY());
                                  activity.setChangedPreviousRange(true);
                              }
                              break;
                          case MotionEvent.ACTION_MOVE:
                              activity.changeFlagsLayoutParams();
                              activity.setTouchViewMove(event.getRawX(), event.getRawY());
                              if (activity.getRestorePosition()
                                     && activity.moveRangeAboveLimit(event)) {
                                  activity.restoreOldPosition();
                              }
                              activity.showSnap((int) event.getRawX(), (int) event.getRawY());
                              break;
                          case MotionEvent.ACTION_UP:
                              activity.setChangedFlags(false);
                              activity.finishSnap(activity.isValidSnap()
                                           && activity.getTimeoutDone());
                              activity.discardTimeout();
                              activity.setChangedPreviousRange(false);
                              break;
                      }
                      return view.onTouchEvent(event);
                 }
        });

        ViewGroup.LayoutParams divider_param = mDividerViews.getLayoutParams();
        divider_param.height = 2;
        mDividerViews.setLayoutParams(divider_param);
    }

    private View findViewByIdHelper(View view, int id, String tag) {
        View v = view.findViewById(id);
        if (v == null) {
            v = findViewWithTag(view, tag);
        }
        return v;
    }

    private View findViewWithTag(View view, String text) {
        if (view.getTag() instanceof String) {
            if (((String) view.getTag()).equals(text)) {
                return view;
            }
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                 final View child = group.getChildAt(i);
                 final View found = findViewWithTag(child, text);
                 if (found != null) {
                     return found;
                 }
            }
        }
        return null;
    }

    public void setFloatingBackgroundColor(int color) {
        if (mTitleBarHeader == null
            || mContentViews == null) {
            return;
        }
        mContentViews.setBackgroundDrawable(makeOutline(color, 1));
        mTitleBarHeader.setBackgroundColor(color);
    }

    public void setFloatingColorFilter(int color) {
        if (mTitleBarClose == null
            || mTitleBarMax == null
            || mTitleBarMin == null
            || mTitleBarMore == null
            || mDividerViews == null) {
            return;
        }
        mTitleBarMore.setColorFilter(color, Mode.SRC_ATOP);
        mTitleBarMax.setColorFilter(color, Mode.SRC_ATOP);
        mTitleBarMin.setColorFilter(color, Mode.SRC_ATOP);
        mTitleBarClose.setColorFilter(color, Mode.SRC_ATOP);
        mDividerViews.setBackgroundColor(color);
    }

    private ShapeDrawable makeOutline(int color, int thickness) {
        ShapeDrawable rectShapeDrawable = new ShapeDrawable(new RectShape());
        Paint paint = rectShapeDrawable.getPaint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        return rectShapeDrawable;
    }

    public static final RelativeLayout.LayoutParams getParams() {
        final RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 0, 0, 0);
        return params;
    }
}
