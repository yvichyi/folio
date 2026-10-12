package com.folio.dailyenglish;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Real Android SQLite regression: switching daily issues must not lose old articles. */
@RunWith(AndroidJUnit4.class)
public class ArticleArchiveInstrumentedTest {
    private FeedRepository.Article sample(String slug, String title) throws Exception {
        JSONObject article=new JSONObject();
        article.put("url","https://example.com/articles/"+slug);
        article.put("title",title);
        article.put("source","Folio Test");
        article.put("topic","科技");
        article.put("date","2026-10-12");
        article.put("summary","Independent short preview for archived content");
        article.put("readingText","A saved article must retain its original learning text after a new issue arrives.");
        article.put("editorNote","独立学习资料");
        return new FeedRepository.Article(article);
    }

    @Test public void oldIssueSurvivesReplacementAndProcessLikeReopen() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String dbName="folio-regression-issue-swap.db";
        context.deleteDatabase(dbName);
        try{
            ArticleArchive first=new ArticleArchive(context,dbName);
            first.ingest(Arrays.asList(sample("day-a","Older article")));
            first.close();

            ArticleArchive second=new ArticleArchive(context,dbName);
            second.ingest(Arrays.asList(sample("day-b","New article")));
            second.close();

            ArticleArchive reopened=new ArticleArchive(context,dbName);
            List<FeedRepository.Article> history=reopened.all();
            assertEquals(2,reopened.count());
            assertEquals(2,history.size());
            assertTrue(history.stream().anyMatch(x->x.title.equals("Older article") && x.readingText.contains("original learning text")));
            assertTrue(history.stream().anyMatch(x->x.title.equals("New article")));

            reopened.ingest(Arrays.asList(sample("day-b","New article")));
            assertEquals("Duplicate URLs must upsert, never multiply",2,reopened.count());
            reopened.close();
        } finally {
            context.deleteDatabase(dbName);
        }
    }

    @Test public void failedOrEmptyFetchDoesNotEraseArchive() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String dbName="folio-regression-offline.db";
        context.deleteDatabase(dbName);
        try{
            ArticleArchive archive=new ArticleArchive(context,dbName);
            archive.ingest(Arrays.asList(sample("saved","Offline reading")));
            archive.ingest(java.util.Collections.emptyList());
            assertEquals(1,archive.count());
            assertEquals("Offline reading",archive.all().get(0).title);
            archive.close();
        } finally {
            context.deleteDatabase(dbName);
        }
    }
}
