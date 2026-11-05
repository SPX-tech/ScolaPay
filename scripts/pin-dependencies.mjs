import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const app = process.argv[2];
if (!['backend', 'web'].includes(app)) throw new Error('Expected backend or web.');
const path = join(app, 'package.json');
const manifest = JSON.parse(readFileSync(path, 'utf8'));
const lock = JSON.parse(readFileSync(join(app, 'package-lock.json'), 'utf8'));
for (const group of ['dependencies', 'devDependencies']) {
  for (const name of Object.keys(manifest[group] ?? {})) {
    const version = lock.packages[`node_modules/${name}`]?.version;
    if (!version || version.includes('-')) throw new Error(`Missing stable version: ${name}`);
    manifest[group][name] = version;
  }
}
writeFileSync(path, `${JSON.stringify(manifest, null, 2)}\n`);
