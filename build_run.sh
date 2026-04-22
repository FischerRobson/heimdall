# 1. build everything from root
./mvnw clean install -DskipTests

# 2. run from heimdall-core
cd heimdall-core
../mvnw quarkus:dev