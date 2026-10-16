# ReelCount

ডিজিটাল ওয়েলবিইং: Facebook, Instagram ও YouTube-এ দিনে কতগুলো Reels/Shorts দেখা হয়, সেটা ফোনে গোনে। ভাসমান কাউন্টার শুধু রিল ভিউয়ার খোলা থাকলে দেখা যায়।

Phase 1 (এই রিপো) অ্যান্ড্রয়েড ট্র্যাকার। সিঙ্ক ও ওয়েব ড্যাশবোর্ড পরে।

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
| 2 | Supabase sync, off by default | not started |
| 3 | Next.js dashboard | not started |
| 4 | Browser extension | not started |

Supabase and Vercel env vars are not used yet. Do not put a service-role key in the app.

See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) and [docs/phase-1-proposal.md](docs/phase-1-proposal.md).

## Manual checks

- Scroll about 10 reels in each app. The counter should show 10, give or take 1.
- Leave the viewer and open it again. The next video counts once.
- Force-stop the app, open it, confirm the service status card.
- Cross local midnight. The home total starts again at 0; history keeps yesterday.
- Turn off the network. Counts still save on the phone. Sync does not exist yet.
