choco install -y -r --no-progress curl
choco install -y -r --no-progress openjdk12
refreshenv
choco install -y -r --no-progress jenkins 
choco install -y -r --no-progress visualstudio2019-workload-manageddesktopbuildtools
choco install -y --limit-output --no-progress nuget.commandline 
choco install -y -r --no-progress git 
choco install -y -r --no-progress python3 
New-NetFirewallRule -DisplayName "Allow Jenkins TCP 8080" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080

refreshenv
# use alias style var, because refreshenv doesnt work for java, even though it is in path
$java = "C:\Program Files\OpenJDK\jdk-12.0.2\bin\java.exe"

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
# set env variable
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$filepath\ConfuserEx-CLI", "Machine")


# get the jenkins package
cd $filepath
git clone https://github.com/mncmb/jenkins-psp
cp "jenkins-psp\jenkins\" "c:\ProgramData\Jenkins\.jenkins\"

restart-service jenkins

# install plugins for jenkins
iwr -useb http://localhost:8080/jnlpJars/jenkins-cli.jar -o cli.jar
.$java -jar .\cli.jar -s http://localhost:8080/ -auth admin:admin install-plugin git powershell nunit msbuild python
# java -jar .\cli.jar -s http://localhost:8080/ -auth admin:admin install-plugin pipeline
.$java -jar .\cli.jar -s http://localhost:8080/ -auth admin:admin restart

# install dependencies for chameleon
python -m pip install numpy colorama

