/* Verifies requested workflow scenarios against a running isolated local instance.
 * WORKBENCH_URL=http://127.0.0.1:8081 node scripts/scenario-smoke.cjs
 * Approves ONLY the reviewed authored Brownfield fixture, never imported AI code.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.WORKBENCH_URL || 'http://127.0.0.1:8081';
const reportPath = process.env.SCENARIO_REPORT || 'target/scenario-results.json';
const results = [];
async function api(route, method = 'GET', body) {
    const response = await fetch(base + '/api' + route, {
        method,
        ...(method !== 'GET' ? {headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body ?? {})} : {})
    });
    const data = await response.json();
    assert.ok(response.ok, `${method} ${route}: ${response.status} ${JSON.stringify(data)}`);
    return data;
}
async function wait(id, predicate) {
    const deadline = Date.now() + 30000;
    let run;
    do {
        run = await api('/runs/' + id);
        if (predicate(run)) return run;
        await new Promise(resolve => setTimeout(resolve, 75));
    } while (Date.now() < deadline);
    throw Error('Timed out waiting for workflow: ' + JSON.stringify(run));
}
const task = (run, stage) => run.tasks.find(t => t.stage === stage);
const gate = id => wait(id, r => task(r, 'VALIDATE').state === 'WAITING_FOR_APPROVAL');
const create = (scenario, mode = 'DEMO') => api('/runs', 'POST', {
    scenario, mode,
    requirement: scenario === 'BROWNFIELD'
        ? 'Add expiration to the basic URL service; reject expired redirects without counting clicks.'
        : scenario === 'AMBIGUOUS'
            ? 'Improve abuse controls and analytics. Clarify limits and retention before implementation.'
            : 'Build a URL shortener with aliases, URL validation, redirects and click counts.'
});
const approval = (run, kind, approve) => api('/runs/' + run.id + '/approvals/' + kind, 'POST', {
    revision: run.revision, hash: run.candidateHash, approve,
    reason: approve ? 'Automated verification of reviewed authored Brownfield fixture; no AI output executed.' : 'Scenario verification: deliberately reject execution.'
});
async function record(name, run, checks) {
    const evidence = await api('/runs/' + run.id + '/export');
    assert.equal(evidence.hashChainValid, true);
    results.push({
        scenario: name, result: 'PASS', runId: run.id, mode: run.mode,
        observedStatus: run.status, revision: run.revision, provider: run.provider,
        checks, candidateHash: run.candidateHash, hashChainValid: evidence.hashChainValid,
        tasks: run.tasks.map(t => ({stage: t.stage, state: t.state, attempts: t.attempts})),
        toolEvidence: run.tasks.filter(t => ['VALIDATE', 'RELEASE'].includes(t.stage) && t.output).map(t => ({stage: t.stage, output: t.output})),
        events: evidence.events.map(e => ({sequence: e.sequence, at: e.at, type: e.type, detail: e.detail}))
    });
}
async function main() {
    assert.ok(/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base), 'Use a loopback verification instance');
    await api('/health');

    let brown = await gate((await create('BROWNFIELD')).id);
    assert.notEqual(brown.files['LinkStore.java'], brown.baseline['LinkStore.java']);
    assert.notEqual(brown.files['CandidateTest.java'], brown.baseline['CandidateTest.java']);
    await approval(brown, 'execution', true);
    brown = await wait(brown.id, r => task(r, 'RELEASE').state === 'WAITING_FOR_APPROVAL' || r.status === 'FAILED' || r.status === 'WAITING_FOR_INPUT');
    assert.equal(task(brown, 'VALIDATE').state, 'SUCCEEDED', task(brown, 'VALIDATE').output);
    assert.match(task(brown, 'VALIDATE').output, /PASS: 9 candidate assertions/);
    assert.match(task(brown, 'VALIDATE').output, /EXIT 0/);
    await approval(brown, 'release', true);
    brown = await wait(brown.id, r => ['COMPLETED', 'FAILED'].includes(r.status));
    assert.equal(brown.status, 'COMPLETED', task(brown, 'RELEASE').output);
    assert.match(task(brown, 'RELEASE').output, /PASS: 9 candidate assertions/);
    await record('Brownfield', brown, ['Current source differs from retained baseline', 'Validation passed nine real assertions', 'Separate release approval required', 'Release repeated nine assertions and completed']);

    let ambiguous = await create('AMBIGUOUS');
    assert.equal(ambiguous.status, 'WAITING_FOR_INPUT');
    assert.ok(JSON.parse(task(ambiguous, 'ANALYZE').output).questions.length > 0);
    assert.equal(task(ambiguous, 'PLAN').state, 'PENDING');
    await api('/runs/' + ambiguous.id + '/clarification', 'POST', {answer: 'Implement expiration first; collect no raw IP addresses. Keep analytics to UTC aggregate click counts.'});
    ambiguous = await wait(ambiguous.id, r => task(r, 'IMPLEMENT').state === 'WAITING_FOR_INPUT' && !r.tasks.some(t => t.state === 'RUNNING'));
    assert.equal(Object.keys(ambiguous.files).length, 0);
    assert.equal(task(ambiguous, 'VALIDATE').attempts, 0);
    const implementationPrompt = await api('/runs/' + ambiguous.id + '/prompt/IMPLEMENT');
    assert.match(implementationPrompt.prompt, /collect no raw IP/);
    await record('Ambiguous', ambiguous, ['Questions block planning', 'Clarification recorded', 'Implementation waits for manual response', 'No canned implementation or code execution', 'Implementation prompt includes the clarification']);

    let manual = await create('GREENFIELD', 'MANUAL');
    assert.equal(task(manual, 'ANALYZE').state, 'WAITING_FOR_INPUT');
    assert.equal(manual.provider, 'MANUAL_CHATGPT_HANDOFF');
    const manualPrompt = await api('/runs/' + manual.id + '/prompt/ANALYZE');
    assert.match(manualPrompt.prompt, /acceptanceCriteria/);
    assert.equal(manualPrompt.revision, manual.revision);
    assert.equal(Object.keys(manual.files).length, 0);
    await record('Manual ChatGPT handoff', manual, ['ANALYZE waits for input', 'Exported prompt includes JSON contract and current revision', 'No source staged or external AI called']);

    let rejected = await gate((await create('GREENFIELD')).id);
    rejected = await approval(rejected, 'execution', false);
    assert.equal(rejected.status, 'REJECTED');
    assert.equal(task(rejected, 'VALIDATE').attempts, 0);
    assert.equal(rejected.executionApproval, '');
    const rejectedEvents = await api('/runs/' + rejected.id + '/events');
    assert.ok(!rejectedEvents.events.some(e => e.type === 'TASK_STARTED' && e.detail.includes('VALIDATE')));
    await record('Reject approval', rejected, ['Run becomes REJECTED', 'Validation attempts remain zero', 'No validation-start event or execution approval recorded']);

    let resumed = await gate((await create('GREENFIELD')).id);
    const originalHash = resumed.candidateHash;
    resumed = await api('/runs/' + resumed.id + '/stop', 'POST');
    assert.equal(resumed.status, 'STOPPED');
    assert.equal(resumed.executionApproval, '');
    assert.equal(task(resumed, 'VALIDATE').attempts, 0);
    resumed = await api('/runs/' + resumed.id + '/resume', 'POST');
    assert.equal(resumed.status, 'WAITING_FOR_APPROVAL');
    assert.equal(task(resumed, 'VALIDATE').state, 'WAITING_FOR_APPROVAL');
    assert.equal(resumed.executionApproval, '');
    assert.equal(resumed.candidateHash, originalHash);
    assert.equal(task(resumed, 'VALIDATE').attempts, 0);
    await record('Safe stop and resume', resumed, ['Safe stop prevents execution', 'Resume preserves reviewed source', 'Execution still has zero attempts', 'Renewed execution approval required']);

    const report = {
        checkedAt: new Date().toISOString(), status: 'PASS', scenarioCount: results.length,
        verification: 'Actual HTTP requests against an isolated packaged application. Authored fixture execution only; no AI API or user database used.',
        results
    };
    fs.mkdirSync(path.dirname(reportPath), {recursive: true});
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n');
    process.stdout.write(JSON.stringify({status: report.status, scenarioCount: report.scenarioCount, results: results.map(r => ({scenario: r.scenario, result: r.result, observedStatus: r.observedStatus})), report: reportPath}, null, 2) + '\n');
}
main().catch(e => {process.stderr.write(e.stack + '\n');process.exitCode = 1;});
