export const meta = {
  name: 'preview-launcher-requirement',
  description: 'Planner-Generator-Evaluator implementation of ONE requirement from preview-launcher-requirements.md',
  whenToUse: 'Invoke once per requirement id (P0-1, P0-2, P1-3, P1-4, P2-5, P2-6, P2-8). Pass args: {id, context, maxRounds}.',
  phases: [
    { title: 'Plan', detail: 'read the spec section + the real code, emit a verifiable plan' },
    { title: 'Implement', detail: 'apply the plan across the launcher and the skill' },
    { title: 'Evaluate', detail: 'three independent lenses: acceptance criteria, regression, spec compliance' },
    { title: 'Remediate', detail: 'close blocking gaps, then re-evaluate' },
  ],
}

// ---------------------------------------------------------------- inputs
const ID = (args && args.id) || 'P0-1'
const EXTRA = (args && args.context) || ''
const MAX_ROUNDS = (args && args.maxRounds) || 2

// Thoroughness dial (product owner decision 9). The Generator always runs at high effort --
// it writes the code. The Planner and the Evaluators are what get trimmed on the small
// requirements, and which lenses are worth running is requirement-specific, not a fixed pair:
// P2-5 and P2-8 must keep 'regression' because they can break an existing contract, while
// P2-6 is documentation-only and has nothing to regress.
const EFFORT = (args && args.effort) || 'high'
const LENS_KEYS = (args && args.lenses) || ['criteria', 'regression', 'spec']

const SPEC = '/Users/hawk/Documents/workspace/AI/agent-skill/zulwriter-showcase/preview-launcher-requirements.md'
const PLANDOC = '/Users/hawk/Documents/workspace/PLUGIN/zkidea/tasks/preview-launcher-implementation.md'
const ZKIDEA = '/Users/hawk/Documents/workspace/PLUGIN/zkidea'
const SKILL = '/Users/hawk/Documents/workspace/AI/agent-skill'

const GROUND = [
  'GROUND RULES — these override any instinct to be helpful beyond the task.',
  '',
  'Repos (both already on branch feat/zul-preview-agent-skill):',
  '  launcher + IntelliJ plugin: ' + ZKIDEA + '  (github zkoss/zkidea)',
  '  agent skill:                ' + SKILL + '   (github zkoss-demo/agent-skill)',
  'Requirements spec:  ' + SPEC,
  'Decisions log:      ' + PLANDOC + '  <-- READ THIS, it records the product owner answers',
  '',
  'FORBIDDEN — the orchestrator does these, never you:',
  '  - ANY mutating git command. Not commit, not push, not tag, not checkout, not reset,',
  '    not stash, and NOT `git add` — the P0-1 generator staged its work and hid the diff',
  '    from review. Leave every edit in the working tree, unstaged. `git diff`, `git log`,',
  '    `git status` and `git show` are fine — read freely, mutate nothing.',
  '  - gh release create|upload|delete, gh pr, any network write',
  '  - editing files outside the two repos above',
  '  - touching requirements in the spec other than ' + ID,
  '',
  'ALLOWED and expected: read anything, run builds and tests, run ./gradlew, run uv/python,',
  'edit source files in the two repos, create fixtures.',
  '',
  'STYLE: match the surrounding code. The launcher is heavily commented with *why*',
  'comments; keep that density there. Python follows the existing helper-function shape.',
  'Do not refactor adjacent code. Every changed line must trace to ' + ID + '.',
  '',
  'ENVIRONMENT facts already verified — do not re-derive:',
  '  - Java: use `withjdk.sh 17 ./gradlew ...` from ' + ZKIDEA,
  '  - The launcher jar builds via `:zk-preview-launcher:releaseLauncher` into build/release/',
  '  - preview-zul.py is a PEP 723 uv script: run it as `uv run skills/zul-writer/scripts/preview-zul.py`',
  '  - /usr/local/opt/python@3.14/bin/python3.14 also has playwright, if uv is unavailable',
  '  - Golden end-to-end case: ' + SKILL + '/zulwriter-showcase/',
  '      src/main/webapp/application-review.zul + src/main/java/zwriter/ApplicationReviewComposer.java',
  '      (already compiled into zulwriter-showcase/target/classes)',
  '  - gh account hawkhero (active) has NO push on zkoss/zkidea; hawkchen does.',
  '    Irrelevant to you — you never publish.',
  '',
  'CONCURRENT GRADLE — this already cost a full evaluation round on P0-2, so read it twice.',
  'The evaluator lenses run in PARALLEL, in this one shared working tree. Two `./gradlew`',
  'invocations sharing a build/ directory make `cleanTest` in the second delete the binary',
  'test-report store the first is still writing. The victim build fails with "Could not write',
  'XML test results" for up to 28 suites and ZERO failing assertions, taking the reports of',
  'unrelated suites with it, so the suite looks like a broken regression gate.',
  'That is an artifact of this harness, NOT a defect in the code you are reviewing. Measured:',
  'the same tree is 3/3 green run sequentially, and the failure reproduces on the first try',
  'with two concurrent runs. Therefore:',
  '  - For ANY gradle invocation, work in a private copy so you get your own build/ dir:',
  '      C=$(mktemp -d)/t && rsync -a --exclude build --exclude .git --exclude .gradle \\',
  '        /Users/hawk/Documents/workspace/PLUGIN/zkidea/ $C/ && cd $C \\',
  '        && withjdk.sh 17 ./gradlew :zk-preview-launcher:cleanTest :zk-preview-launcher:test',
  '    Say which path you ran in, so copies can be told from the live tree.',
  '  - Seeing that message with no assertion failures: say so and move on. Never file it as a',
  '    gap, and never re-diagnose it as a threading or stderr bug in the change — three lenses',
  '    did exactly that on P0-2 and all three were wrong.',
].join('\n')

