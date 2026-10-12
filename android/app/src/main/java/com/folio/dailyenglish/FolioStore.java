package com.folio.dailyenglish;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FolioStore {
    static final String FEED_URL = "https://raw.githubusercontent.com/yvichyi/folio/main/docs/feed/daily.json";
    static final class Article {
        String id, title, source, topic, date, summary, reading, note, question, url;
        JSONArray vocabulary;
        Article(JSONObject j) {
            title = j.optString("title", "Untitled");
            url = j.optString("url", "");
            id = url.isEmpty() ? title : url;
            source = j.optString("source", "Journal");
            topic = j.optString("topic", "思想");
            date = j.optString("date", "");
            summary = j.optString("summary", "");
            reading = j.optString("readingText", summary);
            note = j.optString("editorNote", "");
            question = j.optString("discussionQuestion", "");
            vocabulary = j.optJSONArray("vocabulary");
            if (vocabulary == null) vocabulary = new JSONArray();
        }
    }
    private final SharedPreferences prefs;
    FolioStore(Context context) { prefs=context.getSharedPreferences("folio.native.v1",Context.MODE_PRIVATE); }
    String cachedFeed(){return prefs.getString("feed","");}
    void saveFeed(String json){prefs.edit().putString("feed",json).apply();}
    String lastSync(){return prefs.getString("lastSync","");}
    void setLastSync(String time){prefs.edit().putString("lastSync",time).apply();}
    List<Article> articles(){
        List<Article> out=new ArrayList<>();
        try {
            JSONObject root=new JSONObject(cachedFeed());
            JSONArray a=root.optJSONArray("items");
            if(a!=null)for(int i=0;i<a.length();i++) {
                JSONObject o=a.optJSONObject(i);
                if(o!=null&&o.optString("url").startsWith("https://"))out.add(new Article(o));
            }
        }catch(Exception ignored){}
        return out;
    }
    boolean validateFeed(String json) {
        try {
            JSONObject j=new JSONObject(json);
            if(j.optInt("schemaVersion")!=1)return false;
            JSONArray a=j.optJSONArray("items");
            if(a==null||a.length()==0||a.length()>30)return false;
            for(int i=0;i<a.length();i++){
                JSONObject x=a.getJSONObject(i);
                if(!x.optString("url").startsWith("https://")
                  ||x.optString("title").trim().isEmpty()
                  ||x.optString("summary").trim().isEmpty())return false;
            }
            return true;
        }catch(Exception ex){return false;}
    }
    private JSONObject readMap(String key) {
        try{return new JSONObject(prefs.getString(key,"{}"));}
        catch(JSONException ignored){return new JSONObject();}
    }
    private void writeMap(String key, JSONObject j){prefs.edit().putString(key,j.toString()).apply();}
    boolean favorite(String id){return readMap("favorites").optBoolean(id,false);}
    void toggleFavorite(String id){
        JSONObject j=readMap("favorites");
        try{j.put(id,!j.optBoolean(id,false));}catch(JSONException ignored){}
        writeMap("favorites",j);
    }
    void markRead(String id){
        JSONObject j=readMap("read");
        try{j.put(id,System.currentTimeMillis());}catch(JSONException ignored){}
        writeMap("read",j);
    }
    boolean isRead(String id){return readMap("read").has(id);}
    int readCount(){return readMap("read").length();}
    String note(String id){return readMap("notes").optString(id,"");}
    void setNote(String id,String value){
        JSONObject j=readMap("notes");
        try{j.put(id,value);}catch(JSONException ignored){}
        writeMap("notes",j);
    }
    int notesCount(){return readMap("notes").length();}
    JSONObject words(){return readMap("words");}
    JSONObject word(String w){return words().optJSONObject(normalize(w));}
    static String normalize(String word){return word.toLowerCase(java.util.Locale.ROOT).trim();}
    int wordsCount(){return words().length();}
    void saveWord(String word,String meaning){
        String k=normalize(word);
        if(k.length()<2||k.length()>40)return;
        JSONObject all=words();
        JSONObject entry=all.optJSONObject(k);
        if(entry==null)entry=new JSONObject();
        try{
            entry.put("meaning",meaning==null?"":meaning);
            if(!entry.has("added"))entry.put("added",System.currentTimeMillis());
            if(!entry.has("due"))entry.put("due",System.currentTimeMillis());
            if(!entry.has("stage"))entry.put("stage",0);
            all.put(k,entry);
        }catch(JSONException ignored){}
        writeMap("words",all);
    }
    void removeWord(String word){JSONObject a=words();a.remove(normalize(word));writeMap("words",a);}
    int dueCount(){
        JSONObject a=words();int count=0;long now=System.currentTimeMillis();
        for(java.util.Iterator<String> it=a.keys();it.hasNext();){
            String w=it.next();JSONObject o=a.optJSONObject(w);
            if(o!=null&&o.optLong("due",0)<=now)count++;
        }
        return count;
    }
    List<String> dueWords(boolean all) {
        JSONObject a=words();ArrayList<String> list=new ArrayList<>();
        for(java.util.Iterator<String> it=a.keys();it.hasNext();){
            String w=it.next();JSONObject o=a.optJSONObject(w);
            if(o!=null&&(all||o.optLong("due",0)<=System.currentTimeMillis()))list.add(w);
        }
        Collections.sort(list);
        return list;
    }
    void review(String word,boolean remembered){
        JSONObject a=words();JSONObject o=a.optJSONObject(normalize(word));
        if(o==null)return;
        int stage=remembered?Math.min(5,o.optInt("stage",0)+1):0;
        long[] days={0,1,3,7,14,30};
        long wait=remembered?days[stage]*86400000L:10*60000L;
        try{o.put("stage",stage);o.put("due",System.currentTimeMillis()+wait);
            o.put("lastReview",System.currentTimeMillis());a.put(normalize(word),o);
        }catch(JSONException ignored){}
        writeMap("words",a);
        prefs.edit().putInt("reviewCount",prefs.getInt("reviewCount",0)+1).apply();
    }
    int reviewCount(){return prefs.getInt("reviewCount",0);}
    boolean starredOnly(){return prefs.getBoolean("starredOnly",false);}
    void setStarredOnly(boolean val){prefs.edit().putBoolean("starredOnly",val).apply();}
    int fontSize(){return prefs.getInt("fontSize",19);}
    void setFontSize(int size){prefs.edit().putInt("fontSize",Math.max(16,Math.min(26,size))).apply();}
    long latestRead(){
        JSONObject a=readMap("read");long latest=0;
        for(java.util.Iterator<String> it=a.keys();it.hasNext();){latest=Math.max(latest,a.optLong(it.next()));}
        return latest;
    }
}