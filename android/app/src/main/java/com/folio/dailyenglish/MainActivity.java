package com.folio.dailyenglish;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(246,243,235), INK=Color.rgb(36,55,43);
    private static final int GREEN=Color.rgb(66,95,73), MUTED=Color.rgb(112,129,116);
    private static final int LINE=Color.rgb(217,224,213), SURFACE=Color.rgb(255,254,250);
    private static final int SOFT=Color.rgb(232,237,228), WHITE=Color.WHITE;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private FolioStore store;
    private FrameLayout frame, pageHost;
    private LinearLayout shell, bottom, top;
    private ProgressBar loading;
    private int tab=0, topic=0, reviewIndex=0;
    private boolean inArticle=false, syncing=false, destroyed=false;
    private FolioStore.Article current;
    private ArrayList<String> reviewQueue=new ArrayList<>();
    private boolean revealed=false;
    private TextToSpeech tts;
    private boolean ttsReady=false;
    private final String[] category={"全部","科技","科学","文化","思想"};
    private static final Pattern WORD=Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)*");

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        store=new FolioStore(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildShell();
        render();
        refresh(false);
    }

    private int d(float v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable shape(int color,float radius,int border){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(d(radius));
        if(border!=0)bg.setStroke(d(1),border);
        return bg;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}
    private FrameLayout.LayoutParams fp(int w,int h,int gravity){return new FrameLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h),gravity);}
    private void pad(View v,int h,int vert){v.setPadding(d(h),d(vert),d(h),d(vert));}
    private TextView text(String s,float size,int color,boolean serif,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setIncludeFontPadding(true);
        if(serif)v.setTypeface(Typeface.create("serif",bold?Typeface.BOLD:Typeface.NORMAL));
        else if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        v.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        return v;
    }
    private TextView label(String s){TextView t=text(s,10,MUTED,false,true);t.setLetterSpacing(.12f);return t;}
    private View line(){View v=new View(this);v.setBackgroundColor(LINE);return v;}
    private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,lp(-1,height));}
    private void add(LinearLayout parent,View child){parent.addView(child,lp(-1,-2));}
    private void sectionRule(LinearLayout p){gap(p,20);p.addView(line(),lp(-1,1));gap(p,20);}
    private TextView pill(String title,boolean filled){
        TextView t=text(title,12,filled?WHITE:GREEN,false,true);
        t.setGravity(Gravity.CENTER);pad(t,15,9);
        t.setBackground(shape(filled?INK:SURFACE,50,filled?0:LINE));return t;
    }
    private LinearLayout card(){LinearLayout c=column();pad(c,19,19);c.setBackground(shape(SURFACE,18,LINE));return c;}
    private TextView primary(String s){
        TextView t=text(s,14,WHITE,false,true);pad(t,22,16);t.setGravity(Gravity.CENTER);
        t.setBackground(shape(INK,13,0));t.setMinHeight(d(50));return t;
    }
    private TextView secondary(String s){
        TextView t=text(s,14,INK,false,true);pad(t,18,14);t.setGravity(Gravity.CENTER);
        t.setBackground(shape(SURFACE,13,LINE));t.setMinHeight(d(48));return t;
    }
    private void buildShell(){
        frame=new FrameLayout(this);frame.setBackgroundColor(BG);
        shell=column();frame.addView(shell,fp(-1,-1,Gravity.TOP));
        top=row();top.setBackgroundColor(BG);top.setPadding(d(22),d(12),d(18),d(9));
        shell.addView(top,lp(-1,65));
        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);loading.setIndeterminate(true);loading.setVisibility(View.GONE);
        shell.addView(loading,lp(-1,2));
        pageHost=new FrameLayout(this);
        shell.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));
        bottom=row();bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(SURFACE);
        bottom.setPadding(d(7),d(6),d(7),d(7));
        shell.addView(line(),lp(-1,1));
        shell.addView(bottom,lp(-1,68));
        setContentView(frame);
    }
    private View iconButton(String kind,String description,Runnable action){
        FrameLayout b=new FrameLayout(this);b.setBackground(shape(SOFT,14,0));
        Mark icon=new Mark(kind,GREEN);b.addView(icon,fp(23,23,Gravity.CENTER));
        b.setContentDescription(description);b.setOnClickListener(v->action.run());return b;
    }
    private void toolbar(){
        top.removeAllViews();
        if(inArticle){
            View back=iconButton("back","返回",()->closeArticle());top.addView(back,lp(45,45));
            TextView crumb=text("THE READING ROOM",11,MUTED,false,true);crumb.setLetterSpacing(.08f);
            LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(0,-2,1);
            middle.leftMargin=d(13);top.addView(crumb,middle);
            View speak=iconButton("sound","朗读文章",()->speak(current!=null?current.reading:""));
            top.addView(speak,lp(45,45));
            View fav=iconButton("save","收藏文章",()->{
                if(current!=null){store.toggleFavorite(current.id);toast(store.favorite(current.id)?"已加入收藏":"已取消收藏");}
            });
            LinearLayout.LayoutParams p=lp(45,45);p.leftMargin=d(9);top.addView(fav,p);
        }else{
            LinearLayout logo=column();
            TextView brand=text("folio.",30,INK,true,true);brand.setLetterSpacing(-.06f);add(logo,brand);
            TextView sub=text("DAILY ENGLISH JOURNAL",9,MUTED,false,true);sub.setLetterSpacing(.10f);add(logo,sub);
            top.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
            View sync=iconButton("refresh","同步最新文章",()->refresh(true));
            top.addView(sync,lp(45,45));
        }
    }
    private void navigation(){
        bottom.removeAllViews();
        if(inArticle){bottom.setVisibility(View.GONE);return;}
        bottom.setVisibility(View.VISIBLE);
        String[] captions={"今日","词库","复习","我的"};
        String[] symbols={"home","book","layers","user"};
        for(int i=0;i<4;i++){
            final int next=i;
            LinearLayout item=column();item.setGravity(Gravity.CENTER);
            boolean active=(i==tab);
            Mark glyph=new Mark(symbols[i],active?INK:MUTED);
            item.addView(glyph,lp(22,22));
            gap(item,4);
            TextView caption=text(captions[i],11,active?INK:MUTED,false,active);
            caption.setGravity(Gravity.CENTER);add(item,caption);
            item.setBackground(shape(active?SOFT:SURFACE,14,0));
            item.setContentDescription(captions[i]+"页面");
            item.setOnClickListener(v->{tab=next;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
            p.leftMargin=d(3);p.rightMargin=d(3);bottom.addView(item,p);
        }
    }
    private void render(){
        toolbar();navigation();
        pageHost.removeAllViews();
        if(inArticle && current!=null){pageHost.addView(readerPage(),fp(-1,-1,Gravity.TOP));}
        else{
            View page=tab==0?home():tab==1?wordbook():tab==2?reviewPage():profile();
            pageHost.addView(page,fp(-1,-1,Gravity.TOP));
        }
        pageHost.setAlpha(.82f);pageHost.animate().alpha(1).setDuration(150).start();
    }
    private ScrollView scroll(LinearLayout content){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);
        sv.addView(content,new ScrollView.LayoutParams(-1,-2));return sv;
    }
    private LinearLayout page(){
        LinearLayout content=column();content.setPadding(d(22),d(20),d(22),d(35));return content;
    }
    private String date(){
        return new SimpleDateFormat("EEEE · MMMM d, yyyy",Locale.ENGLISH).format(new Date()).toUpperCase(Locale.ENGLISH);
    }
    private View home(){
        LinearLayout p=page();add(p,label(date()));gap(p,11);
        TextView heading=text("Read a little.\nThink a lot.",41,INK,true,false);
        heading.setLetterSpacing(-.04f);heading.setLineSpacing(d(2),1f);add(p,heading);gap(p,12);
        add(p,text("把世界装进每天十五分钟的英语阅读。",13,MUTED,false,false));gap(p,26);
        LinearLayout metrics=row();
        TextView words=pill(store.wordsCount()+" 词已收藏",false);
        metrics.addView(words,lp(-2,-2));LinearLayout.LayoutParams countLp=lp(-2,-2);countLp.leftMargin=d(9);
        TextView progress=pill(store.readCount()+" 篇已读",false);metrics.addView(progress,countLp);
        add(p,metrics);
        sectionRule(p);
        LinearLayout tools=row();TextView title=label("TODAY'S SELECTION");
        tools.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(syncing?"同步中":store.lastSync().isEmpty()?"等待同步":"已同步",11,MUTED,false,false);
        add(tools,status);add(p,tools);gap(p,15);
        List<FolioStore.Article> articles=store.articles();
        if(articles.isEmpty()){
            LinearLayout empty=card();add(empty,text("打开世界，从第一篇开始。",25,INK,true,false));gap(empty,12);
            add(empty,text("还没有已保存的外刊内容。连接网络后即可同步，每次打开应用也会尝试更新。",13,MUTED,false,false));gap(empty,18);
            TextView retry=primary("获取今日外刊  ↗");retry.setOnClickListener(v->refresh(true));add(empty,retry);
            add(p,empty);return scroll(p);
        }
        HorizontalScrollView filterScroll=new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters=row();
        for(int i=0;i<category.length;i++){
            final int n=i;
            TextView chip=pill(category[i],topic==i);
            chip.setOnClickListener(v->{topic=n;render();});
            LinearLayout.LayoutParams cp=lp(-2,-2);cp.rightMargin=d(8);filters.addView(chip,cp);
        }
        filterScroll.addView(filters);add(p,filterScroll);gap(p,20);
        int shown=0;
        for(FolioStore.Article a:articles){
            if(topic>0 && !a.topic.equals(category[topic]))continue;
            final FolioStore.Article article=a;
            LinearLayout c=card();
            LinearLayout meta=row();TextView type=label(a.topic+"   /   "+a.source);
            meta.addView(type,new LinearLayout.LayoutParams(0,-2,1));
            if(store.isRead(a.id))add(meta,text("✓ 已读",11,GREEN,false,true));
            add(c,meta);gap(c,11);
            TextView h=text(a.title,shown==0?28:22,INK,true,false);
            h.setLineSpacing(d(2),1f);add(c,h);gap(c,13);
            TextView brief=text(a.summary,13,MUTED,false,false);
            brief.setMaxLines(3);brief.setEllipsize(android.text.TextUtils.TruncateAt.END);
            add(c,brief);gap(c,19);
            c.addView(line(),lp(-1,1));gap(c,12);
            LinearLayout action=row();
            TextView read=label("OPEN READING  →");
            action.addView(read,new LinearLayout.LayoutParams(0,-2,1));
            if(store.favorite(a.id))add(action,text("★ 收藏",11,GREEN,false,true));
            add(c,action);
            c.setClickable(true);c.setOnClickListener(v->openArticle(article));
            LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=d(12);
            p.addView(c,cp);shown++;
        }
        if(shown==0)add(p,text("这个主题今天暂时没有精选。",14,MUTED,false,false));
        gap(p,19);
        add(p,label("内容来自公开媒体，精读材料为独立编写。点击“原文”可前往媒体网站。"));
        return scroll(p);
    }
    private void openArticle(FolioStore.Article a){
        current=a;inArticle=true;render();
    }
    private void closeArticle(){inArticle=false;current=null;render();}
    private View readerPage(){
        final FolioStore.Article a=current;
        LinearLayout p=page();
        add(p,label(a.topic+"  /  "+a.source+"  /  ORIGINAL STUDY TEXT"));gap(p,12);
        TextView title=text(a.title,34,INK,true,false);title.setLetterSpacing(-.025f);
        title.setLineSpacing(d(2),1f);add(p,title);gap(p,16);
        add(p,text(a.note,14,MUTED,false,false));gap(p,22);
        LinearLayout settings=row();
        TextView smaller=secondary("A−");smaller.setOnClickListener(v->{store.setFontSize(store.fontSize()-1);render();});
        settings.addView(smaller,lp(60,48));
        TextView larger=secondary("A+");larger.setOnClickListener(v->{store.setFontSize(store.fontSize()+1);render();});
        LinearLayout.LayoutParams big=lp(60,48);big.leftMargin=d(8);settings.addView(larger,big);
        TextView speak=secondary("♪ 朗读");speak.setOnClickListener(v->speak(a.reading));
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(0,d(48),1);sl.leftMargin=d(8);
        settings.addView(speak,sl);add(p,settings);gap(p,25);
        p.addView(line(),lp(-1,1));gap(p,20);
        String[] paragraphs=a.reading.split("\\n\\s*\\n");
        for(String para:paragraphs){
            if(para.trim().isEmpty())continue;
            TextView body=text("",store.fontSize(),INK,true,false);
            body.setLineSpacing(d(8),1.04f);
            SpannableString span=new SpannableString(para.trim());
            Matcher matcher=WORD.matcher(para.trim());
            while(matcher.find()){
                String word=matcher.group();
                String known=lookup(a,word);
                span.setSpan(new ClickableSpan(){
                    @Override public void onClick(View view){showWord(word,known);}
                    @Override public void updateDrawState(TextPaint ds){
                        ds.setColor(INK);ds.setUnderlineText(false);
                    }
                },matcher.start(),matcher.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            body.setText(span);body.setMovementMethod(LinkMovementMethod.getInstance());
            body.setHighlightColor(SOFT);
            add(p,body);gap(p,22);
        }
        LinearLayout notice=card();notice.setBackground(shape(SOFT,15,0));
        add(notice,label("VOCABULARY / TAP TO SAVE"));gap(notice,7);
        add(notice,text("点击正文中的英文单词，可查阅重点释义并加入生词本。",13,GREEN,false,false));
        gap(notice,16);
        LinearLayout chips=row();chips.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<Math.min(4,a.vocabulary.length());i++){
            JSONObject v=a.vocabulary.optJSONObject(i);if(v==null)continue;
            String word=v.optString("word");String meaning=v.optString("meaning");
            TextView chip=pill(word,false);
            chip.setOnClickListener(w->showWord(word,meaning));
            LinearLayout.LayoutParams cl=lp(-2,-2);cl.rightMargin=d(8);
            chips.addView(chip,cl);
        }
        add(notice,chips);add(p,notice);
        gap(p,23);
        add(p,label("ONE QUESTION TO THINK ABOUT"));gap(p,11);
        add(p,text(a.question.isEmpty()?"这篇文章最值得思考的问题是什么？":a.question,21,INK,true,false));
        gap(p,13);
        EditText notes=new EditText(this);notes.setText(store.note(a.id));
        notes.setTextSize(14);notes.setTextColor(INK);notes.setHintTextColor(MUTED);
        notes.setHint("写下你的理解、疑问或想表达的英文句子…");
        notes.setGravity(Gravity.TOP);notes.setMinLines(3);notes.setMaxLines(8);
        notes.setPadding(d(15),d(15),d(15),d(15));
        notes.setBackground(shape(SURFACE,13,LINE));add(p,notes);gap(p,10);
        TextView save=secondary("保存阅读札记");save.setOnClickListener(v->{
            store.setNote(a.id,notes.getText().toString());toast("札记已保存到本机");
        });add(p,save);
        gap(p,24);
        TextView done=primary(store.isRead(a.id)?"✓ 已完成阅读":"✓ 标记为已读");
        done.setOnClickListener(v->{store.setNote(a.id,notes.getText().toString());store.markRead(a.id);toast("已保存阅读记录");render();});
        add(p,done);gap(p,10);
        TextView original=secondary("在原媒体阅读文章  ↗");
        original.setOnClickListener(v->openExternal(a.url));add(p,original);gap(p,22);
        add(p,label("版权说明：本页英语短文和导读为原创学习材料，不是媒体文章全文。"));
        ScrollView sc=scroll(p);return sc;
    }
    private String lookup(FolioStore.Article article,String word){
        JSONObject stored=store.word(word);
        if(stored!=null&&!stored.optString("meaning").isEmpty())return stored.optString("meaning");
        for(int i=0;i<article.vocabulary.length();i++){
            JSONObject v=article.vocabulary.optJSONObject(i);
            if(v!=null&&v.optString("word").equalsIgnoreCase(word))return v.optString("meaning");
        }
        return "";
    }
    private void showWord(String word,String meaning){
        LinearLayout dialog=column();pad(dialog,20,10);
        TextView hint=text("中文释义（可自行填写）",12,MUTED,false,false);add(dialog,hint);gap(dialog,10);
        EditText edit=new EditText(this);edit.setSingleLine(false);
        edit.setText(meaning);edit.setHint("输入释义后加入生词本");
        edit.setTextSize(15);add(dialog,edit);
        AlertDialog d=new AlertDialog.Builder(this)
                .setTitle(word).setView(dialog)
                .setNeutralButton("发音",(di,w)->speak(word))
                .setNegativeButton("取消",null)
                .setPositiveButton("收藏",(di,w)->{
                    store.saveWord(word,edit.getText().toString().trim());
                    toast("已加入词库 · "+word);
                }).create();
        d.show();
    }
    private View wordbook(){
        LinearLayout p=page();add(p,label("PERSONAL VOCABULARY"));gap(p,9);
        add(p,text("Words worth keeping.",33,INK,true,false));gap(p,8);
        add(p,text("不只是收藏单词，也记住它们出现的语境。",13,MUTED,false,false));gap(p,22);
        LinearLayout stats=card();add(stats,text(store.wordsCount()+" 个单词",29,INK,true,false));
        add(stats,text(store.dueCount()+" 个等待复习 · 点击单词可查看或修改释义",12,MUTED,false,false));
        add(p,stats);gap(p,22);
        JSONObject words=store.words();List<String> keys=store.dueWords(true);
        if(keys.isEmpty()){
            LinearLayout empty=card();add(empty,text("你的词库还是空的。",22,INK,true,false));gap(empty,8);
            add(empty,text("在阅读页点一下英文单词，即可保存到这里。",13,MUTED,false,false));add(p,empty);
        }else{
            EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
            search.setHint("查找收藏过的词汇");search.setPadding(d(14),d(12),d(14),d(12));
            search.setBackground(shape(SURFACE,14,LINE));add(p,search);gap(p,14);
            LinearLayout list=column();add(p,list);
            Runnable draw=()->{
                list.removeAllViews();String filter=search.getText().toString().trim().toLowerCase(Locale.ROOT);
                for(String k:keys){
                    JSONObject v=words.optJSONObject(k);if(v==null)continue;
                    String meaning=v.optString("meaning","");
                    if(!k.contains(filter)&&!meaning.contains(filter))continue;
                    LinearLayout c=card();LinearLayout bar=row();
                    TextView w=text(k,23,INK,true,false);bar.addView(w,new LinearLayout.LayoutParams(0,-2,1));
                    TextView speaker=pill("♪",false);speaker.setOnClickListener(x->speak(k));bar.addView(speaker);
                    add(c,bar);gap(c,7);add(c,text(meaning.isEmpty()?"暂无释义，点击补充":meaning,13,MUTED,false,false));
                    gap(c,8);add(c,label(v.optLong("due",0)<=System.currentTimeMillis()?"● 待复习":"○ 已安排复习"));
                    c.setOnClickListener(x->showWord(k,meaning));
                    LinearLayout.LayoutParams cl=lp(-1,-2);cl.bottomMargin=d(9);list.addView(c,cl);
                }
            };
            search.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int st,int count,int after){}
                public void onTextChanged(CharSequence s,int st,int before,int count){draw.run();}
                public void afterTextChanged(android.text.Editable e){}
            });draw.run();
        }
        return scroll(p);
    }
    private View reviewPage(){
        LinearLayout p=page();add(p,label("SPACED REPETITION"));gap(p,9);
        add(p,text("Make it stick.",34,INK,true,false));gap(p,7);
        add(p,text("先回忆，再翻面。根据真实记忆情况安排下次复习。",13,MUTED,false,false));gap(p,23);
        if(reviewQueue.isEmpty()){
            int due=store.dueCount();
            LinearLayout c=card();add(c,label("TODAY'S REVIEW"));gap(c,12);
            add(c,text(""+due,57,INK,true,false));add(c,text("个待复习单词",14,MUTED,false,false));
            gap(c,25);
            TextView begin=primary(due>0?"开始今日复习  →":"练习全部已收藏单词  →");
            begin.setOnClickListener(v->{
                reviewQueue=new ArrayList<>(store.dueWords(due==0));reviewIndex=0;revealed=false;render();
                if(reviewQueue.isEmpty())toast("先阅读并收藏几个单词");
            });add(c,begin);add(p,c);gap(p,20);
            add(p,label("复习间隔：1 / 3 / 7 / 14 / 30 天"));
        }else{
            if(reviewIndex>=reviewQueue.size()){
                LinearLayout card=card();add(card,label("SESSION COMPLETE"));gap(card,12);
                add(card,text("Good work.",43,INK,true,false));gap(card,10);
                add(card,text("本轮 "+reviewQueue.size()+" 个单词已完成。你可以回到阅读，继续积累新的语言材料。",14,MUTED,false,false));
                gap(card,24);
                TextView again=primary("完成并返回");again.setOnClickListener(v->{reviewQueue.clear();render();});add(card,again);add(p,card);
            }else{
                String word=reviewQueue.get(reviewIndex);
                JSONObject obj=store.word(word);String meaning=obj==null?"":obj.optString("meaning");
                add(p,label((reviewIndex+1)+" / "+reviewQueue.size()+"  ·  MEMORY CHECK"));gap(p,17);
                LinearLayout card=card();card.setMinimumHeight(d(295));card.setGravity(Gravity.CENTER);
                add(card,label(revealed?"THE MEANING":"RECALL THE MEANING"));gap(card,27);
                TextView giant=text(word,40,INK,true,false);giant.setGravity(Gravity.CENTER);add(card,giant);
                gap(card,20);
                TextView answer=text(revealed?(meaning.isEmpty()?"暂无释义":meaning):"先在心里想想它的意思",15,MUTED,false,false);
                answer.setGravity(Gravity.CENTER);add(card,answer);add(p,card);gap(p,16);
                if(!revealed){
                    TextView flip=primary("查看释义  ↗");flip.setOnClickListener(v->{revealed=true;render();});add(p,flip);
                }else{
                    LinearLayout actions=row();
                    TextView forget=secondary("还没记住");forget.setOnClickListener(v->submitReview(word,false));
                    actions.addView(forget,new LinearLayout.LayoutParams(0,-2,1));
                    TextView known=primary("记住了 ✓");known.setOnClickListener(v->submitReview(word,true));
                    LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,-2,1);pl.leftMargin=d(10);actions.addView(known,pl);add(p,actions);
                }
                gap(p,16);
                TextView cancel=label("退出本轮复习");cancel.setGravity(Gravity.CENTER);pad(cancel,10,15);
                cancel.setOnClickListener(v->{reviewQueue.clear();render();});add(p,cancel);
            }
        }
        return scroll(p);
    }
    private void submitReview(String word,boolean remembered){
        store.review(word,remembered);reviewIndex++;revealed=false;render();
    }
    private View profile(){
        LinearLayout p=page();add(p,label("YOUR LEARNING SPACE"));gap(p,10);
        add(p,text("A little, every day.",34,INK,true,false));gap(p,22);
        LinearLayout a=row();
        String[] nums={""+store.readCount(),""+store.wordsCount(),""+store.reviewCount()};
        String[] captions={"完成阅读","收藏单词","复习次数"};
        for(int i=0;i<3;i++){
            LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(d(3),d(18),d(3),d(18));
            c.setBackground(shape(SURFACE,12,LINE));
            TextView n=text(nums[i],29,INK,true,false);n.setGravity(Gravity.CENTER);add(c,n);
            TextView l=text(captions[i],11,MUTED,false,false);l.setGravity(Gravity.CENTER);add(c,l);
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)pp.leftMargin=d(8);a.addView(c,pp);
        }
        add(p,a);gap(p,25);
        add(p,label("YOUR COLLECTION"));gap(p,12);
        int count=0;
        for(FolioStore.Article art:store.articles()){
            if(!store.favorite(art.id))continue;
            count++;
            LinearLayout c=card();add(c,label(art.source+" / "+art.topic));gap(c,8);
            add(c,text(art.title,21,INK,true,false));gap(c,10);
            add(c,label("打开收藏文章 →"));
            c.setOnClickListener(v->openArticle(art));
            LinearLayout.LayoutParams cp=lp(-1,-2);cp.bottomMargin=d(9);p.addView(c,cp);
        }
        if(count==0)add(p,text("还没有收藏文章。阅读时点击右上角书签即可收藏。",13,MUTED,false,false));
        sectionRule(p);
        add(p,label("SYNC & PRIVACY"));gap(p,12);
        LinearLayout status=card();add(status,text("在线更新 · 本地保存",19,INK,true,false));gap(status,10);
        add(status,text("每天打开或手动刷新时下载最新精读；生词、收藏和札记留在本机。",13,MUTED,false,false));
        if(!store.lastSync().isEmpty()){gap(status,8);add(status,label("上次同步："+store.lastSync()));}
        gap(status,14);
        TextView reload=secondary("立即同步新文章");reload.setOnClickListener(v->refresh(true));add(status,reload);
        add(p,status);gap(p,14);
        LinearLayout privacy=card();add(privacy,label("READING DATA"));gap(privacy,8);
        add(privacy,text("个人进度仅存储在当前 Android 应用中。卸载应用会删除这些记录。",13,MUTED,false,false));
        gap(privacy,12);
        TextView share=secondary("导出学习数据（文本）");
        share.setOnClickListener(v->exportData());add(privacy,share);add(p,privacy);gap(p,22);
        add(p,label("FOLIO NATIVE  ·  VERSION 0.4.0"));
        return scroll(p);
    }
    private void exportData(){
        try{
            JSONObject export=new JSONObject();
            export.put("format","folio-native-backup-v1");
            export.put("words",store.words());
            export.put("exportedAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(new Date()));
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("application/json");send.putExtra(Intent.EXTRA_TEXT,export.toString(2));
            startActivity(Intent.createChooser(send,"导出生词数据"));
        }catch(Exception e){toast("无法创建导出数据");}
    }
    private void openExternal(String address){
        try{
            Uri uri=Uri.parse(address);
            if(!"https".equalsIgnoreCase(uri.getScheme()))return;
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        }catch(ActivityNotFoundException ex){toast("未找到可打开原文的浏览器");}
    }
    private void speak(String phrase){
        if(phrase==null||phrase.isEmpty())return;
        if(tts==null){
            tts=new TextToSpeech(getApplicationContext(), status->{
                ttsReady=status==TextToSpeech.SUCCESS;
                if(ttsReady){tts.setLanguage(Locale.US);tts.setSpeechRate(.91f);tts.speak(phrase,TextToSpeech.QUEUE_FLUSH,null,"folio-read");}
                else toast("系统朗读不可用");
            });
        }else if(ttsReady){tts.speak(phrase,TextToSpeech.QUEUE_FLUSH,null,"folio-read");}
        else toast("语音引擎正在初始化");
    }
    private void refresh(boolean manual){
        if(syncing)return;syncing=true;loading.setVisibility(View.VISIBLE);
        if(manual)toast("正在获取最新外刊…");
        executor.submit(()->{
            HttpURLConnection connection=null;
            String error=null,received=null;
            try{
                URL url=new URL(FolioStore.FEED_URL+"?t="+System.currentTimeMillis());
                connection=(HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(10000);connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("User-Agent","Folio-Android/0.4");
                if(connection.getResponseCode()!=200)throw new java.io.IOException("HTTP "+connection.getResponseCode());
                InputStream in=connection.getInputStream();
                try(ByteArrayOutputStream buf=new ByteArrayOutputStream()){
                    byte[] chunk=new byte[8192];int n;
                    while((n=in.read(chunk))!=-1){buf.write(chunk,0,n);if(buf.size()>512000)throw new java.io.IOException("oversized feed");}
                    received=buf.toString(StandardCharsets.UTF_8.name());
                }
                if(!store.validateFeed(received))throw new java.io.IOException("invalid feed");
            }catch(Exception ex){error=ex.getMessage();}
            finally{if(connection!=null)connection.disconnect();}
            final String result=received,issue=error;
            runOnUiThread(()->{
                if(destroyed)return;
                syncing=false;loading.setVisibility(View.GONE);
                if(issue==null&&result!=null){
                    store.saveFeed(result);
                    store.setLastSync(new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date()));
                    if(!inArticle)render();
                    if(manual)toast("已同步最新英语精读");
                }else{
                    if(manual||store.articles().isEmpty())toast("联网失败"+(store.articles().isEmpty()?"，请稍后再试":"，继续使用离线内容"));
                    if(!inArticle)render();
                }
            });
        });
    }
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed(){
        if(inArticle){closeArticle();return;}
        if(tab!=0){tab=0;render();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){
        destroyed=true;executor.shutdownNow();if(tts!=null){tts.stop();tts.shutdown();}
        super.onDestroy();
    }
    private final class Mark extends View {
        final String symbol;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final int color;
        Mark(String s,int c){super(MainActivity.this);symbol=s;color=c;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas actual){
            super.onDraw(actual);
            Canvas c=actual;float scale=Math.min(getWidth(),getHeight())/24f;
            c.save();c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);
            paint.setColor(color);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            Path p=new Path();
            switch(symbol){
                case "home":
                    p.moveTo(3,11);p.lineTo(12,4);p.lineTo(21,11);p.moveTo(5,10);p.lineTo(5,21);p.lineTo(19,21);p.lineTo(19,10);
                    p.moveTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);c.drawPath(p,paint);break;
                case "book":
                    p.moveTo(12,5);p.cubicTo(8,3,5,3,3,4);p.lineTo(3,19);p.cubicTo(7,18,10,19,12,21);
                    p.cubicTo(14,19,17,18,21,19);p.lineTo(21,4);p.cubicTo(18,3,15,3,12,5);
                    p.moveTo(12,5);p.lineTo(12,21);c.drawPath(p,paint);break;
                case "layers":
                    c.drawRoundRect(4,5,20,10,2,2,paint);c.drawRoundRect(4,14,20,19,2,2,paint);c.drawLine(12,10,12,14,paint);break;
                case "user":
                    c.drawCircle(12,8,4,paint);p.moveTo(4,21);p.cubicTo(4,13,20,13,20,21);c.drawPath(p,paint);break;
                case "back":
                    c.drawLine(19,12,5,12,paint);c.drawLine(5,12,12,5,paint);c.drawLine(5,12,12,19,paint);break;
                case "save":
                    p.moveTo(6,3);p.lineTo(18,3);p.lineTo(18,21);p.lineTo(12,17);p.lineTo(6,21);p.close();c.drawPath(p,paint);break;
                case "sound":
                    p.moveTo(4,9);p.lineTo(8,9);p.lineTo(13,5);p.lineTo(13,19);p.lineTo(8,15);p.lineTo(4,15);p.close();
                    p.moveTo(16,9);p.quadTo(20,12,16,15);c.drawPath(p,paint);break;
                case "refresh":
                    p.addArc(4,4,20,20,42,282);p.moveTo(19,5);p.lineTo(20,12);p.lineTo(14,9);c.drawPath(p,paint);break;
            }
            c.restore();
        }
    }
}