// ---------------------------------------------------------------- schemas
const PLAN_SCHEMA = {
  type: 'object',
  required: ['summary', 'steps', 'criteria', 'verification', 'risks', 'outOfScope'],
  properties: {
    summary: { type: 'string', description: 'What this requirement changes, in 2-4 sentences.' },
    steps: {
      type: 'array',
      description: 'Ordered edits. One entry per file.',
      items: {
        type: 'object',
        required: ['file', 'change', 'why'],
        properties: {
          file: { type: 'string', description: 'Absolute path.' },
          change: { type: 'string', description: 'Concretely what to edit, naming symbols/lines.' },
          why: { type: 'string', description: 'Which spec clause forces this.' },
        },
      },
    },
    criteria: {
      type: 'array',
      description: 'Every acceptance criterion from the spec section, plus any implied MUST.',
      items: {
        type: 'object',
        required: ['id', 'criterion', 'verifyBy'],
        properties: {
          id: { type: 'string' },
          criterion: { type: 'string' },
          verifyBy: { type: 'string', description: 'The exact command or observation that proves it.' },
        },
      },
    },
    verification: { type: 'array', items: { type: 'string' }, description: 'Shell commands, in order.' },
    risks: { type: 'array', items: { type: 'string' } },
    outOfScope: { type: 'array', items: { type: 'string' }, description: 'Tempting work that belongs to another requirement.' },
  },
}

const IMPL_SCHEMA = {
  type: 'object',
  required: ['filesChanged', 'commands', 'deviations', 'unfinished', 'notes'],
  properties: {
    filesChanged: {
      type: 'array',
      items: {
        type: 'object',
        required: ['path', 'summary'],
        properties: { path: { type: 'string' }, summary: { type: 'string' } },
      },
    },
    commands: {
      type: 'array',
      description: 'Every build/test/run command you executed, with its real outcome. Do not omit failures.',
      items: {
        type: 'object',
        required: ['cmd', 'outcome'],
        properties: { cmd: { type: 'string' }, outcome: { type: 'string' } },
      },
    },
    deviations: { type: 'array', items: { type: 'string' }, description: 'Where you departed from the plan, and why.' },
    unfinished: { type: 'array', items: { type: 'string' }, description: 'Anything you could not complete. Be honest; the evaluators will find it.' },
    notes: { type: 'string' },
  },
}

const EVAL_SCHEMA = {
  type: 'object',
  required: ['verdict', 'gaps', 'evidence', 'summary'],
  properties: {
    verdict: { type: 'string', enum: ['pass', 'fail'] },
    gaps: {
      type: 'array',
      items: {
        type: 'object',
        required: ['severity', 'what', 'evidence', 'fix'],
        properties: {
          severity: { type: 'string', enum: ['blocking', 'minor'] },
          what: { type: 'string', description: 'The unmet criterion or defect.' },
          evidence: { type: 'string', description: 'File:line, or the command output that proves it. No speculation.' },
          fix: { type: 'string', description: 'The smallest change that closes it.' },
        },
      },
    },
    evidence: { type: 'array', items: { type: 'string' }, description: 'Commands you ran and what they printed.' },
    summary: { type: 'string' },
  },
}

// ---------------------------------------------------------------- planner
phase('Plan')
log('Requirement ' + ID + ' — planning')

