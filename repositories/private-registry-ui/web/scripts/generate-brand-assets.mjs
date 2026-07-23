import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import pngToIco from "png-to-ico";
import sharp from "sharp";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const assetDirectory = resolve(scriptDirectory, "../../public/assets");
const sourcePath = join(assetDirectory, "registry-mark.svg");
const source = await readFile(sourcePath);

const transparentIcons = [
  ["registry-mark-32.png", 32],
  ["registry-mark-64.png", 64],
  ["apple-touch-icon.png", 180],
  ["pwa-192.png", 192],
  ["pwa-512.png", 512],
  ["registry-mark.png", 1024],
];

async function renderContainedIcon(
  outputPath,
  canvasSize,
  iconSize = canvasSize,
) {
  const mark = await sharp(source)
    .resize(iconSize, iconSize, { fit: "contain" })
    .png()
    .toBuffer();

  await sharp({
    create: {
      width: canvasSize,
      height: canvasSize,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  })
    .composite([
      {
        input: mark,
        left: Math.floor((canvasSize - iconSize) / 2),
        top: Math.floor((canvasSize - iconSize) / 2),
      },
    ])
    .png({ compressionLevel: 9 })
    .toFile(outputPath);
}

await Promise.all(
  transparentIcons.map(([fileName, size]) =>
    renderContainedIcon(join(assetDirectory, fileName), size),
  ),
);

const maskableMark = await sharp(source)
  .resize(300, 300, { fit: "contain" })
  .png()
  .toBuffer();

await sharp({
  create: {
    width: 512,
    height: 512,
    channels: 4,
    background: "#f7f5ef",
  },
})
  .composite([{ input: maskableMark, left: 106, top: 106 }])
  .png({ compressionLevel: 9 })
  .toFile(join(assetDirectory, "maskable-512.png"));

const openGraphMark = await sharp(source)
  .resize(126, 144, { fit: "contain" })
  .png()
  .toBuffer();
const openGraphWordmark = Buffer.from(`
  <svg width="820" height="190" xmlns="http://www.w3.org/2000/svg">
    <style>
      .organization { font: 600 22px Arial, sans-serif; letter-spacing: 0.4px; fill: #4b5563; }
      .product { font: 700 62px Arial, sans-serif; fill: #111827; }
      .registry { font: 600 42px Arial, sans-serif; fill: #111827; }
      .description { font: 400 26px Arial, sans-serif; fill: #4b5563; }
    </style>
    <text x="0" y="30" class="organization">OREMUS LABS</text>
    <text x="0" y="95" class="product">Terraform</text>
    <line x1="330" y1="49" x2="330" y2="101" stroke="#9ca3af" stroke-width="2"/>
    <text x="360" y="94" class="registry">Registry</text>
    <text x="0" y="152" class="description">Private providers and modules for your teams</text>
  </svg>
`);

await sharp({
  create: {
    width: 1200,
    height: 630,
    channels: 4,
    background: "#f7f5ef",
  },
})
  .composite([
    { input: openGraphMark, left: 98, top: 217 },
    { input: openGraphWordmark, left: 272, top: 220 },
  ])
  .png({ compressionLevel: 9 })
  .toFile(join(assetDirectory, "registry-og.png"));

const temporaryDirectory = await mkdtemp(join(tmpdir(), "registry-favicon-"));
try {
  const icoPaths = [];
  for (const size of [16, 32, 48]) {
    const icoPath = join(temporaryDirectory, `registry-${size}.png`);
    await renderContainedIcon(icoPath, size);
    icoPaths.push(icoPath);
  }
  await writeFile(
    join(assetDirectory, "favicon.ico"),
    await pngToIco(icoPaths),
  );
} finally {
  await rm(temporaryDirectory, { recursive: true, force: true });
}

process.stdout.write(`Generated Registry brand assets from ${sourcePath}\n`);
