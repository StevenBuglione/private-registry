import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import sharp from "sharp";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const uiDirectory = resolve(scriptDirectory, "../..");
const assetDirectory = join(uiDirectory, "public", "assets");
const expectedPngs = new Map([
  ["registry-mark-32.png", [32, 32]],
  ["registry-mark-64.png", [64, 64]],
  ["apple-touch-icon.png", [180, 180]],
  ["pwa-192.png", [192, 192]],
  ["pwa-512.png", [512, 512]],
  ["maskable-512.png", [512, 512]],
  ["registry-mark.png", [1024, 1024]],
  ["registry-og.png", [1200, 630]],
]);

for (const [fileName, [expectedWidth, expectedHeight]] of expectedPngs) {
  const metadata = await sharp(join(assetDirectory, fileName)).metadata();
  if (
    metadata.format !== "png" ||
    metadata.width !== expectedWidth ||
    metadata.height !== expectedHeight
  ) {
    throw new Error(
      `${fileName} must be a ${expectedWidth}x${expectedHeight} PNG; received ` +
        `${metadata.format ?? "unknown"} ${metadata.width ?? "?"}x${metadata.height ?? "?"}`,
    );
  }
}

const ico = await readFile(join(assetDirectory, "favicon.ico"));
if (
  ico.length < 6 ||
  ico[0] !== 0 ||
  ico[1] !== 0 ||
  ico[2] !== 1 ||
  ico[3] !== 0 ||
  ico.readUInt16LE(4) !== 3
) {
  throw new Error("favicon.ico must contain exactly three ICO image entries");
}

const source = await readFile(
  join(assetDirectory, "registry-mark.svg"),
  "utf8",
);
if (!source.includes('fill="#844fba"')) {
  throw new Error(
    "registry-mark.svg is not the canonical purple Registry mark",
  );
}

const index = await readFile(join(uiDirectory, "web", "index.html"), "utf8");
for (const requiredReference of [
  "/assets/registry-mark.svg?v=20260723",
  "/assets/favicon.ico?v=20260723",
  "/assets/registry-mark-32.png?v=20260723",
  "/assets/apple-touch-icon.png?v=20260723",
  "/manifest.webmanifest?v=20260723",
  "/assets/registry-og.png?v=20260723",
]) {
  if (!index.includes(requiredReference)) {
    throw new Error(`index.html is missing ${requiredReference}`);
  }
}

const manifest = JSON.parse(
  await readFile(join(uiDirectory, "public", "manifest.webmanifest"), "utf8"),
);
for (const requiredIcon of [
  "/assets/pwa-192.png?v=20260723",
  "/assets/pwa-512.png?v=20260723",
  "/assets/maskable-512.png?v=20260723",
]) {
  if (
    !manifest.icons.some(
      (icon) =>
        typeof icon === "object" && icon !== null && icon.src === requiredIcon,
    )
  ) {
    throw new Error(`manifest.webmanifest is missing ${requiredIcon}`);
  }
}

process.stdout.write(
  `Validated ${expectedPngs.size} PNG assets, one three-frame ICO, the canonical SVG, and versioned document references.\n`,
);
