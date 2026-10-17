import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      fontFamily: {
        sans: ["Noto Sans Bengali", "Noto Sans", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
