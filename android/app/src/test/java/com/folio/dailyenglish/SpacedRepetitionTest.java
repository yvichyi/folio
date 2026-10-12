package com.folio.dailyenglish;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpacedRepetitionTest {
 @Test public void correctAnswersIncreaseSpacing(){
   assertEquals(1,SpacedRepetition.nextLevel(0,true));
   assertEquals(2,SpacedRepetition.nextLevel(1,true));
   assertEquals(7,SpacedRepetition.nextLevel(7,true));
 }
 @Test public void failureResetsForImmediateRetry(){
   assertEquals(0,SpacedRepetition.nextLevel(4,false));
   assertEquals(42L,SpacedRepetition.nextDue(42L,0));
 }
 @Test public void daysUseWholeDayUnits(){
   assertEquals(86_400_000L,SpacedRepetition.nextDue(0L,1));
   assertEquals(3*86_400_000L,SpacedRepetition.nextDue(0L,2));
 }
}
