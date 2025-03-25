
def key_proyecto = "PRISMA"
def nombre_proyecto = "PRISMA"
def descripcion_proyecto = "Analisis de IaC con Prisma"

def jobName = "checkov"
def jobDesc = "Pipeline para validar la IaC de las distintas nubes con Prisma Checkov by BridgeCrew"
def jobRepoScript = "jenkins-oke-pipeline"
def jobScript = "PRISMA/checkovAnalysis.groovy"

//create folders project
folder("${key_proyecto}") {
    displayName("${nombre_proyecto}")
    description("Folder for ${nombre_proyecto} ${descripcion_proyecto}")
}

println "Creando job: ${jobName}"

pipelineJob("${key_proyecto}/${jobName}") {
    
    description(jobDesc)
    logRotator {
        numToKeep(15)
    }
    environmentVariables {
        env('PIPELINE_BRANCH', BRANCH)
    }
    parameters {
        choiceParam('cloud', ['AWS','AZURE','GCP','OCI'], 'Nombre de la tecnologia')
        stringParam("projectBitbucket", '', "Nombre del proyecto de Bitbucket")
        stringParam("repoBitbucket", '', "Nombre del repositorio de Bitbucket")
        stringParam("branchBitbucket", '', "Nombre de la rama de Bitbucket")       
        stringParam("project", '', "Nombre del o los proyectos, a analizar con Checkov, separados por coma")  
    } 
    definition {
        cpsScm {
            lightweight(true)
            scm {
                git {
                    //BRANCH viene de la ejecucion del seedjob/seedjob.groovy
                    branch(BRANCH)
                    remote {
                        url("${GBL_BITBUCKET_BASEURL}/DSO/${jobRepoScript}.git")
                        credentials(GBL_BITBUCKET_GITCREDENTIALID)
                    }
                }
            }
            scriptPath(jobScript)
        }
    }
}
