pipeline {
  agent any

  environment {
    // Docker Hub credentials ID stored in Jenkins
    DOCKER_HUB_CREDENTIALS = credentials('dockerhub_login')

    // Docker Hub image name (username/repo)
    DOCKER_IMAGE = '12310948/teedy'

    // Use Jenkins build number as Docker tag
    DOCKER_TAG = "${BUILD_NUMBER}"

    // Kubernetes deployment configuration
    DEPLOYMENT_NAME = "teedy"
    CONTAINER_NAME = "teedy"
    IMAGE_NAME = "${DOCKER_IMAGE}:${DOCKER_TAG}"
    K8S_SECRET_NAME = "myregistrykey"
  }

  stages {
    stage('Build') {
      steps {
        checkout scmGit(
          branches: [[name: '*/master']],
          extensions: [],
          userRemoteConfigs: [[url: 'https://github.com/chromics/Teedy.git']]
        )
        sh 'mvn -B -DskipTests clean package'
      }
    }

    stage('Build Docker Image') {
      steps {
        script {
          docker.build("${DOCKER_IMAGE}:${DOCKER_TAG}")
        }
      }
    }

    stage('Push to Docker Hub') {
      steps {
        script {
          docker.withRegistry('https://registry.hub.docker.com', 'dockerhub_login') {
            docker.image("${DOCKER_IMAGE}:${DOCKER_TAG}").push()
            docker.image("${DOCKER_IMAGE}:${DOCKER_TAG}").push('latest') // optional
          }
        }
      }
    }

    stage('Deploy to Kubernetes') {
      steps {
        sh '''
          echo "Updating image in Kubernetes..."
          kubectl set image deployment/${DEPLOYMENT_NAME} ${CONTAINER_NAME}=${IMAGE_NAME}

          echo "Waiting for rollout to complete..."
          kubectl rollout status deployment/${DEPLOYMENT_NAME}
        '''
      }
    }

    stage('Start Minikube') {
      steps {
        sh '''
          if ! minikube status | grep -q "Running"; then
            echo "Starting Minikube..."
            minikube start
          else
            echo "Minikube already running."
          fi
        '''
      }
    }

    stage('Patch Image Pull Secret') {
      steps {
        sh '''
          echo "Patching deployment to use imagePullSecrets..."
          kubectl patch deployment teedy -p '{"spec":{"template":{"spec":{"imagePullSecrets":[{"name":"myregistrykey"}]}}}}'
        '''
      }
    }

    stage('Expose Service') {
      steps {
        sh '''
          if ! kubectl get svc teedy > /dev/null 2>&1; then
            kubectl expose deployment teedy --type=NodePort --port=8080
          fi
        '''
      }
    }

    stage('Verify Rollout') {
      steps {
        sh 'kubectl rollout status deployment/${DEPLOYMENT_NAME}'
        sh 'kubectl get pods'
      }
    }
  }
}
