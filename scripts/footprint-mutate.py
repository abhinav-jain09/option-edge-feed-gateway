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
import json, subprocess, sys, os, shutil, re, time, hashlib

def failure_lines(out, kind):
    """The VERBATIM lines the killedBy names were parsed from, so the record substantiates itself.
    A tail of the output does not: Maven prints its failures well before the build summary, so the
    last N characters of a run contain none of the names the harness reported."""
    pat = r'^\[ERROR\]\s+[\w.$]+\.[\w$]+(?::\d+|\s+--|\s).*$' if kind == 'maven' else r'^not ok \d+ - .*$'
    return [l.rstrip()[:400] for l in re.findall(pat, out, re.M)][:12]

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

def main():
    spec = json.load(open(sys.argv[1]))
    outp = sys.argv[2]
    root, cmd, kind = spec['root'], spec['command'], spec['kind']
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
        if prev is not None and prev.get('patch') == patch \
                and prev.get('evidence', {}).get('repoCommit') == commit:
            continue
        res.pop(k, None)
        f = os.path.join(root, sites[0]['file'])
        src = open(f).read()
        missing = [s0 for s0 in sites if open(os.path.join(root, s0['file'])).read().count(s0['old']) == 0]
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
        still_dirty = tree_dirty(root)
        failed = failing_tests(out, kind)
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
        elif rc != 0 and failed:
            status = 'KILLED'
        elif rc != 0:
            status = 'BUILD-FAILED'
        else:
            status = 'SURVIVED'
        res[k] = {'status': status, 'clause': m.get('clause',''), 'requirement': m.get('requirement', k.split()[0]),
                  'documentText': m.get('documentText'),
                  'file': m['file'], 'occurrence': occ, 'occurrencesInFile': n, 'line': line,
                  'enclosing': encl, 'command': ' '.join(cmd), 'killedBy': failed[:4],
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
