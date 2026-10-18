import type { Metadata, Viewport } from "next";
import { Noto_Sans, Noto_Sans_Bengali } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { PwaRegister } from "@/components/PwaRegister";
import "../globals.css";

const noto = Noto_Sans({ subsets: ["latin"], weight: ["400", "600", "700"], variable: "--font-noto" });
const notoBn = Noto_Sans_Bengali({
  subsets: ["bengali"],
  weight: ["400", "600", "700"],
  variable: "--font-noto-bn",
});

export const metadata: Metadata = {
  title: "ReelCount",
  description: "How many Reels and Shorts you watched.",
  manifest: "/manifest.json",
};

export const viewport: Viewport = { themeColor: "#09090b", width: "device-width", initialScale: 1 };

export function generateStaticParams() {
  return [{ locale: "en" }, { locale: "bn" }];
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (locale !== "en" && locale !== "bn") notFound();
  setRequestLocale(locale);
  const messages = await getMessages();
  return (
    <html lang={locale} className={`dark ${noto.variable} ${notoBn.variable}`}>
      <body className="min-h-screen font-sans">
        <PwaRegister />
        <NextIntlClientProvider messages={messages}>{children}</NextIntlClientProvider>
      </body>
    </html>
  );
}
