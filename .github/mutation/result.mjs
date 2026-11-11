// Qualifie une exécution. Seul un test réellement échoué sur du code compilé vaut détection.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { loadCampaign } from './apply.mjs';

const [, , name, id] = process.argv;
const campaign = await loadCampaign(name);
const reports = 'core/target/surefire-reports';

let tests = 0;
let skipped = 0;
const failed = [];
const assertionFailures = [];
if (existsSync(reports)) {
  for (const file of readdirSync(reports).filter((candidate) => candidate.endsWith('.xml'))) {
    const xml = readFileSync(`${reports}/${file}`, 'utf8');
    const cases = /<testcase name="([^"]+)" classname="([^"]+)"[^>]*?(\/>|>)([\s\S]*?)(?=<testcase |<\/testsuite>)/g;
    for (const match of xml.matchAll(cases)) {
      tests++;
      if (/<skipped[ >\/]/.test(match[4])) skipped++;
      if (match[3] === '>' && /<(failure|error)[ >]/.test(match[4])) {
        const test = `${match[2].split('.').pop()}.${match[1]}`;
        failed.push(test);
        if (/<failure[ >]/.test(match[4])) assertionFailures.push(test);
      }
    }
  }
}

const { APPLIED, COMPILED, TESTED } = process.env;
const control = id.startsWith('control-');
const mustFail = campaign.mutations.find((mutation) => mutation.id === id)?.mustFail ?? [];
let status;
if (APPLIED !== 'success') status = 'INAPPLICABLE';
else if (COMPILED !== 'success') status = 'NE_COMPILE_PAS';
else if (tests === 0) status = 'SANS_RESULTAT';
else if (control) status = TESTED === 'success' && failed.length === 0 && skipped === 0 ? 'VERT' : 'INSTABLE';
else if (failed.length === 0) status = TESTED === 'success' ? 'SURVIVANT' : 'SANS_RESULTAT';
else if (assertionFailures.length === 0) status = 'ERREUR_TEST';
else if (mustFail.some((test) => !assertionFailures.includes(test))) status = 'MAUVAIS_TEST';
else status = 'DETECTE';

console.log(JSON.stringify({ id, status, tests, skipped, failed, mustFail }));
