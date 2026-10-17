import { z } from "zod";

export const settingsSchema = z.object({
  dailyLimit: z.number().int().min(1).max(500),
  locale: z.enum(["en", "bn"]),
});
