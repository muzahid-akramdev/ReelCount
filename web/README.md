# ReelCount web

Next.js dashboard for the counts the Android app syncs. Bengali and English. The anon key is public; the service-role key is only for `npm run seed` and must stay out of the browser.

## Local

```bash
cd web
cp .env.example .env.local
# set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY
npm install
npm run dev
```

Open http://localhost:3000 . Sign in with the same email as the Android app. Sync must be on in the app or the dashboard stays empty.

## Vercel

Set the project root to `web`. Environment variables:

| Name | Where |
|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | client |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | client |
| `SUPABASE_SERVICE_ROLE_KEY` | seed script only, never `NEXT_PUBLIC_` |

## Demo data

```bash
SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... SEED_USER_ID=<auth user uuid> npm run seed
```

## Checks

RLS is `auth.uid() = user_id`. Settings input is checked with zod (limit 1–500, locale en or bn).
