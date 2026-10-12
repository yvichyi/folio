package com.folio.dailyenglish;

final class SpacedRepetition {
    private static final int[] DAYS={0,1,3,7,14,30,60,90};
    static int nextLevel(int prior,boolean known) {
        if(!known)return 0;
        return Math.min(DAYS.length-1,Math.max(0,prior)+1);
    }
    static long nextDue(long now,int level) {
        return now + (long)DAYS[Math.min(DAYS.length-1,Math.max(0,level))]*24*60*60*1000L;
    }
    private SpacedRepetition(){}
}