const plan = await agent([
  GROUND,
  '',
  'You are the PLANNER. You do not write code. You produce a plan another agent will execute.',
  '',
  'TASK: read requirement ' + ID + ' in ' + SPEC + ' — its Scenario, Current behaviour,',
  'Required behaviour, Acceptance criteria and Files list. Then read the actual code it names',
  'and confirm, line by line, what is really there. The spec was written by hand and parts of it',
  'are already stale; where spec and code disagree, the CODE is the fact and you must say so.',
  '',
  'Also read section 3 ("Already implemented — DO NOT re-implement") and honour it.',
  'Also read section 1 (division of responsibility) — do not move work across that line.',
  '',
  EXTRA ? 'ORCHESTRATOR CONTEXT — verified facts, trust these over the spec:\n' + EXTRA : '',
  '',
  'Deliver: a file-by-file plan, the full acceptance-criteria list with a concrete way to verify',
  'each one, the verification commands in order, the real risks, and what you are deliberately',
  'leaving to a later requirement.',
].join('\n'), { label: 'plan:' + ID, phase: 'Plan', schema: PLAN_SCHEMA, effort: EFFORT })

log('Plan: ' + plan.steps.length + ' file edits, ' + plan.criteria.length + ' criteria')

// ---------------------------------------------------------------- generator
phase('Implement')

const planText = [
  'PLAN SUMMARY: ' + plan.summary,
  '',
  'EDITS:',
  ...plan.steps.map((s, i) => (i + 1) + '. ' + s.file + '\n   ' + s.change + '\n   (why: ' + s.why + ')'),
  '',
  'ACCEPTANCE CRITERIA you must satisfy:',
  ...plan.criteria.map(c => '  [' + c.id + '] ' + c.criterion + '\n      verify by: ' + c.verifyBy),
  '',
  'VERIFICATION COMMANDS: ' + plan.verification.join(' ; '),
  '',
  'RISKS: ' + plan.risks.join(' | '),
  'OUT OF SCOPE: ' + plan.outOfScope.join(' | '),
].join('\n')

let impl = await agent([
  GROUND,
  '',
  'You are the GENERATOR. Implement requirement ' + ID + ' exactly as planned below.',
  '',
  planText,
  '',
  'Read ' + SPEC + ' section ' + ID + ' yourself as well — the plan is a guide, the spec is the contract.',
  '',
  'THEN VERIFY YOUR OWN WORK before returning: run the verification commands, run the affected',
  'tests, and actually execute the end-to-end path if the criteria call for it. A returned result',
  'claiming success that the evaluators disprove is worse than an honest "unfinished".',
  '',
  'Report the real outcome of every command, failures included.',
].join('\n'), { label: 'implement:' + ID, phase: 'Implement', schema: IMPL_SCHEMA, effort: 'high' })

// ---------------------------------------------------------------- evaluators
const ALL_LENSES = [
  {
    key: 'criteria',
    brief: [
      'LENS: ACCEPTANCE CRITERIA. Take each criterion in the spec section for ' + ID + ' and each',
      'criterion in the plan, and prove or disprove it BY EXECUTION — run the command, inspect the',
      'output, read the file. A criterion you did not test is a gap, not a pass. Report the exact',
      'command output as evidence.',
    ].join('\n'),
  },
  {
    key: 'regression',
    brief: [
      'LENS: REGRESSION. The launcher has two consumers and the skill has an established text',
      'output contract. Prove nothing else broke:',
      '  - the IntelliJ plugin path (ZulPreviewServerService, build/libs/zk-preview-launcher.jar',
      '    name, prepareSandbox wiring) still works and its DEFAULTS are unchanged',
      '  - the existing launcher test suite still passes (run it; report which tests you ran)',
      '  - preview-zul.py stdout for a plain successful render is unchanged in shape when no new',
      '    flag is passed (spec section 6 and P2-5 both require this)',
      '  - exit codes 0/1/2 still mean what section 2.3 says',
      'Anything you could not run, say so explicitly rather than assuming it is fine.',
    ].join('\n'),
  },
  {
    key: 'spec',
    brief: [
      'LENS: SPEC COMPLIANCE, ADVERSARIAL. Your job is to REFUTE the claim that ' + ID + ' is done.',
      'Re-read the requirement and hunt for:',
      '  - every MUST in the section that the implementation quietly skipped',
      '  - documentation the spec demands (README, SKILL.md, references/, javadoc) but that was not written',
      '  - comments left in the code that are now factually FALSE (the spec explicitly calls out',
      '    stale javadoc as part of the work)',
      '  - scope creep: work belonging to another requirement, or speculative abstraction',
      '  - hardcoded values, dead code, or orphaned imports introduced by this change',
      'Default to reporting a gap when uncertain, but every gap needs file:line or command output',
      'as evidence. Do not invent problems.',
    ].join('\n'),
  },
]

