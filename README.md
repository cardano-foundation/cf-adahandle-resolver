# ADA Handle Resolver 🚧️ Under Construction 🚧️

This project aims to provide a scoped indexer for retrieving ADA Handle information from the Cardano blockchain 
and exposing it via REST using [Yaci Store](https://github.com/bloxbean/yaci-store).

## Getting Started

### Prerequisites

Java 17

```
./mvnw clean package
java -jar target/adahandle-resolver-0.0.1-SNAPSHOT.jar
```

## API Endpoints

 - http://localhost:9095/api/v1/address/{adahandle} : Get the stake address for a given ADA Handle
**Swagger UI:**  http://localhost:9095/swagger-ui.html