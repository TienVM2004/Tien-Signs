// File: SwipeGestureListener.java
package com.example.tiensigns;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {

    private static final int SWIPE_THRESHOLD = 100; // Minimum distance for a swipe
    private static final int SWIPE_VELOCITY_THRESHOLD = 100; // Minimum velocity for a swipe

    private SwipeListener swipeListener;

    public SwipeGestureListener(SwipeListener swipeListener) {
        this.swipeListener = swipeListener;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        float diffX = e2.getX() - e1.getX();

        if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY())) {
            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    // Swipe Right
                    swipeListener.onSwipeRight();
                } else {
                    // Swipe Left
                    swipeListener.onSwipeLeft();
                }
                return true;
            }
        }
        return false;
    }

    public interface SwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }
}
