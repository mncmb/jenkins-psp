choco install -y -r --no-progress curl
choco install -y -r --no-progress openjdk11jre
refreshenv
choco install -y -r --no-progress jenkins # tested with version 2.387.2
choco install -y -r --no-progress visualstudio2019-workload-manageddesktopbuildtools
choco install -y --limit-output --no-progress nuget.commandline 
choco install -y -r --no-progress git 
choco install -y -r --no-progress python3 
# WARNING exposes jenkins, which will be setup with admin:admin creds later!
# use at your own risk or change credentials in "./jenkins/init.groovy.d/basic-security.groovy"
New-NetFirewallRule -DisplayName "Allow Jenkins TCP 8080" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080

refreshenv
# use alias style var, because refreshenv doesnt work for java, even though it is in path
#$java = "C:\Program Files\OpenJDK\jdk-12.0.2\bin\java.exe"
# "C:\Program Files\Eclipse Adoptium\jre-11.0.18.10-hotspot\bin\"

# rename python3.11.exe or later version to python.exe
$pyExe = (ls C:\ProgramData\chocolatey\bin\pyth* |select name).name
cp "C:\ProgramData\chocolatey\bin\$pyExe" C:\ProgramData\chocolatey\bin\python.exe

# get ConfuserEx-CLI$
$filepath = "C:\opt"
New-Item $filepath -ItemType Directory
cd $filepath
$outfile = "$filepath\ConfuserEx-CLI.zip"
iwr -useb https://github.com/mkaring/ConfuserEx/releases/download/v1.6.0/ConfuserEx-CLI.zip -o $outfile
expand-archive $outfile -DestinationPath "$filepath\ConfuserEx-CLI"

# set env variable and manually update current env
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$filepath\ConfuserEx-CLI;C:\Program Files\Eclipse Adoptium\jre-11.0.18.10-hotspot\bin\", "Machine")
#$env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine")

# get the jenkins package, move files for autoconf on first start to jenkins dir
cd $filepath
git clone https://github.com/mncmb/jenkins-psp
Copy-Item -Path "jenkins-psp\jenkins\*" -Destination "c:\ProgramData\Jenkins\.jenkins" -Recurse

# restart to apply settings
restart-service jenkins

write-host "sleeping 20 seconds to give jenkins some restart time"
sleep 20
# load jenkins-cli to install plugins for jenkins
iwr -useb http://localhost:8080/jnlpJars/jenkins-cli.jar -o cli.jar
java -jar .\cli.jar -s http://localhost:8080/ -auth admin:admin install-plugin  workflow-aggregator git powershell nunit msbuild python
# java -jar .\cli.jar -s http://localhost:8080/ -auth admin:admin install-plugin pipeline
java -jar .\cli.jar -s http://localhost:8080/ -auth admin:admin restart

# install dependencies for chameleon
python -m pip install numpy colorama

