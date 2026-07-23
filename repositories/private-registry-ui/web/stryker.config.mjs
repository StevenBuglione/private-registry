export default {
  $schema: "./node_modules/@stryker-mutator/core/schema/stryker-schema.json",
  mutate: [
    "src/api/client.ts",
    "src/api/catalog.ts",
    "src/features/package-detail/model.ts",
    "src/runtime-config.ts",
    "src/theme.ts",
    "src/utils.ts",
  ],
  plugins: [
    "@stryker-mutator/typescript-checker",
    "@stryker-mutator/vitest-runner",
  ],
  ignorePatterns: [
    ".lighthouseci",
    "coverage",
    "dist",
    "playwright-report",
    "test-results",
  ],
  testRunner: "vitest",
  vitest: {
    configFile: "vite.config.ts",
  },
  checkers: ["typescript"],
  tsconfigFile: "tsconfig.app.json",
  coverageAnalysis: "perTest",
  concurrency: 4,
  reporters: ["clear-text", "progress", "html"],
  thresholds: {
    high: 80,
    low: 65,
    break: 60,
  },
  tempDirName: ".stryker-tmp",
};
