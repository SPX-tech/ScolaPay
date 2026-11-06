// Rapport de campagne. Concluante seulement si tous les défauts sont détectés et tous les contrôles verts.
import { readdirSync, readFileSync } from 'node:fs';

const directory = process.argv[2];
const results = readdirSync(directory)
  .map((folder) => JSON.parse(readFileSync(`${directory}/${folder}/${folder}.json`, 'utf8')))
  .sort((a, b) => a.id.localeCompare(b.id));

const controls = results.filter((result) => result.id.startsWith('control-'));
const mutations = results.filter((result) => !result.id.startsWith('control-'));
const detected = mutations.filter((result) => result.status === 'DETECTE');
const problems = results.filter((result) => !['DETECTE', 'VERT'].includes(result.status));
const counts = new Set(controls.map((result) => result.tests));
const conclusive = problems.length === 0 && controls.length > 0 && counts.size === 1 && mutations.length > 0;

console.log(`Conclusion : ${conclusive ? 'CONCLUANTE' : 'NON CONCLUANTE'}`);
console.log('');
console.log(`Commit : ${process.env.COMMIT}`);
console.log(`Défauts détectés : ${detected.length} sur ${mutations.length}`);
console.log(`Contrôles du code intact : ${controls.filter((result) => result.status === 'VERT').length} verts sur ${controls.length}, ${[...counts].join(', ')} tests`);
if (problems.length > 0) {
  console.log('');
  console.log('## À traiter');
  console.log('');
  for (const result of problems) {
    console.log(`- \`${result.id}\` : ${result.status}, ${result.failed.length} échec(s) ${result.failed.slice(0, 5).join(', ')}`);
  }
}
console.log('');
console.log('## Détail');
console.log('');
console.log('| Exécution | Résultat | Tests | Échecs | Tests qui échouent |');
console.log('| --- | --- | --- | --- | --- |');
for (const result of results) {
  const shown = result.failed.slice(0, 3).join(', ') + (result.failed.length > 3 ? ', ...' : '');
  console.log(`| \`${result.id}\` | ${result.status} | ${result.tests} | ${result.failed.length} | ${shown} |`);
}
