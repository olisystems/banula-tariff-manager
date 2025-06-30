# Banula Tariff Manager

This repository contains a Tariff Manager service that follows the Open Charge Point Interface (OCPI) protocol version 2.2.1. The service is designed to manage and version tariffs for the OCN (Open Charging Network) node.

## Supported Modules

### OCPI Standard Modules

- **Versions** (v2.2): For discovering available OCPI versions and modules
- **Tariffs** (v2.2.1): For managing tariff information and pricing structures

### Integration Features

- OCN Node integration for tariff management
- Secure API endpoints with token-based authentication
- MongoDB for data persistence
- OpenAPI/Swagger documentation

## Prerequisites

- Java 17+
- Maven 3.6+
- MongoDB 4.4+
- Access to an OCN node (if using OCN functionality)

## Configuration

Create an `application.yml` file in the `src/main/resources` directory with the following properties (or set these as environment variables):

```yaml
server:
  port: 8080

spring:
  data:
    mongodb:
      username: ${MONGO_USER:}
      password: ${MONGO_PASSWORD:}
      database: ${MONGO_ENV:}
      host: ${MONGO_ADDRESS:}

party:
  private-key: ${TARIFF_MANAGER_PRIVATE_KEY:}
  url: ${TARIFF_MANAGER_URL:}
  api-prefix: ${TARIFF_MANAGER_API_PREFIX:/tariff-manager/ocpi/other}
  tokenB: ${TARIFF_MANAGER_TOKEN_B:}
  role: OTHER
  log-payload: ${TARIFF_MANAGER_LOG_PAYLOAD:false}
  party-id: ${TARIFF_MANAGER_PARTY_ID:}
  country-code: ${TARIFF_MANAGER_COUNTRY_CODE:}
  collection-prefix: "TariffManager_"

ocn-node:
  url: ${OCN_NODE_URL:}
  admin-key: ${OCN_NODE_ADMIN_KEY:}
  signing-supported: ${OCN_NODE_ENABLE_SIGNING:false}
  updating-party: ${TARIFF_MANAGER_OCN_UPDATING_PARTY:false}

springdoc:
  swagger-ui:
    path: /tariff-manager/non-ocpi/other/swagger-ui
  api-docs:
    path: /tariff-manager/non-ocpi/other/api-docs
```

### Configuration Details

1. **Server Configuration**:
   - `server.port`: The port on which the application will run (default: 8080)

2. **MongoDB Configuration**:
   - Configure the MongoDB connection details
   - The `party.collection-prefix` will be added to all MongoDB collection names

3. **Party Configuration**:
   - `party.url`: The base URL where this service is hosted
   - `party.tokenB`: A secure token for authenticating with other parties
   - `party.role`: Set to `OTHER` for this service
   - `party.party-id` and `party.country-code`: Your unique identifiers in the e-mobility ecosystem

4. **OCN Node Configuration**:
   - `ocn-node.url`: URL of your OCN node
   - `ocn-node.admin-key`: Admin key for OCN node access
   - `party.private-key`: Your private key for signing OCN messages
   - `ocn-node.signing-supported`: Set to true if using signed messages

## Building and Running

### Using Maven

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/banula-tariff-manager-0.0.1-SNAPSHOT.jar
```

### Using Docker

```bash
# Build Docker image
docker build -t banula-tariff-manager .

# Run Docker container
docker run -p 8080:8080 -v /path/to/config:/app/config banula-tariff-manager
```

## API Documentation

Once the service is running, you can access the API documentation at:
- Swagger UI: `http://localhost:8080/tariff-manager/non-ocpi/other/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/tariff-manager/non-ocpi/other/api-docs`

## Integration with OCPI

This service implements the OCPI 2.2.1 protocol for tariff management. To integrate with other OCPI parties:

1. Use the credentials module to register with the other party
2. Exchange tokens through the OCPI registration process
3. Begin sharing tariff data through the tariffs module

For detailed integration steps, refer to the [OCPI Documentation](https://github.com/ocpi/ocpi/blob/master/credentials.asciidoc).

## Troubleshooting

### Common Issues

- **Connection refused errors**:
  - Check MongoDB connection settings
  - Verify the OCN node is accessible

- **Authentication failures**:
  - Verify your tokens and credentials
  - Check the `tokenB` and `private-key` values

- **OCN handshake errors**:
  - Ensure OCN node URL and credentials are correct
  - Verify the OCN node is running and accessible


## License

Apache License 2.0

## Author

<b>OLI Systems GmbH</b>

Contributors:

- [Matheus Rosendo](https://github.com/matheusrosendo) 
- [Diego Rosales](https://github.com/dv-rosales)
- [Elton Saraci](https://github.com/EltonSaraci99)