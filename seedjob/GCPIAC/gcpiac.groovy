
def key_proyecto = "DSO"
def nombre_carpeta = "GCPIAC"
def nombre_proyecto = "GCPIAC"
def descripcion_proyecto = "Proyecto para la creación de infraestructura en GCP"

def jobName = "gcpTerraformDeploy"
def jobNameProjects = "gcpTerraformDeployProject"
def jobNameResources = "gcpTerraformDeployResource"
def jobDesc = "Pipeline para la creacion de infraestructura en GCP"
def jobRepoScript = "jenkins-oke-pipeline"
def jobScript = "GCPIAC/gcpTerraformDeploy.groovy"
def jobScriptNew = "GCPIAC/gcpTerraformDeployInfra.groovy"

//create folders project
folder("${nombre_carpeta}") {
    displayName("${nombre_proyecto}")
    description("Folder for ${nombre_proyecto} ${descripcion_proyecto}")
}

pipelineJob("${nombre_carpeta}/${jobNameProjects}") {
    
    description("Pipeline para la creacion de proyectos en GCP")
    logRotator {
        numToKeep(15)
    }
    environmentVariables {
        env('PIPELINE_BRANCH', BRANCH)
        env('INFRA_TYPE', 'project')
    }
    parameters {
        choiceParam('ENV', ['dv','qa', 'pr', 'eds'], 'Environment donde desplegar')
        stringParam('REPO_NAME', '', 'Nombre del repositorio a desplegar')
        stringParam('BRANCH', 'master', 'Nombre de la rama del repositorio')
        booleanParam('PLAN_ONLY', false, 'Only execute the plan stage and not apply')
        booleanParam('DESTROY', false, 'Destroy infrastructure')
        booleanParam('ANALYSIS', false, 'Ejecuta el análisis de código estático')
    } 
    definition {
        cpsScm {
            lightweight(true)
            scm {
                git {
                    branch(BRANCH)
                    remote {
                        url("${GBL_BITBUCKET_BASEURL}/${key_proyecto}/${jobRepoScript}.git")
                        credentials(GBL_BITBUCKET_GITCREDENTIALID)
                    }
                }
            }
            scriptPath(jobScriptNew)
        }
    }
}

pipelineJob("${nombre_carpeta}/${jobNameResources}") {
    
    description("Pipeline para la creacion de recursos en GCP")
    logRotator {
        numToKeep(15)
    }
    environmentVariables {
        env('PIPELINE_BRANCH', BRANCH)
        env('INFRA_TYPE', 'resources')
    }
    parameters {
        choiceParam('ENV', ['dv','qa', 'pr', 'eds'], 'Environment donde desplegar')
        stringParam('REPO_NAME', '', 'Nombre del repositorio a desplegar')
        stringParam('BRANCH', 'master', 'Nombre de la rama del repositorio')
        booleanParam('PLAN_ONLY', false, 'Only execute the plang stage and not apply')
        booleanParam('DESTROY', false, 'Destroy infrastructure')
        booleanParam('ANALYSIS', false, 'Ejecuta el análisis de código estático')
    } 
    definition {
        cpsScm {
            lightweight(true)
            scm {
                git {
                    branch(BRANCH)
                    remote {
                        url("${GBL_BITBUCKET_BASEURL}/${key_proyecto}/${jobRepoScript}.git")
                        credentials(GBL_BITBUCKET_GITCREDENTIALID)
                    }
                }
            }
            scriptPath(jobScriptNew)
        }
    }
}