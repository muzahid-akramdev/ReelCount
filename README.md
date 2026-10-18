# ReelCount

ডিজিটাল ওয়েলবিইং: Facebook, Instagram ও YouTube-এ দিনে কতগুলো Reels/Shorts দেখা হয়, সেটা ফোনে গোনে। ভাসমান কাউন্টার শুধু রিল ভিউয়ার খোলা থাকলে দেখা যায়।

Phase 1 অ্যান্ড্রয়েড ট্র্যাকার। Phase 2-এ ঐচ্ছিক Supabase সিঙ্ক (ডিফল্টে বন্ধ)। Phase 3 ওয়েব ড্যাশবোর্ড `web/`-এ।

## এখন কী করতে হবে

1. GitHub-এ Actions ট্যাব খুলুন। `Android debug APK` সবুজ হলে নিচের Artifacts থেকে `reelcount-debug-apk` ডাউনলোড করুন।
2. APK ফোনে ইনস্টল করুন। Android 13 বা নতুন হলে: অ্যাপের তথ্য → তিন বিন্দু → **Allow restricted settings**।
3. অ্যাপের প্রথম স্ক্রিন পড়ুন। Accessibility-তে ReelCount চালু করুন। ব্যাটারি অপটিমাইজেশন থেকে বাদ দিন।
4. Instagram/YouTube/Facebook-এ কয়েকটা রিল সোয়াইপ করুন। উপরে মাঝখানে 🧠 আর সংখ্যা আসা উচিত।

সিড রুল ভুল হলে: Settings → Debug inspector → Copy → `rules.json` ঠিক করে Import। রিবিল্ড লাগে না।

## Build on your machine

Needs JDK 17. Android Studio is optional.

```bash
cd android
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:ktlintCheck
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

`compileSdk` / `targetSdk` are 37 (`platforms;android-37.0`). AGP 9.4.1, Gradle 9.6.1.

GitHub Actions (`.github/workflows/android-debug-apk.yml`) runs the same build and uploads the APK.

## What is stored

Room tables `events(id, ts, app)` and `daily_stats(day, app, count, est_seconds)`. Day keys use the device time zone. Settings live in DataStore. Captions, usernames and URLs are not stored. The inspector keeps view ids, class names and content descriptions in memory only.

## Later phases

| Phase | What | Status |
|---|---|---|
| 2 | Supabase sync, off by default | Android client in this repo |
| 3 | Next.js dashboard | in `web/` |
| 4 | Browser extension | not started |

## Phase 2 — Supabase

Schema: [supabase/schema.sql](supabase/schema.sql). Run it once in the SQL editor if the tables are not there yet. Email auth must be enabled. RLS is `auth.uid() = user_id`.

Copy [android/local.properties.example](android/local.properties.example) to `android/local.properties` and set `SUPABASE_URL` plus the **anon** key. Do not put the service-role key in the app or in git. `local.properties` is gitignored. A build without those values still works; sync then says the keys are missing.

In the app: Settings → Cloud sync. Sign in, turn sync on (off by default). Counts stay in Room. Sync upserts absolute daily and hourly totals for this `device_id`, so repeating a sync does not double the numbers. WorkManager retries about every 6 hours when the network is up, and the app also tries once on open.

GitHub Actions can bake the public values in if the repository secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY` exist. They are optional.

## Phase 3 — web dashboard

Next.js app in [web/](web/). Same email as the Android app. Pages: `/login`, `/dashboard` (today, limit ring, per-app donut, watch time, weekly sentence), `/history` (7/30/90 days, hour heatmap, lightest/heaviest day, streak), `/settings` (limit, bn/en, CSV, delete cloud rows).

```bash
cd web
cp .env.example .env.local
npm install
npm run dev
```

Set `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` only. The service-role key is for `npm run seed`, never a `NEXT_PUBLIC_` variable. On Vercel, set the project root to `web`. Details: [web/README.md](web/README.md).

See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) and [docs/phase-1-proposal.md](docs/phase-1-proposal.md).

## Manual checks

- Scroll about 10 reels in each app. The counter should show 10, give or take 1.
- Leave the viewer and open it again. The next video counts once.
- Force-stop the app, open it, confirm the service status card.
- Cross local midnight. The home total starts again at 0; history keeps yesterday.
- Turn off the network. Counts still save on the phone. Turn the network back on and open the app; the dashboard shows the same totals after sync.
