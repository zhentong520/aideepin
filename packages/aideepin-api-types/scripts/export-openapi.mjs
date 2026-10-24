#!/usr/bin/env node
/**
 * OPT-12: export the OpenAPI contract from a running backend (dev profile).
 * Default http://localhost:9999/v3/api-docs ; override with OPENAPI_URL / OUT.
 */
import { writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const url = process.env.OPENAPI_URL || 'http://localhost:9999/v3/api-docs'
const out = process.env.OUT || join(here, '..', 'openapi.json')

const res = await fetch(url)
if (!res.ok) {
  console.error(`fetch ${url} -> ${res.status}; is the backend running with springdoc enabled?`)
  process.exit(1)
}
const body = await res.text()
try {
  JSON.parse(body)
} catch {
  console.error('response is not JSON (token filter or error page?): first bytes:', body.slice(0, 120))
  process.exit(1)
}
await writeFile(out, body, 'utf8')
console.log(`openapi.json exported -> ${out} (${body.length} bytes)`)
