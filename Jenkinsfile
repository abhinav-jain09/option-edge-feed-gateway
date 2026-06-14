pipeline {
  agent any
  environment {
    JAVA_HOME = '/usr/lib/jvm/java-26'
    PATH = "/usr/lib/jvm/java-26/bin:${env.PATH}"
  }
  parameters {
    string(name: 'IMAGE_REGISTRY', defaultValue: '192.168.100.252:5000', description: 'Docker registry namespace')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Docker tag. Defaults to current git SHA.')
    string(name: 'DEV_IMAGE_TAG', defaultValue: 'dev', description: 'Also publish this mutable dev tag for the deploy job. Empty disables it.')
    string(name: 'CONTRACTS_BRANCH', defaultValue: 'main', description: 'options-edge-contracts branch to install before building the gateway')
    booleanParam(name: 'PUSH_IMAGE', defaultValue: true, description: 'Push built image to registry')
  }
  stages {
    stage('Install Contracts') {
      steps {
        sh '''
          export JAVA_HOME=/usr/lib/jvm/java-26
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
          export JAVA_HOME=/usr/lib/jvm/java-26
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
          export JAVA_HOME=/usr/lib/jvm/java-26
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
          set -euo pipefail
          TAG="${IMAGE_TAG:-$(git rev-parse --short=12 HEAD)}"
          DEV_TAG="${DEV_IMAGE_TAG:-}"
          docker build --no-cache -t "$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG" .
          if [ -n "$DEV_TAG" ] && [ "$DEV_TAG" != "$TAG" ]; then
            docker tag "$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG" "$IMAGE_REGISTRY/options-edge-feed-gateway:$DEV_TAG"
          fi
          if [ "$PUSH_IMAGE" = "true" ]; then
            docker push "$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG"
            if [ -n "$DEV_TAG" ] && [ "$DEV_TAG" != "$TAG" ]; then
              docker push "$IMAGE_REGISTRY/options-edge-feed-gateway:$DEV_TAG"
            fi
          fi
        '''
      }
    }
  }
}
