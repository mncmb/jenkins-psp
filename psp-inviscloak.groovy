pipeline { 
    agent any
    
    environment { 
        // << CHANGE THESE >> 
        TOOLNAME = "Rubeus"
        OBS_TOOLNAME = "Dudebeus"
        GITURL = "https://github.com/GhostPack/Rubeus.git"
        BRANCH = "master"
        WORKDIR = "C:\\opt\\jenkins-psp"           // git-cloned directory 
        
        PSP_OUTPUT = "${WORKDIR}\\Invoke-${OBS_TOOLNAME}.ps1"
        OBS_PSP_OUTPUT = "${WORKDIR}\\Obs-Invoke-${OBS_TOOLNAME}.ps1"

        // << CHANGE THESE >> - .NET Compile configs
        CONFIG="Release"
        PLATFORM="Any CPU"
        DOTNETVERSION="v4.0"
        DOTNETNUMBER="net40"
        
        // 3rd party tools 
        INVISCLOAKPATH = "${WORKDIR}\\InvisibilityCloak\\InvisibilityCloak.py"
        CHAMELEONPATH = "${WORKDIR}\\chameleon\\chameleon.py"
        EMBEDDOTNETPATH = "${WORKDIR}\\embedDotNet.ps1"
        PREPPSPPATH = "${WORKDIR}\\PSPprep.ps1"
        TEMPLATEPATH = "${WORKDIR}\\template.ps1"
    }

    
    stages {
        stage('Cleanup'){
            steps{
                deleteDir()
                dir("${TOOLNAME}"){
                    deleteDir()
                }
            }
        }

        // Try main, then master for old github repos.
        stage('Git-Clone'){
            steps{
                script {     
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        userRemoteConfigs: [[url: "${GITURL}"]]
                    ]) 
                }
            }
        }

        // Skip prep powersharppack if the tool already has public class/main function.
        stage('Prep-PSP'){
            steps{
                powershell "${PREPPSPPATH} -inputDir ${WORKSPACE} -toolName ${TOOLNAME}"
            }
        }

        // Obfuscate with invisibilitycloak. 
        stage('InvisibilityCloak-Obfuscate') { 
            steps { 
                bat """python ${INVISCLOAKPATH} -d ${WORKSPACE} -n ${OBS_TOOLNAME} -m rot13 """
            }
        }

        // Some projects doesn't need nuget restore. Continue on failure.
        // TODO: what's this "msbuild PROJECT.sln /t:Restore /p:Configuration=Release"
        stage('Nuget-Restore'){
            steps{
                script{
                    def slnPath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '${OBS_TOOLNAME}.sln' -Recurse).FullName")
                    env.SLNPATH = slnPath
                    
                    try{ 
                        bat "nuget restore ${SLNPATH}"
                    }
                    catch(Exception e){
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            bat """dotnet restore ${SLNPATH} """
                        }
                    }
                }
            }
        }

        // If Compilation fails due to invisiblity cloak, run without string obfuscation (delete -m rot 13)
        stage('Compile'){ 
            steps {
                script{
                    def slnPath = powershell(returnStdout: true, script: "(Get-ChildItem -Path ${WORKSPACE} -Include '${OBS_TOOLNAME}.sln' -Recurse).FullName")
                    env.SLNPATH = slnPath
                    
                    try{
                        bat "\"${tool 'MSBuild_VS2019'}\\MSBuild.exe\" /p:Configuration=${CONFIG} \"/p:Platform=${PLATFORM}\" /maxcpucount:%NUMBER_OF_PROCESSORS% /nodeReuse:false /p:DebugType=None /p:DebugSymbols=false /p:TargetFrameworkMoniker=\".NETFramework,Version=${DOTNETVERSION}\" ${SLNPATH}" 
                    }   
                    catch(Exception e){
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            bat """dotnet build --configuration ${CONFIG} ${SLNPATH} """ 
                        }
                    }  
                }
            }
        }

        stage('Create-PSP'){
            steps{
                script{
                    def exePath = powershell(returnStdout: true, script: """
                    \$exeFiles = (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release' -and \$_.DirectoryName -match 'bin' } ).FullName
                    if (\$exeFiles -match "${DOTNETNUMBER}"){
                        \$exeFiles.trim()
                    }
                    else{
                        (Get-ChildItem -Path ${WORKSPACE} -Include '*.exe' -Recurse | Where-Object {\$_.DirectoryName -match 'release'} )[0].FullName
                    }
                    """)
                    env.EXEPATH = exePath

                    // Beaware of environment variable created from ps in jenkins (exePath). Always .trim() INSIDE powershell.
                    powershell "${EMBEDDOTNETPATH} -inputFile \"${EXEPATH}\".trim() -outputFile ${PSP_OUTPUT} -templatePath ${TEMPLATEPATH} -toolName ${OBS_TOOLNAME}"
                }
            }
        }


        stage('Obfuscate-PSP'){
            steps{
                bat encoding: 'UTF-8', script: """python ${CHAMELEONPATH} -v -d -c -f -r -i -l 4 ${PSP_OUTPUT} -o ${OBS_PSP_OUTPUT}"""
            }
        }
    }
}