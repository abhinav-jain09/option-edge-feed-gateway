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
    // Serialize builds so each build's push -> Deploy+verify is atomic: two concurrent
    // builds must not both move the mutable :dev tag while the other's Deploy+verify
    // stage resolves it (Codex: a moving-tag race would let the wrong build verify green).
    disableConcurrentBuilds()
  }
  parameters {
    choice(name: 'ENVIRONMENT', choices: ['dev', 'production'], description: 'Target environment — drives registry + build platform from oeProfile (single source of truth)')
    string(name: 'IMAGE_REGISTRY', defaultValue: '', description: 'Override registry. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers (e.g. bring-up-all).')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Docker tag. Defaults to current git SHA.')
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
    stage('Install Contracts') {
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
          java -version
          rm -rf .deps/options-edge-contracts
          git clone git@github.com:abhinav-jain09/options-edge-contracts.git .deps/options-edge-contracts
          git -C .deps/options-edge-contracts checkout "${CONTRACTS_BRANCH:-main}"
          # RECORD THE REVISION, because the branch is mutable and the contract is EXECUTABLE here.
          # The gateway validates vol-premium readings by deserialising them through
          # IvRvReading's own constructor, so what this clone resolved to decides which payloads
          # the gateway admits at runtime. Without the SHA, two images built from the same gateway
          # commit can behave differently at that boundary with nothing in their provenance to say
          # why. Mirrors what options-edge-processing already records.
          git -C .deps/options-edge-contracts rev-parse HEAD > .contracts-sha
          echo "contracts revision: $(cat .contracts-sha)"
          mvn -B -f .deps/options-edge-contracts/pom.xml install
        '''
      }
    }
    stage('Test') {
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
          java -version
          mvn -B test
        '''
      }
    }
    stage('Footprint reverification') {
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
          # while all 34 mutations reproduce and the gate goes green. --check regenerates the
          # section and refuses if what is committed is not byte-for-byte what the record yields.
          scripts/footprint-reqstate.sh --check
        '''
      }
    }
    stage('Package') {
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
          java -version
          mvn -B package
        '''
      }
    }
    stage('Image') {
      steps {
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
          if [ "${ENVIRONMENT:-dev}" = "production" ]; then
            TAG="${IMAGE_TAG:-prod-${BUILD_NUMBER:-manual}-$(git rev-parse --short=12 HEAD)}"
          else
            TAG="${IMAGE_TAG:-${BUILD_NUMBER:-manual}-$(git rev-parse --short=12 HEAD)}"
          fi
          DEV_TAG="${DEV_IMAGE_TAG:-}"
          BUILD_PLATFORM="${BUILD_PLATFORM:-linux/arm64}"
          # Refs are built from PUSH_REGISTRY (this builder's address for the registry), which
          # equals IMAGE_REGISTRY everywhere except a builder that is not the registry host.
          # Same underlying storage, so the deploy still resolves the digest via IMAGE_REGISTRY.
          PUSH_REGISTRY="${PUSH_REGISTRY:-$IMAGE_REGISTRY}"
          IMAGE="$PUSH_REGISTRY/options-edge-feed-gateway:$TAG"
          DEV_IMAGE="$PUSH_REGISTRY/options-edge-feed-gateway:$DEV_TAG"
          PROD_IMAGE="$PUSH_REGISTRY/options-edge-feed-gateway:prod"  # self-documenting prod moving tag
          BUILDER_NAME="options-edge-feed-gateway-${BUILD_NUMBER:-local}"
          BUILDKITD_CONFIG="$(mktemp)"
          # Register the file-only cleanup IMMEDIATELY: several fallible commands run before the
          # builder exists, and under `set -e` a failure there would otherwise leak the temp
          # config. Redefined below once the builder is actually created.
          cleanup() { rm -f "$BUILDKITD_CONFIG"; }
          trap cleanup EXIT
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
          # Builder now exists — widen cleanup to remove it too.
          cleanup() {
            docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
            rm -f "$BUILDKITD_CONFIG"
          }
          CONTRACTS_SHA="$(cat .contracts-sha 2>/dev/null || echo unknown)"
          # A LABEL, so the revision travels with the image rather than only with the build log —
          # see the note in the contracts install stage.
          BUILD_LABELS="--label options-edge.contracts-revision=$CONTRACTS_SHA"
          TAG_ARGS="-t $IMAGE"
          if [ -n "$DEV_TAG" ] && [ "$DEV_TAG" != "$TAG" ]; then
            TAG_ARGS="$TAG_ARGS -t $DEV_IMAGE"
          fi
          if [ "${ENVIRONMENT:-dev}" = "production" ]; then
            TAG_ARGS="$TAG_ARGS -t $PROD_IMAGE"   # prod also gets the self-documenting :prod moving tag
          fi
          if [ "$PUSH_IMAGE" = "true" ]; then
            docker buildx build --builder "$BUILDER_NAME" --platform "$BUILD_PLATFORM" --no-cache $BUILD_LABELS $TAG_ARGS --push .
          else
            docker buildx build --builder "$BUILDER_NAME" --platform "$BUILD_PLATFORM" --no-cache $BUILD_LABELS $TAG_ARGS --load .
          fi
        '''
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
        expression {
          params.ENVIRONMENT == 'dev' && params.PUSH_IMAGE && params.DEPLOY_AND_VERIFY &&
            params.DEV_IMAGE_TAG == 'dev' &&
            (env.JOB_NAME?.endsWith('option-edge-feed-gateway'))
        }
      }
      steps {
        build job: 'service-deploy',
          parameters: [
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
