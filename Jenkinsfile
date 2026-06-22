@Library('oe') _

pipeline {
  agent any
  parameters {
    choice(name: 'ENVIRONMENT', choices: ['dev', 'production'], description: 'Target environment — drives registry + build platform from oeProfile (single source of truth)')
    string(name: 'IMAGE_REGISTRY', defaultValue: '', description: 'Override registry. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers (e.g. bring-up-all).')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Docker tag. Defaults to current git SHA.')
    string(name: 'DEV_IMAGE_TAG', defaultValue: 'dev', description: 'Also publish this mutable dev tag for the deploy job. Empty disables it.')
    string(name: 'BUILD_PLATFORM', defaultValue: '', description: 'Override platform. Empty = derive from oeProfile(ENVIRONMENT). Kept for back-compat callers.')
    string(name: 'CONTRACTS_BRANCH', defaultValue: 'main', description: 'options-edge-contracts branch to install before building the gateway')
    booleanParam(name: 'PUSH_IMAGE', defaultValue: true, description: 'Push built image to registry')
  }
  stages {
    stage('Resolve profile') {
      steps {
        script {
          def p = oeProfile(params.ENVIRONMENT)
          env.IMAGE_REGISTRY = params.IMAGE_REGISTRY?.trim() ? params.IMAGE_REGISTRY : p.registry
          env.BUILD_PLATFORM = params.BUILD_PLATFORM?.trim() ? params.BUILD_PLATFORM : p.platform
          // The set of plain-http registries — derived from EVERY profile whose
          // insecureRegistry is true, plus dev-registry loopback aliases. The Image stage
          // writes a buildkit insecure-registry config for the EFFECTIVE IMAGE_REGISTRY iff
          // it's in this set (so prod pushes work via http, not just dev).
          def insecure = ['dev', 'production'].findAll { oeProfile(it).insecureRegistry }
                                                          .collect { oeProfile(it).registry }
          insecure += ['localhost:5001', '127.0.0.1:5001']   // loopback aliases of the dev registry
          env.INSECURE_REGISTRIES = insecure.unique().join(' ')
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
          # Write a buildkit insecure-registry entry for the EFFECTIVE registry iff it is
          # in $INSECURE_REGISTRIES (derived from oeProfile in the Resolve profile stage).
          # Without this, prod pushes via docker buildx fail with
          # 'http: server gave HTTP response to HTTPS client'.
          registry_insecure=false
          for r in $INSECURE_REGISTRIES; do
            if [ "$IMAGE_REGISTRY" = "$r" ]; then registry_insecure=true; break; fi
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
          if [ "$PUSH_IMAGE" = "true" ]; then
            docker buildx build --platform "$BUILD_PLATFORM" --no-cache $TAG_ARGS --push .
          else
            docker buildx build --platform "$BUILD_PLATFORM" --no-cache $TAG_ARGS --load .
          fi
        '''
      }
    }
  }
}
