package com.folio.dailyenglish;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

final class LearningStore {
    private final SharedPreferences prefs;
    static final class Card {
        final String word, meaning;
        final int level;
        final long nextAt;
        Card(String word,String meaning,int level,long nextAt){
            this.word=word;this.meaning=meaning;this.level=level;this.nextAt=nextAt;
        }
    }
    LearningStore(Context context) {
        prefs=context.getSharedPreferences("folio-learning",Context.MODE_PRIVATE);
    }
    private String wordKey(String word){return word.trim().toLowerCase(Locale.ROOT);}
    List<Card> cards(){
        List<Card> list=new ArrayList<>();
        try{
            JSONObject root=new JSONObject(prefs.getString("cards","{}"));
            Iterator<String> keys=root.keys();
            while(keys.hasNext()){
                String k=keys.next();JSONObject item=root.optJSONObject(k);
                if(item!=null)list.add(new Card(k,item.optString("meaning"),item.optInt("level"),item.optLong("nextAt")));
            }
        }catch(Exception ignored){}
        list.sort((a,b)->Long.compare(a.nextAt,b.nextAt));
        return list;
    }
    boolean hasWord(String word){
        try{return new JSONObject(prefs.getString("cards","{}")).has(wordKey(word));}
        catch(Exception e){return false;}
    }
    void saveWord(String word,String meaning) {
        String k=wordKey(word);
        if(!k.matches("[a-z][a-z'-]{1,35}"))return;
        try{
            JSONObject root=new JSONObject(prefs.getString("cards","{}"));
            if(root.has(k))return;
            JSONObject item=new JSONObject();
            item.put("meaning",meaning);item.put("level",0);item.put("nextAt",0);
            root.put(k,item);prefs.edit().putString("cards",root.toString()).apply();
        }catch(Exception ignored){}
    }
    void removeWord(String word){
        try{
            JSONObject root=new JSONObject(prefs.getString("cards","{}"));
            root.remove(wordKey(word));prefs.edit().putString("cards",root.toString()).apply();
        }catch(Exception ignored){}
    }
    void grade(String word,boolean known) {
        try{
            JSONObject root=new JSONObject(prefs.getString("cards","{}"));
            String key=wordKey(word);
            JSONObject obj=root.optJSONObject(key);
            if(obj==null)return;
            int level=SpacedRepetition.nextLevel(obj.optInt("level"),known);
            long due=SpacedRepetition.nextDue(System.currentTimeMillis(),level);
            obj.put("level",level);obj.put("nextAt",due);
            prefs.edit().putString("cards",root.toString()).apply();
        }catch(Exception ignored){}
    }
    int dueCount(){
        long now=System.currentTimeMillis();int count=0;
        for(Card c:cards())if(c.nextAt<=now)count++;
        return count;
    }
    boolean isBookmarked(String url){return prefs.getBoolean("fav:"+url,false);}
    void bookmark(String url,boolean value){prefs.edit().putBoolean("fav:"+url,value).apply();}
    boolean isRead(String url){return prefs.getBoolean("read:"+url,false);}
    void markRead(String url){
        if(!isRead(url)){
            prefs.edit().putBoolean("read:"+url,true)
                .putInt("reading_total",totalRead()+1).apply();
            String day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new java.util.Date());
            prefs.edit().putString("last_read_day",day).apply();
        }
    }
    int totalRead(){return prefs.getInt("reading_total",0);}
    int savedCount(){return cards().size();}
    String lastReadingDay(){return prefs.getString("last_read_day","");}
    String note(String url){return prefs.getString("note:"+url,"");}
    void note(String url,String text){prefs.edit().putString("note:"+url,text).apply();}
    int fontSize(){return prefs.getInt("font",19);}
    void setFontSize(int value){prefs.edit().putInt("font",Math.max(16,Math.min(26,value))).apply();}
    int completedCount(List<FeedRepository.Article> items) {
        int count=0;for(FeedRepository.Article item:items)if(isRead(item.url))count++;return count;
    }
    static String shortDate(long timestamp){
        return new java.text.SimpleDateFormat("MM/dd",Locale.getDefault()).format(new java.util.Date(timestamp));
    }
}
