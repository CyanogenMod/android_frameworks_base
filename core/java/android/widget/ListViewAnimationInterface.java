package android.widget;

import android.view.View;
import android.view.ViewPropertyAnimator;

public interface ListViewAnimationInterface {

    void initView(View item, int position, int scrollDirection);

    void setupAnimation(View item, int position, int scrollDirection, ViewPropertyAnimator animator);
}