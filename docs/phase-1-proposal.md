# Phase 1 প্রস্তাব — ফোল্ডার স্ট্রাকচার ও rules.json স্কিমা

এই ডক অ্যাপ কোড নয়। আপনি অ্যাপ্রুভ করলে এই অনুযায়ী Android ট্র্যাকার লেখা শুরু হবে।

This document is the Phase 1 proposal only. No app code until you approve it.

## স্পষ্ট অনুমান (নীরবে অনুমান করা হয়নি)

1. অ্যাপলিকেশন আইডি `dev.akram.reelcount`। বদলালে বলুন।
2. DI ফ্রেমওয়ার্ক না (Hilt নয়)। ম্যানুয়াল কম্পোজিশন, যেন বিল্ড সরল ও হেডলেস থাকে।
3. `minSdk 26`। `compileSdk` / `targetSdk` = বর্তমান স্টেবল API **36**। কোড লেখার সময় স্টেবল AGP 9.x + সেই AGP-এর সাথে মেল করা Kotlin পিন করা হবে। JDK 17।
4. ডিফল্ট দৈনিক লিমিট **50**। নাজ স্নুজ ডিফল্ট **60 মিনিট**, আর “আজকের বাকি দিন” অপশন।
5. ভাষা: সিস্টেম ফলো, যতক্ষণ না Settings-এ bn/en বেছে নেওয়া হয়। স্ট্রিং `values/` + `values-bn/`।
6. ভিডিও চেনা যাবে না URL বা ক্যাপশন দিয়ে। প্রাইভেসি: টেক্সট, ক্যাপশন, ইউজারনেম, URL কোথাও সেভ বা লগ হবে না।
7. কোন ভিডিও বদলেছে তা দুইভাবে: (ক) pager-এর index থাকলে সেটা, (খ) না থাকলে view-id + class + মোটা বাউন্ডসের স্ট্রাকচারাল হ্যাশ। content-description শুধু রুল মিলিয়ে দেখতে মেমরিতে ব্যবহার, Room-এ নয়।
8. index না থাকলে: নতুন হ্যাশ যদি ২ সেকেন্ডের মধ্যে দুই ধাপ আগের হ্যাশের সমান হয়, সেটাকে ব্যাকওয়ার্ড ধরা হবে; নইলে ফরওয়ার্ড। Settings-এর টগল ব্যাকওয়ার্ড কাউন্ট বন্ধ রাখে (ডিফল্ট বন্ধ)।
9. 600ms ডিবাউন্স = শেষ কাউন্টের 600ms-এর মধ্যে নতুন কাউন্ট নেই। খুব দ্রুত ডবল সোয়াইপ ১টি কম দেখাতে পারে। আপনার টেস্ট সহনশীলতা +/-1।
10. আনুমানিক দেখার সময় = এক সেশনে পরপর দুই কাউন্টের ব্যবধান, প্রতি গ্যাপ সর্বোচ্চ 180 সেকেন্ড। ফোন খোলা রেখে চলে গেলে সময় ফুলে উঠবে না।
11. দিনের সীমা ডিভাইসের টাইমজোন (`ZoneId.systemDefault()`), দিনের কী `yyyy-MM-dd`।
12. প্যাকেজ ফিল্টার রানটাইমে `AccessibilityService.setServiceInfo` দিয়ে। পুরো ফোনের ইভেন্ট শোনা হবে না।
13. ডিবাগ ইনস্পেক্টরের content-description শুধু মেমরির রিং বাফার (শেষ ~৫০ ইভেন্ট)। প্রসেস মরলে মুছে যায়। কপি বাটন আছে, ডিস্কে অটোসেভ নেই।
14. বান্ডল করা view-id সিড মাত্র। Instagram/Facebook/YouTube আপডেটে ভাঙবে। ঠিক করার পথ: ইনস্পেক্টর → rules ইমপোর্ট। রিবিল্ড লাগবে না।
15. `Delete all data` মুছে দেবে events, daily_stats। Settings ও ইমপোর্ট করা রুল আলাদা কনফার্ম ছাড়া থাকবে।
16. Xiaomi/Oppo/Vivo-র অটোস্টার্ট অটোমেট করা যায় না — অনবোর্ডিংয়ে লিখিত ধাপ থাকবে।
17. Phase 1-এ `web/` ও `extension/` শুধু এক লাইনের README। Supabase কোড নেই। সিঙ্ক ডিফল্টে বন্ধ — সেটি Phase 2।
18. GitHub Actions শুধু debug APK আর্টিফ্যাক্ট। Play Store লিস্টিং এই ফেজে নেই।

