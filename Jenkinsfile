@Library('oe') _

pipeline {
  agent { label 'mac' }
  parameters {
    choice(name: 'ENVIRONMENT', choices: ['dev', 'production', 'experiment'], description: 'Target environment — drives registry + build platform from oeProfile (single source of truth)')
    string(name: 'IMAGE_REGISTRY', defaultValue: '', description: 'Override registry. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers (e.g. bring-up-all).')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Docker tag. Defaults to current git SHA.')
    string(name: 'DEV_IMAGE_TAG', defaultValue: 'dev', description: 'Also publish this mutable dev tag for the deploy job. Empty disables it.')
    string(name: 'BUILD_PLATFORM', defaultValue: '', description: 'Override platform. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers.')
    string(name: 'CONTRACTS_BRANCH', defaultValue: 'main', description: 'options-edge-contracts branch to install before building the gateway')
    booleanParam(name: 'PUSH_IMAGE', defaultValue: true, description: 'Push built image to registry')
    string(name: 'REMOTE_BUILD_HOST', defaultValue: '', description: 'Override remote build host. Empty = per-env default (production=192.168.100.252; experiment=192.168.100.2). Dev builds locally on the Mac.')
    string(name: 'REMOTE_BUILD_USER', defaultValue: '', description: 'Override SSH user on the remote build host. Empty = per-env default (production=abhinav; experiment=AMSKEL).')
    string(name: 'REMOTE_BUILD_ROOT', defaultValue: '', description: 'Override remote workspace root. Empty = per-env default (production=/home/abhinav/ci/remote-builds; experiment=/Users/rajuljain/ci/remote-builds, mounted read-only into the .2 Lima VM).')
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
          def knownEnvs = ['dev', 'production', 'experiment']
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
          TAG="${IMAGE_TAG:-$(git rev-parse --short=12 HEAD)}"
          DEV_TAG="${DEV_IMAGE_TAG:-}"
          BUILD_PLATFORM="${BUILD_PLATFORM:-linux/arm64}"
          IMAGE="$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG"
          DEV_IMAGE="$IMAGE_REGISTRY/options-edge-feed-gateway:$DEV_TAG"
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
          # production AND experiment build the image ON a remote host (Jenkins only issues the command;
          # NO built-binary copy — source is rsync'd and `docker build` runs there). production -> host
          # docker daemon; experiment -> the .2 box, whose docker lives INSIDE a Lima VM (no docker on the
          # Mac host), so build/push run via `limactl shell` against the in-VM localhost:5000.
          if { [ "${ENVIRONMENT:-dev}" = "production" ] || [ "${ENVIRONMENT:-dev}" = "experiment" ]; } && [ "$PUSH_IMAGE" = "true" ]; then
            case "${ENVIRONMENT:-dev}" in
              experiment) def_host=192.168.100.2;   def_user=AMSKEL;  def_root=/Users/rajuljain/ci/remote-builds ;;
              *)          def_host=192.168.100.252; def_user=abhinav; def_root=/home/abhinav/ci/remote-builds ;;
            esac
            remote_host="${REMOTE_BUILD_HOST:-$def_host}"
            remote_user="${REMOTE_BUILD_USER:-$def_user}"
            remote_root="${REMOTE_BUILD_ROOT:-$def_root}"
            case "$remote_user" in *[!a-zA-Z0-9._-]*|'') echo "invalid REMOTE_BUILD_USER: '$remote_user'" >&2; exit 1 ;; esac
            case "$remote_host" in *[!a-zA-Z0-9.:_-]*|'') echo "invalid REMOTE_BUILD_HOST: '$remote_host'" >&2; exit 1 ;; esac
            case "$remote_root" in ''|[!/]*|*[!a-zA-Z0-9._/-]*) echo "invalid REMOTE_BUILD_ROOT (absolute path required): '$remote_root'" >&2; exit 1 ;; esac
            remote_job="$(printf '%s' "${JOB_NAME:-options-edge-feed-gateway}" | tr '/ ' '__')"
            remote_dir="$remote_root/$remote_job-${BUILD_NUMBER:-manual}"
            remote="$remote_user@$remote_host"
            push_refs="$IMAGE"
            if [ -n "${DEV_IMAGE_TAG:-}" ] && [ "${DEV_IMAGE_TAG:-}" != "$TAG" ]; then
              push_refs="$push_refs $DEV_IMAGE"
            fi
            for ref in $push_refs; do
              case "$ref" in *[!a-zA-Z0-9._/:-]*|'') echo "invalid image ref: '$ref'" >&2; exit 1 ;; esac
            done
            echo "Image build runs on $remote ($BUILD_PLATFORM) for env ${ENVIRONMENT:-dev}: $TAG_ARGS"
            ssh "$remote" "rm -rf '$remote_dir' && mkdir -p '$remote_dir'"
            rsync -az --delete \
              --exclude '.git' \
              --exclude '.deps/options-edge-contracts/.git' \
              ./ "$remote:$remote_dir/"
            if [ "${ENVIRONMENT:-dev}" = "experiment" ]; then
              ssh "$remote" "limactl shell centos -- sudo docker build --no-cache $TAG_ARGS '$remote_dir'"
              for ref in $push_refs; do
                ssh "$remote" "limactl shell centos -- sudo docker push '$ref'"
              done
              ssh "$remote" "rm -rf '$remote_dir'"
            else
              push_cmd=""
              for ref in $push_refs; do push_cmd="$push_cmd && docker push '$ref'"; done
              ssh "$remote" "cd '$remote_dir' && docker build --no-cache $TAG_ARGS . $push_cmd && rm -rf '$remote_dir'"
            fi
          elif [ "$PUSH_IMAGE" = "true" ]; then
            docker buildx build --platform "$BUILD_PLATFORM" --no-cache $TAG_ARGS --push .
          else
            docker buildx build --platform "$BUILD_PLATFORM" --no-cache $TAG_ARGS --load .
          fi
        '''
      }
    }
  }
}
