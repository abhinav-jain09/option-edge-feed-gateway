#!/usr/bin/env python3
"""Mutation campaign harness — repo-agnostic, and it records what it did.

A mutation result is only worth as much as its provenance, so every entry carries the exact patch,
which occurrence of it was changed and at what line and in what enclosing declaration, the command
that was run, and how the run ended. Three outcomes are distinguished, because conflating them is
how a campaign starts lying:

  KILLED         a TEST failed. The clause is pinned.
  SURVIVED       the suite passed. Nothing caught the regression.
  BUILD-FAILED   the mutant did not compile, or the run died before testing. Says nothing about
                 pinning; the mutation must be rewritten.

A clean baseline is proven before any mutation is applied — a campaign against an already-red suite
would report every clause as pinned.
"""
import json, subprocess, sys, os, shutil, signal, re, time, hashlib

def failure_lines(out, kind):
    """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
    A tail of the output does not: Maven prints its failures well before the build summary, so the
    last N characters of a run contain none of the names the harness reported."""
    pat = r'^\[ERROR\]\s+[\w.$]+\.[\w$]+(?::\d+|\s+--|\s).*$' if kind == 'maven' else r'^not ok \d+ - .*$'
    lines = [l.rstrip()[:400] for l in re.findall(pat, out, re.M)]
    # The cap must not cut the record loose from its own names: a truncated list left a record naming
    # four failing tests whose lines were not in it, which a reader cannot check and a generator that
    # correlates the two will (correctly) refuse.
    if kind != 'maven':
        return lines[:40]
    return lines[:40]

def assertion_failures(out, kind):
    """Failures that are an ASSERTION, not an infrastructure error.

    A mutation that makes the test class fail to construct, a format string blow up, or a fixture
    throw, produces a non-zero exit and names the right test — and is NOT evidence that the clause
    is held. One recorded kill in this campaign was exactly that: adding a `%s` to a format template
    failed with MissingFormatArgument under the very test whose assertion was supposed to catch the
    shared module. Surefire distinguishes `<<< FAILURE!` (assertion) from `<<< ERROR!` (threw);
    node's TAP reports `code: 'ERR_ASSERTION'` for an assert and something else for a throw.
    """
    if kind == 'maven':
        # the summary lines under "[ERROR] Failures:" are assertions; those under "[ERROR] Errors:"
        # are throws. Walk the trailing summary and attribute each name to the section it is under.
        names, section = set(), None
        for line in out.split('\n'):
            if re.match(r'^\[ERROR\]\s+Failures:\s*$', line): section = 'assert'; continue
            if re.match(r'^\[ERROR\]\s+Errors:\s*$', line): section = 'error'; continue
            if re.match(r'^\[ERROR\]\s+Tests run:', line): section = None; continue
            m = re.match(r'^\[ERROR\]\s{2,}([\w.$]+\.[\w$]+)[:\s]', line)
            if m and section == 'assert':
                names.add('.'.join(m.group(1).split('.')[-2:]))
        return sorted(names)
    # node --test: a failing subtest block carries `code: 'ERR_ASSERTION'` for an assertion
    names = set()
    for block in re.split(r'^not ok \d+ - ', out, flags=re.M)[1:]:
        head, rest = block.split('\n', 1) if '\n' in block else (block, '')
        body = rest.split('\nnot ok ')[0].split('\nok ')[0]
        if "ERR_ASSERTION" in body or "AssertionError" in body:
            names.add(head.strip())
    return sorted(names)

def failing_tests(out, kind):
    if kind == 'maven':
        # Surefire's failure lines take two shapes:
        #   [ERROR]   ClassName.method:123 message
        #   [ERROR] pkg.ClassName.method -- Time elapsed ... <<< FAILURE!
        # A naive (\w+\.\w+) truncates the second to its package, which then gets recorded as the
        # failing test. Take the last two dotted segments before the colon or the double dash.
        names = set()
        for m in re.finditer(r'^\[ERROR\]\s+([\w.$]+\.[\w$]+)(?::\d+|\s+--|\s)', out, re.M):
            parts = m.group(1).split('.')
            if len(parts) >= 2 and parts[-1][:1].islower() or len(parts) >= 2:
                names.add('.'.join(parts[-2:]))
        return sorted(names)
    return sorted({m.group(1).strip() for m in re.finditer(r'^not ok \d+ - (.+)$', out, re.M)})

