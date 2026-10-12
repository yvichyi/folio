package com.folio.dailyenglish;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String START_URL = "https://yvichyi.github.io/folio/";
    private static final String TRUSTED_HOST = "yvichyi.github.io";
    private static final int PAPER = Color.rgb(245, 242, 234);
    private static final int INK = Color.rgb(38, 55, 44);

    private WebView browser;
    private LinearLayout errorView;
    private ProgressBar progress;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUI();
        browser.loadUrl(START_URL);
    }

    private void buildUI() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(PAPER);

        browser = new WebView(this);
        browser.setBackgroundColor(PAPER);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // Local reading cache and saved vocabulary.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isInternal(uri)) return false;
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    try { startActivity(intent); } catch (ActivityNotFoundException ignored) {}
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                errorView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showNetworkError();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showNetworkError();
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });
        frame.addView(browser, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 5, Gravity.TOP);
        frame.addView(progress, barParams);

        errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(dp(28), dp(32), dp(28), dp(32));
        errorView.setBackgroundColor(PAPER);
        TextView brand = new TextView(this);
        brand.setText("folio.");
        brand.setTextSize(49);
        brand.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
        brand.setTextColor(INK);
        errorView.addView(brand);
        TextView explanation = new TextView(this);
        explanation.setText("暂时无法加载每日英语外刊。\\n请检查网络连接，或确认 GitHub Pages 已发布。");
        explanation.setTextSize(15);
        explanation.setGravity(Gravity.CENTER);
        explanation.setTextColor(Color.rgb(107, 120, 110));
        explanation.setPadding(0, dp(18), 0, dp(24));
        errorView.addView(explanation);
        Button retry = new Button(this);
        retry.setText("重新连接");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            browser.loadUrl(START_URL);
        });
        errorView.addView(retry);
        errorView.setVisibility(View.GONE);
        frame.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(frame);
    }

    private static boolean isInternal(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && TRUSTED_HOST.equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && (uri.getPath().equals("/folio") || uri.getPath().startsWith("/folio/"));
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private void showNetworkError() {
        progress.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        // The reading overlay is a single-page view; let its back button handle closing.
        if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
