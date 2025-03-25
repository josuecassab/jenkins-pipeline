@Library('jenkins-oke-libs') l1

import javaposse.jobdsl.plugin.GlobalJobDslSecurityConfiguration
import jenkins.model.GlobalConfiguration

GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).useScriptSecurity=false

//Parametros de entrada
//FOLDER: carpeta a ejecutar dentro de seedjob/FOLDER/**/*.groovy
//BRANCH: Rama para obtener los seedjob (master, develop, feature, etc)
//SEEDJOBFILE: Archivo seedjob especifico a ejecutar, ignora el FOLDER
pipeline {
    agent {
        //Se ejecuta en el nodo master
        label 'controller'
    }

    options {
        ansiColor('xterm')
    }
    stages {
        stage('Bitbucket') {
            steps {
                script{
                    logc.titulo("STAGE: Bitbucket")
                    cleanWs()
                    logc.info("Desde la rama: '${BRANCH}'")
                    //Descarga el repositorio de pipeline
                    checkout([$class: 'GitSCM', 
                        branches: [[name: BRANCH]], 
                        userRemoteConfigs: [[credentialsId: GBL_BITBUCKET_GITCREDENTIALID, 
                                url: "${GBL_BITBUCKET_BASEURL}/DSO/jenkins-oke-pipeline.git"]]])
                    dir('config') {
                        try{
                            checkout([$class: 'GitSCM',
                                branches: [[name: BRANCH]],
                                userRemoteConfigs: [[credentialsId: GBL_BITBUCKET_GITCREDENTIALID,
                                        url: "${GBL_BITBUCKET_BASEURL}/DSO/jenkins-oke-config.git"]]])
                        } catch (Exception e) {
                            checkout([$class: 'GitSCM',
                                branches: [[name: GBL_PIPELINE_BRANCH]],
                                userRemoteConfigs: [[credentialsId: GBL_BITBUCKET_GITCREDENTIALID,
                                        url: "${GBL_BITBUCKET_BASEURL}/DSO/jenkins-oke-config.git"]]])
                        }

                    }
                    currentBuild.displayName = "#${BUILD_NUMBER};${FOLDER};${BRANCH}"
                }
            }
        }
        stage('Select seedJob') {
            steps {
                script{
                    logc.titulo("STAGE: Select seedJob")

                    def seedjobselected = ""
                    if ("${SEEDJOBFILE}" != ""){
                        seedjobselected = SEEDJOBFILE
                    }else{
                        //Busca todos los archivos en la carpeta FOLDER que se llaman *.groovy
                        def files = findFiles(glob: "seedjob/" + FOLDER + "/**/*.groovy")

                        //Toma solo el path de los archivos y los deja en la lista fileList
                        def fileList = []
                        files.each{
                            fileList.add(it.path)
                        }
                        logc.info("Seedjob's encontrados: ${fileList}")
                        
                        //Si no encontro archivos *.groovy termina con error
                        if (fileList.size() == 0){
                            logc.errorm("No se encontraron archivos *.groovy")
                        }
                        
                        //Si solo encuentra un archivo *.groovy se ejecuta con ese archivo
                        if (fileList.size() == 1){
                            seedjobselected = fileList[0]
                        }else{
                            //Si encuentra mas de un *.groovy debe seleccionar uno o ALL
                            fileList.add(0, "ALL")
                            timeout(time: 300, unit: 'SECONDS') {
                                seedjobselected = input message: "Seleccione el seedjob que desea ejecutar",
                                        parameters: [choice(name: "", choices: fileList, description: "")]
                            }
                            logc.info("Opcion seleccionada: ${seedjobselected}")
                            //Si selecciona ALL ejecuta todos
                            if (seedjobselected == 'ALL'){
                                seedjobselected = "seedjob/" + FOLDER + "/**/*.groovy"
                            }
                        }
                    }
                    env.SEED_JOB_SELECTED = seedjobselected
                }
            }
        }
        stage('Execute seedJob') {
            steps {
                script{
                    logc.titulo("STAGE: Execute seedJob")
                    logc.info("Ejecutando seedjob: ${SEED_JOB_SELECTED}")
                    System.setProperty("GBL_LOCAL_TIMEZONE", GBL_LOCAL_TIMEZONE)
                    System.setProperty("GBL_JENKINS_VERSION", GBL_JENKINS_VERSION)
                    //Ejecuta el seedjob seleccionado
                    jobDsl(additionalClasspath: "${propertiesUtil.getLibPathClasses()}",
                        targets: SEED_JOB_SELECTED, 
                        additionalParameters: [WORKSPACE: WORKSPACE])
                }
            }
        }
    }
}

GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).useScriptSecurity=true
