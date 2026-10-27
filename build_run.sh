# 1. build everything from root (also builds the dashboard into heimdall-core)
./mvnw clean install -DskipTests

# 2. run the demo app, which embeds Heimdall like a real project would
cd heimdall-demo
../mvnw quarkus:dev
# dashboard: http://localhost:8080/heimdall/
