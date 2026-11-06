// Applique un défaut d'une campagne au code extrait sur le runner. Un motif qui ne correspond pas
// exactement une fois est une erreur : un défaut non appliqué ne doit jamais compter comme détecté.
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

export async function loadCampaign(name) {
  if (!/^[a-z0-9-]+$/.test(name)) throw new Error(`Nom de campagne invalide : ${name}`);
  const url = pathToFileURL(resolve('.github/mutation', `${name}.mjs`)).href;
  return (await import(url)).default;
}

const read = (file) => readFileSync(file, 'utf8').replace(/\r\n/g, '\n');

function applySteps(mutation, write) {
  const contents = new Map();
  for (const [file, find, replace] of mutation.steps) {
    const text = contents.get(file) ?? read(file);
    const found = text.split(find).length - 1;
    if (found !== 1) {
      throw new Error(`${mutation.id} : le motif doit apparaître une fois, trouvé ${found} : ${find.slice(0, 70)}`);
    }
    contents.set(file, text.replace(find, () => replace));
  }
  if (write) contents.forEach((text, file) => writeFileSync(file, text));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [, , name, target] = process.argv;
  const campaign = await loadCampaign(name);
  const ids = campaign.mutations.map((mutation) => mutation.id);
  if (new Set(ids).size !== ids.length) throw new Error('Identifiants de défauts en double');
  const controls = Array.from({ length: campaign.controls }, (_, index) => `control-${index + 1}`);

  if (target === '--list') {
    console.log(JSON.stringify([...ids, ...controls]));
  } else if (target === '--check-all') {
    campaign.mutations.forEach((mutation) => applySteps(mutation, false));
    console.log(`${ids.length} défauts applicables, ${controls.length} exécutions de contrôle`);
  } else if (!target.startsWith('control-')) {
    const mutation = campaign.mutations.find((candidate) => candidate.id === target);
    if (!mutation) throw new Error(`Défaut inconnu : ${target}`);
    applySteps(mutation, true);
  }
}
