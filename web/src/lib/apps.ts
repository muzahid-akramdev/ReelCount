export const APP_LABELS: Record<string, string> = {
  "com.instagram.android": "Instagram",
  "com.google.android.youtube": "YouTube",
  "com.facebook.katana": "Facebook",
  "com.facebook.lite": "Facebook Lite",
  browser: "Browser",
};

export function appLabel(id: string): string {
  return APP_LABELS[id] ?? id;
}

export const APP_COLORS = ["#22c55e", "#eab308", "#ef4444", "#38bdf8", "#a78bfa", "#fb7185"];