## ফোল্ডার স্ট্রাকচার

```text
ReelCount/
├── README.md
├── KNOWN_LIMITATIONS.md
├── .gitignore
├── .github/workflows/android-debug-apk.yml
├── docs/
│   ├── phase-1-proposal.md
│   └── rules.schema.json
├── android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/wrapper/
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── assets/rules.json
│           │   ├── res/values/strings.xml
│           │   ├── res/values-bn/strings.xml
│           │   ├── res/xml/accessibility_service_config.xml
│           │   └── kotlin/dev/akram/reelcount/
│           │       ├── ReelCountApp.kt
│           │       ├── MainActivity.kt
│           │       ├── accessibility/
│           │       │   ├── ReelCounterService.kt
│           │       │   ├── RuleEngine.kt
│           │       │   ├── ViewerSession.kt
│           │       │   └── NodeSnapshot.kt
│           │       ├── overlay/CounterOverlay.kt
│           │       ├── data/
│           │       │   ├── db/          # Room: events, daily_stats
│           │       │   ├── settings/    # DataStore
│           │       │   └── rules/       # assets + imported override
│           │       ├── domain/
│           │       │   ├── CountPolicy.kt
│           │       │   └── DayBoundary.kt
│           │       ├── nudge/NudgeController.kt
│           │       └── ui/
│           │           ├── theme/
│           │           ├── home/
│           │           ├── history/
│           │           ├── settings/
│           │           ├── onboarding/
│           │           └── debug/
│           └── test/                    # JVM: CountPolicy, RuleEngine, DayBoundary
├── web/README.md                        # Phase 3 stub
└── extension/README.md                  # Phase 4 stub
```

### কেন এভাবে

- `domain/` অ্যান্ড্রয়েড ফ্রেমওয়ার্ক ছাড়া। কাউন্ট ও দিনের হিসাব ইউনিট টেস্ট হয়, ডিভাইস ছাড়া।
- রুল ইঞ্জিন আলাদা, যাতে ইনস্পেক্টরের নমুনা JSON দিয়ে ম্যাচ পরীক্ষা করা যায়।
- ইমপোর্ট করা `filesDir/rules.json` অ্যাসেটের ওপর জেতে। অ্যাপ আপডেটে অ্যাসেট বদলালেও আপনার ইমপোর্ট থাকে, যতক্ষণ `schemaVersion` মেলে।
- ওভারলে `TYPE_ACCESSIBILITY_OVERLAY`। আলাদা “Display over other apps” পারমিশন চাওয়া হবে না।
- Room-এ শুধু `events(id, ts, packageName)` ও `daily_stats(day, packageName, count, estSeconds)`। PK: `(day, packageName)`।

### স্ক্রিন (Compose, Material 3)

| স্ক্রিন | কাজ |
|---|---|
| Onboarding | কী সংগ্রহ হয় / হয় না; Accessibility ডিপ লিংক; Android 13+ restricted settings-এর ধাপ; ব্যাটারি অপটিমাইজেশন বাদ দেওয়ার প্রম্পট |
| Home | আজকের মোট, অ্যাপওয়ারি ভাঙন, লিমিটের প্রোগ্রেস, সার্ভিস চলছে কি না |
| History | ৭/৩০ দিনের বার, ঘণ্টার হিটম্যাপ, লিমিটের নিচে থাকার স্ট্রিক |
| Settings | লিমিট, নাজ, ওভারলে, অ্যাপ তালিকা, ভাষা, রুল ইমপোর্ট, CSV এক্সপোর্ট, ডেটা মুছা, ব্যাকওয়ার্ড সোয়াইপ টগল |
| Debug | সাম্প্রতিক অ্যাক্সেসিবিলিটি ইভেন্ট + নোড ট্রি (view id, class, content description; text নয়), কপি |

