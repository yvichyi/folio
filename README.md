# Folio · 每日英语外刊

## Online Study Edition v0.4 (October 12, 2026)

**The current installed v0.3 Android preview can load this newer interface by reopening or refreshing the app.** This is a remote web-client feature update; the Android package itself remains the v0.3 preview. No APK rebuild is needed for article, UI or study-content changes hosted on GitHub Pages.

The full online study flow now includes:

- Four primary destinations: Today, Explore (search and subject filters), Wordbook (saved words and due flashcard reviews), and Profile (progress, saved stories, journal, backups).
- Reading Studio: clearly attributed original English learning text, Chinese editor's notes, word-by-word lookup, curated vocabulary, three retrieval questions, user notes, English reading aloud when supported, reading completion and original-publisher link.
- Saved-word spaced review, locally saved bookmarks and archived reading notes even after the daily issue changes.
- Local-first cached issues and honest offline messaging. Older first-generation wordbook items and cached articles are migrated where available.
- Android-friendly copy/paste JSON backup and restore, with optional file download in supported browsers.
- Upgraded mobile layout and larger text/touch controls; UI transitions respect reduced-motion settings.
- Daily editorial updates change `docs/feed/daily.json` and do not invoke the Android APK build workflow.

**Verification:** A local Chromium UI exercise tested mobile (390px and 320px), desktop (1280px), loading the issue, opening an article, vocabulary lookup/save, quiz steps, note persistence, bookmarking, review list, offline fallback, reading-archive retrieval, JSON restore, and horizontal overflow. GitHub Actions checks JavaScript syntax, key app wiring, feed schema and original study-text presence. These checks are not a substitute for Android device and accessibility testing.

**Not yet release-quality:** the Android shell is a minimal WebView wrapper; Web Speech and external link behavior require Android-device acceptance, offline first launch cannot show genuine articles until a feed has been cached, all long-form passages are **original pedagogical adaptations rather than publisher full texts**, and debug APKs are not stable signed production releases. Do not describe the app as having silent background data sync or native notifications.

---

Personal English-reading companion: the **article feed updates online**, not through daily APK rebuilds.

## Live content

- JSON feed: https://raw.githubusercontent.com/yvichyi/folio/main/docs/feed/daily.json
- [Daily issue source](docs/feed/daily.json)
- [Web reading interface](docs/index.html) (requires GitHub Pages to be enabled)
- Format validator: `node scripts/validate-content.cjs`

### Enable the reading website

Open this repository’s **Settings → Pages**. Under **Build and deployment**, choose **Deploy from a branch**, branch **main**, folder **/docs**, and save. After GitHub publishes the site, open:

https://yvichyi.github.io/folio/

The site fetches `docs/feed/daily.json` each time it opens or the Refresh button is tapped. Local browser storage saves vocabulary and cached editorial data.

### Enable the existing Android app

The separately delivered Folio Android v0.2 source includes a cloud-feed setting.

1. Install a successfully compiled v0.2+ Android APK (not built or installed by this repository at present).
2. Open **我的 → 每日精选云端地址**.
3. Paste `https://raw.githubusercontent.com/yvichyi/folio/main/docs/feed/daily.json` and save.
4. Tap **获取最新**. Future content updates use the same URL without installing a new APK.

An installable minimal Android client has now been added under [android/](android/). It wraps the same GitHub Pages reading site in an Android WebView with external media links opened in the system browser.

[Build Folio Android Preview](../../actions/workflows/android.yml) runs only when the Android project or its build workflow changes, **not when daily article data changes**. Once the build completes successfully, download the `folio-android-preview-apk` artifact ZIP and extract `app-debug.apk`. The APK is a debug preview, not a production release; stable production signing must be configured before distributing updates. The full-featured earlier v0.2.1 source package is separate and has not been migrated into this minimal connected client.

**A pushed workflow is not evidence of a successful APK build.** Check the GitHub Actions status before installing.

### Publishing process

Daily maintenance selects and verifies recent English articles, then writes original English learning summaries, Chinese editorial notes, and vocabulary into `docs/feed/daily.json`. It links to the original publisher and **does not redistribute paid/full copyrighted articles**.

The GitHub Action validates content changes but does not trigger any Android APK build. If a feed is unavailable, the website uses cached items and reports the offline state.

Content is informational and written for English learning; scientific/medical subjects should not be treated as personalized advice.
