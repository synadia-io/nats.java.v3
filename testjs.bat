call nclean
call gradlew.bat :jetstream:test
taskkill /F /IM nats-server.exe
call gradlew :jetstream:jacocoTestReport
:start chrome file:///C:/nats/nats.java.v3/jetstream/build/reports/jacoco/test/html/index.html
:start chrome file:///C:/nats/nats.java.v3/jetstream/build/reports/tests/test/index.html
start explorer file:///C:/nats/nats.java.v3/jetstream/build/reports/jacoco/test/html/index.html
start explorer file:///C:/nats/nats.java.v3/jetstream/build/reports/tests/test/index.html
