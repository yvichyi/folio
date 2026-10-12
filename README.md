# Folio · 每日英语外刊

## Android Native Preview v0.5 · October 12, 2026

This release addresses the main product-retention defect, rather than simply adding another screen:

- `ArticleArchive` persists full learning-article snapshots in private on-device SQLite, keyed by canonical article URL. A daily issue refresh **upserts** the new article snapshots; it does not wipe older content. The Library reads the archive, including older bookmarked and completed stories.
- On first launch after upgrade, the currently cached issue (or bundled dated seed) is migrated into SQLite. **Content discarded by much older builds cannot be reconstructed retroactively.**
- Bookmarking, saving a curated word and marking an article complete no longer throw the reader back to the top. Font changes attempt to keep proportional reading position.
- Tapping a word opens a themed native bottom vocabulary sheet. A failed external-dictionary request does not get saved as a bogus definition.
- The Android UI smoke test uses an actual emulator. Instrumentation regression tests explicitly swap daily issues, reopen the SQLite database, verify both records remain, check deduplication and an empty update. See [Folio Native UI Smoke](../../actions/workflows/native-ui-smoke.yml) for the latest observed result.

**Limits:** This is still a debug-signed preview, not a production release. Signing keys may differ across builds; do not uninstall the previous app assuming learning data will survive. Archived data is local to the installed app, not synced across devices. Reliable import/export, stable private release signing, long archive-list virtualization, and full reader/touch/accessibility acceptance are future release gates. These limitations are not solved by a successful APK build.

---


A personal English-reading application with two separate clients sharing one live editorial feed.

## Android Native Preview · v0.4.0

The native Android client lives in [android/](android/). **It is not a WebView wrapper.**

- Native, bottom-tab app navigation: **今日 / 收藏 / 生词 / 我的**.
- Editorial article cards with topic filters and search; native scrolling and navigation.
- Long-form original English study editions, reading progress and selectable font size.
- Tap individual English words to view curated meanings or query an online dictionary, then save to a local wordbook.
- Local bookmarks, reading-completed history, article notes, three-question vocabulary check, English TTS and spaced flashcard review.
- Live JSON issue refresh, last-good offline cache, Android Internet-only permission.
- Native adaptive app icon and Android system back-button support.
- Local unit tests for vocabulary spacing.

The preview uses the Android application ID `com.folio.dailyenglish.nativepreview` so it installs alongside the previous WebView preview instead of overwriting its data. **WebView localStorage data is not automatically imported into the native preview.** Preview builds use ephemeral debug signing and must not be treated as a stable update channel.

**Current validation:** source checked in and compiled through [GitHub Actions](../../actions/workflows/android.yml); an emulator capture and navigation smoke job is configured at [Native UI Smoke](../../actions/workflows/native-ui-smoke.yml). Report real emulator status separately; compilation does not establish full functionality or aesthetic quality.

### Install native preview

Open [Build Folio Android Preview](../../actions/workflows/android.yml), choose a successful run, and download the `folio-android-preview-apk` artifact. Extract `app-debug.apk` and install it on Android 8+.

This build is a **native prototype**, not a production-signed release. Because debug keys may differ between GitHub workers, avoid installing a new preview over old preview builds without a stable signing configuration and a backup/export.

## Daily editorial feed (no daily APK updates)

- Public JSON data: https://raw.githubusercontent.com/yvichyi/folio/main/docs/feed/daily.json
- Primary source: [docs/feed/daily.json](docs/feed/daily.json)
- Update content only, not Android code, when articles change.
- Daily editorial JSON is checked by `node scripts/validate-content.cjs`.
- GitHub's content CI does not build APKs when daily content is updated.
- Old published items remain accessible **only if explicitly preserved in cloud data or saved in a local client cache**; daily updates do not imply automatic unlimited archives.

For each published article, include real publisher attribution and a link to the original source, an independently written learning passage, Chinese editorial guidance, vocabulary and a discussion question. Never copy or represent protected publisher full text as original.

## Web reading edition (separate client)

The previous web client remains available at https://yvichyi.github.io/folio/ with source under `docs/`. The earlier v0.3 Android shell still shows this website. Web and native clients currently have **separate locally stored learning data**.

## Known limits and next gates

- Native launch, article scrolling, word taps, review navigation and font scaling need hands-on device acceptance; the browser version does not prove native quality.
- First-ever launch offline has no articles until a successful sync; an offline return visit uses the last-good cached issue.
- Reading-aloud depends on an installed Android TTS engine; dictionary definitions depend on external service access except curated vocabulary.
- The native client does not yet support account sync, notifications, automatic APK installation, or reliable cross-version signing.
- A future production distribution should add stable private signing, migration/restore of learning data, and more UI/device accessibility tests.

Folio is designed for personal learning, not to redistribute publisher content. Scientific and medical stories are not individualized advice.
