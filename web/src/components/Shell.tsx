"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { browserSupabase } from "@/lib/supabase";

export function Shell({ locale, children }: { locale: string; children: React.ReactNode }) {
  const t = useTranslations("nav");
  const path = usePathname();
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const supabase = browserSupabase();
    supabase.auth.getSession().then(({ data }) => {
      if (!data.session) router.replace(`/${locale}/login`);
      else setReady(true);
    });
  }, [locale, router]);

  if (!ready) return <p className="p-6 text-zinc-400">…</p>;

  const item = (href: string, label: string) => (
    <Link
      href={href}
      className={`rounded-full px-3 py-1 text-sm ${path?.startsWith(href) ? "bg-zinc-100 text-zinc-950" : "text-zinc-300"}`}
    >
      {label}
    </Link>
  );

  return (
    <div className="mx-auto flex min-h-screen max-w-lg flex-col px-4 pb-8 pt-4">
      <header className="mb-4 flex items-center justify-between gap-2">
        <nav className="flex gap-1">
          {item(`/${locale}/dashboard`, t("today"))}
          {item(`/${locale}/history`, t("history"))}
          {item(`/${locale}/settings`, t("settings"))}
        </nav>
        <button
          className="text-sm text-zinc-400"
          onClick={async () => {
            await browserSupabase().auth.signOut();
            router.replace(`/${locale}/login`);
          }}
        >
          {t("signOut")}
        </button>
      </header>
      {children}
    </div>
  );
}
