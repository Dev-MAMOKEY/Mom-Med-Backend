$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# WSL2 + Java Netty 사이 IPv6 resolve 이슈 회피 — Java가 IPv4만 쓰도록 강제
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true"

# DUR CSV paths are configured in src/main/resources/application-local.yml.
# Only need to enable the ETL flag here.
Set-Location "C:\Users\tiilk\Desktop\projectwindow\mamoki\backend"
& .\gradlew.bat bootRun --args="--spring.profiles.active=local --app.etl.dur.enabled=true" --console=plain --no-daemon
