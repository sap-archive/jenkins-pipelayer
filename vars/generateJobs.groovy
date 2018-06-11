#!/usr/bin/env groovy

import com.sap.corydoras.Parser

def call(String path, String destination) {
    arrFiles = []
    def parser = new Parser()
    if (!path) {
        path = 'jobs/**/*.groovy'
    }

    findFiles(glob: path).each { file ->
        fileContent = sh returnStdout: true, script: "cat ${file.path}"
        arrFiles << [
            path: file.path,
            name: parser.getBaseName(file.name),
            // get description from the first comment /* */ of the file
            description: parser.getDescription(fileContent, file.path),
            // copy as is triggers definition
            triggers: parser.getTriggers(fileContent, file.path),

            // try to extract parameters in order to generate parameters with the job
            // normaly we would have to wait a first run
            // of the pipeline in order to generate the parameters.
            // Instead we create them in the jobdsl script
            parameters: parser.getParameters(fileContent, file.path),
            // extract autorization matrix rights
            authorizations: parser.getAuthorizations(fileContent, file.path),
            // extract environment variables
            environmentVariables: parser.getEnvironmentVariables(fileContent, file.path),
            author: sh(returnStdout: true, script: "git log --format=%an ${file.path} | tail -1").trim()
        ]
    }

    def jobDefinition = libraryResource 'com/sap/corydoras/seed/jobs.groovy'
    writeFile file: 'seed/jobs.groovy', text: jobDefinition

    jobDsl removedJobAction: 'DELETE',
            removedViewAction: 'DELETE',
            targets: 'seed/jobs.groovy',
            unstableOnDeprecation: true,
            additionalParameters: [
                pipelineJobs: arrFiles,
                props: [
                    // root folder to generate the pipeline jobs
                    basePath: destination,
                    // address to fetch the jobs
                    gitRemoteUrl: "${commit.GIT_URL}",
                    // branch the jenkins jobs are on
                    gitConfigJenkinsBranch: "${commit.GIT_BRANCH.replaceAll(/^origin\//, '')}"
                ]
            ]
}
