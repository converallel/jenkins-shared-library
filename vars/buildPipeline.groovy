#!/usr/bin/env groovy

def call(body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    def hosts = [
            'jenkins': ['db': 'localhost', 'credentialId': 'jenkins-local-db'],
            'test'   : ['web': 'test-web1', 'db': 'test-mysql1-private', 'credentialId': 'test-mysql1'],
            'prod'   : [['web': 'prod-web1', 'db': 'prod-mysql1-private', 'credentialId': 'prod-mysql1'],
                        ['web': 'prod-web1w', 'db': 'prod-mysql1w-private', 'credentialId': 'prod-mysql1w']]
    ]

    node {
        def gitRepoFullUrl = scm.getUserRemoteConfigs()[0].getUrl()
        def gitRepoUrl = gitRepoFullUrl.minus('https://')
        def gitOrganizationName = gitRepoUrl.tokenize('/')[1]
        def gitRepoName = gitRepoUrl.tokenize('/')[-1].minus('.git')
        def shellScriptDir = "${env.WORKSPACE}@libs/shared-library/resources"
        def isPullRequest = env.CHANGE_ID != null
        def deployingToProd = env.BRANCH_NAME == 'master'
        def remoteDir = deployingToProd ? gitRepoName : "${gitRepoName}/${env.CHANGE_BRANCH ?: env.BRANCH_NAME}"
        def dbName = isPullRequest && env.CHANGE_BRANCH != 'integration' ? "${gitRepoName}_${env.CHANGE_BRANCH}" : gitRepoName
        def reportDir = 'reports'

        // replace all special characters in the db name with underscore
        dbName = dbName.replaceAll("\\W", '_')

        // only allow 'integration' or 'hotfix' branches to merge into master
        if (env.CHANGE_TARGET == 'master' && env.CHANGE_BRANCH != 'integration' && !env.CHANGE_BRANCH.startsWith('hotfix')) {
            dir("${env.WORKSPACE}@libs") {
                deleteDir()
            }
            throw new Exception("Cannot merge ${env.CHANGE_BRANCH} into master, this attempt has been reported.")
        }

        // skip builds for developing branches
        if (!isPullRequest && !['master', 'integration'].contains(env.BRANCH_NAME)) {
            dir("${env.WORKSPACE}@libs") {
                deleteDir()
            }
            return
        }

        withEnv(["SHELL_SCRIPT_DIR=${shellScriptDir}", "GIT_REPO_NAME=${gitRepoName}", "GIT_REPO_URL=${gitRepoUrl}",
                 "REPORT_DIR=${reportDir}", "SSH_REMOTE_DIR=${remoteDir}", "DB_NAME=${dbName}"]) {

            try {
                stage('Checkout') {
                    checkout scm
                }

                stage('Build') {
                    // create .env file, which contains the environment specific configuration
                    execute("${shellScriptDir}/env-config.sh", hosts.jenkins.db, hosts.jenkins.credentialId)

                    // create .htaccess files
                    sh "${shellScriptDir}/htaccess.sh"

                    // install (dev) dependencies
                    sh "composer install"

                    // setup db on jenkins db
                    execute("${shellScriptDir}/development/test-db-setup.sh", hosts.jenkins.db, hosts.jenkins.credentialId)
                }

                try {
                    stage('Test') {
                        sh "${shellScriptDir}/development/test.sh"
                    }
                } finally {
                    stage('Publish test reports') {
                        // publish checkstyle analysis report
                        checkstyle canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '', unHealthy: ''

                        // publish duplicate code analysis report, compatible with PHPUnit >= 7
                        // current version of CakePHP is incompatible with PHPUnit 7
                        // dry canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '', unHealthy: ''

                        // publish mess detector analysis report
                        pmd canComputeNew: false, canRunOnFailed: true, defaultEncoding: '', healthy: '', pattern: '', unHealthy: ''

                        // publish code coverage report
                        step([
                                $class              : 'CloverPublisher',
                                cloverReportDir     : reportDir,
                                cloverReportFileName: 'clover.xml',
                                healthyTarget       : [methodCoverage: 70, conditionalCoverage: 80, statementCoverage: 80], // optional, default is: method=70, conditional=80, statement=80
                                unhealthyTarget     : [methodCoverage: 50, conditionalCoverage: 50, statementCoverage: 50], // optional, default is none
                                failingTarget       : [methodCoverage: 0, conditionalCoverage: 0, statementCoverage: 0]     // optional, default is none
                        ])

                        // publish phpunit test report
                        junit "${reportDir}/phpunit.xml"

                        // remove test db
                        execute("echo \${DB_PASSWORD} | mysql -h \${DB_HOST} -u \${DB_USERNAME} -p -e 'DROP DATABASE IF EXISTS test_${DB_NAME};'",
                                hosts.jenkins.db, hosts.jenkins.credentialId)
                    }
                }

                if (isPullRequest) {
                    // send files to staging server if tests passed and it's not a hotfix branch
                    if (!env.CHANGE_BRANCH.startsWith('hotfix')) {
                        stage('Deploy - Staging') {
                            deployTo(hosts.test.web, hosts.test.db, hosts.test.credentialId, true)
                        }
                    }
                } else { // only push to master or integration gets here
                    switch (env.BRANCH_NAME) {
                        case 'master':
                            stage('Deploy - Production') {
                                hosts.prod.each { host ->
                                    deployTo(host.web, host.db, host.credentialId, false)
                                }
                            }
                            break
                        case 'integration':
                            stage('Deploy - Staging') {
                                deployTo(hosts.test.web, hosts.test.db, hosts.test.credentialId, true)
                            }
                            break
                    }
                }
            } finally {
                stage('Cleanup') {
                    withCredentials([usernamePassword(credentialsId: 'jenkins-github-user', passwordVariable: 'JENKINS_GIT_PASSWORD', usernameVariable: 'JENKINS_GIT_USERNAME')]) {
                        withEnv(["GIT_REPO_URL=${gitRepoUrl}", "SSH_USERNAME=jenkins", "SSH_SERVER=${hosts.test.web}",
                                 "JENKINS_GIT_PASSWORD=${URLEncoder.encode(JENKINS_GIT_PASSWORD, "UTF-8")}",
                                 "JENKINS_GIT_USERNAME=${JENKINS_GIT_USERNAME}",
                                 "REPO_LOCAL_DIR=${env.JENKINS_HOME}/jobs/${gitOrganizationName}/jobs/${gitRepoName}"]) {
                            // remove test dbs and folders on staging server
                            execute("${shellScriptDir}/cleanup.sh", resolveHostname(hosts.test.db), hosts.test.credentialId)
                        }
                    }

                    // clean jenkins workspace
                    cleanWs()
                    dir("${env.WORKSPACE}@tmp") {
                        deleteDir()
                    }
                    dir("${env.WORKSPACE}@libs") {
                        deleteDir()
                    }
                    dir("${env.WORKSPACE}@script") {
                        deleteDir()
                    }
                    dir("${env.WORKSPACE}@script@tmp") {
                        deleteDir()
                    }
                }
            }
        }
    }
}

def execute(scrip, dbHost, credentialId) {
    withCredentials([usernamePassword(credentialsId: credentialId, passwordVariable: 'DB_PASSWORD', usernameVariable: 'DB_USERNAME')]) {
        withEnv(["DB_HOST=${dbHost}"]) {
            sh scrip
        }
    }
}

def deployTo(serverName, dbHost, credentialId, isStaging) {
    withCredentials([usernamePassword(credentialsId: credentialId, passwordVariable: 'DB_PASSWORD', usernameVariable: 'DB_USERNAME')]) {
        withEnv(["DB_HOST=${resolveHostname(dbHost)}", "SERVER_NAME=${serverName}", "STAGING=${isStaging}"]) {
            sh "${SHELL_SCRIPT_DIR}/env-config.sh"
            sh "${SHELL_SCRIPT_DIR}/ssh-publish.sh"
        }
    }
}

def resolveHostname(hostname) {
    return sh(script: "resolveip -s ${hostname} | tr -d '\n'", returnStdout: true)
}
