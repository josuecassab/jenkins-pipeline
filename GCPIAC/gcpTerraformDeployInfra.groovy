@Library('jenkins-oke-libs')l1

def propsListEnv

def project = "GCPFE"
def repo = "${GBL_BITBUCKET_BASEURL}/${project}/${REPO_NAME}.git"
def jobKeys = env.JOB_NAME.toString().split("/")
def pipelineName = jobKeys[1]

pipeline {
    agent {
        kubernetes {
            yamlFile 'GCPIAC/gcpiac.yaml'
            showRawYaml false
            //idleMinutes "20"
        }
    }
    environment{
        DIRECTORY = "${INFRA_TYPE == "project" ? 'project' : 'resources'}"
    }
    options {
        ansiColor('xterm')
    }
    stages {
        stage('Carga Properties Env') {
            steps {
                script {
                    logc.titulo("STAGE: Carga Properties Env")
                    container('terraform') {

                        logc.info 'Terraform version'
                        sh "terraform --version"
                    }
                    logc.info "ENV: ${ENV}"
                    logc.info "REPO_NAME: ${REPO_NAME}"
                    logc.info "INFRA_TYPE: ${INFRA_TYPE}"
                    logc.info "BRANCH: ${BRANCH}"
                    logc.info "PLAN_ONLY: ${PLAN_ONLY}"
                    logc.info "DESTROY: ${DESTROY}"
                    logc.info "ANALYSIS: ${ANALYSIS}"
                    propsListEnv = propertiesUtil.getProjectProp("GCPIAC", "DSO", pipelineName, ENV)
                    logc.info "CREDENTIAL_NAME: ${propsListEnv.credential_name}"
                }
            }
        }
        stage('Static code analysis') {
            when { 
                allOf{
                    expression { DESTROY == 'false' }
                    expression { ANALYSIS == 'true' }
                }
            }
            steps {
                script {
                    // Invocar Pipeline Prisma
                    build job: 'PRISMA/checkov', parameters: [
                        string(name: 'projectBitbucket', value: project),
                        string(name: 'repoBitbucket', value: REPO_NAME),
                        string(name: 'branchBitbucket', value: BRANCH),
                        string(name: 'analyze_project', value: DIRECTORY), // Pasar una cadena con los proyectos separados por coma
                        string(name: 'cloud', value: 'GCP')
                    ]
                }
            }
        }
        stage('Bitbucket') {
            steps {
                script {
                    logc.titulo('STAGE: Bitbucket')

                    // Descarga repo FrontEnd
                    logc.info('Descargando repositorio FrontEnd')
                    logc.info "GitURL: ${repo}"
                    checkout([$class: 'GitSCM',
                        branches: [[name: BRANCH]],
                        userRemoteConfigs: [[
                            credentialsId: GBL_BITBUCKET_GITCREDENTIALID,
                            url: repo
                            ]]]
                    )
                }
            }
        }
        stage('Terraform Init') {
            steps {
                script {
                    logc.titulo("STAGE: Terraform Init")
                    container('terraform') {
                        dir(DIRECTORY) {               
                            withCredentials([gitUsernamePassword(credentialsId: GBL_BITBUCKET_GITCREDENTIALID), 
                            file(credentialsId: propsListEnv.credential_name, variable:'GOOGLE_APPLICATION_CREDENTIALS')]) {
                                sh "terraform init -backend-config=${propsListEnv.backend_file}"
                            }
                        }
                    }
                }
            }
        }
        stage('Terraform Plan') {
            when { expression { DESTROY == 'false' } }
            steps {
                script {
                    logc.titulo("STAGE: Terraform Plan")
                    container('terraform') {
                        dir(DIRECTORY) {  
                            withCredentials([file(credentialsId: propsListEnv.credential_name, variable:'GOOGLE_APPLICATION_CREDENTIALS')]) {
                                sh "terraform plan -var-file=${propsListEnv.infra_file} -var-file=${propsListEnv.labels_file} -out 'tfplan'"
                                sh "terraform show -json 'tfplan' > tfplan.json"
                            }
                        }
                    }
                    container("jnlp") {
                        dir(DIRECTORY) {  
                            withCredentials([file(credentialsId: propsListEnv.credential_name, variable:'GOOGLE_APPLICATION_CREDENTIALS')]) {
                                sh "jq '.planned_values' tfplan.json"
                            }
                        }
                    }
                }
            }
        }
        stage('Terraform Apply') {
            when { 
                allOf{
                    expression { DESTROY == 'false' }
                    expression { PLAN_ONLY == 'false' }
                }
            }
            steps {
                script {
                    logc.titulo("STAGE: Terraform Apply")
                    container('terraform') {
                        dir(DIRECTORY) {  
                            withCredentials([file(credentialsId: propsListEnv.credential_name, variable:'GOOGLE_APPLICATION_CREDENTIALS')]) {
                                sh 'terraform apply tfplan'
                            }
                        }
                    }
                }
            }
        }
        stage('Terraform Destroy') {
            when { expression { DESTROY == 'true' } }
            steps {
                script {
                    logc.titulo("STAGE: Terraform Destroy")
                    container('terraform') {
                        dir(DIRECTORY) {  
                            withCredentials([file(credentialsId: propsListEnv.credential_name, variable:'GOOGLE_APPLICATION_CREDENTIALS')]) {
                                sh "terraform destroy -var-file=${propsListEnv.infra_file} -var-file=${propsListEnv.labels_file} --auto-approve"
                            }
                        }
                    }
                }
            }
        }
        stage('Limpia Workspace') {
            steps {
                script {
                    logc.titulo("STAGE: Limpia Workspace")
                    cleanWs()
                }
            }                
        } 
    }
}