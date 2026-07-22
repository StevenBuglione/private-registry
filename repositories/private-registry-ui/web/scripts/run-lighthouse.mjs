import { spawn, spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

import { chromium } from "@playwright/test";
import { launch } from "chrome-launcher";
import lighthouse from "lighthouse";
import desktopConfig from "lighthouse/core/config/lr-desktop-config.js";

const reportDirectory = resolve("reports", "lighthouse");
mkdirSync(reportDirectory, { recursive: true });

if (process.platform !== "win32") {
  const lighthouseCli = resolve(
    "node_modules",
    "@lhci",
    "cli",
    "src",
    "cli.js",
  );
  const result = spawnSync(process.execPath, [lighthouseCli, "autorun"], {
    env: {
      ...process.env,
      CHROME_PATH: chromium.executablePath(),
    },
    stdio: "inherit",
  });

  if (result.error) {
    throw result.error;
  }

  process.exitCode = result.status ?? 1;
} else {
  await runWindowsAudit();
}

async function runWindowsAudit() {
  const previewPort = 4176;
  const chromeProfile = resolve(".lighthouseci", "chrome-profile");
  mkdirSync(chromeProfile, { recursive: true });
  const preview = spawn(
    process.execPath,
    [
      resolve("node_modules", "vite", "bin", "vite.js"),
      "preview",
      "--host",
      "127.0.0.1",
      "--port",
      String(previewPort),
      "--strictPort",
    ],
    { stdio: "ignore" },
  );
  let chrome;

  try {
    await waitForServer(`http://127.0.0.1:${previewPort}/`);
    chrome = await launch({
      chromePath: chromium.executablePath(),
      chromeFlags: ["--headless=new", "--no-sandbox"],
      logLevel: "silent",
      userDataDir: chromeProfile,
    });

    const result = await lighthouse(
      `http://127.0.0.1:${previewPort}/`,
      {
        logLevel: "error",
        onlyCategories: [
          "accessibility",
          "best-practices",
          "performance",
          "seo",
        ],
        output: ["html", "json"],
        port: chrome.port,
      },
      desktopConfig,
    );

    if (!result) {
      throw new Error("Lighthouse did not return an audit result.");
    }

    const reports = Array.isArray(result.report)
      ? result.report
      : [result.report];
    const html = reports.find((report) => report.startsWith("<!doctype html>"));
    const json = reports.find((report) => report.startsWith("{"));
    if (html) {
      writeFileSync(resolve(reportDirectory, "index.html"), html);
    }
    writeFileSync(
      resolve(reportDirectory, "lhr.json"),
      json ?? JSON.stringify(result.lhr, null, 2),
    );

    assertMinimum(result.lhr, "accessibility", 0.95);
    assertMinimum(result.lhr, "best-practices", 0.9);
    assertMinimum(result.lhr, "performance", 0.8);
    assertMinimum(result.lhr, "seo", 0.9);
    assertMaximum(result.lhr, "cumulative-layout-shift", 0.1);
    assertMaximum(result.lhr, "largest-contentful-paint", 2500);
    assertMaximum(result.lhr, "total-blocking-time", 300);
    assertScriptBudget(result.lhr, 256_000);

    const summary = Object.fromEntries(
      Object.entries(result.lhr.categories).map(([name, category]) => [
        name,
        Math.round((category.score ?? 0) * 100),
      ]),
    );
    console.log("Lighthouse budgets passed:", summary);
  } finally {
    if (chrome) {
      await chrome.kill();
    }
    preview.kill();
  }
}

async function waitForServer(url) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        return;
      }
    } catch {
      // The preview server is still starting.
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 100));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

function assertMinimum(lhr, categoryName, minimum) {
  const score = lhr.categories[categoryName]?.score;
  if (score === null || score === undefined || score < minimum) {
    throw new Error(
      `Lighthouse ${categoryName} score ${String(score)} is below ${minimum}.`,
    );
  }
}

function assertMaximum(lhr, auditName, maximum) {
  const value = lhr.audits[auditName]?.numericValue;
  if (value === undefined || value > maximum) {
    throw new Error(
      `Lighthouse ${auditName} value ${String(value)} exceeds ${maximum}.`,
    );
  }
}

function assertScriptBudget(lhr, maximum) {
  const resources = lhr.audits["resource-summary"]?.details?.items;
  const script = resources?.find((item) => item.resourceType === "script");
  const value = script?.transferSize;
  if (typeof value !== "number" || value > maximum) {
    throw new Error(
      `Lighthouse script transfer size ${String(value)} exceeds ${maximum}.`,
    );
  }
}
