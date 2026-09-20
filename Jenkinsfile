@Library('oe') _

pipeline {
  // agent none: the build agent is chosen per-environment from oeProfile(ENVIRONMENT).
  // buildAgentLabel, which cannot be read before the pipeline starts. BOTH dev and production
  // now compile on the .74 arm64 builders so the dev Mac (.102) does no build work during
  // market hours — it runs the controller, the dev cluster, the registry and the broker.
  // The architecture-independent jar and amd64 production image are both built on CI.
  // Production receives only the pushed image through the Jenkins deployment job.
  // The Deploy stage needs no cluster access — it triggers `service-deploy`, which is
  // itself pinned to an agent on .102 that holds the kubeconfigs.
  agent none
  options {
    disableRestartFromStage()   // Deployment Permission Rule: no "Restart from Stage" past the permitted-commit guard
    // Serialize builds so each build's push -> Deploy+verify is atomic: two concurrent
    // builds must not both move the mutable :dev tag while the other's Deploy+verify
    // stage resolves it (Codex: a moving-tag race would let the wrong build verify green).
    disableConcurrentBuilds()
  }
  parameters {
    string(name: 'PERMITTED_SHA', defaultValue: '', trim: true,
      description: 'REQUIRED — Deployment Permission Rule (options-edge rule.md). The full 40-character commit id of THIS repository that Abhinav permitted for this image build. The Permitted commit guard stage — FIRST inside Build, before contracts install, tests, package and image — refuses the build unless the checked-out HEAD is exactly this commit AND on origin/main; empty, short or mismatched values are refused and nothing is substituted. An SCM-triggered build has no value and therefore stops at the guard before anything is built. A manual click needs it too: copy it from `git rev-parse origin/main`.')
    string(name: 'PERMITTED_SHA_GUARD_VERSION', defaultValue: '1d0fd0c60c4a2764dffb0bd26b15525bd08470a3c49be7ae7d9ef177d1a1a6b9',
      description: 'DO NOT EDIT BY HAND — the sha256 of scripts/jenkins/permitted-sha-guard.sh this definition runs (Deployment Permission Rule). The guard refuses to run under any other value. It is a DECLARATION, not proof that this job enforces the guard: a caller that triggers this job judges its SCM definition and its Jenkinsfile at the forwarded commit (scripts/jenkins/require-guarded-downstream.sh). Regenerate with scripts/jenkins/permitted-sha-guard-version.sh when the guard changes.')
    string(name: 'CONTRACTS_PERMITTED_SHA', defaultValue: '', trim: true,
      description: 'REQUIRED — Deployment Permission Rule. The full 40-character commit id of options-edge-contracts permitted for this build: the Install Contracts stage clones contracts at CONTRACTS_BRANCH and compiles that source into the gateway, so it is a second source of the image and is bound on its own. Refused before mvn install unless the clone is exactly this commit on main; empty, short or mismatched values are refused and nothing is substituted.')
    string(name: 'DEPLOY_PERMITTED_SHA', defaultValue: '', trim: true,
      description: 'REQUIRED when the dev Deploy+verify stage runs (ENVIRONMENT=dev, PUSH_IMAGE, DEPLOY_AND_VERIFY) — Deployment Permission Rule. The full 40-character commit id of options-edge-deploy permitted for the dev rollout this build triggers (service-deploy SERVICE=feed-gateway). Forwarded to that job as its PERMITTED_SHA, where ITS guard refuses unless its checkout is exactly this commit. Empty or malformed values stop this build before the downstream deploy is triggered.')
    choice(name: 'ENVIRONMENT', choices: ['dev', 'production'], description: 'Target environment — drives registry + build platform from oeProfile (single source of truth)')
    string(name: 'IMAGE_REGISTRY', defaultValue: '', description: 'Override registry. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers (e.g. bring-up-all).')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'MUST stay empty (Deployment Permission Rule, artifact identity): the per-build tag is derived inside the pipeline from BUILD_ID and the permitted SHA, so the image lock can name THIS build\'s image; a caller-supplied tag is refused. Kept only so an old caller that passes it is refused loudly rather than silently ignored.')
    string(name: 'DEV_IMAGE_TAG', defaultValue: 'dev', description: 'Also publish this mutable dev tag for the deploy job. Empty disables it.')
    string(name: 'BUILD_PLATFORM', defaultValue: '', description: 'Override platform. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers.')
    string(name: 'CONTRACTS_BRANCH', defaultValue: 'main', description: 'options-edge-contracts branch to install before building the gateway')
    booleanParam(name: 'PUSH_IMAGE', defaultValue: true, description: 'Push built image to registry')
    booleanParam(name: 'DEPLOY_AND_VERIFY', defaultValue: true, description: 'Dev only: after a successful build+push, trigger service-deploy to roll the dev pod and VERIFY it picked up the new image (fails the build if the running pod does not report the pinned digest). Closes the silent-stale-build gap. No effect on production (manual promote gate).')
    string(name: 'REMOTE_BUILD_HOST', defaultValue: '192.168.100.252', description: 'Deprecated compatibility parameter; all image builds run on the resolved CI agent.')
    string(name: 'REMOTE_BUILD_ROOT', defaultValue: '/home/abhinav/ci/remote-builds', description: 'Deprecated compatibility parameter; no remote build workspace is used.')
  }
  stages {
    stage('Resolve profile') {
      // Runs anywhere: pure Groovy, no workspace needed. Publishes the agent label the
      // Build stage then pins itself to.
      // skipDefaultCheckout: a declarative `agent` normally triggers an implicit `checkout scm`,
      // so this stage was doing a full git fetch on whatever node it landed on — including the
      // dev Mac, which must not do build I/O during market hours. It only calls oeProfile(), so
      // it needs no working copy at all.
      agent any
      options { skipDefaultCheckout() }
      steps {
        script {
          // Deployment Permission Rule: the contracts source is selected by its exact branch name.
          // An alias (origin/main, refs/heads/main) or any other branch is refused before anything
          // is checked out — the guard judges the literal name, and so does this.
          if ((params.CONTRACTS_BRANCH ?: 'main') != 'main') {
            error("CONTRACTS_BRANCH must be exactly 'main' (got '${params.CONTRACTS_BRANCH}'): every environment builds contracts from main, selected by that name.")
          }
          def p = oeProfile(params.ENVIRONMENT)
          env.IMAGE_REGISTRY = params.IMAGE_REGISTRY?.trim() ? params.IMAGE_REGISTRY : p.registry
          // Push address for THIS builder. Identical to IMAGE_REGISTRY except where the
          // builder is not the registry host (dev on .74): host.docker.internal is
          // host-relative and would resolve to the builder itself. Same storage either
          // way, so the digest the deploy resolves via IMAGE_REGISTRY is unchanged.
          // An explicit IMAGE_REGISTRY override applies to both, preserving back-compat
          // for callers like bring-up-all that pass one registry for everything.
          env.PUSH_REGISTRY = params.IMAGE_REGISTRY?.trim() ? params.IMAGE_REGISTRY : p.registryFromBuildAgent
          // Strict preference with fallback, resolved at runtime. NOT a `a || b` label
          // expression: that is an unordered union and would let .102 win while the .74
          // builders sat idle, which defeats the offload.
          env.BUILD_AGENT_LABEL = oeBuildAgent(p)
          env.BUILD_PLATFORM = params.BUILD_PLATFORM?.trim() ? params.BUILD_PLATFORM : p.platform
          // Build the set of plain-http registries from the deployable profiles listed in
          // knownEnvs below (dev + production; `experiment` is deliberately out of scope for
          // this job, whose ENVIRONMENT choices are dev/production), normalize
          // for robust matching (strip scheme + trailing slash + lowercase), and add
          // dev-registry loopback aliases. The Image stage writes a buildkit insecure-
          // registry config for the EFFECTIVE IMAGE_REGISTRY iff its normalized form is
          // in this set (so prod pushes work via http, not just dev).
          def normalize = { String r ->
            r?.toString()?.trim()?.toLowerCase()?.replaceFirst(/^https?:\/\//, '')?.replaceFirst(/\/+$/, '')
          }
          def knownEnvs = ['dev', 'production']
          // Include BOTH vantage points on each registry: an off-host builder pushes to
          // registryFromBuildAgent, and buildkit needs that exact address marked insecure
          // or the plain-http push fails with 'server gave HTTP response to HTTPS client'.
          def insecure = knownEnvs.findAll { oeProfile(it).insecureRegistry }
                                  .collectMany { [normalize(oeProfile(it).registry),
                                                  normalize(oeProfile(it).registryFromBuildAgent)] }
          insecure += ['localhost:5001', '127.0.0.1:5001']   // loopback aliases of the dev registry
          env.INSECURE_REGISTRIES = insecure.unique().findAll { it }.join(' ')
          echo "resolved (env=${params.ENVIRONMENT}): registry=${env.IMAGE_REGISTRY} push=${env.PUSH_REGISTRY} buildAgent=${env.BUILD_AGENT_LABEL} platform=${env.BUILD_PLATFORM} insecureRegistries='${env.INSECURE_REGISTRIES}'"
        }
      }
    }
    // All four build stages share ONE agent and ONE workspace (Package's jar is consumed by
    // Image), so they are nested under a single parent stage that pins the agent. Their
    // bodies are unchanged and deliberately left at their original indentation to keep this
    // diff reviewable line-by-line.
    stage('Build') {
      agent { label "${env.BUILD_AGENT_LABEL}" }
      stages {
    // ---- Deployment Permission Rule (options-edge rule.md): Jenkins enforces the permitted commit ----
    // PERMITTED_SHA is REQUIRED. scripts/jenkins/permitted-sha-guard.sh refuses, in this order: a checkout
    // that is not on origin/main (the environment-branch restriction, kept as its own condition —
    // BRANCH_NAME and GIT_BRANCH are each judged, one never masks the other); a missing, empty, short or
    // otherwise malformed PERMITTED_SHA (nothing is substituted for it); a checked-out HEAD that is not
    // exactly PERMITTED_SHA. FIRST inside Build, in the one workspace the contracts install, the tests,
    // the jar and the image all come from, before ANY build step: an SCM-triggered build carries no
    // PERMITTED_SHA and therefore stops right here — nothing compiled, nothing written to ~/.m2, no
    // image built or pushed, no dev pod rolled — until it is triggered with the permitted commit.
    // error(), never catchError: a refusal is a stop, not a coloured result. Both SHAs are in the log.
    stage('Permitted commit guard') {
      options { timeout(time: 10, unit: 'MINUTES') }
      steps {
        script {
          def rc = sh(returnStatus: true, script: 'bash scripts/jenkins/permitted-sha-guard.sh')
          if (rc != 0) {
            error("Permitted commit guard REFUSED this build (rc=${rc}) — see its output above. Nothing was built, installed, pushed or deployed.")
          }
          env.PERMITTED_SHA_GUARD = 'PASSED'
        }
      }
    }
    // The dev rollout's inputs are judged HERE, before the first build step, so a missing
    // DEPLOY_PERMITTED_SHA or an unguarded service-deploy never surfaces only after the image was
    // already pushed. Same predicate as the Deploy + verify (dev) stage below, which judges them again.
    stage('Deploy preflight (dev rollout inputs)') {
      when {
        expression { env.PERMITTED_SHA_GUARD == 'PASSED' && (
          params.ENVIRONMENT == 'dev' && params.PUSH_IMAGE && params.DEPLOY_AND_VERIFY &&
            params.DEV_IMAGE_TAG == 'dev' &&
            (env.JOB_NAME?.endsWith('option-edge-feed-gateway')))
        }
      }
      steps {
        script {
          def dsha = (params.DEPLOY_PERMITTED_SHA ?: '').trim()
          if (!dsha.matches('^[0-9a-f]{40}$')) {
            error("This build would roll the dev pod, which needs DEPLOY_PERMITTED_SHA: the full 40-character options-edge-deploy commit permitted for that rollout (got '${dsha}'). Nothing was built.")
          }
          // Early, so a rollout that could not be triggered stops the build before anything is built: the
          // SAME definition check the trigger stage repeats right before `build job:` (service-deploy's
          // job configuration, DEPLOY_PERMITTED_SHA as options-edge-deploy main's tip, its
          // Jenkinsfile.service-deploy at that commit judged by this repository's validator).
          def compat = sh(returnStatus: true, script: 'bash scripts/jenkins/require-guarded-downstream.sh service-deploy "${DEPLOY_PERMITTED_SHA:?}" REQUIRED_IMAGE')
          if (compat != 0) {
            error("service-deploy's definition at DEPLOY_PERMITTED_SHA could not be confirmed to run the permitted-commit guard (rc=${compat}) — this build would end by triggering it, so it stops before building. Nothing was built.")
          }
        }
      }
    }
    stage('Install Contracts') {
      when { expression { env.PERMITTED_SHA_GUARD == 'PASSED' } }
      steps {
        // The contracts clone is compiled INTO the gateway (IvRvReading's constructor decides which payloads it
        // admits). Acquired in its own step, bound by the dedicated guard step, and only then installed.
        sh '''
          set -eu
          rm -rf .deps/options-edge-contracts
          git clone git@github.com:abhinav-jain09/options-edge-contracts.git .deps/options-edge-contracts
          git -C .deps/options-edge-contracts checkout main
        '''
        // SECOND SOURCE, SECOND BINDING (Deployment Permission Rule): the contracts clone above is compiled in,
        // so it needs its own permitted commit — judged on that checkout, against CONTRACTS_PERMITTED_SHA, with
        // the selected ref judged as the literal name, BEFORE mvn install. A DEDICATED step whose whole script is
        // the guard command: the step fails if and only if the guard refuses (validator rule 9).
        timeout(time: 10, unit: 'MINUTES') {
          sh 'PERMITTED_SHA="${CONTRACTS_PERMITTED_SHA:-}" bash scripts/jenkins/permitted-sha-guard.sh --dir .deps/options-edge-contracts --ref main'
        }
        // Toolchain (a step that is not an effect): resolve Java 21 and hand it to the effect steps as their environment
        // — a dedicated effect step's body is its one command, so nothing is exported inside it.
        sh '''
          set -eu
          if [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
            JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
          elif [ -x /usr/lib/jvm/java-21/bin/java ]; then
            JAVA_HOME=/usr/lib/jvm/java-21
          elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
            JAVA_HOME="$JAVA_HOME"
          else
            echo "Java 21 was not found on this Jenkins agent" >&2
            exit 1
          fi
          mkdir -p .jenkins-tmp
          printf '%s\n' "$JAVA_HOME" > .jenkins-tmp/java-home
          "$JAVA_HOME/bin/java" -version
          # RECORD THE REVISION, because the branch is mutable and the contract is EXECUTABLE here.
          # The gateway validates vol-premium readings by deserialising them through
          # IvRvReading's own constructor, so what this clone resolved to decides which payloads
          # the gateway admits at runtime. Without the SHA, two images built from the same gateway
          # commit can behave differently at that boundary with nothing in their provenance to say
          # why. Mirrors what options-edge-processing already records.
          git -C .deps/options-edge-contracts rev-parse HEAD > .contracts-sha
          echo "contracts revision: $(cat .contracts-sha)"
        '''
        script {
          // Per-WORKSPACE Maven repository (artifact identity): the contracts jar this build installs
          // must be the one its tests, its footprint campaign and its packaging consume. A shared
          // ~/.m2 lets ANY other job on this builder overwrite the same coordinate in between.
          def jh = readFile('.jenkins-tmp/java-home').trim()
          withEnv(["JAVA_HOME=${jh}", "PATH+JDK=${jh}/bin", 'MAVEN_SKIP_RC=true',
                   "MAVEN_OPTS=-Dmaven.repo.local=${env.WORKSPACE}/.m2/repository${env.MAVEN_OPTS ? ' ' + env.MAVEN_OPTS : ''}"]) {
            // PROVENANCE (Deployment Permission Rule, validator rule 9b): the verify re-proves, immediately before the
            // contracts source is compiled in, that its checkout is still exactly the permitted commit's tree (target/ is
            // its own build output), and the install is a DEDICATED step whose whole body is the one mvn command — nothing
            // can run between the verification and the consumption.
            timeout(time: 10, unit: 'MINUTES') {
              sh 'PERMITTED_SHA="${CONTRACTS_PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir .deps/options-edge-contracts --allow-ignored target'
            }
            sh 'mvn -B -f .deps/options-edge-contracts/pom.xml install'
            // ARTIFACT IDENTITY, part 1: the contracts jar just installed, by CONTENT. Recorded here,
            // re-verified before packaging, and verified INSIDE the packaged jar — the binding of the
            // permitted contracts commit is carried to the artifact, not assumed.
            sh '''
              set -eu
              CV="$(mvn -q -f .deps/options-edge-contracts/pom.xml help:evaluate -Dexpression=project.version -DforceStdout)"
              CJAR="$WORKSPACE/.m2/repository/com/optionsedge/options-edge-contracts/$CV/options-edge-contracts-$CV.jar"
              [ -f "$CJAR" ] || { echo "installed contracts jar not found at $CJAR" >&2; exit 1; }
              printf '%s\n' "$CV" > .contracts-version
              bash scripts/jenkins/permitted-sha-guard-version.sh "$CJAR" > .contracts-jar-sha256
              echo "contracts jar $CV sha256: $(cat .contracts-jar-sha256)"
            '''
          }
        }
      }
    }
    stage('Test') {
      when { expression { env.PERMITTED_SHA_GUARD == 'PASSED' } }
      steps {
        // `mvn test` COMPILES this repository's source and runs it. By the validator's effect definition it is
        // not an effect — it installs, ships and publishes nothing, so there is no artifact to bind — and that
        // is exactly why Codex M1 could replace a tracked file after the guard and have it compiled and executed
        // here, before the first verify (which used to be in Package). The compile does not get to be the one
        // step that reads an unproved tree: the toolchain is resolved in a step that consumes nothing, the
        // workspace is verified, and then the test command runs ALONE, with nothing between the verification and
        // the compile. The declared ignored paths are this build's own output.
        sh '''
          set -eu
          if [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
            JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
          elif [ -x /usr/lib/jvm/java-21/bin/java ]; then
            JAVA_HOME=/usr/lib/jvm/java-21
          elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
            JAVA_HOME="$JAVA_HOME"
          else
            echo "Java 21 was not found on this Jenkins agent" >&2
            exit 1
          fi
          mkdir -p .jenkins-tmp
          printf '%s\n' "$JAVA_HOME" > .jenkins-tmp/java-home
          "$JAVA_HOME/bin/java" -version
        '''
        script {
          def jh = readFile('.jenkins-tmp/java-home').trim()
          // Per-WORKSPACE Maven repository (artifact identity): the contracts jar this build installed
          // must be the one its tests, its footprint campaign and its packaging consume. A shared
          // ~/.m2 lets ANY other job on this builder overwrite the same coordinate in between.
          withEnv(["JAVA_HOME=${jh}", "PATH+JDK=${jh}/bin", 'MAVEN_SKIP_RC=true',
                   "MAVEN_OPTS=-Dmaven.repo.local=${env.WORKSPACE}/.m2/repository${env.MAVEN_OPTS ? ' ' + env.MAVEN_OPTS : ''}"]) {
            timeout(time: 10, unit: 'MINUTES') {
              sh 'PERMITTED_SHA="${PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir . --allow-ignored target --allow-ignored dev/raw-feed-replicator/target --allow-ignored .m2 --allow-ignored .deps --allow-ignored .jenkins-tmp --allow-ignored .contracts-sha --allow-ignored .contracts-version --allow-ignored .contracts-jar-sha256 --allow-ignored .jar-sha256 --allow-ignored scripts/__pycache__ --allow-ignored scripts/jenkins/__pycache__'
            }
            sh 'mvn -B test'
          }
        }
      }
    }
    stage('Footprint reverification') {
      when { expression { env.PERMITTED_SHA_GUARD == 'PASSED' } }
      steps {
        sh '''
          set -eu
          if [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
            export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
          elif [ -x /usr/lib/jvm/java-21/bin/java ]; then
            export JAVA_HOME=/usr/lib/jvm/java-21
          elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
            export JAVA_HOME="$JAVA_HOME"
          else
            echo "Java 21 was not found on this Jenkins agent" >&2
            exit 1
          fi
          export MAVEN_SKIP_RC=true
          export PATH="$JAVA_HOME/bin:$PATH"
          # Per-WORKSPACE Maven repository (artifact identity): the contracts jar this build installs
          # must be the one its tests, its footprint campaign and its packaging consume. A shared
          # ~/.m2 lets ANY other job on this builder overwrite the same coordinate in between.
          export MAVEN_OPTS="-Dmaven.repo.local=$WORKSPACE/.m2/repository${MAVEN_OPTS:+ $MAVEN_OPTS}"
          # The pinned column of ES-FOOTPRINT-GATEWAY-DESIGN.md rests on ES-FOOTPRINT-CAMPAIGN.json,
          # and that is a file: the generator's refusals compare the record against itself and
          # against the source it names, which a self-consistent forgery satisfies. Only re-running
          # the campaign settles it, so it runs HERE, where this repository is actually built. It
          # cannot run in a GitHub check: this build needs options-edge-contracts installed from
          # source (see the Install Contracts stage), which no hosted runner has.
          #
          # Unconditionally, not on a changeset predicate: a gate that decides for itself when to
          # run is a gate that stops running.
          scripts/footprint-reverify.sh
          # ...and the DOCUMENT must be the one that record produces. Reverification compares the
          # spec, the record and a fresh run; it never looks at the rendered section, so a pinned
          # cell typed straight into ES-FOOTPRINT-GATEWAY-DESIGN.md by hand survives it untouched
          # while every mutation in the record reproduces and the gate goes green. --check regenerates the
          # section and refuses if what is committed is not byte-for-byte what the record yields.
          scripts/footprint-reqstate.sh --check
        '''
      }
    }
    stage('Package') {
      when { expression { env.PERMITTED_SHA_GUARD == 'PASSED' } }
      steps {
        sh '''
          set -eu
          if [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
            export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
          elif [ -x /usr/lib/jvm/java-21/bin/java ]; then
            export JAVA_HOME=/usr/lib/jvm/java-21
          elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
            export JAVA_HOME="$JAVA_HOME"
          else
            echo "Java 21 was not found on this Jenkins agent" >&2
            exit 1
          fi
          export MAVEN_SKIP_RC=true
          export PATH="$JAVA_HOME/bin:$PATH"
          # Per-WORKSPACE Maven repository (artifact identity): the contracts jar this build installs
          # must be the one its tests, its footprint campaign and its packaging consume. A shared
          # ~/.m2 lets ANY other job on this builder overwrite the same coordinate in between.
          mkdir -p .jenkins-tmp
          printf '%s\n' "$JAVA_HOME" > .jenkins-tmp/java-home
          java -version
          # ARTIFACT IDENTITY, part 2: the contracts jar about to be packaged in is still the one this
          # build installed and bound (the repository is per-workspace, but verified, never assumed).
          CV="$(cat .contracts-version)"
          CJAR="$WORKSPACE/.m2/repository/com/optionsedge/options-edge-contracts/$CV/options-edge-contracts-$CV.jar"
          [ "$(bash scripts/jenkins/permitted-sha-guard-version.sh "$CJAR")" = "$(cat .contracts-jar-sha256)" ] \
            || { echo "the installed contracts jar changed since it was bound — refusing to package" >&2; exit 1; }
        '''
        script {
          def jh = readFile('.jenkins-tmp/java-home').trim()
          withEnv(["JAVA_HOME=${jh}", "PATH+JDK=${jh}/bin", 'MAVEN_SKIP_RC=true',
                   "MAVEN_OPTS=-Dmaven.repo.local=${env.WORKSPACE}/.m2/repository${env.MAVEN_OPTS ? ' ' + env.MAVEN_OPTS : ''}"]) {
            // PROVENANCE (Deployment Permission Rule, validator rule 9b): the gateway source is compiled into the jar the
            // image carries, so the package is a DEDICATED step (its whole body is the one mvn command) right after the
            // verify of the primary checkout. The declared ignored names are what this build itself writes: build
            // output, the per-workspace Maven repository, the contracts clone (verified on its own before its install),
            // the provenance files, .jenkins-tmp/ and Python bytecode from the footprint scripts.
            timeout(time: 10, unit: 'MINUTES') {
              sh 'PERMITTED_SHA="${PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir . --allow-ignored target --allow-ignored dev/raw-feed-replicator/target --allow-ignored .m2 --allow-ignored .deps --allow-ignored .jenkins-tmp --allow-ignored .contracts-sha --allow-ignored .contracts-version --allow-ignored .contracts-jar-sha256 --allow-ignored .jar-sha256 --allow-ignored scripts/__pycache__ --allow-ignored scripts/jenkins/__pycache__'
            }
            sh 'mvn -B package'
            // part 3: the packaged jar, by content, and the contracts jar INSIDE it (Spring Boot fat jar,
            // BOOT-INF/lib) must be the bound one. This is what proves what was consumed.
            sh '''
              set -eu
              JAR="$(ls target/options-edge-feed-gateway-*.jar | grep -v '\\.original$' | head -1)"
              [ -n "$JAR" ] || { echo "no packaged gateway jar under target/" >&2; exit 1; }
              bash scripts/jenkins/permitted-sha-guard-version.sh "$JAR" > .jar-sha256
              bash scripts/jenkins/verify-embedded-contracts.sh "$JAR" "$(cat .contracts-jar-sha256)"
              echo "packaged $JAR sha256: $(cat .jar-sha256)"
            '''
          }
        }
      }
    }
    stage('Image') {
      when { expression { env.PERMITTED_SHA_GUARD == 'PASSED' } }
      steps {
        // Image: prepare (not an effect) — every value the build needs is derived here and handed to the build step as
        // its environment, because the build is a DEDICATED step whose whole body is the one docker command.
        sh '''
          set -eu
          # Preflight: this stage may run on a builder whose Docker is not always up (the .74
          # agents use Docker Desktop, which needs a GUI session and does not survive reboot).
          # Fail here with something actionable rather than obscurely mid-buildx.
          # All environments build here on the resolved CI agent, including production.
          if ! docker info >/dev/null 2>&1; then
            echo "Docker daemon is not reachable on this build agent ($(hostname))." >&2
            echo "If this is the .74 builder, start Docker Desktop on it and re-run." >&2
            exit 1
          fi
          # <build-number>-<git-sha>: unique per run (git-sha alone repeats across rebuilds of
          # one commit); :dev (DEV_TAG) pushed alongside. Deploy pins by digest of :dev.
          # Clean replace: prod publishes ONLY :prod, NOT :dev — the deploy resolves :prod, so
          # suppress the :dev moving tag for prod (empties the DEV_IMAGE_TAG guard below).
          if [ "${ENVIRONMENT:-dev}" = "production" ]; then DEV_IMAGE_TAG=""; fi
          # dev = <build>-<sha>; PROD = prod-<build>-<sha> (self-documents env+build+commit).
          # ARTIFACT IDENTITY: the unique per-build tag the image lock is resolved through is DERIVED here
          # from BUILD_ID and the permitted SHA (which the guard proved equals HEAD) — never taken from a
          # caller: a shared caller-chosen tag would let another build's push be recorded as this one's.
          if [ -n "${IMAGE_TAG:-}" ]; then
            echo "IMAGE_TAG='$IMAGE_TAG' is refused: the per-build tag is derived from BUILD_ID and PERMITTED_SHA so the image lock names THIS build's image. Nothing was built." >&2
            exit 1
          fi
          [ "$(git rev-parse HEAD)" = "${PERMITTED_SHA:?}" ] || { echo "HEAD is not PERMITTED_SHA — refusing to tag" >&2; exit 1; }
          SHORT_SHA="$(printf '%s' "$PERMITTED_SHA" | cut -c1-12)"
          if [ "${ENVIRONMENT:-dev}" = "production" ]; then
            TAG="prod-${BUILD_ID:?}-$SHORT_SHA"
          else
            TAG="${BUILD_ID:?}-$SHORT_SHA"
          fi
          DEV_TAG="${DEV_IMAGE_TAG:-}"
          BUILD_PLATFORM="${BUILD_PLATFORM:-linux/arm64}"
          # Refs are built from PUSH_REGISTRY (this builder's address for the registry), which
          # equals IMAGE_REGISTRY everywhere except a builder that is not the registry host.
          # Same underlying storage, so the deploy still resolves the digest via IMAGE_REGISTRY.
          PUSH_REGISTRY="${PUSH_REGISTRY:-$IMAGE_REGISTRY}"
          IMAGE="$PUSH_REGISTRY/options-edge-feed-gateway:$TAG"
          SECOND_IMAGE=""
          if [ -n "$DEV_TAG" ] && [ "$DEV_TAG" != "$TAG" ]; then
            SECOND_IMAGE="$PUSH_REGISTRY/options-edge-feed-gateway:$DEV_TAG"
          fi
          if [ "${ENVIRONMENT:-dev}" = "production" ]; then
            SECOND_IMAGE="$PUSH_REGISTRY/options-edge-feed-gateway:prod"  # prod also gets the self-documenting :prod moving tag
          fi
          BUILDER_NAME="options-edge-feed-gateway-${BUILD_NUMBER:-local}"
          mkdir -p .jenkins-tmp
          BUILDKITD_CONFIG=".jenkins-tmp/buildkitd-$BUILD_ID.toml"
          # A failure below removes what this step created; on success the builder and its config stay for the build
          # step, and the stage's finally removes them.
          cleanup() {
            docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
            rm -f "$BUILDKITD_CONFIG"
          }
          trap 'rc=$?; [ "$rc" -eq 0 ] || cleanup' EXIT
          # Write a buildkit insecure-registry entry for the registry we actually PUSH to
          # (normalized: scheme stripped, trailing slash stripped, lowercased) iff it matches
          # any entry in $INSECURE_REGISTRIES (derived from oeProfile in Resolve profile,
          # normalized the same way). Without this, pushes via docker buildx fail with
          # 'http: server gave HTTP response to HTTPS client'.
          # This MUST test and emit PUSH_REGISTRY, not IMAGE_REGISTRY: when the builder is
          # not the registry host they differ, and a stanza written for the pull-side name
          # leaves the push endpoint unconfigured, failing before anything is uploaded.
          normalize() {
            printf '%s' "$1" | tr 'A-Z' 'a-z' \
              | sed -e 's#^http://##' -e 's#^https://##' \
              | sed -e 's#/*$##'
          }
          effective_norm=$(normalize "$PUSH_REGISTRY")
          registry_insecure=false
          for r in $INSECURE_REGISTRIES; do
            if [ "$effective_norm" = "$(normalize "$r")" ]; then registry_insecure=true; break; fi
          done
          if [ "$registry_insecure" = "true" ]; then
            cat > "$BUILDKITD_CONFIG" <<EOF
[registry."$PUSH_REGISTRY"]
  http = true
  insecure = true
EOF
          else
            : > "$BUILDKITD_CONFIG"
          fi
          # Own the builder explicitly so concurrent jobs cannot change our selection.
          docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
          docker buildx create --name "$BUILDER_NAME" --driver docker-container --config "$BUILDKITD_CONFIG" >/dev/null
          # ARTIFACT IDENTITY, part 4: the jar about to become the image is the one packaged and
          # verified above — by content, again — and its provenance travels as labels.
          JAR="$(ls target/options-edge-feed-gateway-*.jar | grep -v '\\.original$' | head -1)"
          [ "$(bash scripts/jenkins/permitted-sha-guard-version.sh "$JAR")" = "$(cat .jar-sha256)" ] \
            || { echo "the packaged jar changed since it was verified — refusing to build the image" >&2; exit 1; }
          bash scripts/jenkins/verify-embedded-contracts.sh "$JAR" "$(cat .contracts-jar-sha256)"
          rm -f ".jenkins-tmp/push-metadata-$BUILD_ID.json" ".jenkins-tmp/image-lock-$BUILD_ID.env" ".jenkins-tmp/required-image-$BUILD_ID"
          # push = a registry export; no push = load into the local daemon (the same --output either way)
          if [ "$PUSH_IMAGE" = "true" ]; then BUILD_OUTPUT="type=image,push=true"; else BUILD_OUTPUT="type=docker"; fi
          # LABELS, so the revisions and contents travel with the image rather than only with the
          # build log — see the note in the contracts install stage.
          {
            echo "TAG=$TAG"
            echo "BUILD_PLATFORM=$BUILD_PLATFORM"
            echo "BUILDER_NAME=$BUILDER_NAME"
            echo "BUILDKITD_CONFIG=$BUILDKITD_CONFIG"
            echo "IMAGE_REF_1=$IMAGE"
            echo "IMAGE_REF_2=$SECOND_IMAGE"
            echo "BUILD_OUTPUT=$BUILD_OUTPUT"
            echo "PUSH_METADATA=.jenkins-tmp/push-metadata-$BUILD_ID.json"
            echo "LABEL_CONTRACTS_REVISION=$(cat .contracts-sha)"
            echo "LABEL_SOURCE_REVISION=$(git rev-parse HEAD)"
            echo "LABEL_JAR_SHA256=$(cat .jar-sha256)"
            echo "LABEL_CONTRACTS_JAR_SHA256=$(cat .contracts-jar-sha256)"
            echo "LABEL_GUARD_VERSION=${PERMITTED_SHA_GUARD_VERSION:-}"
          } > ".jenkins-tmp/image-env-$BUILD_ID"
          sed 's/^/image-env: /' ".jenkins-tmp/image-env-$BUILD_ID"
        '''
        script {
          def imageEnv = readFile(".jenkins-tmp/image-env-${env.BUILD_ID}").split('\n').collect { it.trim() }.findAll { it }
          def secondTag = imageEnv.any { it.startsWith('IMAGE_REF_2=') && it.length() > 'IMAGE_REF_2='.length() }
          withEnv(imageEnv) {
            try {
              // PROVENANCE (Deployment Permission Rule, validator rule 9b): the image is built from the whole workspace
              // context, which holds the primary checkout AND the contracts clone — so both are re-verified immediately
              // before the build, and the build is a DEDICATED step whose whole body is the one docker command.
              if (secondTag) {
                timeout(time: 10, unit: 'MINUTES') {
                  sh 'PERMITTED_SHA="${CONTRACTS_PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir .deps/options-edge-contracts --allow-ignored target'
                }
                timeout(time: 10, unit: 'MINUTES') {
                  sh 'PERMITTED_SHA="${PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir . --allow-ignored target --allow-ignored dev/raw-feed-replicator/target --allow-ignored .m2 --allow-ignored .deps --allow-ignored .jenkins-tmp --allow-ignored .contracts-sha --allow-ignored .contracts-version --allow-ignored .contracts-jar-sha256 --allow-ignored .jar-sha256 --allow-ignored scripts/__pycache__ --allow-ignored scripts/jenkins/__pycache__'
                }
                sh 'docker buildx build --builder "${BUILDER_NAME}" --platform "${BUILD_PLATFORM}" --no-cache --label "options-edge.contracts-revision=${LABEL_CONTRACTS_REVISION}" --label "options-edge.source-revision=${LABEL_SOURCE_REVISION}" --label "options-edge.jar-sha256=${LABEL_JAR_SHA256}" --label "options-edge.contracts-jar-sha256=${LABEL_CONTRACTS_JAR_SHA256}" --label "options-edge.guard-version=${LABEL_GUARD_VERSION}" -t "${IMAGE_REF_1}" -t "${IMAGE_REF_2}" --metadata-file "${PUSH_METADATA}" --output "${BUILD_OUTPUT}" .'
              } else {
                timeout(time: 10, unit: 'MINUTES') {
                  sh 'PERMITTED_SHA="${CONTRACTS_PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir .deps/options-edge-contracts --allow-ignored target'
                }
                timeout(time: 10, unit: 'MINUTES') {
                  sh 'PERMITTED_SHA="${PERMITTED_SHA:-}" bash scripts/jenkins/verify-permitted-tree.sh --dir . --allow-ignored target --allow-ignored dev/raw-feed-replicator/target --allow-ignored .m2 --allow-ignored .deps --allow-ignored .jenkins-tmp --allow-ignored .contracts-sha --allow-ignored .contracts-version --allow-ignored .contracts-jar-sha256 --allow-ignored .jar-sha256 --allow-ignored scripts/__pycache__ --allow-ignored scripts/jenkins/__pycache__'
                }
                sh 'docker buildx build --builder "${BUILDER_NAME}" --platform "${BUILD_PLATFORM}" --no-cache --label "options-edge.contracts-revision=${LABEL_CONTRACTS_REVISION}" --label "options-edge.source-revision=${LABEL_SOURCE_REVISION}" --label "options-edge.jar-sha256=${LABEL_JAR_SHA256}" --label "options-edge.contracts-jar-sha256=${LABEL_CONTRACTS_JAR_SHA256}" --label "options-edge.guard-version=${LABEL_GUARD_VERSION}" -t "${IMAGE_REF_1}" --metadata-file "${PUSH_METADATA}" --output "${BUILD_OUTPUT}" .'
              }
            } finally {
              sh 'docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true; rm -f "$BUILDKITD_CONFIG"'
            }
            // Image: lock — from THIS build's own push result.
            sh '''
              set -eu
              if [ "$PUSH_IMAGE" = "true" ]; then
                # IMAGE LOCK, from THIS build's own push result: buildx's metadata file for this BUILD_ID names
                # the digest it pushed; the registry must serve that same digest for the unique per-build tag.
                # The dev rollout below forwards it as REQUIRED_IMAGE so service-deploy rolls THIS image.
                PUSH_REGISTRY="${PUSH_REGISTRY:-$IMAGE_REGISTRY}"
                PUSHED="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("containerimage.digest",""))' "$PUSH_METADATA")"
                printf '%s' "$PUSHED" | grep -Eq '^sha256:[0-9a-f]{64}$' || { echo "this build's push reported no image digest ('$PUSHED') — no image lock" >&2; exit 1; }
                DIGEST="$(bash scripts/jenkins/resolve-pushed-digest.sh "$PUSH_REGISTRY" options-edge-feed-gateway "$TAG")"
                [ "$DIGEST" = "$PUSHED" ] || { echo "the registry serves $DIGEST for $TAG, this build pushed $PUSHED — refusing to record another build's image" >&2; exit 1; }
                {
                  echo "OPTIONS_EDGE_IMAGE_LOCK_FORMAT=1"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_SOURCE_REPO=$(git config --get remote.origin.url || true)"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_GIT_COMMIT=$LABEL_SOURCE_REVISION"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_CONTRACTS_GIT_COMMIT=$LABEL_CONTRACTS_REVISION"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_CONTRACTS_JAR_SHA256=$LABEL_CONTRACTS_JAR_SHA256"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_JAR_SHA256=$LABEL_JAR_SHA256"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_GUARD_VERSION=${PERMITTED_SHA_GUARD_VERSION:-}"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_BUILD_ID=$BUILD_ID"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_BUILD_URL=${BUILD_URL:-}"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_PLATFORM=$BUILD_PLATFORM"
                  echo "OPTIONS_EDGE_IMAGE_LOCK_TAG=$TAG"
                  echo "FEED_GATEWAY_IMAGE=$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG@$DIGEST"
                  echo "FEED_GATEWAY_IMAGE_GIT_COMMIT=$LABEL_SOURCE_REVISION"
                } > ".jenkins-tmp/image-lock-$BUILD_ID.env"
                printf '%s\n' "$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG@$DIGEST" > ".jenkins-tmp/required-image-$BUILD_ID"
                sed 's/^/image-lock: /' ".jenkins-tmp/image-lock-$BUILD_ID.env"
              fi
            '''
          }
        }
        script {
          // Only THIS build's lock (keyed by BUILD_ID, written from its own push result) is archived and
          // forwarded; a build that pushed nothing publishes no lock and forwards no REQUIRED_IMAGE.
          if (params.PUSH_IMAGE && fileExists(".jenkins-tmp/image-lock-${env.BUILD_ID}.env")) {
            def lock = readFile(".jenkins-tmp/image-lock-${env.BUILD_ID}.env")
            if (!lock.contains("OPTIONS_EDGE_IMAGE_LOCK_BUILD_ID=${env.BUILD_ID}\n")) {
              error("the image lock for build ${env.BUILD_ID} does not name this build. Nothing was deployed.")
            }
            writeFile file: '.jenkins-tmp/options-edge-image-lock.env', text: lock
            archiveArtifacts artifacts: '.jenkins-tmp/options-edge-image-lock.env', fingerprint: true
            env.GATEWAY_REQUIRED_IMAGE = readFile(".jenkins-tmp/required-image-${env.BUILD_ID}").trim()
          }
        }
      }
    }
      }
    }   // end stage('Build')

    // --- CLOSE THE SILENT-STALE-BUILD GAP -------------------------------------------
    // A build that pushes a new image does NOT update the running pod (build != deploy),
    // so a dev pod can silently keep an old image. Here every DEV build ends by calling
    // service-deploy, which pins the freshly-pushed :dev digest, rolls the pod, and runs
    // its §13.3 gate: rollout Ready + the running pod's imageID MUST contain the pinned
    // digest + restartCount==0. propagate:true => if the pod fails to pick up the new
    // image, THIS build turns red. Dev only: production keeps the manual promote gate.
    // Guarded to the canonical main job (JOB_NAME) so PR/branch jobs never auto-deploy.
    stage('Deploy + verify (dev)') {
      // `build job:` is a controller-side step and needs no cluster access here: the
      // downstream service-deploy job is pinned to its own agent on .102 which holds the
      // kubeconfigs. So this can run anywhere.
      agent any
      when {
        expression { env.PERMITTED_SHA_GUARD == 'PASSED' && (
          params.ENVIRONMENT == 'dev' && params.PUSH_IMAGE && params.DEPLOY_AND_VERIFY &&
            params.DEV_IMAGE_TAG == 'dev' &&
            (env.JOB_NAME?.endsWith('option-edge-feed-gateway')))
        }
      }
      steps {
        script {
          // A second workspace on its own agent (`agent any`, with Declarative's implicit checkout):
          // re-bound to the permitted commit before any repository script runs from it.
          timeout(time: 10, unit: 'MINUTES') {
            def rg = sh(returnStatus: true, script: 'bash scripts/jenkins/permitted-sha-guard.sh')
            if (rg != 0) {
              error("Permitted commit guard (rollout workspace) REFUSED this checkout (rc=${rg}) — the image was pushed; nothing was deployed.")
            }
          }
          // Downstream deployment trigger (Deployment Permission Rule): the rollout is a deployment of
          // its own, of ANOTHER repository (options-edge-deploy), and needs that repository's permitted
          // commit. Judged here, before `build job:`, so a missing value never starts a build that
          // would only fail at its own guard.
          def dsha = (params.DEPLOY_PERMITTED_SHA ?: '').trim()
          if (!dsha.matches('^[0-9a-f]{40}$')) {
            error("Deploy+verify needs DEPLOY_PERMITTED_SHA: the full 40-character options-edge-deploy commit permitted for the dev rollout (got '${dsha}'). The image was pushed; nothing was deployed.")
          }
          if (!(env.GATEWAY_REQUIRED_IMAGE ?: '').matches('^[^@\\s]+@sha256:[0-9a-f]{64}$')) {
            error("no digest-pinned image was recorded for this build (got '${env.GATEWAY_REQUIRED_IMAGE}') — refusing to roll an image this build cannot name. Nothing was deployed.")
          }
          // FAIL-CLOSED definition check, in the same block as the trigger (judged in the preflight too):
          // forwarding a SHA binds nothing unless service-deploy EXECUTES the guard. The helper reads its
          // job configuration (Pipeline from SCM, options-edge-deploy */main, Jenkinsfile.service-deploy),
          // requires DEPLOY_PERMITTED_SHA to be main's tip, fetches that commit and runs this repository's
          // validator on the Jenkinsfile the child will load, with its guard hashing to ours.
          def compat = sh(returnStatus: true, script: 'bash scripts/jenkins/require-guarded-downstream.sh service-deploy "${DEPLOY_PERMITTED_SHA:?}" REQUIRED_IMAGE')
          if (compat != 0) {
            error("service-deploy's definition at DEPLOY_PERMITTED_SHA could not be confirmed to run the permitted-commit guard (rc=${compat}) — not triggering it. The image was pushed; nothing was deployed.")
          }
          build job: 'service-deploy',
            parameters: [
              // service-deploy's own guard compares ITS checkout against this, byte for byte, before
              // any kubectl; REQUIRED_IMAGE makes it roll exactly the digest this build pushed.
              string(name: 'PERMITTED_SHA', value: params.DEPLOY_PERMITTED_SHA.trim()),
              string(name: 'REQUIRED_IMAGE', value: env.GATEWAY_REQUIRED_IMAGE),
              string(name: 'SERVICE', value: 'feed-gateway'),
              string(name: 'ENVIRONMENT', value: 'dev'),
              booleanParam(name: 'BUILD_IMAGES', value: false),
              booleanParam(name: 'DEPLOY_DRY_RUN', value: false)
            ],
            wait: true, propagate: true
        }
      }
    }
  }
}
