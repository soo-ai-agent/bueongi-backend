// CDN 산출물 검증 — manifest + 컬렉션 파일을 JSON Schema 로 검사.
// 실행: npm run validate  (파이프라인 실행 후)

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import Ajv from "ajv";
import addFormats from "ajv-formats";

const ROOT = resolve(import.meta.dirname, "..");
const SCHEMA_DIR = join(ROOT, "schema");
const CDN_DIR = join(ROOT, "..", "cdn");

function loadSchema(name: string) {
  return JSON.parse(readFileSync(join(SCHEMA_DIR, name), "utf8"));
}

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (p.endsWith(".json")) out.push(p);
  }
  return out;
}

function main(): void {
  const ajv = new Ajv({ allErrors: true, strict: false });
  addFormats(ajv);
  ajv.addSchema(loadSchema("cdn-item.schema.json"));
  const validateCollection = ajv.compile(loadSchema("collection.schema.json"));
  const validateManifest = ajv.compile(loadSchema("manifest.schema.json"));

  let failures = 0;
  for (const file of walk(CDN_DIR)) {
    const rel = file.slice(CDN_DIR.length + 1);
    if (rel.startsWith("report/")) continue;
    const data = JSON.parse(readFileSync(file, "utf8"));
    const validate = rel === "manifest.json" ? validateManifest : validateCollection;
    if (!validate(data)) {
      failures++;
      console.error(`✗ ${rel}`);
      for (const e of validate.errors ?? []) console.error(`   ${e.instancePath} ${e.message}`);
    } else {
      console.log(`✓ ${rel}`);
    }
  }

  if (failures > 0) {
    console.error(`\n${failures} file(s) failed validation`);
    process.exit(1);
  }
  console.log("\nall CDN outputs valid");
}

main();
