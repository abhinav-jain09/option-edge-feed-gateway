pipeline {
  agent any
  environment {
    JAVA_HOME = '/usr/lib/jvm/java-26'
    PATH = "/usr/lib/jvm/java-26/bin:${env.PATH}"
  }
  parameters {
    string(name: 'IMAGE_REGISTRY', defaultValue: 'ghcr.io/abhinav-jain09', description: 'Docker registry namespace')
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Docker tag. Defaults to current git SHA.')
    booleanParam(name: 'PUSH_IMAGE', defaultValue: true, description: 'Push built image to registry')
  }
  stages {
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
          docker build -t "$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG" .
          if [ "$PUSH_IMAGE" = "true" ]; then
            docker push "$IMAGE_REGISTRY/options-edge-feed-gateway:$TAG"
          fi
        '''
      }
    }
  }
}
