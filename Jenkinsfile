pipeline {
  agent any
  
  triggers {
    pollSCM('')
    githubPush()
  }
  
  options {
    buildDiscarder(logRotator(numToKeepStr: '1'))
    disableConcurrentBuilds()
    timeout(time: 1, unit: 'HOURS')
    retry(3)
  }
  
  tools {
    jdk "jdk8"
    maven "maven"
  }
  
  stages {
    stage("Build Nannoq-Tools") {
      steps {
        withCredentials([string(credentialsId: 'gpg-pass-nannoq', variable: 'TOKEN')]) {
          configFileProvider([configFile(fileId: 'ossrh-nannoq-config', variable: 'MAVEN_SETTINGS')]) {
            sh 'mvn -s $MAVEN_SETTINGS -N -Dgpg.passphrase=$TOKEN clean deploy'
          }
        }
      }
    }
  }
  
  post {
    failure {
      mail to: 'mikkelsen.anders@gmail.com',
          subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
          body: "Something is wrong with ${env.BUILD_URL}"
    }
  }
}
