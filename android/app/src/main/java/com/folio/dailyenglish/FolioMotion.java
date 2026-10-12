package com.folio.dailyenglish;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Small, state-triggered motion grammar for native Folio.
 * Every transition respects Android's system-wide Remove Animations setting.
 */
final class FolioMotion {
    private FolioMotion() {}
    static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }
    private static float dp(View v,float n) {
        return n*v.getResources().getDisplayMetrics().density;
    }
    /** mode: 1 tab (directional); 2 article forward; 3 article back. */
    static void enter(View view,int mode,int direction) {
        if(view==null || mode==0 || !enabled())return;
        final boolean lateral=mode==1;
        view.animate().cancel();
        view.setAlpha(0f);
        if(lateral)view.setTranslationX(dp(view,Math.max(-1,Math.min(1,direction))*15f));
        else view.setTranslationY(dp(view,mode==3?-13f:19f));
        view.animate().alpha(1f).translationX(0f).translationY(0f)
            .setInterpolator(new DecelerateInterpolator(1.7f))
            .setStartDelay(0).setDuration(lateral?235:285).start();
    }
    /** Physical press hint; click ownership remains with the underlying View. */
    static void attachPress(View view) {
        view.setOnTouchListener((v,event)->{
            if(!enabled())return false;
            final int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                v.animate().cancel();
                v.animate().scaleX(.985f).scaleY(.985f)
                    .setDuration(95).setInterpolator(new DecelerateInterpolator()).start();
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(175).setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
            return false;
        });
    }
    /** Only follows a confirmed bookmark change; never runs on an idle screen. */
    static void bookmark(View view) {
        if(!enabled())return;
        view.animate().cancel();
        view.setScaleX(.74f);view.setScaleY(.74f);
        view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160)
            .setInterpolator(new OvershootInterpolator(1.3f))
            .withEndAction(()->view.animate().scaleX(1f).scaleY(1f)
                .setDuration(110).setInterpolator(new DecelerateInterpolator()).start()).start();
    }
}
