# Known limitations

## বাংলা

- Facebook, Instagram বা YouTube আপডেট দিলে ভিউ আইডি বদলাতে পারে। তখন কাউন্টার থেমে যাবে বা ভুল গুনবে। ঠিক করার উপায়: ডিবাগ ইনস্পেক্টর থেকে আইডি কপি করে Settings-এ নতুন `rules.json` ইমপোর্ট। অ্যাপ আবার বিল্ড করতে হয় না।
- বান্ডল করা রুল শুধু শুরুর অনুমান। ফোনে মিলিয়ে নেওয়া দরকার।
- Facebook Lite-এ হোম ফিড গোনা হয় না। ভিডিও/রিলস ট্যাব বা পুরো স্ক্রিনের প্লেয়ার গোনা হয়। ট্যাবের নাম বদলালে ডিবাগ ইনস্পেক্টর থেকে রুল ইমপোর্ট করতে হবে।
- iOS-এ এই অ্যাপ চলবে না। অন্য অ্যাপের ওপর গোনা বা আঁকা iOS দেয় না।
- Google Play-তে প্রকাশের আগে Accessibility নীতির রিভিউ লাগবে। সাইডলোড করে নিজের ফোনে চালানো আলাদা বিষয়।
- খুব দ্রুত দুইবার সোয়াইপ করলে একটা কম গুনতে পারে।
- আনুমানিক দেখার সময় দুই গণনার ব্যবধান, প্রতিটা সর্বোচ্চ ১৮০ সেকেন্ড।
- Xiaomi, Oppo, Vivo-র Autostart অ্যাপ নিজে চালু করতে পারে না।
- সিঙ্ক ডিফল্টে বন্ধ। চালু করলে শুধু কাউন্ট ও সময় Supabase-এ যায়, ক্যাপশন যায় না। ওয়েব ড্যাশবোর্ড সেই সারিগুলো দেখায়। ব্রাউজার এক্সটেনশন এখনো নেই।

## English

- Detection breaks when Facebook, Instagram or YouTube change their view ids. Fix it from the debug inspector and import a new `rules.json`. No rebuild.
- Bundled rules are best-effort seeds, not a guarantee.
- Facebook Lite counts the Video/Reels tab and a fullscreen player, not the home feed. If a Lite update renames that tab, import a new rules file from the debug inspector.
- iOS is unsupported. A third-party app cannot observe or draw over other apps there.
- Google Play's Accessibility policy review is required before publishing. Sideloading for personal use is a separate step.
- A very fast double swipe can under-count by one. The manual test allows ±1.
- Estimated watch time is the gap between counts, capped at 180 seconds each.
- OEM autostart (Xiaomi, Oppo, Vivo) cannot be granted by the app.
- Sync is off until you turn it on. It uploads counts and estimated time only, not captions. The web dashboard reads those rows. The browser extension is not built.
