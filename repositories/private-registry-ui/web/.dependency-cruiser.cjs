/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: "no-circular-dependencies",
      severity: "error",
      from: {},
      to: { circular: true },
    },
    {
      name: "no-unresolvable-dependencies",
      severity: "error",
      from: {},
      to: { couldNotResolve: true },
    },
    {
      name: "production-does-not-import-tests",
      severity: "error",
      from: { pathNot: "\\.(?:test|spec)\\.[cm]?[jt]sx?$|^src/test/" },
      to: { path: "\\.(?:test|spec)\\.[cm]?[jt]sx?$|^src/test/" },
    },
    {
      name: "components-do-not-import-routes",
      severity: "error",
      from: { path: "^src/components/" },
      to: { path: "^src/routes/" },
    },
    {
      name: "hooks-do-not-import-presentation",
      severity: "error",
      from: { path: "^src/hooks\\.ts$" },
      to: { path: "^src/(?:components|routes)/" },
    },
    {
      name: "api-adapter-stays-independent",
      severity: "error",
      from: { path: "^src/(?:api|runtime-config|types|utils)\\.ts$" },
      to: { path: "^src/(?:components|routes|hooks|router)" },
    },
    {
      name: "production-does-not-import-generated-output",
      severity: "error",
      from: { path: "^src/" },
      to: {
        path: "^(?:coverage|dist|playwright-report|reports|test-results)/",
      },
    },
  ],
  options: {
    doNotFollow: { path: "node_modules" },
    exclude:
      "(?:^|/)(?:coverage|dist|node_modules|playwright-report|reports|test-results)(?:/|$)",
    tsConfig: { fileName: "tsconfig.app.json" },
    enhancedResolveOptions: {
      exportsFields: ["exports"],
      conditionNames: ["types", "import", "default"],
    },
  },
};
