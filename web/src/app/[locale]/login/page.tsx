"use client";

import { useTranslations } from "next-intl";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { browserSupabase } from "@/lib/supabase";

export default function LoginPage() {
  const t = useTranslations("login");
  const router = useRouter();
  const { locale } = useParams<{ locale: string }>();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    const { error: authError } = await browserSupabase().auth.signInWithPassword({ email: email.trim(), password });
    if (authError) {
      setError(authError.message);
      return;
    }
    router.replace(`/${locale}/dashboard`);
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center px-4">
      <h1 className="text-3xl font-semibold">{t("title")}</h1>
      <p className="mt-2 text-sm text-zinc-400">{t("subtitle")}</p>
      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-3">
        <label className="text-sm">
          {t("email")}
          <input
            className="mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label className="text-sm">
          {t("password")}
          <input
            className="mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        {error ? <p className="text-sm text-red-400">{error}</p> : null}
        <button className="rounded-lg bg-emerald-500 px-3 py-2 font-semibold text-zinc-950" type="submit">
          {t("signIn")}
        </button>
      </form>
      <p className="mt-4 text-xs text-zinc-500">{t("needAccount")}</p>
    </main>
  );
}
