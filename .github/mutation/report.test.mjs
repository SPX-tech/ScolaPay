import { test } from 'node:test';
import assert from 'node:assert/strict';
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

function fixture(run) {
  const root = mkdtempSync(join(tmpdir(), 'scolapay-campaign-'));
  const scripts = join(root, '.github/mutation');
  mkdirSync(scripts, { recursive: true });
  for (const name of ['apply.mjs', 'result.mjs', 'report.mjs']) {
    copyFileSync(resolve('.github/mutation', name), join(scripts, name));
  }
  writeFileSync(join(scripts, 'sample.mjs'),
    "export default {controls:1,mutations:[{id:'defect',mustFail:['Scenario.rule']}]}");
  function execute(script, args, env = {}) {
    const result = spawnSync(process.execPath, [join(scripts, script), ...args],
      { cwd: root, encoding: 'utf8', env: { ...process.env, ...env } });
    assert.equal(result.status, 0, result.stderr);
    return result.stdout;
  }
  try { run(root, execute); }
  finally { rmSync(root, { recursive: true, force: true }); }
}

function writeResult(root, folder, id, status) {
  const path = join(root, 'results', folder);
  mkdirSync(path, { recursive: true });
  writeFileSync(join(path, folder + '.json'), JSON.stringify({ id, status, tests: 1, failed: [], mustFail: [] }));
}

test('une campagne complète peut conclure', () => fixture((root, execute) => {
  writeResult(root, 'result-defect', 'defect', 'DETECTE');
  writeResult(root, 'result-control', 'control-1', 'VERT');
  assert.match(execute('report.mjs', ['results', 'sample']), /^Conclusion : CONCLUANTE/);
}));

test('un mutant VERT interdit de conclure', () => fixture((root, execute) => {
  writeResult(root, 'result-defect', 'defect', 'VERT');
  writeResult(root, 'result-control', 'control-1', 'VERT');
  assert.match(execute('report.mjs', ['results', 'sample']), /^Conclusion : NON CONCLUANTE/);
}));

test('un contrôle DETECTE interdit de conclure', () => fixture((root, execute) => {
  writeResult(root, 'result-defect', 'defect', 'DETECTE');
  writeResult(root, 'result-control', 'control-1', 'DETECTE');
  assert.match(execute('report.mjs', ['results', 'sample']), /^Conclusion : NON CONCLUANTE/);
}));

test('une mutation manquante interdit de conclure', () => fixture((root, execute) => {
  writeResult(root, 'result-control', 'control-1', 'VERT');
  assert.match(execute('report.mjs', ['results', 'sample']), /^Conclusion : NON CONCLUANTE/);
}));

test('un doublon ne remplace pas un résultat attendu', () => fixture((root, execute) => {
  writeResult(root, 'result-defect', 'defect', 'DETECTE');
  writeResult(root, 'result-duplicate', 'defect', 'DETECTE');
  writeResult(root, 'result-control', 'control-1', 'VERT');
  assert.match(execute('report.mjs', ['results', 'sample']), /^Conclusion : NON CONCLUANTE/);
}));

for (const [element, expected] of [['error', 'ERREUR_TEST'], ['failure', 'DETECTE']]) {
  test(`un élément ${element} reçoit la qualification ${expected}`, () => fixture((root, execute) => {
    const path = join(root, 'core/target/surefire-reports');
    mkdirSync(path, { recursive: true });
    writeFileSync(join(path, 'TEST-Scenario.xml'),
      `<testsuite><testcase name="rule" classname="example.Scenario"><${element} message="défaut"></${element}></testcase></testsuite>`);
    const result = JSON.parse(execute('result.mjs', ['sample', 'defect'],
      { APPLIED: 'success', COMPILED: 'success', TESTED: 'failure' }));
    assert.equal(result.status, expected);
  }));
}
