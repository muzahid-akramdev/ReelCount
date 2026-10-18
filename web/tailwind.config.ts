import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      fontFamily: {
        sans: ["var(--font-noto-bn)", "var(--font-noto)", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
