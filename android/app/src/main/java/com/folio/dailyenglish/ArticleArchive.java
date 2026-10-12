package com.folio.dailyenglish;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent, local-first snapshot of every successfully delivered article.
 * Daily feed changes must never delete an article that the reader previously saw.
 */
final class ArticleArchive extends SQLiteOpenHelper {
    private static final String DATABASE="folio-articles.db";
    private static final int VERSION=1;
    ArticleArchive(Context context){this(context,DATABASE);}
    // Different database name allows isolated instrumentation tests.
    ArticleArchive(Context context,String database){super(context,database,null,VERSION);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS articles ("+
            "url TEXT PRIMARY KEY NOT NULL,"+
            "payload TEXT NOT NULL,"+
            "seen_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS articles_seen_at ON articles(seen_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        // Never DROP learning data during upgrades.
        throw new IllegalStateException("Unsupported archive migration "+oldVersion+" to "+newVersion);
    }
    void ingest(List<FeedRepository.Article> items){
        if(items==null || items.isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            long now=System.currentTimeMillis();
            for(FeedRepository.Article item:items){
                if(item==null || item.url.isEmpty() || !item.url.startsWith("https://"))continue;
                ContentValues values=new ContentValues();
                values.put("url",item.url);
                values.put("payload",item.rawJson);
                values.put("seen_at",now);
                db.insertWithOnConflict("articles",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    List<FeedRepository.Article> all(){
        ArrayList<FeedRepository.Article> items=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().rawQuery(
             "SELECT payload FROM articles ORDER BY seen_at DESC, url ASC",null)){
            while(cursor.moveToNext()){
                try{items.add(new FeedRepository.Article(new JSONObject(cursor.getString(0))));}
                catch(Exception ignored){/* Preserve remaining readable records. */}
            }
        }
        return items;
    }
    int count(){
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM articles",null)){
            return cursor.moveToFirst()?cursor.getInt(0):0;
        }
    }
}
