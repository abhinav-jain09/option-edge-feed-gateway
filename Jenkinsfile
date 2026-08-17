@Library('oe') _

pipeline {
  // agent none: the build agent is chosen per-environment from oeProfile(ENVIRONMENT).
  // buildAgentLabel, which cannot be read before the pipeline starts. BOTH dev and production
  // now compile on the .74 arm64 builders so the dev Mac (.102) does no build work during
  // market hours — it runs the controller, the dev cluster, the registry and the broker.
  // Production still builds its amd64 IMAGE natively on REMOTE_BUILD_HOST (.252); only the
  // Maven compile/test/package moves, and the jar is architecture-independent.
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
    string(name: 'REMOTE_BUILD_HOST', defaultValue: '192.168.100.252', description: 'Production only: Linux amd64 host that performs native docker build/push. Dev remains local on the Mac.')
    string(name: 'REMOTE_BUILD_ROOT', defaultValue: '/home/abhinav/ci/remote-builds', description: 'Production only: temporary remote workspace root for native Linux image builds.')
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
          # ONLY for builds that actually use the local daemon: production ships the source to
          # REMOTE_BUILD_HOST and builds there, so it must not depend on Docker being up on the
          # agent (Codex: that would fail prod for a reason that has nothing to do with prod).
          needs_local_docker=true
          if [ "${ENVIRONMENT:-dev}" = "production" ] && [ "$PUSH_IMAGE" = "true" ]; then
            needs_local_docker=false
          fi
          if [ "$needs_local_docker" = "true" ] && ! docker info >/dev/null 2>&1; then
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
          # Same reasoning as the preflight: the production remote-build path never touches the
          # local daemon, so do not create/destroy a local buildx builder for it.
          cleanup() { rm -f "$BUILDKITD_CONFIG"; }
          if [ "$needs_local_docker" = "true" ]; then
            docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
            docker buildx create --name "$BUILDER_NAME" --driver docker-container --config "$BUILDKITD_CONFIG" --use >/dev/null
            cleanup() {
              docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
              rm -f "$BUILDKITD_CONFIG"
            }
          fi
          trap cleanup EXIT
          TAG_ARGS="-t $IMAGE"
          if [ -n "$DEV_TAG" ] && [ "$DEV_TAG" != "$TAG" ]; then
            TAG_ARGS="$TAG_ARGS -t $DEV_IMAGE"
          fi
          if [ "${ENVIRONMENT:-dev}" = "production" ]; then
            TAG_ARGS="$TAG_ARGS -t $PROD_IMAGE"   # prod also gets the self-documenting :prod moving tag
          fi
          if [ "${ENVIRONMENT:-dev}" = "production" ] && [ "$PUSH_IMAGE" = "true" ]; then
            remote_host="${REMOTE_BUILD_HOST:-192.168.100.252}"
            remote_root="${REMOTE_BUILD_ROOT:-/home/abhinav/ci/remote-builds}"
            remote_job="$(printf '%s' "${JOB_NAME:-options-edge-feed-gateway}" | tr '/ ' '__')"
            remote_dir="$remote_root/$remote_job-${BUILD_NUMBER:-manual}"
            remote="abhinav@$remote_host"
            push_refs="$IMAGE"
            if [ -n "${DEV_IMAGE_TAG:-}" ] && [ "${DEV_IMAGE_TAG:-}" != "$TAG" ]; then
              push_refs="$push_refs $DEV_IMAGE"
            fi
            push_refs="$push_refs $PROD_IMAGE"   # push the :prod moving tag for prod
            echo "Production image build runs natively on $remote_host ($BUILD_PLATFORM): $TAG_ARGS"
            ssh "$remote" "rm -rf '$remote_dir' && mkdir -p '$remote_dir'"
            rsync -az --delete \
              --exclude '.git' \
              --exclude '.deps/options-edge-contracts/.git' \
              ./ "$remote:$remote_dir/"
            push_cmd=""
            for ref in $push_refs; do push_cmd="$push_cmd && docker push '$ref'"; done
            ssh "$remote" "cd '$remote_dir' && docker build --no-cache $TAG_ARGS . $push_cmd && rm -rf '$remote_dir'"
          elif [ "$PUSH_IMAGE" = "true" ]; then
            docker buildx build --platform "$BUILD_PLATFORM" --no-cache $TAG_ARGS --push .
          else
            docker buildx build --platform "$BUILD_PLATFORM" --no-cache $TAG_ARGS --load .
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
