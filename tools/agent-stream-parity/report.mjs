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

function suspendedParts(render) {
  if (!render || !Array.isArray(render.parts)) return [];
  return render.parts.filter((part) => part.type === "data-tool-call-suspended");
}

function summarizeFixture(fixture) {
  const artifactDir = path.join(root, "artifacts", fixture.id);
  const ios = readJsonIfExists(path.join(artifactDir, "ios-render-completed.json"));
  const android = readJsonIfExists(path.join(artifactDir, "android-render-completed.json"));
  const iosEntries = entries(ios);
  const androidEntries = entries(android);
  const iosSuspended = suspendedParts(ios);
  const androidSuspended = suspendedParts(android);
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
  lines.push("## Suspended Cards");
  lines.push("");
  lines.push(`- iOS suspended: ${iosSuspended.length}`);
  lines.push(`- Android suspended: ${androidSuspended.length}`);
  const maxSuspended = Math.max(iosSuspended.length, androidSuspended.length);
  for (let index = 0; index < maxSuspended; index += 1) {
    const iosPart = iosSuspended[index];
    const androidPart = androidSuspended[index];
    lines.push(`- Suspended ${index + 1} iOS: ${suspendedLabel(iosPart)}`);
    lines.push(`- Suspended ${index + 1} Android: ${suspendedLabel(androidPart)}`);
  }
  lines.push("");
  lines.push("## Current Status");
  lines.push("");
  lines.push(statusFor(fixture, ios, android, iosEntries, androidEntries, iosSuspended, androidSuspended));
  lines.push("");
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(path.join(artifactDir, "parity-report.md"), `${lines.join("\n")}\n`);
  return { id: fixture.id, report: path.join(artifactDir, "parity-report.md") };
}

function suspendedLabel(part) {
  if (!part) return "none";
  const keys = Array.isArray(part.payloadKeys) ? part.payloadKeys.join(", ") : "none";
  return [
    part.suspendedKind || part.toolName || part.type || "unknown",
    keys === "none" ? "" : `payloadKeys: ${keys}`,
  ].filter(Boolean).join(" · ");
}

function statusFor(fixture, ios, android, iosEntries, androidEntries, iosSuspended, androidSuspended) {
  if (!ios && !android) return "- `blocked`: both clients are missing render JSON.";
  if (!ios) return "- `blocked`: iOS render JSON is missing.";
  if (!android) return "- `blocked`: Android render JSON is missing.";
  if (iosEntries.length === 0 && androidEntries.length > 0) return "- `ios-missing`: Android has tool card entries but iOS does not.";
  if (androidEntries.length === 0 && iosEntries.length > 0) return "- `android-missing`: iOS has tool card entries but Android does not.";
  if (iosSuspended.length === 0 && androidSuspended.length > 0) return "- `ios-missing`: Android has suspended card parts but iOS does not.";
  if (androidSuspended.length === 0 && iosSuspended.length > 0) return "- `android-missing`: iOS has suspended card parts but Android does not.";
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
