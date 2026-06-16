#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const rootArgIndex = process.argv.indexOf("--root");
const root = rootArgIndex >= 0
  ? process.argv[rootArgIndex + 1]
  : path.resolve("docs/agent-stream-fixtures");

const manifestPath = path.join(root, "manifest.json");
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) return null;
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function entries(render) {
  if (!render || !render.toolRoot || render.toolRoot === null) return [];
  return Array.isArray(render.toolRoot.entries) ? render.toolRoot.entries : [];
}

function summarizeFixture(fixture) {
  const artifactDir = path.join(root, "artifacts", fixture.id);
  const ios = readJsonIfExists(path.join(artifactDir, "ios-render-completed.json"));
  const android = readJsonIfExists(path.join(artifactDir, "android-render-completed.json"));
  const iosEntries = entries(ios);
  const androidEntries = entries(android);
  const lines = [];
  lines.push(`# ${fixture.id}`);
  lines.push("");
  lines.push(`Priority: ${fixture.priority}`);
  lines.push(`Expected initial status: ${fixture.expectedInitialStatus}`);
  lines.push(`Migration direction: ${fixture.migrationDirection}`);
  lines.push("");
  lines.push("## Replay");
  lines.push("");
  lines.push(`- iOS render JSON: ${ios ? "present" : "missing"}`);
  lines.push(`- Android render JSON: ${android ? "present" : "missing"}`);
  lines.push("");
  lines.push("## Tool Entries");
  lines.push("");
  lines.push(`- iOS entries: ${iosEntries.length}`);
  lines.push(`- Android entries: ${androidEntries.length}`);
  lines.push(`- iOS card types: ${iosEntries.map((entry) => entry.cardType).join(", ") || "none"}`);
  lines.push(`- Android card types: ${androidEntries.map((entry) => entry.cardType).join(", ") || "none"}`);
  lines.push("");
  lines.push("## Props Keys");
  lines.push("");
  const max = Math.max(iosEntries.length, androidEntries.length);
  for (let index = 0; index < max; index += 1) {
    const iosKeys = iosEntries[index]?.propsKeys ?? [];
    const androidKeys = androidEntries[index]?.propsKeys ?? [];
    lines.push(`- Entry ${index + 1} iOS: ${iosKeys.join(", ") || "none"}`);
    lines.push(`- Entry ${index + 1} Android: ${androidKeys.join(", ") || "none"}`);
  }
  lines.push("");
  lines.push("## Current Status");
  lines.push("");
  lines.push(statusFor(fixture, ios, android, iosEntries, androidEntries));
  lines.push("");
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(path.join(artifactDir, "parity-report.md"), `${lines.join("\n")}\n`);
  return { id: fixture.id, report: path.join(artifactDir, "parity-report.md") };
}

function statusFor(fixture, ios, android, iosEntries, androidEntries) {
  if (!ios && !android) return "- `blocked`: both clients are missing render JSON.";
  if (!ios) return "- `blocked`: iOS render JSON is missing.";
  if (!android) return "- `blocked`: Android render JSON is missing.";
  if (iosEntries.length === 0 && androidEntries.length > 0) return "- `ios-missing`: Android has tool card entries but iOS does not.";
  if (androidEntries.length === 0 && iosEntries.length > 0) return "- `android-missing`: iOS has tool card entries but Android does not.";
  const iosTypes = iosEntries.map((entry) => entry.cardType).join("|");
  const androidTypes = androidEntries.map((entry) => entry.cardType).join("|");
  if (iosTypes !== androidTypes) return "- `data-divergent`: card type sequence differs.";
  return `- \`${fixture.expectedInitialStatus}\`: render JSON exists on both clients; review screenshots and props key differences.`;
}

const results = manifest.fixtures.map(summarizeFixture);
console.log(`Wrote ${results.length} parity reports`);
for (const result of results) {
  console.log(`${result.id}: ${result.report}`);
}