def build_failed(out, kind):
    if kind == 'maven':
        return 'COMPILATION ERROR' in out or 'BUILD FAILURE' in out and 'Tests run:' not in out
    return 'SyntaxError' in out

def run(cmd, cwd):
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=3600)
    return p.returncode, p.stdout + p.stderr

def sha(text):
    return hashlib.sha256(text.encode('utf-8', 'replace')).hexdigest()

def head_commit(root):
    rc, out = run(['git', 'rev-parse', 'HEAD'], root)
    return out.strip() if rc == 0 else None

def tree_dirty(root):
    rc, out = run(['git', 'status', '--porcelain'], root)
    return [l for l in out.split('\n') if l.strip()]

# A campaign that is killed between the write and the restore leaves the tree mutated AND leaves
# compiled classes built from the mutation. Restoring the source is not enough: `mv` puts back the
# ORIGINAL mtime, so an incremental build keeps testing the mutant. Handle the signal, restore, and
# discard the compiled output so the next run cannot inherit a mutant.
_ACTIVE = {'paths': [], 'root': None}

def _restore_and_exit(signum, frame):
    for path in _ACTIVE['paths']:
        if os.path.exists(path + '.bak'):
            shutil.move(path + '.bak', path)
            os.utime(path, None)
    root = _ACTIVE['root']
    if root:
        for d, _, _ in list(os.walk(root)):
            if os.path.basename(d) == 'classes' and os.path.basename(os.path.dirname(d)) == 'target':
                shutil.rmtree(d, ignore_errors=True)
    print(f"\ninterrupted by signal {signum}: tree restored and compiled classes discarded")
    sys.exit(130)

