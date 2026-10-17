"use client";

import { Shell } from "@/components/Shell";
import { appLabel, APP_COLORS } from "@/lib/apps";
import { byApp, sumDays, weekInsight, type DailyRow } from "@/lib/aggregate";
import { addDays, todayKey } from "@/lib/dates";
import { browserSupabase } from "@/lib/supabase";
import { useTranslations } from "next-intl";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Cell, Pie, PieChart, ResponsiveContainer } from "recharts";

export default function DashboardPage() {
  const { locale } = useParams<{ locale: string }>();
  return (
    <Shell locale={locale}>
      <Dashboard />
    </Shell>
  );
}

function Dashboard() {
  const t = useTranslations("dash");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [todayCount, setTodayCount] = useState(0);
  const [seconds, setSeconds] = useState(0);
  const [limit, setLimit] = useState(50);
  const [apps, setApps] = useState<{ app: string; count: number }[]>([]);
  const [insight, setInsight] = useState<{ kind: "none" | "same" | "fewer" | "more"; pct: number }>({ kind: "none", pct: 0 });

  useEffect(() => {
    const supabase = browserSupabase();
    const today = todayKey();
    const from = addDays(today, -14);
    Promise.all([
      supabase.from("daily_counts").select("day,app,reels_count,est_seconds").gte("day", from),
      supabase.from("profiles").select("daily_limit").maybeSingle(),
    ]).then(([counts, profile]) => {
      if (counts.error) {
        setError(counts.error.message);
        setLoading(false);
        return;
      }
      const rows = (counts.data ?? []) as DailyRow[];
      const todayRows = rows.filter((row) => row.day === today);
      setTodayCount(todayRows.reduce((n, row) => n + row.reels_count, 0));
      setSeconds(todayRows.reduce((n, row) => n + row.est_seconds, 0));
      setApps(byApp(todayRows));
      setLimit(profile.data?.daily_limit ?? 50);
      setInsight(weekInsight(sumDays(rows), today));
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="h-40 animate-pulse rounded-2xl bg-zinc-800" />;
  if (error) return <p className="text-red-400">{error}</p>;

  const ratio = Math.min(1, todayCount / Math.max(1, limit));
  const minutes = Math.round(seconds / 60);

  return (
    <main className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <section className="flex items-center gap-4 rounded-2xl bg-zinc-900 p-4">
        <Ring ratio={ratio} label={String(todayCount)} />
        <div>
          <p className="text-sm text-zinc-400">{t("ofLimit", { limit })}</p>
          <p className="mt-2 text-sm text-zinc-400">{t("watch")}</p>
          <p>{minutes < 1 ? t("under") : t("minutes", { n: minutes })}</p>
        </div>
      </section>
      <section className="rounded-2xl bg-zinc-900 p-4">
        <h2 className="mb-2 text-sm text-zinc-400">{t("byApp")}</h2>
        {apps.length === 0 ? (
          <p className="text-sm text-zinc-500">{t("empty")}</p>
        ) : (
          <div className="h-48">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={apps} dataKey="count" nameKey="app" innerRadius={48} outerRadius={72}>
                  {apps.map((row, index) => (
                    <Cell key={row.app} fill={APP_COLORS[index % APP_COLORS.length]} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
        )}
        <ul className="mt-2 text-sm">
          {apps.map((row) => (
            <li key={row.app} className="flex justify-between">
              <span>{appLabel(row.app)}</span>
              <span>{row.count}</span>
            </li>
          ))}
        </ul>
      </section>
      <p className="text-sm text-zinc-300">
        {t("week")}: {insight.kind === "none" ? t("noPrior") : insight.kind === "same" ? t("same") : t(insight.kind, { n: insight.pct })}
      </p>
    </main>
  );
}

function Ring({ ratio, label }: { ratio: number; label: string }) {
  const r = 36;
  const c = 2 * Math.PI * r;
  const color = ratio < 0.5 ? "#22c55e" : ratio < 1 ? "#eab308" : "#ef4444";
  return (
    <svg width="96" height="96" viewBox="0 0 96 96" aria-hidden>
      <circle cx="48" cy="48" r={r} stroke="#3f3f46" strokeWidth="8" fill="none" />
      <circle
        cx="48"
        cy="48"
        r={r}
        stroke={color}
        strokeWidth="8"
        fill="none"
        strokeDasharray={`${c * ratio} ${c}`}
        strokeLinecap="round"
        transform="rotate(-90 48 48)"
      />
      <text x="48" y="53" textAnchor="middle" fill="white" fontSize="18">
        {label}
      </text>
    </svg>
  );
}
