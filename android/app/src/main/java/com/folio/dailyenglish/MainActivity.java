package com.folio.dailyenglish;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int BG=0xFFF6F4ED, INK=0xFF1F3228, GREEN=0xFF274B39,
        SAGE=0xFF8CA58F, MUTED=0xFF728077, LINE=0xFFDADDD3, PANEL=0xFFFFFFFF,
        SOFT=0xFFE7ECE4, GOLD=0xFFE7BD7F, DARKPANEL=0xFF203E30;
    private static final Typeface SERIF=Typeface.create("serif",Typeface.NORMAL);
    private static final Typeface SERIF_BOLD=Typeface.create("serif",Typeface.BOLD);
    private static final Typeface SANS=Typeface.create("sans-serif",Typeface.NORMAL);
    private static final Typeface SANS_MED=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private static final Pattern WORD_PATTERN=Pattern.compile("[A-Za-z][A-Za-z'-]*");
    private FeedRepository feed;
    private ArticleArchive archive;
    private LearningStore learning;
    private TextToSpeech speech;
    private ExecutorService lookup=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout outer,body,navbar;
    private int activeTab=0; // 0 edition / 1 saved / 2 words / 3 me
    private String topic="全部",search="";
    private FeedRepository.Article reading;
    private boolean busy=false;
    private ProgressBar readingProgress;
    private int nextMotion=0;
    private int tabDirection=1;
    private ScrollView readerScroll;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        learning=new LearningStore(this);
        feed=new FeedRepository(this);
        archive=new ArticleArchive(this);
        // Migrate the existing cached issue and any bundled seed into the permanent archive.
        archive.ingest(feed.articles);
        render();
        if(!feed.hasCache)refresh();
        else if(System.currentTimeMillis()-feed.lastSync>15*60*1000L)refresh();
    }
    private int d(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    private LinearLayout row(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private TextView text(String value,float size,int color,Typeface font) {
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(font);v.setIncludeFontPadding(false);
        v.setLineSpacing(d(3),1.04f);
        return v;
    }
    private TextView caption(String value) {
        TextView v=text(value,11,MUTED,SANS_MED);
        v.setLetterSpacing(.13f);return v;
    }
    private GradientDrawable shape(int color,int corner) {
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(corner));return g;
    }
    private GradientDrawable outlined(int color,int corner,int border) {
        GradientDrawable g=shape(color,corner);g.setStroke(d(1),border);return g;
    }
    private void surface(View v,int color,int radius,boolean outline){
        GradientDrawable gd=outline?outlined(color,radius,LINE):shape(color,radius);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18274B39),gd,null));
    }
    private LinearLayout.LayoutParams lp(int w,int h){
        return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));
    }
    private void pad(View v,int left,int top,int right,int bottom){
        v.setPadding(d(left),d(top),d(right),d(bottom));
    }
    private void gap(LinearLayout parent,int px) {
        View spacer=new View(this);parent.addView(spacer,lp(1,px));
    }
    private TextView action(String title,boolean filled,Runnable run) {
        TextView b=text(title,13,filled?Color.WHITE:GREEN,SANS_MED);
        b.setGravity(Gravity.CENTER);b.setMinHeight(d(44));pad(b,17,11,17,11);
        surface(b,filled?GREEN:SOFT,13,false);
        FolioMotion.attachPress(b);
        b.setOnClickListener(v->run.run());
        return b;
    }
    private void addDivider(LinearLayout col){
        View v=new View(this);v.setBackgroundColor(LINE);col.addView(v,lp(-1,1));
    }
    private void setStatus(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    private void render(){
        outer=column();outer.setBackgroundColor(BG);
        outer.setFocusableInTouchMode(true);
        body=column();
        outer.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        if(reading!=null)renderReader();else {
            if(activeTab==0)home();
            else if(activeTab==1)library();
            else if(activeTab==2)wordbook();
            else profile();
            navbar=bottomNavigation();outer.addView(navbar,lp(-1,76));
        }
        setContentView(outer);
        final int motion=nextMotion;
        nextMotion=0;
        if(motion!=0)FolioMotion.enter(body,motion,tabDirection);
    }
    private void switchTab(int tab){
        if(tab==activeTab&&reading==null)return;
        tabDirection=tab>=activeTab?1:-1;
        nextMotion=1;
        activeTab=tab;reading=null;render();
    }
    private LinearLayout bottomNavigation(){
        LinearLayout wrap=column();
        View line=new View(this);line.setBackgroundColor(LINE);wrap.addView(line,lp(-1,1));
        LinearLayout bar=row();bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(BG);
        String[] names={"今日","收藏","生词","我的"};
        for(int i=0;i<4;i++){
            final int tab=i;
            boolean selected=activeTab==i;
            LinearLayout item=column();item.setGravity(Gravity.CENTER);
            item.setContentDescription(names[i]+"选项卡");
            item.setClickable(true);item.setFocusable(true);
            IconView icon=new IconView(i,selected?GREEN:MUTED);
            item.addView(icon,lp(26,27));
            TextView label=text(names[i],11,selected?GREEN:MUTED,selected?SANS_MED:SANS);
            label.setGravity(Gravity.CENTER);item.addView(label,lp(-1,20));
            if(selected){View rule=new View(this);rule.setBackground(shape(GREEN,4));item.addView(rule,lp(15,3));}
            else item.addView(new View(this),lp(15,3));
            surface(item,BG,11,false);
            FolioMotion.attachPress(item);
            item.setOnClickListener(v->switchTab(tab));
            if(selected && nextMotion==1 && FolioMotion.enabled()) {
                icon.setScaleX(.86f);icon.setScaleY(.86f);
                icon.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            }
            bar.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
        wrap.addView(bar,lp(-1,75));return wrap;
    }
    private final class IconView extends View {
        private final int type,color;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        IconView(int type,int color){super(MainActivity.this);this.type=type;this.color=color;}
        @Override protected void onDraw(Canvas real){
            super.onDraw(real);Canvas c=real;int save=c.save();
            c.translate((getWidth()-d(24))/2f,(getHeight()-d(24))/2f);
            c.scale(d(1),d(1));p.setColor(color);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.85f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            switch(type){
                case 0:
                    path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);
                    path.moveTo(6,10);path.lineTo(6,21);path.lineTo(18,21);path.lineTo(18,10);
                    path.moveTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);
                    c.drawPath(path,p);break;
                case 1:
                    path.moveTo(6,3);path.lineTo(18,3);path.quadTo(20,3,20,5);
                    path.lineTo(20,22);path.lineTo(12,17);path.lineTo(4,22);
                    path.lineTo(4,5);path.quadTo(4,3,6,3);c.drawPath(path,p);break;
                case 2:
                    c.drawRoundRect(3,4,21,20,3,3,p);
                    c.drawLine(7,9,17,9,p);c.drawLine(7,13,15,13,p);
                    c.drawLine(7,17,13,17,p);break;
                default:
                    c.drawCircle(12,8,4,p);c.drawArc(4,14,20,25,190,160,false,p);break;
            }
            c.restoreToCount(save);
        }
    }
    private ScrollView scroll(LinearLayout container){
        ScrollView s=new ScrollView(this);
        s.setFillViewport(false);s.setClipToPadding(false);
        s.setVerticalScrollBarEnabled(false);
        container.addView(s,new LinearLayout.LayoutParams(-1,0,1));
        return s;
    }
    private LinearLayout screenContent(ScrollView s){
        LinearLayout col=column();pad(col,22,18,22,36);s.addView(col);return col;
    }
    private LinearLayout top(String eyebrow,String title,String sub){
        LinearLayout col=column();
        LinearLayout cap=row();
        TextView logo=text("folio.",32,INK,SERIF_BOLD);
        logo.setTypeface(Typeface.create("serif",Typeface.ITALIC));
        cap.addView(logo,new LinearLayout.LayoutParams(0,d(45),1));
        TextView stamp=caption(eyebrow);stamp.setGravity(Gravity.RIGHT);
        cap.addView(stamp,lp(-2,35));col.addView(cap,lp(-1,-2));gap(col,22);
        if(title!=null){
            col.addView(text(title,30,INK,SERIF),lp(-1,-2));gap(col,8);
        }
        if(sub!=null){TextView t=text(sub,13,MUTED,SANS);col.addView(t);}
        gap(col,20);addDivider(col);gap(col,18);
        return col;
    }
    private void refresh(){
        if(busy)return;busy=true;
        feed.refresh((success,message)->{
            busy=false;
            if(success) archive.ingest(feed.articles);
            setStatus(message);
            if(reading==null)render();
        });
    }
    private String dateText(){
        return new SimpleDateFormat("EEEE · MMM d",Locale.ENGLISH)
          .format(new Date()).toUpperCase(Locale.ENGLISH);
    }
    private void home(){
        ScrollView sc=scroll(body);LinearLayout col=screenContent(sc);
        LinearLayout headline=top(dateText(),null,null);col.addView(headline);
        LinearLayout intro=row();intro.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout introTexts=column();
        TextView h=text("今日阅读",31,INK,SERIF_BOLD);
        h.setLineSpacing(d(1),1.02f);introTexts.addView(h,lp(-1,-2));
        gap(introTexts,8);introTexts.addView(text("每天一份精选，英语和见识一起增长。",13,MUTED,SANS));
        intro.addView(introTexts,new LinearLayout.LayoutParams(0,-2,1));
        TextView sync=action(busy?"同步中":"↻",false,()->refresh());
        sync.setContentDescription("从云端刷新外刊");
        intro.addView(sync,lp(50,46));col.addView(intro);gap(col,15);
        if(feed.articles.isEmpty()){
            empty(col,"等待今日编辑稿","联网后可同步每日外刊；若网络中断，已缓存的文章仍可阅读。","重试同步",()->refresh());
            return;
        }
        LinearLayout issueStrip=row();pad(issueStrip,15,13,15,13);
        surface(issueStrip,SOFT,14,false);
        TextView issue=text("DAILY EDITION  ·  "+feed.articles.size()+" 篇精选",11,GREEN,SANS_MED);
        issueStrip.addView(issue,new LinearLayout.LayoutParams(0,-2,1));
        TextView due=text(learning.dueCount()+" 待复习  ↗",11,GREEN,SANS_MED);
        issueStrip.addView(due);
        issueStrip.setClickable(true);issueStrip.setOnClickListener(v->switchTab(2));
        col.addView(issueStrip,lp(-1,-2));gap(col,20);
        LinearLayout sec=row();
        TextView section=text("今日外刊",22,INK,SERIF_BOLD);
        sec.addView(section,new LinearLayout.LayoutParams(0,-2,1));
        TextView searchButton=action("⌕  搜索",false,()->searchDialog());
        sec.addView(searchButton,lp(-2,42));col.addView(sec);gap(col,14);
        categoryFilters(col);gap(col,12);
        int shown=0;
        for(int i=0;i<feed.articles.size();i++){
            FeedRepository.Article a=feed.articles.get(i);
            if(!topic.equals("全部")&&!topic.equals(a.topic))continue;
            if(!search.isEmpty()&&!a.title.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))
                    &&!a.summary.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
            articleCard(col,a,i==0);shown++;
            gap(col,12);
        }
        if(shown==0)empty(col,"没有找到相关内容","尝试其他分类或搜索词。","清除筛选",()->{search="";topic="全部";render();});
        gap(col,13);
        TextView foot=text(feed.lastSync==0?"尚未同步":"最近同步："+new SimpleDateFormat("MM/dd HH:mm",Locale.getDefault()).format(new Date(feed.lastSync))+"  ·  可离线阅读缓存文章",11,MUTED,SANS);
        foot.setGravity(Gravity.CENTER);col.addView(foot);
    }
    private void searchDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setText(search);input.setHint("标题或文章内容");pad(input,20,10,20,10);
        new AlertDialog.Builder(this).setTitle("搜索外刊").setView(input)
            .setNegativeButton("取消",null)
            .setNeutralButton("清除",(d,w)->{search="";render();})
            .setPositiveButton("搜索",(d,w)->{search=input.getText().toString().trim();render();}).show();
    }
    private void categoryFilters(LinearLayout col){
        HorizontalScrollView sc=new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=row();String[] categories={"全部","科技","科学","文化","思想"};
        for(String value:categories){
            boolean selected=value.equals(topic);
            TextView chip=text(value,12,selected?Color.WHITE:GREEN,SANS_MED);
            pad(chip,17,9,17,9);chip.setGravity(Gravity.CENTER);
            surface(chip,selected?GREEN:SOFT,30,false);
            LinearLayout.LayoutParams params=lp(-2,39);params.rightMargin=d(8);
            chips.addView(chip,params);
            chip.setOnClickListener(v->{topic=value;render();});
        }
        sc.addView(chips);col.addView(sc,lp(-1,43));
    }
    private void articleCard(LinearLayout col,FeedRepository.Article a,boolean first){
        LinearLayout card=column();pad(card,19,19,19,17);
        surface(card,PANEL,17,true);
        card.setClickable(true);card.setFocusable(true);
        card.setContentDescription("阅读 "+a.title);
        FolioMotion.attachPress(card);
        card.setOnClickListener(v->openArticle(a));
        LinearLayout meta=row();
        TextView cat=caption(a.topic+" / "+a.source.toUpperCase(Locale.ENGLISH));
        meta.addView(cat,new LinearLayout.LayoutParams(0,-2,1));
        if(learning.isRead(a.url))meta.addView(text("✓ 已读",11,GREEN,SANS_MED));
        else if(learning.isBookmarked(a.url))meta.addView(text("◆ 已收藏",11,GREEN,SANS_MED));
        card.addView(meta);gap(card,14);
        TextView title=text(a.title,first?24:21,INK,SERIF_BOLD);
        title.setMaxLines(4);card.addView(title);
        gap(card,11);
        TextView desc=text(a.summary,13,MUTED,SANS);
        desc.setMaxLines(first?4:3);desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(desc);gap(card,19);
        addDivider(card);gap(card,13);
        LinearLayout bottom=row();
        TextView minutes=caption(Math.max(2,a.readingText.split("\\s+").length/180)+" MIN  ·  "+a.words.size()+" 个重点词");
        bottom.addView(minutes,new LinearLayout.LayoutParams(0,-2,1));
        bottom.addView(text("精读  →",12,GREEN,SANS_MED));card.addView(bottom);
        col.addView(card,lp(-1,-2));
    }
    private void empty(LinearLayout col,String heading,String detail,String cta,Runnable run){
        LinearLayout card=column();pad(card,23,35,23,31);card.setGravity(Gravity.CENTER);
        surface(card,PANEL,19,true);
        TextView mark=text("◎",42,SAGE,SERIF);mark.setGravity(Gravity.CENTER);card.addView(mark);
        gap(card,14);TextView title=text(heading,22,INK,SERIF_BOLD);title.setGravity(Gravity.CENTER);card.addView(title);
        gap(card,9);TextView msg=text(detail,13,MUTED,SANS);msg.setGravity(Gravity.CENTER);card.addView(msg);
        gap(card,23);card.addView(action(cta,true,run),lp(-2,44));col.addView(card,lp(-1,-2));
    }
    private void library(){
        ScrollView sc=scroll(body);LinearLayout col=screenContent(sc);
        col.addView(top("YOUR LIBRARY","收藏与历史","日报会更新，你读过和收藏的文章不会消失。"));
        List<FeedRepository.Article> history=archive.all();
        TextView index=caption("已归档 "+history.size()+" 篇文章  ·  保存在本机");
        col.addView(index);gap(col,23);
        sectionLabel(col,"已收藏的文章");
        int count=0;
        for(FeedRepository.Article a:history){
            if(learning.isBookmarked(a.url)){articleCard(col,a,false);gap(col,12);count++;}
        }
        if(count==0)empty(col,"还没有收藏","在阅读页面点击右上角的收藏标记，文章会留在这里。","去发现文章",()->switchTab(0));
        gap(col,23);sectionLabel(col,"阅读完成");
        int completed=0;
        for(FeedRepository.Article a:history)if(learning.isRead(a.url)){
            TextView t=action("✓  "+a.title,false,()->openArticle(a));
            t.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);t.setMaxLines(2);
            col.addView(t,lp(-1,-2));gap(col,9);completed++;
        }
        if(completed==0)col.addView(text("读完文章后点击「完成本篇」，就能留下阅读记录。",13,MUTED,SANS));
        gap(col,27);sectionLabel(col,"往期外刊");
        int previous=0;
        for(FeedRepository.Article a:history){
            boolean current=false;
            for(FeedRepository.Article issue:feed.articles)if(issue.url.equals(a.url)){current=true;break;}
            if(current || learning.isRead(a.url)||learning.isBookmarked(a.url))continue;
            articleCard(col,a,false);gap(col,12);previous++;
        }
        if(previous==0)col.addView(text("这里将保存今后每一期已经同步的文章。",13,MUTED,SANS));
    }
    private void sectionLabel(LinearLayout col,String value){
        col.addView(text(value,22,INK,SERIF_BOLD));gap(col,16);
    }
    private void wordbook(){
        ScrollView sc=scroll(body);LinearLayout col=screenContent(sc);
        col.addView(top("PERSONAL LEXICON","你的生词本","不是简单收藏，而是把单词真正记住。"));
        LinearLayout summary=row();summary.setGravity(Gravity.CENTER_VERTICAL);
        TextView t=text("待复习  "+learning.dueCount(),16,GREEN,SANS_MED);
        summary.addView(t,new LinearLayout.LayoutParams(0,d(46),1));
        summary.addView(action("开始复习 →",true,()->reviewNext()),lp(-2,45));
        col.addView(summary);gap(col,15);
        List<LearningStore.Card> cards=learning.cards();
        if(cards.isEmpty()){
            empty(col,"从一篇文章开始","阅读时点击单词，查询释义并保存；它就会出现在这里。","去阅读",()->switchTab(0));
            return;
        }
        for(LearningStore.Card card:cards){
            LinearLayout item=column();pad(item,17,15,17,15);
            surface(item,PANEL,14,true);
            LinearLayout header=row();
            TextView word=text(card.word,20,INK,SERIF_BOLD);
            header.addView(word,new LinearLayout.LayoutParams(0,-2,1));
            TextView right=caption(card.nextAt<=System.currentTimeMillis()?"今日待复习":"下次 "+LearningStore.shortDate(card.nextAt));
            header.addView(right);item.addView(header);gap(item,7);
            item.addView(text(card.meaning.isEmpty()?"已加入单词本":card.meaning,13,MUTED,SANS));
            item.setOnClickListener(v->wordActions(card));
            col.addView(item,lp(-1,-2));gap(col,9);
        }
    }
    private void wordActions(LearningStore.Card card){
        new AlertDialog.Builder(this).setTitle(card.word).setMessage(card.meaning)
            .setNegativeButton("取消",null)
            .setNeutralButton("移除",(d,w)->{learning.removeWord(card.word);render();})
            .setPositiveButton("复习",(d,w)->reviewCard(card)).show();
    }
    private void reviewNext(){
        long now=System.currentTimeMillis();
        for(LearningStore.Card card:learning.cards()){
            if(card.nextAt<=now){reviewCard(card);return;}
        }
        setStatus("今天的词汇已复习完毕");
    }
    private void reviewCard(LearningStore.Card card){
        new AlertDialog.Builder(this).setTitle(card.word).setMessage("请先回想中文含义")
            .setNegativeButton("稍后",null)
            .setPositiveButton("查看答案",(d,w)->new AlertDialog.Builder(this)
                .setTitle(card.word+" · "+card.meaning)
                .setMessage("刚才你记住了吗？")
                .setNegativeButton("没记住",(dialog,which)->{
                    learning.grade(card.word,false);render();main.postDelayed(()->reviewNext(),170);
                })
                .setPositiveButton("记住了",(dialog,which)->{
                    learning.grade(card.word,true);render();main.postDelayed(()->reviewNext(),170);
                }).show()).show();
    }
    private void profile(){
        ScrollView sc=scroll(body);LinearLayout col=screenContent(sc);
        col.addView(top("YOUR LEARNING STUDIO","保持好奇","让习惯自然发生，不必追逐毫无意义的打卡数字。"));
        LinearLayout stats=row();
        statCard(stats,String.valueOf(learning.totalRead()),"累计读完");
        View spacer=new View(this);stats.addView(spacer,lp(12,1));
        statCard(stats,String.valueOf(learning.savedCount()),"生词收藏");
        col.addView(stats);gap(col,23);
        sectionLabel(col,"阅读设置");
        settingsAction(col,"A−  缩小字号","当前正文 "+learning.fontSize()+"sp",()->{
            learning.setFontSize(learning.fontSize()-1);render();setStatus("字号已调整");
        });
        settingsAction(col,"A+  放大字号","让长时间阅读更舒适",()->{
            learning.setFontSize(learning.fontSize()+1);render();setStatus("字号已调整");
        });
        settingsAction(col,"↻  同步今日内容","文章来自 GitHub，刷新不用重新安装 APK",()->refresh());
        gap(col,22);sectionLabel(col,"数据和隐私");
        settingsAction(col,"↗  导出学习词汇","通过系统分享，保留自己的单词",()->shareWords());
        settingsAction(col,"ⓘ  关于 Folio","原生 Android 客户端 · v0.4.0",()->new AlertDialog.Builder(this)
            .setTitle("Folio · Native")
            .setMessage("一款个人英语外刊学习应用。原生阅读、词汇复习与阅读记录保存在本地；英文学习材料通过在线数据更新。媒体原文版权属于原媒体。")
            .setPositiveButton("知道了",null).show());
        gap(col,35);
        col.addView(text("MADE FOR SLOW READING, NOT ENDLESS SCROLLING.",10,MUTED,SANS_MED));
    }
    private void statCard(LinearLayout parent,String number,String label){
        LinearLayout panel=column();pad(panel,19,19,19,19);surface(panel,SOFT,16,false);
        panel.addView(text(number,30,GREEN,SERIF_BOLD));gap(panel,6);
        panel.addView(text(label,12,MUTED,SANS_MED));parent.addView(panel,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void settingsAction(LinearLayout col,String heading,String sub,Runnable action){
        LinearLayout item=row();pad(item,16,14,13,14);
        surface(item,PANEL,13,true);
        LinearLayout words=column();words.addView(text(heading,15,INK,SANS_MED));gap(words,6);
        words.addView(text(sub,11,MUTED,SANS));
        item.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        item.addView(text("›",26,MUTED,SERIF));
        item.setClickable(true);item.setOnClickListener(v->action.run());
        col.addView(item,lp(-1,-2));gap(col,10);
    }
    private void shareWords(){
        try{
            JSONArray arr=new JSONArray();
            for(LearningStore.Card c:learning.cards()){
                JSONObject obj=new JSONObject();obj.put("word",c.word);obj.put("meaning",c.meaning);
                obj.put("level",c.level);arr.put(obj);
            }
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_TEXT,arr.toString(2));
            startActivity(Intent.createChooser(send,"导出 Folio 生词数据"));
        }catch(Exception e){setStatus("导出失败");}
    }
    private void openArticle(FeedRepository.Article a){
        archive.ingest(java.util.Collections.singletonList(a));
        nextMotion=2;
        reading=a;render();
    }
    private void renderReader(){
        LinearLayout toolbar=row();pad(toolbar,15,9,16,9);
        toolbar.setBackgroundColor(BG);
        TextView back=action("‹",false,()->{reading=null;nextMotion=3;render();});
        back.setTextSize(29);back.setContentDescription("返回文章列表");
        toolbar.addView(back,lp(48,45));
        TextView room=caption("THE READING ROOM");room.setGravity(Gravity.CENTER);
        toolbar.addView(room,new LinearLayout.LayoutParams(0,-1,1));
        boolean fav=learning.isBookmarked(reading.url);
        TextView star=action(fav?"◆":"◇",false,()->{});
        star.setOnClickListener(v->{
            boolean selected=!learning.isBookmarked(reading.url);
            learning.bookmark(reading.url,selected);
            star.setText(selected?"◆":"◇");
            star.setContentDescription(selected?"取消收藏":"收藏文章");
            FolioMotion.bookmark(star);
            setStatus(selected?"已收藏文章":"已取消收藏");
        });
        star.setTextSize(23);star.setContentDescription(fav?"取消收藏":"收藏文章");
        toolbar.addView(star,lp(48,45));
        body.addView(toolbar,lp(-1,64));
        readingProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        readingProgress.setMax(100);readingProgress.setProgress(0);
        readingProgress.setProgressTintList(ColorStateList.valueOf(GREEN));
        readingProgress.setProgressBackgroundTintList(ColorStateList.valueOf(SOFT));
        body.addView(readingProgress,lp(-1,3));
        ScrollView sc=scroll(body);readerScroll=sc;LinearLayout col=screenContent(sc);
        sc.setOnScrollChangeListener((View.OnScrollChangeListener)(view,x,y,oldX,oldY)->{
            int maximum=Math.max(1,sc.getChildAt(0).getHeight()-sc.getHeight());
            readingProgress.setProgress(Math.max(0,Math.min(100,100*y/maximum)),true);
        });
        col.addView(caption(reading.topic.toUpperCase(Locale.ENGLISH)+"  /  "+reading.source.toUpperCase(Locale.ENGLISH)));
        gap(col,21);
        // Length-aware editorial typography: long titles should not crowd out the text.
        int titleLength=reading.title.length();
        int titleSp=titleLength>82?26:(titleLength>54?29:33);
        TextView title=text(reading.title,titleSp,INK,SERIF_BOLD);
        title.setLineSpacing(d(1),1.04f);
        col.addView(title);gap(col,17);
        TextView date=caption(formatDate(reading.date)+"  ·  "+
            Math.max(2,reading.readingText.trim().split("\\s+").length/180)+" MIN READ");
        col.addView(date);gap(col,25);addDivider(col);gap(col,23);
        TextView notice=text("ORIGINAL LEARNING EDITION  ·  原创英语学习材料",10,GREEN,SANS_MED);
        notice.setLetterSpacing(.06f);col.addView(notice);gap(col,16);
        TextView content=text("",learning.fontSize(),INK,SERIF);
        content.setLineSpacing(d(7),1.17f);
        bindClickableWords(content,reading.readingText);
        col.addView(content);gap(col,19);
        TextView tip=text("轻触任意英文单词可查询与收藏  ·  仅此精读页",11,MUTED,SANS);
        col.addView(tip);gap(col,20);
        LinearLayout utilities=row();
        utilities.addView(action("▶  英文朗读",false,()->speak()),new LinearLayout.LayoutParams(0,d(46),1));
        View sep=new View(this);utilities.addView(sep,lp(9,1));
        utilities.addView(action("A−",false,()->changeReadingFont(-1)),lp(52,46));
        View sep2=new View(this);utilities.addView(sep2,lp(7,1));
        utilities.addView(action("A+",false,()->changeReadingFont(1)),lp(52,46));
        col.addView(utilities);gap(col,30);
        if(!reading.editorNote.isEmpty()){
            LinearLayout editorial=column();pad(editorial,18,20,18,20);
            surface(editorial,SOFT,15,false);
            editorial.addView(caption("EDITOR'S NOTE  /  中文导读"));gap(editorial,12);
            editorial.addView(text(reading.editorNote,14,INK,SANS));
            col.addView(editorial);gap(col,27);
        }
        sectionLabel(col,"重点词汇");
        if(reading.words.size()>0){
            for(FeedRepository.Word w:reading.words){
                LinearLayout line=row();pad(line,2,11,2,11);
                LinearLayout labels=column();labels.addView(text(w.word,19,INK,SERIF_BOLD));
                gap(labels,4);labels.addView(text(w.meaning,13,MUTED,SANS));
                line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                TextView add=action(learning.hasWord(w.word)?"✓ 已保存":"＋ 收藏",false,()->{});
                add.setOnClickListener(v->{
                    learning.saveWord(w.word,w.meaning);
                    add.setText("✓ 已保存");
                    add.setContentDescription(w.word+" 已保存");
                    setStatus(w.word+" 已加入生词本");
                });
                line.addView(add,lp(-2,43));col.addView(line);addDivider(col);
            }
        }
        gap(col,32);
        sectionLabel(col,"读完以后");
        if(!reading.question.isEmpty()){
            col.addView(text(reading.question,17,INK,SERIF));gap(col,18);
        }
        col.addView(action("✎  阅读札记",false,()->editNote()),lp(-1,46));gap(col,12);
        col.addView(action("◇  三题词汇小测",false,()->startQuiz()),lp(-1,46));gap(col,12);
        boolean completed=learning.isRead(reading.url);
        TextView finished=action(completed?"✓  本篇已完成":"✓  完成本篇阅读",true,()->{});
        finished.setOnClickListener(v->{
            learning.markRead(reading.url);
            finished.setText("✓  本篇已完成");
            setStatus("阅读记录已保存");
        });
        col.addView(finished,lp(-1,52));gap(col,15);
        TextView original=action("↗  前往媒体原文",false,()->openOriginal());
        col.addView(original,lp(-1,46));gap(col,30);
        TextView credit=text("本文精读材料独立编写，并非媒体原文全文。请通过原文链接访问出版方。",11,MUTED,SANS);
        credit.setGravity(Gravity.CENTER);col.addView(credit);
    }
    private void changeReadingFont(int change){
        float fraction=0f;
        if(readerScroll!=null&&readerScroll.getChildCount()>0){
            int max=Math.max(1,readerScroll.getChildAt(0).getHeight()-readerScroll.getHeight());
            fraction=readerScroll.getScrollY()/(float)max;
        }
        learning.setFontSize(learning.fontSize()+change);
        final float keep=fraction;
        render();
        if(readerScroll!=null)readerScroll.post(()->{
            int max=Math.max(0,readerScroll.getChildAt(0).getHeight()-readerScroll.getHeight());
            readerScroll.scrollTo(0,Math.round(keep*max));
        });
        setStatus("正文 "+learning.fontSize()+"sp");
    }
    private String formatDate(String date){
        try{
            String day=date.length()>=10?date.substring(0,10):date;
            return day.replace("-"," / ");
        }catch(Exception e){return "";}
    }
    private void openOriginal(){
        if(reading==null)return;
        try{
            Uri url=Uri.parse(reading.url);
            if(!"https".equals(url.getScheme()))return;
            startActivity(new Intent(Intent.ACTION_VIEW,url));
        }catch(ActivityNotFoundException e){setStatus("未找到浏览器");}
    }
    private void editNote(){
        EditText input=new EditText(this);input.setMinLines(4);input.setMaxLines(9);
        input.setGravity(Gravity.TOP|Gravity.START);
        input.setText(learning.note(reading.url));
        input.setHint("这篇文章改变了我的哪些想法？");pad(input,18,15,18,15);
        final String url=reading.url;
        new AlertDialog.Builder(this).setTitle("阅读札记")
          .setView(input).setNegativeButton("取消",null)
          .setPositiveButton("保存",(dialog,which)->{
              String val=input.getText().toString().trim();
              if(val.length()>6000){setStatus("札记过长，请缩短");return;}
              learning.note(url,val);setStatus("札记已保存在本机");
          }).show();
    }
    private void speak(){
        if(speech==null){
            speech=new TextToSpeech(getApplicationContext(),status->{
                if(status==TextToSpeech.SUCCESS&&speech!=null){
                    speech.setLanguage(Locale.US);speak();
                }else setStatus("无法使用设备语音引擎");
            });
            return;
        }
        if(speech.isSpeaking()){speech.stop();setStatus("已停止朗读");return;}
        if(reading!=null){
            speech.setSpeechRate(.9f);
            speech.speak(reading.readingText,TextToSpeech.QUEUE_FLUSH,null,"folio-reading");
            setStatus("朗读已开始 · 再次点击可停止");
        }
    }
    private void bindClickableWords(TextView target,String source){
        SpannableStringBuilder builder=new SpannableStringBuilder(source);
        Matcher matcher=WORD_PATTERN.matcher(source);
        while(matcher.find()){
            final String word=source.substring(matcher.start(),matcher.end());
            boolean important=false;
            for(FeedRepository.Word w:reading.words)if(w.word.equalsIgnoreCase(word)){important=true;break;}
            final boolean tinted=important;
            ClickableSpan span=new ClickableSpan(){
                @Override public void onClick(View view){lookupWord(word);}
                @Override public void updateDrawState(TextPaint paint){
                    paint.setColor(tinted?GREEN:INK);
                    paint.setUnderlineText(tinted);
                    if(tinted)paint.setFakeBoldText(true);
                }
            };
            builder.setSpan(span,matcher.start(),matcher.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        target.setText(builder);
        target.setHighlightColor(0x558CA58F);
        target.setMovementMethod(LinkMovementMethod.getInstance());
    }
    private void lookupWord(String raw){
        final String word=raw.toLowerCase(Locale.ROOT);
        String curated="";
        if(reading!=null)for(FeedRepository.Word w:reading.words){
            if(w.word.equalsIgnoreCase(word)){curated=w.meaning;break;}
        }
        final boolean hasCurated=!curated.isEmpty();
        final String[] meaning={curated};
        Dialog sheet=new Dialog(this);
        LinearLayout panel=column();
        pad(panel,23,20,23,29);
        panel.setBackground(shape(BG,25));
        TextView handle=text("━━━━",16,SAGE,SANS);
        handle.setGravity(Gravity.CENTER);panel.addView(handle,lp(-1,23));
        gap(panel,12);
        panel.addView(caption("FOLIO LEXICON  /  轻触查词"));gap(panel,12);
        TextView title=text(word,34,INK,SERIF_BOLD);panel.addView(title);
        gap(panel,15);
        TextView meaningView=text(hasCurated?curated:"正在获取英语释义…",16,INK,SANS);
        meaningView.setMinHeight(d(62));
        panel.addView(meaningView);gap(panel,14);
        TextView note=text(hasCurated?"本期编辑精选词汇":"释义由外部词典提供；离线可学习已精选词汇。",11,MUTED,SANS);
        panel.addView(note);gap(panel,24);
        LinearLayout actions=row();
        TextView dismiss=action("返回阅读",false,sheet::dismiss);
        actions.addView(dismiss,new LinearLayout.LayoutParams(0,d(48),1));
        View spacing=new View(this);actions.addView(spacing,lp(10,1));
        TextView save=action(learning.hasWord(word)?"✓ 已收藏":"＋ 收藏单词",true,()->{});
        boolean ready=hasCurated||learning.hasWord(word);
        save.setEnabled(ready);save.setAlpha(ready?1f:.45f);
        save.setOnClickListener(v->{
            if(learning.hasWord(word)){sheet.dismiss();return;}
            if(meaning[0]==null||meaning[0].trim().isEmpty()){setStatus("释义尚未就绪");return;}
            learning.saveWord(word,meaning[0]);
            setStatus("已收藏 "+word);
            sheet.dismiss();
        });
        actions.addView(save,new LinearLayout.LayoutParams(0,d(48),1.15f));
        panel.addView(actions);
        sheet.setContentView(panel);
        Window window=sheet.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params=window.getAttributes();
            params.width=WindowManager.LayoutParams.MATCH_PARENT;
            params.height=WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity=Gravity.BOTTOM;
            params.dimAmount=.37f;
            window.setAttributes(params);
            window.setNavigationBarColor(BG);
        }
        sheet.show();
        window=sheet.getWindow();
        if(window!=null)window.setLayout(-1,-2);
        if(hasCurated || learning.hasWord(word))return;

        lookup.execute(()->{
            String definition=null;
            HttpURLConnection conn=null;
            try{
                URL url=new URL("https://api.dictionaryapi.dev/api/v2/entries/en/"+word);
                conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(5500);conn.setReadTimeout(5500);
                if(conn.getResponseCode()==200){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream input=conn.getInputStream()){
                        byte[] buffer=new byte[1024];int count;
                        while((count=input.read(buffer))!=-1&&out.size()<45000)
                            out.write(buffer,0,count);
                    }
                    JSONArray items=new JSONArray(out.toString("UTF-8"));
                    JSONArray meanings=items.getJSONObject(0).optJSONArray("meanings");
                    if(meanings!=null&&meanings.length()>0){
                        JSONObject first=meanings.getJSONObject(0);
                        JSONArray definitions=first.optJSONArray("definitions");
                        if(definitions!=null&&definitions.length()>0){
                            String text=definitions.getJSONObject(0).optString("definition");
                            if(!text.trim().isEmpty())definition=first.optString("partOfSpeech")+" · "+text;
                        }
                    }
                }
            }catch(Exception ignored){}
            finally{if(conn!=null)conn.disconnect();}
            final String result=definition;
            main.post(()->{
                if(!sheet.isShowing())return;
                if(result==null){
                    meaningView.setText("释义暂不可用。稍后联网重试，或使用本期精选词汇。");
                    return;
                }
                meaning[0]=result;
                meaningView.setText(result);
                save.setEnabled(true);save.setAlpha(1f);
            });
        });
    }
    private void startQuiz(){
        if(reading.words.size()<3){setStatus("本篇词汇不足三题");return;}
        List<FeedRepository.Word> list=new ArrayList<>(reading.words);
        Collections.shuffle(list);quizQuestion(list,0,0);
    }
    private void quizQuestion(List<FeedRepository.Word> list,int current,int correct){
        if(current>=3){
            new AlertDialog.Builder(this).setTitle("练习完成")
                .setMessage("本次答对 "+correct+" / 3 题。继续阅读或复习生词吧。")
                .setPositiveButton("完成",null).show();
            return;
        }
        FeedRepository.Word answer=list.get(current);
        ArrayList<String> choices=new ArrayList<>();choices.add(answer.meaning);
        for(FeedRepository.Word other:list) {
            if(!other.word.equals(answer.word)&&!choices.contains(other.meaning))
                choices.add(other.meaning);
            if(choices.size()==3)break;
        }
        Collections.shuffle(choices);
        String[] options=choices.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("QUESTION "+(current+1)+" / 3")
          .setMessage("「"+answer.word+"」是什么意思？")
          .setCancelable(false)
          .setItems(options,(dialog,index)->{
              boolean hit=options[index].equals(answer.meaning);
              Toast.makeText(this,hit?"✓ 正确":"答案："+answer.meaning,Toast.LENGTH_SHORT).show();
              main.postDelayed(()->quizQuestion(list,current+1,correct+(hit?1:0)),330);
          }).setNegativeButton("退出练习",(d,w)->{}).show();
    }
    @Override public void onBackPressed(){
        if(reading!=null){if(speech!=null)speech.stop();reading=null;nextMotion=3;render();return;}
        if(activeTab!=0){switchTab(0);return;}super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(speech!=null){speech.stop();speech.shutdown();}
        feed.close();archive.close();lookup.shutdownNow();super.onDestroy();
    }
}