def main():
    spec = json.load(open(sys.argv[1]))
    outp = sys.argv[2]
    root, cmd, kind = spec['root'], spec['command'], spec['kind']
    _ACTIVE['root'] = root
    signal.signal(signal.SIGTERM, _restore_and_exit)
    signal.signal(signal.SIGINT, _restore_and_exit)
    res = json.load(open(outp)) if os.path.exists(outp) else {}

    dirty = tree_dirty(root)
    if dirty:
        print("TREE IS DIRTY — a campaign must run against a committed tree, or its record names a state"); 
        print("nobody can return to:"); [print("  ", d) for d in dirty[:10]]; sys.exit(3)
    commit = head_commit(root)
    rc, out = run(cmd, root)
    if rc != 0:
        print("BASELINE IS RED — refusing to run a campaign against a failing suite"); print(out[-3000:]); sys.exit(2)
    baseline = {'commit': commit, 'command': ' '.join(cmd), 'returnCode': rc,
                'outputSha256': sha(out), 'outputTail': out[-1500:]}
    base_tests = re.search(r'Tests run: (\d+)', out) or re.search(r'# pass (\d+)', out)
    print(f"baseline GREEN ({base_tests.group(1) if base_tests else '?'} tests) — {' '.join(cmd)}\n")

    for m in spec['mutations']:
        k = m['key']
        # Resume only when the stored record was produced by THIS patch at THIS commit. Keying the
        # skip on the name alone silently reuses a record for a patch that has since been edited,
        # and the campaign then reports a result no run ever produced.
        prev = res.get(k)
        # A clause may be implemented at SEVERAL sites (a guard duplicated for defence in depth). One
        # clause is still ONE mutation: `sites` breaks every site of the clause together, because
        # breaking one site of a duplicated guard changes no behaviour and would be recorded as a
        # survivor of a test that is in fact perfectly capable of catching the clause's removal.
        sites = m.get('sites') or [{'file': m['file'], 'old': m['old'], 'new': m['new'],
                                    'occurrence': m.get('occurrence', 1)}]
        patch = {'sites': sites} if m.get('sites') else {'old': m['old'], 'new': m['new']}
        def resumable(p):
            # A stored record is only reusable if it is internally consistent: a KILLED entry must
            # carry a non-zero exit, named assertion failures, and the verbatim lines those names
            # came from. Matching the patch and the commit says nothing about the rest of the file.
            if p is None or p.get('patch') != patch: return False
            e, b = p.get('evidence', {}), p.get('baseline', {})
            if e.get('repoCommit') != commit: return False
            if e.get('treeRestoredClean') is not True: return False   # missing is not clean
            if not e.get('outputSha256') or b.get('returnCode') != 0: return False
            if b.get('commit') != commit: return False   # missing is not a match
            lines = e.get('failureLines') or []
            if p.get('status') == 'KILLED':
                if not e.get('returnCode'): return False
                names = p.get('assertionFailures') or []
                t = p.get('killedByThrow')
                if names:
                    # every name must sit on a line of this record, on an assertion line
                    for n in names:
                        short = n.split('.')[-1]
                        carrying = [l for l in lines if short in l]
                        if not carrying: return False
                        if kind == 'maven' and not any('<<< FAILURE!' in l or re.search(r':\d+ ', l)
                                                       for l in carrying):
                            return False
                elif isinstance(t, dict) and t.get('test') and t.get('throws'):
                    joined = '\n'.join(lines)
                    if t['test'].split('.')[-1] not in joined or t['throws'] not in joined: return False
                else:
                    return False
            elif p.get('status') == 'SURVIVED':
                if e.get('returnCode'): return False
            else:
                if not e.get('returnCode'): return False   # every other status means the run failed
            return True
        if resumable(prev):
            continue
        res.pop(k, None)
        f = os.path.join(root, sites[0]['file'])
        src = open(f).read()
        # The requested OCCURRENCE must exist, not merely the anchor: an out-of-range occurrence
        # left the splice index at -1, applied a different edit, and recorded its verdict as though
        # the requested mutation had run.
        def occurrences(text, needle):
            # The splice locator advances by ONE character, so it finds overlapping matches; str.count
            # does not. Counting differently from the locator refuses a legal occurrence.
            n, at = 0, 0
            while True:
                i = text.find(needle, at)
                if i < 0: return n
                n += 1; at = i + 1
        missing = [s0 for s0 in sites
                   if occurrences(open(os.path.join(root, s0['file'])).read(), s0['old']) < s0.get('occurrence', 1)]
        if missing:
            res[k] = {'status': 'ANCHOR-MISSING', 'file': missing[0]['file']}
            print(f"  {k:<40} ANCHOR-MISSING"); json.dump(res, open(outp,'w'), indent=1); continue
        m = dict(m, old=sites[0]['old'], new=sites[0]['new'])
        n = src.count(sites[0]['old'])
        occ = sites[0].get('occurrence', 1)
        # locate the chosen occurrence
        pos, seen = -1, 0
        start = 0
        while True:
            i = src.find(m['old'], start)
            if i < 0: break
            seen += 1
            if seen == occ: pos = i; break
            start = i + 1
        line = src[:pos].count('\n') + 1
        lines = src.split('\n')
        # The enclosing DECLARATION, found by scanning FORWARD from the top of the file and tracking
        # brace depth, so the answer is the declaration whose body actually contains the mutated line.
        # A backwards "nearest line that looks like a signature" scan is what reported appendHwm()
        # for a mutation inside barsPage(); a forensic record that misplaces the mutation is worse
        # than none.
        encl, depth, stack = None, 0, []
        for idx, t in enumerate(lines, start=1):
            opens = t.count('{')
            stripped = t.strip()
            is_decl = ('(' in stripped and not re.match(r'^(if|for|while|switch|catch|try|else|do|synchronized|return)\b', stripped)) \
                      or re.match(r'^\s*(public|private|protected|static|final|abstract)?\s*(class|interface|enum|record)\b', t)
            if opens and is_decl:
                stack.append((depth, ' '.join(t.split())[:140]))
            depth += opens - t.count('}')
            if idx == line:
                # Capture BEFORE popping. A one-line method opens and closes on the same line, so a
                # pop-first order removes it from the stack before the mutated line can name it —
                # which is how `static int clamp(...)` was recorded as `public class GatewayController`.
                encl = stack[-1][1] if stack else None
                break
            while stack and depth <= stack[-1][0]:
                stack.pop()

        touched = sorted({os.path.join(root, s0['file']) for s0 in sites})
        _ACTIVE['paths'] = touched
        originals, mutants = {}, {}
        for path in touched:
            shutil.copy(path, path + '.bak')
        t0 = time.time()
        try:
            for s0 in sites:
                path = os.path.join(root, s0['file'])
                text = open(path).read()
                originals.setdefault(s0['file'], sha(text))
                at, seen2, start2 = -1, 0, 0
                while True:
                    j = text.find(s0['old'], start2)
                    if j < 0: break
                    seen2 += 1
                    if seen2 == s0.get('occurrence', 1): at = j; break
                    start2 = j + 1
                if at < 0:
                    raise RuntimeError(f"occurrence {s0.get('occurrence', 1)} of the anchor is not in {s0['file']}")
                open(path, 'w').write(text[:at] + s0['new'] + text[at+len(s0['old']):])
            for s0 in sites:
                path = os.path.join(root, s0['file'])
                mutants[s0['file']] = sha(open(path).read())
            rc, out = run(cmd, root)
        finally:
            # UNCONDITIONAL. A timeout, an interrupt or a crash between the write and the restore
            # would otherwise leave the tree mutated and poison every later mutation AND the baseline.
            for path in touched:
                shutil.move(path + '.bak', path)
                os.utime(path, None)   # mv restores the ORIGINAL mtime; Maven would skip recompiling
            _ACTIVE['paths'] = []
        still_dirty = tree_dirty(root)
        failed = failing_tests(out, kind)
        asserted = assertion_failures(out, kind)
        # A declared throw-kill must name BOTH the test that should propagate it and a signature of
        # the throw itself, and both must appear in the run's own failure lines. `killedByThrow:
        # true` alone would accept any error under any failing test.
        want = m.get('killedByThrow') or {}
        throw_kill_ok = False
        if isinstance(want, dict) and want.get('test') and want.get('throws'):
            lines = '\n'.join(failure_lines(out, kind))
            throw_kill_ok = (want['test'] in failed or any(want['test'] in f2 for f2 in failed)) \
                and want['throws'] in lines
        # A `kind` that does not match the runner parses no failure names, and every kill is then
        # recorded as BUILD-FAILED — silently, because the run really did exit non-zero. Refuse
        # instead: the output plainly names failing tests in the other runner's shape.
        if rc != 0 and not failed:
            other = failing_tests(out, 'node' if kind == 'maven' else 'maven')
            if other:
                print(f"KIND MISMATCH: kind={kind!r} parsed no failures, but the output names {other[:3]}")
                print("  the spec's `kind` does not match the test runner; refusing to record a wrong verdict")
                sys.exit(4)
        if build_failed(out, kind) and not failed:
            status = 'BUILD-FAILED'
        elif rc != 0 and asserted:
            status = 'KILLED'
            want_test = m.get('expectTest')
            if want_test and not any(want_test in a for a in asserted):
                # the suite went red, but not at the test this clause is supposed to be held by
                status = 'KILLED-BY-ANOTHER-TEST'
        elif rc != 0 and failed and throw_kill_ok:
            # The clause under test IS "this must not throw", so a test that propagates the throw is
            # detecting exactly the right thing. The spec must SAY so in advance AND name the throw
            # it expects — a bare boolean would accept any incidental error under any failing test,
            # which is the laundering this whole distinction exists to prevent.
            status = 'KILLED'
        elif rc != 0 and failed:
            # the right test failed, but on a THROW rather than an assertion, and the spec did not
            # declare that mode: the mutation may have broken the fixture rather than the behaviour
            status = 'KILLED-BY-ERROR'
        elif rc != 0:
            status = 'BUILD-FAILED'
        else:
            status = 'SURVIVED'
        res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                  'documentText': m.get('documentText'),
                  'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': (asserted or failed)[:4], 'failureCount': len(failed), 'assertionFailures': [n for n in asserted if any(n.split('.')[-1] in l for l in failure_lines(out, kind))],
                  'anyFailures': failed,
                  'killedByThrow': (m.get('killedByThrow') if throw_kill_ok else None),
                  'seconds': round(time.time()-t0, 1),
                  'patch': patch,
                  'evidence': {'repoCommit': commit, 'returnCode': rc,
                               'mutatedFileSha256': mutants.get(sites[0]['file']), 'originalFileSha256': originals.get(sites[0]['file']),
                               'filesSha256': {p: {'original': originals[p], 'mutated': mutants.get(p)} for p in originals},
                               'outputSha256': sha(out), 'outputTail': out[-1200:],
                               'failureLines': failure_lines(out, kind),
                               'treeRestoredClean': not still_dirty},
                  'baseline': baseline}
        print(f"  {k:<40} {status:<13} {(failed[0][:52] if failed else '')}")
        json.dump(res, open(outp,'w'), indent=1)
    from collections import Counter
    print("\n", Counter(v['status'] for v in res.values()))

main()
