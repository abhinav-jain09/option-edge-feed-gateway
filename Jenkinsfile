@Library('oe') _

pipeline {
  agent { label 'mac' }
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
      steps {
        script {
          def p = oeProfile(params.ENVIRONMENT)
          env.IMAGE_REGISTRY = params.IMAGE_REGISTRY?.trim() ? params.IMAGE_REGISTRY : p.registry
          env.BUILD_PLATFORM = params.BUILD_PLATFORM?.trim() ? params.BUILD_PLATFORM : p.platform
          // Build the set of plain-http registries from EVERY profile (not a hardcoded
          // list of env names — so adding a new profile auto-extends this), normalize
          // for robust matching (strip scheme + trailing slash + lowercase), and add
          // dev-registry loopback aliases. The Image stage writes a buildkit insecure-
          // registry config for the EFFECTIVE IMAGE_REGISTRY iff its normalized form is
          // in this set (so prod pushes work via http, not just dev).
          def normalize = { String r ->
            r?.toString()?.trim()?.toLowerCase()?.replaceFirst(/^https?:\/\//, '')?.replaceFirst(/\/+$/, '')
          }
          def knownEnvs = ['dev', 'production']
          def insecure = knownEnvs.findAll { oeProfile(it).insecureRegistry }
                                  .collect { normalize(oeProfile(it).registry) }
          insecure += ['localhost:5001', '127.0.0.1:5001']   // loopback aliases of the dev registry
          env.INSECURE_REGISTRIES = insecure.unique().findAll { it }.join(' ')
          echo "resolved (env=${params.ENVIRONMENT}): registry=${env.IMAGE_REGISTRY} platform=${env.BUILD_PLATFORM} insecureRegistries='${env.INSECURE_REGISTRIES}'"
        }
      }
    }
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
          IMAGE="$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG"
          DEV_IMAGE="$IMAGE_REGISTRY/options-edge-feed-gateway:$DEV_TAG"
          PROD_IMAGE="$IMAGE_REGISTRY/options-edge-feed-gateway:prod"  # self-documenting prod moving tag
          BUILDER_NAME="options-edge-feed-gateway-${BUILD_NUMBER:-local}"
          BUILDKITD_CONFIG="$(mktemp)"
          # Write a buildkit insecure-registry entry for the EFFECTIVE registry iff it
          # (normalized: scheme stripped, trailing slash stripped, lowercased) matches
          # any entry in $INSECURE_REGISTRIES (derived from oeProfile in Resolve profile,
          # normalized the same way). Without this, prod pushes via docker buildx fail
          # with 'http: server gave HTTP response to HTTPS client'.
          normalize() {
            printf '%s' "$1" | tr 'A-Z' 'a-z' \
              | sed -e 's#^http://##' -e 's#^https://##' \
              | sed -e 's#/*$##'
          }
          effective_norm=$(normalize "$IMAGE_REGISTRY")
          registry_insecure=false
          for r in $INSECURE_REGISTRIES; do
            if [ "$effective_norm" = "$(normalize "$r")" ]; then registry_insecure=true; break; fi
          done
          if [ "$registry_insecure" = "true" ]; then
            cat > "$BUILDKITD_CONFIG" <<EOF
[registry."$IMAGE_REGISTRY"]
  http = true
  insecure = true
EOF
          else
            : > "$BUILDKITD_CONFIG"
          fi
          docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
          docker buildx create --name "$BUILDER_NAME" --driver docker-container --config "$BUILDKITD_CONFIG" --use >/dev/null
          cleanup() {
            docker buildx rm "$BUILDER_NAME" >/dev/null 2>&1 || true
            rm -f "$BUILDKITD_CONFIG"
          }
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

    // --- CLOSE THE SILENT-STALE-BUILD GAP -------------------------------------------
    // A build that pushes a new image does NOT update the running pod (build != deploy),
    // so a dev pod can silently keep an old image. Here every DEV build ends by calling
    // service-deploy, which pins the freshly-pushed :dev digest, rolls the pod, and runs
    // its §13.3 gate: rollout Ready + the running pod's imageID MUST contain the pinned
    // digest + restartCount==0. propagate:true => if the pod fails to pick up the new
    // image, THIS build turns red. Dev only: production keeps the manual promote gate.
    // Guarded to the canonical main job (JOB_NAME) so PR/branch jobs never auto-deploy.
    stage('Deploy + verify (dev)') {
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
