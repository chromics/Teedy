pipeline {
  agent any

  environment {
    // Docker Hub credentials ID stored in Jenkins
    DOCKER_HUB_CREDENTIALS = credentials('dockerhub_login')

    // Docker Hub image name (username/repo)
    DOCKER_IMAGE = '12310948/teedy'

    // Use Jenkins build number as Docker tag
    DOCKER_TAG = "${BUILD_NUMBER}"
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
            docker.image("${DOCKER_IMAGE}:${DOCKER_TAG}").push('latest') // Optional
          }
        }
      }
    }

    stage('Run Containers') {
      steps {
        script {
          // Stop and remove any existing containers on these ports
          sh 'docker stop teedy-container-8082 || true'
          sh 'docker rm teedy-container-8082 || true'
          sh 'docker stop teedy-container-8083 || true'
          sh 'docker rm teedy-container-8083 || true'
          sh 'docker stop teedy-container-8084 || true'
          sh 'docker rm teedy-container-8084 || true'

          // Run three containers on different ports
          docker.image("${DOCKER_IMAGE}:${DOCKER_TAG}").run('--name teedy-container-8082 -d -p 8082:8080')
          docker.image("${DOCKER_IMAGE}:${DOCKER_TAG}").run('--name teedy-container-8083 -d -p 8083:8080')
          docker.image("${DOCKER_IMAGE}:${DOCKER_TAG}").run('--name teedy-container-8084 -d -p 8084:8080')

          // List all teedy containers
          sh 'docker ps --filter "name=teedy-container"'
        }
      }
    }
  }
}
