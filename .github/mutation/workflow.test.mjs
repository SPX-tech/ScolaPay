import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const workflow = readFileSync('.github/workflows/mutation-campaign.yml', 'utf8').replace(/\r\n/g, '\n');

// Exécute les commandes réelles du workflow avec les options de son shell GitHub Actions.
function step(name) {
  const marker = '      - name: ' + name + '\n';
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1, 'Étape absente ' + name);
  const end = workflow.indexOf('\n      - ', start + marker.length);
  const block = workflow.slice(start, end === -1 ? workflow.length : end);
  const run = block.match(/^        run: (.+)$/m);
  assert.ok(run, 'Commande absente ' + name);
  let command = run[1];
  if (command === '|') {
    const remaining = block.slice(run.index + run[0].length + 1);
    command = remaining.split('\n').filter((line) => line.startsWith('          '))
      .map((line) => line.slice(10)).join('\n');
  } else if (command.startsWith("'") && command.endsWith("'")) {
    command = command.slice(1, -1).replace(/''/g, "'");
  }
  command = command.replaceAll('${{ needs.plan.outputs.campaign }}', 'sample');
  const options = block.includes('        shell: bash\n') ? ['-e', '-o', 'pipefail'] : ['-e'];
  return { command, options };
}

const generate = step('Établir le rapport');
const conclude = step("Échouer si la campagne n'est pas concluante");

function execute(output, exitCode) {
  const root = mkdtempSync(join(tmpdir(), 'scolapay-workflow-'));
  const env = {
    ...process.env,
    TEST_REPORT: output,
    TEST_EXIT: String(exitCode),
    GITHUB_STEP_SUMMARY: join(root, 'summary.md'),
    COMMIT: 'sample',
  };
  try {
    const generated = spawnSync('bash', [...generate.options, '-c',
      'node() { printf "%s" "$TEST_REPORT"; return "$TEST_EXIT"; }\n' + generate.command],
    { cwd: root, env, encoding: 'utf8' });
    assert.ifError(generated.error);
    if (generated.status !== 0) return generated.status;
    const concluded = spawnSync('bash', [...conclude.options, '-c', conclude.command],
      { cwd: root, env, encoding: 'utf8' });
    assert.ifError(concluded.error);
    return concluded.status;
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('le workflow accepte une génération réussie explicitement concluante', () => {
  assert.equal(execute('Conclusion : CONCLUANTE\n', 0), 0);
});

for (const report of ['', 'Rapport sans conclusion\n', 'Conclusion : NON CONCLUANTE\n']) {
  test('le workflow refuse une conclusion positive absente ' + JSON.stringify(report), () => {
    assert.notEqual(execute(report, 0), 0);
  });
}

for (const report of ['', 'Conclusion : CONCLUANTE\n']) {
  test('le workflow refuse un générateur en échec ' + JSON.stringify(report), () => {
    assert.notEqual(execute(report, 7), 0);
  });
}
