import { cp, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, "..");
const sourceDir = path.join(rootDir, "node_modules", "web-ifc");
const targetDir = path.join(rootDir, "public", "web-ifc");

await mkdir(targetDir, { recursive: true });

const wasmFiles = ["web-ifc.wasm", "web-ifc-mt.wasm"];
for (const file of wasmFiles) {
  await cp(path.join(sourceDir, file), path.join(targetDir, file), { force: true });
}

console.log("web-ifc WASM files copied to public/web-ifc/");

// Copy the fragments worker (must match installed @thatopen/fragments version)
const workerSrc = path.join(
  rootDir,
  "node_modules",
  "@thatopen",
  "fragments",
  "dist",
  "Worker",
  "worker.mjs"
);
await cp(workerSrc, path.join(rootDir, "public", "fragments-worker.mjs"), { force: true });

console.log("fragments worker copied to public/fragments-worker.mjs");
