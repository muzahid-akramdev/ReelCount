export type DailyRow = {
  day: string;
  app: string;
  reels_count: number;
  est_seconds: number;
};

export type HourlyRow = {
  day: string;
  hour: number;
  app: string;
  reels_count: number;
};

export type DayTotal = { day: string; count: number; seconds: number };

export function sumDays(rows: DailyRow[]): DayTotal[] {
  const map = new Map<string, DayTotal>();
  for (const row of rows) {
    const current = map.get(row.day) ?? { day: row.day, count: 0, seconds: 0 };
    current.count += row.reels_count;
    current.seconds += row.est_seconds;
    map.set(row.day, current);
  }
  return [...map.values()].sort((a, b) => a.day.localeCompare(b.day));
}

export function byApp(rows: DailyRow[]): { app: string; count: number }[] {
  const map = new Map<string, number>();
  for (const row of rows) map.set(row.app, (map.get(row.app) ?? 0) + row.reels_count);
  return [...map.entries()]
    .map(([app, count]) => ({ app, count }))
    .sort((a, b) => b.count - a.count);
}

export function weekInsight(totals: DayTotal[], today: string): { kind: "none" | "same" | "fewer" | "more"; pct: number } {
  const thisWeek = totals.filter((row) => row.day > add(today, -7) && row.day <= today);
  const lastWeek = totals.filter((row) => row.day > add(today, -14) && row.day <= add(today, -7));
  const a = thisWeek.reduce((n, row) => n + row.count, 0);
  const b = lastWeek.reduce((n, row) => n + row.count, 0);
  if (b === 0) return { kind: "none", pct: 0 };
  const delta = Math.round((Math.abs(a - b) / b) * 100);
  if (delta < 5) return { kind: "same", pct: delta };
  return { kind: a < b ? "fewer" : "more", pct: delta };
}

function add(day: string, delta: number): string {
  const [y, m, d] = day.split("-").map(Number);
  const date = new Date(y, (m ?? 1) - 1, d ?? 1);
  date.setDate(date.getDate() + delta);
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${mm}-${dd}`;
}

export function streak(totals: DayTotal[], limit: number, today: string): number {
  const map = new Map(totals.map((row) => [row.day, row.count]));
  let n = 0;
  let cursor = today;
  while ((map.get(cursor) ?? 0) <= limit) {
    if (!map.has(cursor) && cursor !== today) break;
    n += 1;
    cursor = add(cursor, -1);
    if (n > 400) break;
  }
  return n;
}

export function hourTotals(rows: HourlyRow[]): number[] {
  const hours = Array.from({ length: 24 }, () => 0);
  for (const row of rows) {
    if (row.hour >= 0 && row.hour < 24) hours[row.hour] += row.reels_count;
  }
  return hours;
}
