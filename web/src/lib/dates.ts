export function todayKey(now = new Date()): string {
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function addDays(day: string, delta: number): string {
  const [y, m, d] = day.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  date.setDate(date.getDate() + delta);
  return todayKey(date);
}

export function daysBack(count: number, end = todayKey()): string[] {
  const days: string[] = [];
  for (let i = count - 1; i >= 0; i -= 1) days.push(addDays(end, -i));
  return days;
}
