package com.folio.dailyenglish;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class FeedRepository {
    static final String FEED_URL = "https://raw.githubusercontent.com/yvichyi/folio/main/docs/feed/daily.json";
    static final class Word {
        final String word, meaning;
        Word(String w,String m) { word=w;meaning=m; }
    }
    static final class Article {
        final String title, source, topic, date, summary, readingText, editorNote, question, url;
        final List<Word> words;
        Article(JSONObject j) {
            title=j.optString("title"); source=j.optString("source"); topic=j.optString("topic");
            date=j.optString("date"); summary=j.optString("summary");
            readingText=j.optString("readingText",summary); editorNote=j.optString("editorNote");
            question=j.optString("discussionQuestion"); url=j.optString("url");
            words=new ArrayList<>();
            JSONArray a=j.optJSONArray("vocabulary");
            if(a!=null)for(int i=0;i<a.length();i++){
                JSONObject w=a.optJSONObject(i);
                if(w!=null)words.add(new Word(w.optString("word"),w.optString("meaning")));
            }
        }
        String key() { return url; }
    }
    interface Callback { void onResult(boolean success, String message); }
    final List<Article> articles = new ArrayList<>();
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    boolean hasCache;
    long lastSync;
    private boolean updating;
    FeedRepository(Context context) {
        prefs=context.getSharedPreferences("folio-feed",Context.MODE_PRIVATE);
        String cache=prefs.getString("feed","");
        lastSync=prefs.getLong("last_sync",0);
        if(!cache.isEmpty()) {
            try { parse(cache);hasCache=true; }catch(Exception ignore){}
        }
    }
    boolean isUpdating(){return updating;}
    void refresh(Callback cb) {
        if(updating){ cb.onResult(false,"正在同步中");return; }
        updating=true;
        worker.execute(()->{
            boolean ok=false; String msg;
            HttpURLConnection connection=null;
            try{
                URL url=new URL(FEED_URL);
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8500);connection.setReadTimeout(8500);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("User-Agent","FolioNative/0.4");
                if(connection.getResponseCode()!=200)throw new IllegalStateException("HTTP "+connection.getResponseCode());
                StringBuilder builder=new StringBuilder();
                try(InputStream stream=connection.getInputStream();
                    BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                    String line;
                    while((line=reader.readLine())!=null) {
                        builder.append(line).append('\n');
                        if(builder.length()>350000)throw new IllegalStateException("数据太大");
                    }
                }
                final String json=builder.toString();
                // Parse before replacing the last known-good cache.
                final List<Article> checked = decode(json);
                main.post(()->{
                    articles.clear();articles.addAll(checked);hasCache=true;lastSync=System.currentTimeMillis();
                    prefs.edit().putString("feed",json).putLong("last_sync",lastSync).apply();
                    updating=false;cb.onResult(true,"已同步 "+articles.size()+" 篇精选");
                });
                return;
            } catch(Exception e){msg="网络同步失败，可继续阅读已缓存内容";}
            finally{if(connection!=null)connection.disconnect();}
            final String message=msg;
            main.post(()->{updating=false;cb.onResult(false,message);});
        });
    }
    private void parse(String json) throws Exception {articles.clear();articles.addAll(decode(json));}
    static List<Article> decode(String json)throws Exception{
        JSONObject root=new JSONObject(json);
        if(root.optInt("schemaVersion")!=1)throw new IllegalArgumentException("unsupported schema");
        JSONArray a=root.optJSONArray("items");
        if(a==null || a.length()<1||a.length()>40)throw new IllegalArgumentException("invalid feed");
        List<Article> found=new ArrayList<>();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.getJSONObject(i);
            Article x=new Article(o);
            if(x.title.isEmpty()||x.url.isEmpty()||!x.url.startsWith("https://")||x.summary.isEmpty())continue;
            if(x.title.length()>500||x.readingText.length()>15000)continue;
            found.add(x);
        }
        if(found.isEmpty())throw new IllegalArgumentException("empty feed");
        return found;
    }
    void close(){worker.shutdownNow();}
}
