import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  publicDir: "../public",
  server: {
    host: "0.0.0.0",
    allowedHosts: ["localhost", "terminal.local"],
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("@phosphor-icons")) return "icons";
          if (
            id.includes("react-markdown") ||
            id.includes("remark-") ||
            id.includes("rehype-") ||
            id.includes("unified")
          )
            return "markdown";
          if (
            id.includes("@tanstack") ||
            id.includes("react-router") ||
            id.includes("@headlessui")
          )
            return "application-vendor";
          if (
            id.includes("node_modules/react") ||
            id.includes("node_modules\\react")
          )
            return "react-vendor";
          return undefined;
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    css: true,
  },
});
