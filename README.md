# Folio · 每日英语外刊

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

The complete Android application source and APK build workflow are **not yet uploaded to this repository**. This repository currently hosts the live editorial feed, validation workflow, and a web-reading client. Do not assume an APK has been built.

### Publishing process

Daily maintenance selects and verifies recent English articles, then writes original English learning summaries, Chinese editorial notes, and vocabulary into `docs/feed/daily.json`. It links to the original publisher and **does not redistribute paid/full copyrighted articles**.

The GitHub Action validates content changes but does not trigger any Android APK build. If a feed is unavailable, the website uses cached items and reports the offline state.

Content is informational and written for English learning; scientific/medical subjects should not be treated as personalized advice.
