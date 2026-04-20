call nclean
call gradlew :core:test
taskkill /F /IM nats-server.exe
call gradlew :core:jacocoTestReport
:start chrome file:///C:/nats/nats.java.v3/core/build/reports/jacoco/test/html/index.html
:start chrome file:///C:/nats/nats.java.v3/core/build/reports/tests/test/index.html
start explorer file:///C:/nats/nats.java.v3/core/build/reports/jacoco/test/html/index.html
start explorer file:///C:/nats/nats.java.v3/core/build/reports/tests/test/index.html
