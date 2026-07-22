import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { configDefaults, defineConfig } from "vitest/config";

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
    exclude: [...configDefaults.exclude, ".stryker-tmp/**", "e2e/**"],
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    css: true,
    coverage: {
      provider: "istanbul",
      reporter: ["text", "json", "html", "lcov"],
      reportsDirectory: "coverage",
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "src/**/*.test.{ts,tsx}",
        "src/main.tsx",
        "src/test/**",
        "src/vite-env.d.ts",
      ],
      thresholds: {
        statements: 80,
        branches: 70,
        functions: 80,
        lines: 80,
      },
    },
  },
});
