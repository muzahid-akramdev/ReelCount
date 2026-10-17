/**
 * Inserts demo rows for an existing auth user so the dashboard can be previewed
 * without the Android app. Requires SUPABASE_SERVICE_ROLE_KEY in the environment.
 * Do not import this key into the Next.js client.
 *
 *   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... SEED_USER_ID=<uuid> npm run seed
 */
import { createClient } from "@supabase/supabase-js";

const url = process.env.SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
const userId = process.env.SEED_USER_ID;
if (!url || !key || !userId) {
  console.error("Need SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, and SEED_USER_ID");
  process.exit(1);
}

const supabase = createClient(url, key, { auth: { persistSession: false } });
const device = "demo";
const apps = ["com.instagram.android", "com.google.android.youtube", "com.facebook.katana"];

function dayOffset(n) {
  const date = new Date();
  date.setDate(date.getDate() - n);
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${m}-${d}`;
}

const daily = [];
const hourly = [];
for (let ago = 0; ago < 21; ago += 1) {
  const day = dayOffset(ago);
  apps.forEach((app, index) => {
    const count = (ago * 3 + index * 5) % 28;
    if (count === 0) return;
    daily.push({
      user_id: userId,
      device_id: device,
      day,
      app,
      reels_count: count,
      est_seconds: count * 12,
    });
    hourly.push({
      user_id: userId,
      device_id: device,
      day,
      hour: (18 + index) % 24,
      app,
      reels_count: count,
    });
  });
}

const { error: profileError } = await supabase.from("profiles").upsert({
  id: userId,
  daily_limit: 50,
  locale: "bn",
});
if (profileError) throw profileError;
const { error: dailyError } = await supabase.from("daily_counts").upsert(daily);
if (dailyError) throw dailyError;
const { error: hourlyError } = await supabase.from("hourly_counts").upsert(hourly);
if (hourlyError) throw hourlyError;
console.log(`Seeded ${daily.length} daily rows for ${userId}`);
