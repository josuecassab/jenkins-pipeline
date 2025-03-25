@Library('jenkins-oke-libs')l1

def propsListEnv

def TERRAFORM_VERSION_OCI = "0.12.18"

def jobData = [
    project     : "${projectBitbucket}",
    repo        : "${repoBitbucket}",
    branch      : "${branchBitbucket}",
    environment : "dev",
    analyze_project: project.split(','),
    cloud: "${cloud}"
]

def gitUrl = [
    frontEnd: "${GBL_BITBUCKET_BASEURL}/${projectBitbucket}/${repoBitbucket}.git"
]

//if(!['AWSBE','AFC', 'AZIACBE', 'OCIBE'].contains(projectBitbucket)){
switch("${cloud}"){
    case "AWS":
        gitUrl.backEnd = "${GBL_BITBUCKET_BASEURL}/AFC/${repoBitbucket}.git"
        
        if("${repoBitbucket}" == "aws-dev")
            jobData.aws_credentials = "iacAwsBambooDev"
        else if("${repoBitbucket}" == "aws-qa")
            jobData.aws_credentials = "iacAwsBambooQa"
        else{
            jobData.aws_credentials = "SnotifDevServiceUserAWS"
            jobData.tfstate_bucket = "tfstate-37whd5sqhk-6pv0cew9ke"
            jobData.tfstate_dynamodb_table = "tfstate-lock"
        }
        break
    case "OCI":
        gitUrl.backEnd = "${GBL_BITBUCKET_BASEURL}/ocibe/oci_ansible_roles.git"
        break
    default:
        break
}
//}

//env.RESULT_CKV =

def runCheckov(proj, path_scan){
    def result_ckv = []
    container('checkov') {     
    
        logc.titulo("STAGE: Prisma Code Security Analysis in ${proj}")

        withCredentials([
            gitUsernamePassword(credentialsId: GBL_BITBUCKET_GITCREDENTIALID),
            usernamePassword(credentialsId: 'prismaCheckov', 
                passwordVariable: 'PRISMA_CHECKOV_SECRET_ID', 
                usernameVariable: 'PRISMA_CHECKOV_KEY_ID')])
        {
            def partName = "${cloud}_${projectBitbucket}_${repoBitbucket}"
            env.ANALYSIS_OWNER  = partName
            env.ANALYSIS_FOLDER = proj

            //env.LOG_LEVEL="DEBUG"

            if(proj == ""){
                env.ANALYSIS_FOLDER = "${repoBitbucket}"
            }

            def checkov_key='--bc-api-key ${PRISMA_CHECKOV_KEY_ID}::${PRISMA_CHECKOV_SECRET_ID}'
            def result = sh script: """
                checkov --directory ${path_scan}\
                    --repo-id ${partName}/${ANALYSIS_FOLDER}\
                    --branch ${branchBitbucket}\
                    --download-external-modules true\
                    --external-modules-download-path ${path_scan}/external\
                    ${checkov_key} \
                    --prisma-api-url https://api2.prismacloud.io --framework terraform --support --compact --quiet 
            """, returnStatus: true
            //  --framework secrets terraform terraform_json terraform_plan
                
            println "STATUS: ${result}"
            //result_ckv << ((result != 0) ? 'FAIL' : 'OK'
            env.RESULT_CKV = result

            nexusUtil.configPythonLibs()
            writeFile file: 'addProjToGroup.py', text: env.IAC_ANALYSIS
            sh """
                python3 addProjToGroup.py
                rm -rf addProjToGroup.py
            """
                
        } 
    }
}

def downloadTerraformVersion(version){
    def urlTerraform = "https://releases.hashicorp.com/terraform/${version}/terraform_${version}_linux_amd64.zip"

    sh "wget -q -P ${WORKSPACE} ${urlTerraform} --timeout=20 "
    sh " unzip -q -o ${WORKSPACE}/terraform_${version}_linux_amd64.zip -d ${WORKSPACE}"
}

