import { readdir } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const rootDirectory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const diagramDirectory = join(rootDirectory, "docs", "diagrams");
const configurationPath = join(diagramDirectory, "mermaid.config.json");
const puppeteerConfigurationPath = join(
  diagramDirectory,
  "puppeteer.config.json",
);
const rendererPackage = "@mermaid-js/mermaid-cli@11.16.0";
const npxCommand = "npx";

const availableSources = (await readdir(diagramDirectory))
  .filter((fileName) => extname(fileName) === ".mmd")
  .sort();
const requestedSources = process.argv.slice(2);
const sources =
  requestedSources.length === 0 ? availableSources : requestedSources.sort();

if (sources.length === 0) {
  throw new Error(`No Mermaid sources found in ${diagramDirectory}`);
}
for (const source of sources) {
  if (!availableSources.includes(source)) {
    throw new Error(`Unknown Mermaid source: ${source}`);
  }
}

function render(sourcePath, outputPath, format) {
  const argumentsList = [
    "--yes",
    "--package",
    rendererPackage,
    "mmdc",
    "--input",
    sourcePath,
    "--output",
    outputPath,
    "--configFile",
    configurationPath,
    "--puppeteerConfigFile",
    puppeteerConfigurationPath,
    "--backgroundColor",
    format === "svg" ? "transparent" : "#ffffff",
  ];
  if (format === "png") {
    argumentsList.push("--width", "2400", "--scale", "1");
  }

  const result = spawnSync(npxCommand, argumentsList, {
    cwd: rootDirectory,
    shell: process.platform === "win32",
    stdio: "inherit",
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(
      `Mermaid rendering failed for ${sourcePath} with exit code ${result.status}`,
    );
  }
}

for (const sourceFileName of sources) {
  const sourcePath = join(diagramDirectory, sourceFileName);
  const baseName = sourceFileName.slice(0, -extname(sourceFileName).length);
  process.stdout.write(`Rendering ${sourceFileName}\n`);
  render(sourcePath, join(diagramDirectory, `${baseName}.svg`), "svg");
  render(sourcePath, join(diagramDirectory, `${baseName}.png`), "png");
}

process.stdout.write(
  `Rendered ${sources.length} Mermaid diagrams as SVG and PNG.\n`,
);
