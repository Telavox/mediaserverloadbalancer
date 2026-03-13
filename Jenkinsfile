@Library("Telavox") _
tvxInit()

pipeline {
  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers {
    upstream tvxUpstream(["base","telavox-commons"])
  }

  environment {
    MAVENLOCALREPO_INWORKSPACE = "${WORKSPACE}/m2"
    DOCKER_REGISTRY_CREDENTIALS = "jenkins-docker-registry"
    REGISTRY = "docker-registry.service.telavox.se"
    DTRACK_URL = "https://dependency-track.service.telavox.se/api/v1"
    DTRACK_API_KEY = credentials("DTRACK_API_KEY")
  }

  parameters {
    booleanParam(name: 'BUILD_DOCKER_CONTAINER', defaultValue: false, description: 'Check to build a Docker container')
      string(
        name: 'TELAVOX_COMMONS',
        defaultValue: 'master',
        description: 'Which branch should be used?'
      )
  }

  agent {
    docker {
      image 'docker-registry.service.telavox.se/telavox/corretto-jdk21-buildenv'
      args '-u root'
      reuseNode true
    }
  }

  stages {

    stage("Setup variables") {
      steps {
        script {
          env.MAVEN_EXTRA_ARGS = "-Dmaven.repo.local=${MAVENLOCALREPO_INWORKSPACE}"
          if (env.CHANGE_BRANCH) {
              env.MAVEN_EXTRA_ARGS += " -Drevision=${env.CHANGE_BRANCH}-SNAPSHOT"
          }
        }
      }
    }

    stage("Maven build") {
      steps {
        sh "mvn -B clean package ${env.MAVEN_EXTRA_ARGS} -DskipTests"
      }
    }

    stage("Deploy maven artifacts") {
      steps {
        script {
          tvxPublish(skipTests: true, mavenArgs: env.MAVEN_EXTRA_ARGS)
        }
      }
    }

    stage('Build&Deploy Docker Container') {
      agent {
        dockerfile {
          additionalBuildArgs '--pull'
          reuseNode true
        }
      }
      when {
        expression { params.BUILD_DOCKER_CONTAINER }
      }
      steps {
        script {
          def base = "telavox/mediaserverloadbalancer"
          def version = "${env.CHANGE_BRANCH ?: 'master'}-${env.BUILD_NUMBER}"
          docker.build("${base}:${version}", ".")
          def img = docker.image("${base}:${version}")
          docker.withRegistry("http://${REGISTRY}", DOCKER_REGISTRY_CREDENTIALS) {
            img.push()
          }
        }
      }
    }

    stage("Vulnerability management") {
        when {
          branch 'master'
        }
        steps {
            tvxDependencyTrackSource(
              projectName: "mediaserverloadbalancer",
              parentName: "PBX & Core Services",
              tags: ["Owner:pcs", "Tool:Dependency-Check", "Scan-Env:Jenkins"],
              dtrackUrl: "${DTRACK_URL}",
              apiKey: "${DTRACK_API_KEY}"
            )
        }
    }
  }

  post {
    always {
      tvxPostBuild(
        sendMail: false,
        cleanWS: true,
        testng: null,
        junit: null,
        sendChat: false,
        sendChatOnUnstable: false
      )
    }
  }
}