/*
    Pipeline
*/
pipeline {
    agent {
        kubernetes {
            yaml propertiesUtil.getKubernetesYml("PRISMA/checkovAnalysis.yaml")
            showRawYaml false
            //idleMinutes "20"
        }
    }

    environment{
        IAC_ANALYSIS = readFile(file: "shell/SEC/iacAnalysis/addProjToGroup.py")
    }

    options {
        ansiColor('xterm')
    }

    /*
        Stages
    */
    stages {
        stage('Clean Workspace') {
            steps {
                script {
                    logc.titulo('STAGE: Clean Workspace')

                    buildDescription "Analisis de Infraestructura en ${cloud}:\n\t"+
                        "Proyecto: ${projectBitbucket}\n\t"+
                        "Repo: ${repoBitbucket}\n\t"+
                        "Rama: ${branchBitbucket}\n\t"+
                        "Carpetas: ${project}\n\t"
                    cleanWs()

                }
            }
        } 

        stage('Properties Env') {
            steps {
                script {
                    logc.titulo("STAGE: Properties Env")
                    logc.info "propsListEnv: ${jobData}"
                }
            }
        }

        stage('Bitbucket') {
            steps {
                script {
                    logc.titulo('STAGE: Bitbucket')

                    // Descarga repo FrontEnd
                    logc.info('Descargando repositorio FrontEnd')
                    logc.info "GitURL: ${gitUrl['frontEnd']}"
                 
                    checkout([$class: 'GitSCM',
                        branches: [[ name: jobData['branch'] ]],
                        userRemoteConfigs: [[
                            credentialsId: GBL_BITBUCKET_GITCREDENTIALID,
                            url: gitUrl['frontEnd']
                            ]]]
                    )

                    if(jobData['cloud'] == 'AWS' && jobData['project'] != "AWSIAC"){
                        // Descarga repo FrontConfig
                        dir('tfvars') {
                            logc.info('Descargando repositorio FrontConfig')
                            logc.info "GitURL: ${gitUrl['backEnd']}"
                            checkout([$class: 'GitSCM',
                                branches: [[ name: jobData['environment'] ]],
                                userRemoteConfigs: [[
                                    credentialsId: GBL_BITBUCKET_GITCREDENTIALID,
                                    url: gitUrl['backEnd']
                                    ]]]
                                )
                        } 

                        //Validation
                        def failure = []

                        jobData['analyze_project'].each{ proj ->
                            jobData['path_tfvars'] = "${env.WORKSPACE}/tfvars/${proj.trim()}/config.tfvars"

                            logc.info "Validando que existe archivo: ${jobData['path_tfvars']}"

                            if (!fileExists(jobData['path_tfvars'])) {
                                failure <<  jobData['path_tfvars']
                            }
                            logc.info("Archivo ${jobData['path_tfvars']} existe.")
                        }

                        if(failure.size() > 0){
                            failure.each { f-> 
                                def fail = f.trim()
                                logc.error("Archivo ${fail} no existe.")
                                error("FAILURE: Archivo ${fail} no existe.")
                            }
                            currentBuild.result = 'FAILURE'
                        }
                    }
                }
            }
        } 

        stage('Terraform Init') {
            steps {
                script {
                    logc.titulo("STAGE: Terraform Init")
                    logc.info 'Inicializando Terraform'

                    def PATH_SCAN = "${WORKSPACE}"

                    if(jobData['project'] == "AWSIAC"){
                        logc.info "Inicializando Terraform ${jobData['project']}/${jobData['repo']}"
                        container('terraform') {
                            downloadTerraformVersion('0.12.29')

                            withCredentials([ usernamePassword (credentialsId: jobData['aws_credentials'], usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                                // Ejecutar Terraform Init  
                                sh """
                                    ${WORKSPACE}/terraform init
                                """
                            }
                        }

                        runCheckov("${jobData['repo']}", "${WORKSPACE}")
                    }  

                    if(jobData['analyze_project'].size() > 0) {
                        jobData['analyze_project'].each{ proj -> 
                            dir(proj.trim()) {
                                container('terraform') {

                                    if(jobData['cloud'] == "AWS" && jobData['project'] != "AWSIAC"){
                                        withCredentials([ usernamePassword (credentialsId: jobData['aws_credentials'], usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                                                    gitUsernamePassword( credentialsId: GBL_BITBUCKET_GITCREDENTIALID)]) {
                                            // Ejecutar Terraform Init
                                            logc.info "${jobData['repo']}/${proj.trim()}"
                                            sh """
                                                terraform init -backend-config="bucket="${jobData['tfstate_bucket']}"" \
                                                    -backend-config="key=${jobData['repo']}/${proj.trim()}.tfstate" \
                                                    -backend-config="region=us-east-1" \
                                                    -backend-config="dynamodb_table=${jobData['tfstate_dynamodb_table']}"
                                            """
                                        }
                                    }

                                    if(jobData['cloud'] == "OCI"){
                                        def version = TERRAFORM_VERSION_OCI

                                        def exists = fileExists "terraform_version"
                                        if (exists) {
                                            def lines = readFile "terraform_version"
                                            version = lines.readLines()[-1]
                                        }

                                        downloadTerraformVersion(version)

                                        withCredentials([
                                            usernamePassword (credentialsId: 'iacOciAws', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY'),
                                            gitUsernamePassword(  credentialsId: GBL_BITBUCKET_GITCREDENTIALID)]){
                                                sh "${WORKSPACE}/terraform init" 
                                        }
                                    }
                                } 

                                runCheckov(proj, "${WORKSPACE}/${proj}")
                            }
                        }
                    }
                }
            }
        }

        stage('Prisma Code Security Analysis Validate') {
            steps{
                script {
                    logc.titulo("STAGE: Status of Prisma Code Security Analysis")
                    println env.RESULT_CKV

                    if(env.RESULT_CKV == "1"){
                        //logc.error("La IaC No cumple con las politicas definidas en Prisma Cloud. Por favor analizar y corregir las alertas.")
                        logc.info('La IaC No cumple con las politicas definidas en Prisma Cloud. Por favor analizar y corregir las alertas.')
                    }
                }
            }
        }
    }
}