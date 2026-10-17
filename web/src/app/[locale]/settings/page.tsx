"use client";

import { Shell } from "@/components/Shell";
import { settingsSchema } from "@/lib/schemas";
import { browserSupabase } from "@/lib/supabase";
import { useLocale, useTranslations } from "next-intl";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function SettingsPage() {
  const { locale } = useParams<{ locale: string }>();
  return (
    <Shell locale={locale}>
      <Settings />
    </Shell>
  );
}

function Settings() {
  const t = useTranslations("settings");
  const locale = useLocale();
  const router = useRouter();
  const [limit, setLimit] = useState("50");
  const [lang, setLang] = useState(locale === "bn" ? "bn" : "en");
  const [notice, setNotice] = useState("");
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    browserSupabase()
      .from("profiles")
      .select("daily_limit,locale")
      .maybeSingle()
      .then(({ data }) => {
        if (!data) return;
        setLimit(String(data.daily_limit));
        if (data.locale === "bn" || data.locale === "en") setLang(data.locale);
      });
  }, []);

  async function save() {
    const parsed = settingsSchema.safeParse({ dailyLimit: Number(limit), locale: lang });
    if (!parsed.success) {
      setNotice(parsed.error.issues[0]?.message ?? "");
      return;
    }
    const supabase = browserSupabase();
    const { data } = await supabase.auth.getUser();
    if (!data.user) return;
    const { error } = await supabase
      .from("profiles")
      .update({ daily_limit: parsed.data.dailyLimit, locale: parsed.data.locale })
      .eq("id", data.user.id);
    if (error) {
      setNotice(error.message);
      return;
    }
    setNotice(t("saved"));
    if (parsed.data.locale !== locale) router.push(`/${parsed.data.locale}/settings`);
  }

  async function exportCsv() {
    const { data, error } = await browserSupabase()
      .from("daily_counts")
      .select("day,app,reels_count,est_seconds,device_id")
      .order("day");
    if (error || !data) {
      setNotice(error?.message ?? "");
      return;
    }
    const lines = ["day,app,reels_count,est_seconds,device_id"];
    for (const row of data) {
      lines.push([row.day, row.app, row.reels_count, row.est_seconds, row.device_id].join(","));
    }
    const blob = new Blob([lines.join("\n")], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "reelcount.csv";
    anchor.click();
    URL.revokeObjectURL(url);
  }

  async function wipe() {
    const supabase = browserSupabase();
    const { data } = await supabase.auth.getUser();
    if (!data.user) return;
    const id = data.user.id;
    const daily = await supabase.from("daily_counts").delete().eq("user_id", id);
    const hourly = await supabase.from("hourly_counts").delete().eq("user_id", id);
    setConfirm(false);
    setNotice(daily.error?.message || hourly.error?.message || t("deleted"));
  }

  return (
    <main className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <label className="text-sm">
        {t("limit")}
        <input
          className="mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2"
          inputMode="numeric"
          value={limit}
          onChange={(event) => setLimit(event.target.value.replace(/\D/g, "").slice(0, 3))}
        />
      </label>
      <label className="text-sm">
        {t("language")}
        <select
          className="mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2"
          value={lang}
          onChange={(event) => setLang(event.target.value)}
        >
          <option value="en">English</option>
          <option value="bn">বাংলা</option>
        </select>
      </label>
      <button className="rounded-lg bg-emerald-500 px-3 py-2 font-semibold text-zinc-950" onClick={save}>
        {t("save")}
      </button>
      <button className="rounded-lg bg-zinc-800 px-3 py-2" onClick={exportCsv}>
        {t("export")}
      </button>
      <button className="rounded-lg bg-red-950 px-3 py-2 text-red-200" onClick={() => setConfirm(true)}>
        {t("delete")}
      </button>
      {notice ? <p className="text-sm text-zinc-300">{notice}</p> : null}
      {confirm ? (
        <div className="rounded-2xl bg-zinc-900 p-4">
          <p className="text-sm">{t("deleteBody")}</p>
          <div className="mt-3 flex gap-2">
            <button className="rounded-lg bg-red-600 px-3 py-2" onClick={wipe}>
              {t("confirm")}
            </button>
            <button className="rounded-lg bg-zinc-800 px-3 py-2" onClick={() => setConfirm(false)}>
              {t("cancel")}
            </button>
          </div>
        </div>
      ) : null}
    </main>
  );
}
