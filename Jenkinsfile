#!/usr/bin/env groovy
/*
 * Declarative Jenkins pipeline for the Order Management platform.
 *
 * Jenkins prerequisites (Manage Jenkins -> Tools / Credentials):
 *   - JDK tool name:   jdk-21
 *   - Maven tool name: maven-3.9
 *   - String credential:       docker-registry-url        (e.g. registry.example.com)
 *   - Username/password credential: docker-registry-credentials
 *   - File credential:         kubeconfig                 (YAML for the target cluster)
 *   - Plugins: JUnit, JaCoCo, Credentials Binding.
 *
 * Assumptions: service Dockerfiles live at services/<name>/Dockerfile and use
 * the repository root as build context; Kubernetes Deployment and container
 * names match the service names in K8S_NAMESPACE.
 */

pipeline {
    agent any

    options {
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    tools {
        jdk 'jdk-21'
        maven 'maven-3.9'
    }

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        K8S_NAMESPACE = 'order-platform'
        SERVICES = 'api-gateway order-service inventory-service notification-service'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B -ntp clean package -DskipTests'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn -B -ntp test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                // Full verify runs Surefire and the Testcontainers-based Failsafe suite.
                sh 'mvn -B -ntp verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml'
                    jacoco execPattern: '**/target/jacoco.exec',
                           classPattern: '**/target/classes',
                           sourcePattern: '**/src/main/java',
                           changeBuildStatus: false
                    archiveArtifacts artifacts: '**/target/site/jacoco/**,**/target/failsafe-reports/**', allowEmptyArchive: true
                }
            }
        }

        stage('Static Analysis') {
            steps {
                sh 'mvn -B -ntp checkstyle:check spotbugs:check -DskipTests'
            }
            post {
                always {
                    archiveArtifacts artifacts: '**/target/checkstyle-result.xml,**/target/spotbugsXml.xml', allowEmptyArchive: true
                }
            }
        }

        stage('Dependency Scan') {
            steps {
                sh 'mvn -B -ntp org.owasp:dependency-check-maven:check -DskipTests -DfailBuildOnCVSS=7'
            }
            post {
                always {
                    archiveArtifacts artifacts: '**/target/dependency-check-report.html,**/target/dependency-check-report.json', allowEmptyArchive: true
                }
            }
        }

        stage('Build Images') {
            steps {
                withCredentials([string(credentialsId: 'docker-registry-url', variable: 'REGISTRY')]) {
                    script {
                        env.SERVICES.split(' ').each { service ->
                            withEnv(["SERVICE=${service}"]) {
                                sh 'docker build -t "$REGISTRY/order-platform/$SERVICE:$IMAGE_TAG" -f "services/$SERVICE/Dockerfile" .'
                            }
                        }
                    }
                }
            }
        }

        stage('Push Images') {
            steps {
                withCredentials([
                    string(credentialsId: 'docker-registry-url', variable: 'REGISTRY'),
                    usernamePassword(credentialsId: 'docker-registry-credentials', usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASS')
                ]) {
                    script {
                        sh 'echo "$REGISTRY_PASS" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
                        env.SERVICES.split(' ').each { service ->
                            withEnv(["SERVICE=${service}"]) {
                                sh 'docker push "$REGISTRY/order-platform/$SERVICE:$IMAGE_TAG"'
                            }
                        }
                        sh 'docker logout "$REGISTRY"'
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([
                    string(credentialsId: 'docker-registry-url', variable: 'REGISTRY'),
                    file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')
                ]) {
                    script {
                        sh 'kubectl apply -f deploy/k8s/ -n "$K8S_NAMESPACE"'
                        env.SERVICES.split(' ').each { service ->
                            withEnv(["SERVICE=${service}"]) {
                                sh 'kubectl set image deployment/"$SERVICE" "$SERVICE=$REGISTRY/order-platform/$SERVICE:$IMAGE_TAG" -n "$K8S_NAMESPACE"'
                                sh 'kubectl rollout status deployment/"$SERVICE" -n "$K8S_NAMESPACE" --timeout=180s'
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/target/surefire-reports/**,**/target/failsafe-reports/**', allowEmptyArchive: true
            deleteDir()
        }
        failure {
            echo 'Pipeline failed. Check test reports, static analysis output and dependency scan artifacts.'
        }
    }
}
