"use client";

import { Shell } from "@/components/Shell";
import { hourTotals, streak, sumDays, type DailyRow, type HourlyRow } from "@/lib/aggregate";
import { daysBack, todayKey } from "@/lib/dates";
import { browserSupabase } from "@/lib/supabase";
import { useTranslations } from "next-intl";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Bar, BarChart, ResponsiveContainer, XAxis, YAxis } from "recharts";

const RANGES = [7, 30, 90] as const;

export default function HistoryPage() {
  const { locale } = useParams<{ locale: string }>();
  return (
    <Shell locale={locale}>
      <History />
    </Shell>
  );
}

function History() {
  const t = useTranslations("history");
  const [range, setRange] = useState<(typeof RANGES)[number]>(7);
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState<{ day: string; count: number }[]>([]);
  const [hours, setHours] = useState<number[]>(Array.from({ length: 24 }, () => 0));
  const [best, setBest] = useState<string>("");
  const [worst, setWorst] = useState<string>("");
  const [streakDays, setStreakDays] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    const today = todayKey();
    const from = daysBack(Math.max(range, 14), today)[0];
    setLoading(true);
    let supabase;
    try {
      supabase = browserSupabase();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "config");
      setLoading(false);
      return;
    }
    Promise.all([
      supabase.from("daily_counts").select("day,app,reels_count,est_seconds").gte("day", from),
      supabase.from("hourly_counts").select("day,hour,app,reels_count").gte("day", daysBack(range, today)[0]),
      supabase.from("profiles").select("daily_limit").maybeSingle(),
    ]).then(([daily, hourly, profile]) => {
      const dailyRows = (daily.data ?? []) as DailyRow[];
      const span = new Set(daysBack(range, today));
      const totals = sumDays(dailyRows).filter((row) => span.has(row.day));
      const filled = daysBack(range, today).map((day) => ({
        day: day.slice(5),
        count: totals.find((row) => row.day === day)?.count ?? 0,
      }));
      setRows(filled);
      const ranked = totals.filter((row) => row.count > 0);
      const light = [...ranked].sort((a, b) => a.count - b.count)[0];
      const heavy = [...ranked].sort((a, b) => b.count - a.count)[0];
      setBest(light ? `${light.day} · ${light.count}` : "—");
      setWorst(heavy ? `${heavy.day} · ${heavy.count}` : "—");
      setHours(hourTotals((hourly.data ?? []) as HourlyRow[]));
      setStreakDays(streak(sumDays(dailyRows), profile.data?.daily_limit ?? 50, today));
      setLoading(false);
    });
  }, [range]);

  const maxHour = Math.max(1, ...hours);
  const empty = rows.every((row) => row.count === 0);

  return (
    <main className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <div className="flex gap-2">
        {RANGES.map((value) => (
          <button
            key={value}
            className={`rounded-full px-3 py-1 text-sm ${range === value ? "bg-zinc-100 text-zinc-950" : "bg-zinc-800"}`}
            onClick={() => setRange(value)}
          >
            {t(value === 7 ? "d7" : value === 30 ? "d30" : "d90")}
          </button>
        ))}
      </div>
      {loading ? <div className="h-40 animate-pulse rounded-2xl bg-zinc-800" /> : null}
      {error ? <p className="text-sm text-red-400">{error}</p> : null}
      {!loading && !error && empty ? <p className="text-sm text-zinc-500">{t("empty")}</p> : null}
      {!loading && !error && !empty ? (
        <section className="h-52 rounded-2xl bg-zinc-900 p-2">
          <p className="px-2 text-sm text-zinc-400">{t("bars")}</p>
          <ResponsiveContainer width="100%" height="85%">
            <BarChart data={rows}>
              <XAxis dataKey="day" hide={range > 7} stroke="#a1a1aa" fontSize={11} />
              <YAxis hide />
              <Bar dataKey="count" fill="#22c55e" radius={4} />
            </BarChart>
          </ResponsiveContainer>
        </section>
      ) : null}
      <section>
        <h2 className="mb-2 text-sm text-zinc-400">{t("heat")}</h2>
        <div className="grid grid-cols-6 gap-1">
          {hours.map((count, hour) => (
            <div
              key={hour}
              title={`${hour}: ${count}`}
              className="rounded py-2 text-center text-xs"
              style={{ background: `rgba(34,197,94,${0.12 + 0.88 * (count / maxHour)})` }}
            >
              {hour}
            </div>
          ))}
        </div>
      </section>
      <section className="grid grid-cols-2 gap-2 text-sm">
        <div className="rounded-2xl bg-zinc-900 p-3">
          <p className="text-zinc-400">{t("best")}</p>
          <p>{best}</p>
        </div>
        <div className="rounded-2xl bg-zinc-900 p-3">
          <p className="text-zinc-400">{t("worst")}</p>
          <p>{worst}</p>
        </div>
      </section>
      <p>
        {t("streak")}: {streakDays}
      </p>
    </main>
  );
}