const LENSES = ALL_LENSES.filter(l => LENS_KEYS.indexOf(l.key) !== -1)
if (!LENSES.length) throw new Error('args.lenses matched no lens: ' + LENS_KEYS.join(','))
log('Requirement ' + ID + ' — effort=' + EFFORT + ', lenses=' + LENSES.map(l => l.key).join('+'))

let round = 0
let blocking = []
const history = []

while (true) {
  round++
  phase('Evaluate')
  log('Round ' + round + ': evaluating')

  const implText = [
    'FILES THE GENERATOR CHANGED:',
    ...impl.filesChanged.map(f => '  ' + f.path + ' — ' + f.summary),
    '',
    'COMMANDS IT CLAIMS TO HAVE RUN:',
    ...impl.commands.map(c => '  $ ' + c.cmd + '\n    -> ' + c.outcome),
    '',
    'IT ADMITS UNFINISHED: ' + (impl.unfinished.length ? impl.unfinished.join(' | ') : 'nothing'),
    'IT DEVIATED FROM THE PLAN: ' + (impl.deviations.length ? impl.deviations.join(' | ') : 'nowhere'),
    'ITS NOTES: ' + impl.notes,
  ].join('\n')

  const raw = await parallel(LENSES.map(l => () => agent([
    GROUND,
    '',
    'You are an EVALUATOR for requirement ' + ID + '. You verify; you do NOT fix. Read-only on',
    'source: do not edit files. (Creating a throwaway fixture under a temp dir to test with is fine.)',
    '',
    l.brief,
    '',
    planText,
    '',
    implText,
    '',
    'Verify against the working tree as it exists NOW — `git diff` in both repos shows the change.',
    'Return verdict "fail" if any blocking gap exists, otherwise "pass".',
  ].join('\n'), {
    label: 'eval:' + l.key + (round > 1 ? '#' + round : ''),
    phase: 'Evaluate',
    schema: EVAL_SCHEMA,
    effort: EFFORT,
  })))

  // Tag each result with its lens BEFORE filtering, so a dead agent cannot shift the
  // index mapping, then copy the tag onto each gap the orchestrator prints back.
  const results = raw.map((r, i) => (r ? Object.assign({}, r, { lens: LENSES[i].key }) : null)).filter(Boolean)
  const allGaps = results.flatMap(r => (r.gaps || []).map(g => Object.assign({}, g, { lens: r.lens })))
  blocking = allGaps.filter(g => g.severity === 'blocking')

  history.push({ round: round, verdicts: results.map(r => r.verdict), gaps: allGaps, summaries: results.map(r => r.summary) })
  log('Round ' + round + ': ' + results.filter(r => r.verdict === 'pass').length + '/' + results.length +
      ' lenses pass, ' + blocking.length + ' blocking gaps')

  if (!blocking.length) break
  if (round >= MAX_ROUNDS) {
    log('Reached maxRounds=' + MAX_ROUNDS + ' with ' + blocking.length + ' blocking gaps still open — returning for human review')
    break
  }

  phase('Remediate')
  impl = await agent([
    GROUND,
    '',
    'You are the GENERATOR again, on remediation round ' + round + ' for ' + ID + '.',
    'Three evaluators reviewed your work. Close EVERY blocking gap below and nothing else —',
    'do not take the opportunity to improve unrelated code.',
    '',
    'BLOCKING GAPS:',
    ...blocking.map((g, i) => (i + 1) + '. [' + g.lens + '] ' + g.what + '\n   evidence: ' + g.evidence + '\n   suggested fix: ' + g.fix),
    '',
    'MINOR gaps (fix only if genuinely trivial and in scope):',
    ...allGapsMinor(allGaps),
    '',
    planText,
    '',
    'If you believe an evaluator is WRONG, do not silently ignore it: say so in `deviations` with',
    'the evidence that refutes it. Re-run the verification commands afterwards.',
  ].join('\n'), { label: 'remediate:' + ID + '#' + round, phase: 'Remediate', schema: IMPL_SCHEMA, effort: 'high' })
}

function allGapsMinor(gaps) {
  const m = gaps.filter(g => g.severity === 'minor')
  return m.length ? m.map(g => '  - ' + g.what) : ['  (none)']
}

return {
  requirement: ID,
  settings: { effort: EFFORT, lenses: LENSES.map(l => l.key), maxRounds: MAX_ROUNDS },
  rounds: round,
  status: blocking.length ? 'blocked' : 'passed',
  plan: plan,
  implementation: impl,
  evaluation: history,
  openBlockingGaps: blocking,
}
