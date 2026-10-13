# Cloud Drive

Remote file storage with desktop and web clients.

Requires JDK 21 and Maven 3.9+.

```bash
mvn install                                     # build and test
java -jar server/target/cloud-drive-server.jar   # server and web client
mvn -pl desktop javafx:run                       # desktop client (another terminal)
```

Open the web client at http://localhost:8080.

Modules: `common` (file model and operations), `server` (Spring Boot REST API and web client), `desktop` (JavaFX).
