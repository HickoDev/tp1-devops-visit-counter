// Alternative Jenkins : agent Linux equipe de Docker, Java 21 et Maven 3.6+.
pipeline {
    agent any
    options { disableConcurrentBuilds(); timestamps() }
    triggers { githubPush() }
    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Build and unit tests') {
            steps { sh 'mvn -B -ntp verify' }
            post { always { junit 'target/surefire-reports/*.xml' } }
        }
        stage('Build image') {
            steps { sh 'docker build -t counter-app:ci-$BUILD_NUMBER .' }
        }
        stage('Publish main to Docker Hub') {
            when {
                expression {
                    env.BRANCH_NAME == 'main' ||
                    env.GIT_BRANCH in ['main', 'origin/main']
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'HUB_USER', passwordVariable: 'HUB_TOKEN')]) {
                    sh '''
                        set +x
                        export DOCKER_CONFIG=$(mktemp -d)
                        trap 'rm -rf "$DOCKER_CONFIG"' EXIT
                        printf '%s' "$HUB_TOKEN" | docker login -u "$HUB_USER" --password-stdin
                        IMAGE="$HUB_USER/counter-app"
                        docker tag counter-app:ci-$BUILD_NUMBER "$IMAGE:build-$BUILD_NUMBER"
                        docker tag counter-app:ci-$BUILD_NUMBER "$IMAGE:latest"
                        docker push "$IMAGE:build-$BUILD_NUMBER"
                        docker push "$IMAGE:latest"
                    '''
                }
            }
        }
    }
    post {
        always { sh 'docker image rm counter-app:ci-$BUILD_NUMBER || true' }
    }
}