ভাসমান পিল: উপরে মাঝখানে, ব্রেইন ইমোজি + সংখ্যা, টেনে সরানো যায়, জায়গা মনে থাকে। শুধু রিল ভিউয়ার খোলা থাকলে দেখা যায়। ৫০%-এর নিচে সবুজ, লিমিটের আগে অ্যাম্বার, লিমিটে/পরে লাল। নাজ: ৫০%, ১০০%, তারপর প্রতি +২৫।

## rules.json স্কিমা (সংক্ষেপ)

পূর্ণ JSON Schema: [rules.schema.json](rules.schema.json)।

মূল ধারণা:

- `schemaVersion` (এখন 1)। ইমপোর্টে মিল না থাকলে ফাইল বাতিল, পুরোনো রুল থাকে।
- `global`: debounceMs (600), countOnViewerEnter (true), countBackwardSwipes (false)। Settings-এর টগল এই শেষ দুটিকে ওভাররাইড করতে পারে।
- `apps[]`: প্রতি প্যাকেজ এক রুল।
  - `viewer.signals[]`: viewId / className / contentDescription, অপারেশন equals|contains|regex, স্কোর।
  - `viewer.minScore`: এই স্কোর না হলে ভিউয়ার বন্ধ ধরা।
  - `viewer.blockers[]`: মিললে ভিউয়ার নয় (যেমন ইনবক্স)।
  - `identity.extractors[]`: আগে pagerIndex (CollectionItemInfo), না পেলে structuralHash। হ্যাশে টেক্সট বা content-description ঢোকে না।
- প্যাটার্নগুলো ইমপোর্টযোগ্য। অ্যাপ রিবিল্ড লাগে না।

ডিফল্ট প্যাকেজ:

- `com.facebook.katana`
- `com.facebook.lite`
- `com.instagram.android`
- `com.google.android.youtube`

সিড view-id (ইনস্পেক্টরে মিলিয়ে ঠিক করতে হবে, এগুলো গ্যারান্টি নয়):

- Instagram: `clips_viewer`, `clips_viewer_view_pager`
- YouTube: `reel_player`, `reel_recycler`, shorts পাতা
- Facebook / Lite: নাম প্রায়ই অস্পষ্ট; সিড দুর্বল, ইনস্পেক্টরই আসল উৎস

### উদাহরণ (সংক্ষিপ্ত)

```json
{
  "schemaVersion": 1,
  "global": {
    "debounceMs": 600,
    "countOnViewerEnter": true,
    "countBackwardSwipes": false
  },
  "apps": [
    {
      "id": "instagram",
      "packageName": "com.instagram.android",
      "enabled": true,
      "viewer": {
        "minScore": 2,
        "signals": [
          {
            "source": "viewId",
            "op": "regex",
            "value": "(?i)clips_viewer|reel_viewer",
            "score": 3
          }
        ],
        "blockers": [
          { "source": "viewId", "op": "contains", "value": "direct_inbox" }
        ]
      },
      "identity": {
        "extractors": [
          {
            "kind": "pagerIndex",
            "source": "viewId",
            "op": "regex",
            "value": "(?i)view_pager|clips_viewer"
          },
          {
            "kind": "structuralHash",
            "include": ["viewId", "className", "boundsBucket"]
          }
        ]
      }
    }
  ]
}
```

## Phase 1 শেষ হলে কীভাবে যাচাই (কোডের পর)

কোড লেখার পর আলাদা সারাংশ দেব। সংক্ষেপে পরিকল্পনা:

- GitHub Actions থেকে debug APK ডাউনলোড।
- ফোনে সাইডলোড, restricted settings অনুমতি, Accessibility চালু, ব্যাটারি সাদা তালিকা।
- প্রতি অ্যাপে ~১০টা রিল: কাউন্টার ১০ (±১)।
- বেরিয়ে আবার ঢোকা, ফোর্স-স্টপ, স্থানীয় মধ্যরাত, ব্যাক সোয়াইপ।
- ইনস্পেক্টর দিয়ে একটা ভাঙা রুল ইমপোর্ট করে দেখা যে রিবিল্ড লাগে না।

## আপনার সিদ্ধান্ত দরকার

উত্তরে লিখুন:

1. এই স্ট্রাকচার ও স্কিমায় **ওকে**, নাকি বদল চান।
2. অ্যাপ আইডি `dev.akram.reelcount` রাখব, নাকি অন্য প্যাকেজ।
3. ডিফল্ট দৈনিক লিমিট ৫০-ই রাখব?
