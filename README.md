# OpenAPI to MCP Generator

This project provides a powerful tool for automatically converting OpenAPI/Swagger specifications into Model Context Protocol (MCP) servers, allowing LLMs to interact with any REST API through standardized tools.

## Components

### SwaggerToMcpGenerator.java

A comprehensive utility that converts any OpenAPI/Swagger specification into a fully functional MCP server:
- Parses OpenAPI specification files
- Converts API endpoints to MCP tools
- Handles path parameters, query parameters, and request bodies
- Supports multiple HTTP methods (GET, POST, PUT, DELETE, PATCH)
- Provides authentication support (API keys, Bearer tokens, Basic auth)
- Formats JSON responses for readability
- Generates robust error handling
- Includes parameter documentation with valid values and defaults

## Generating an MCP Server from OpenAPI

To generate an MCP server from any OpenAPI specification:

```bash
jbang SwaggerToMcpGenerator.java path/to/swagger.json GeneratedMcpServer [options]
```

Parameters:
- `path/to/swagger.json`: Path to the OpenAPI/Swagger specification file
- `GeneratedMcpServer`: Name of the output Java file (without .java extension)

Options:
- `--server-index <index>`: Index of the server to use from the OpenAPI specification (0-based)
- `--server-url <url>`: URL of the server to use (overrides server-index)

This will create a new file `GeneratedMcpServer.java` that implements an MCP server with tools for each API endpoint defined in the swagger file. The generator will emit a warning if multiple servers are defined in the OpenAPI specification and none is explicitly selected.

## Running the Generated MCP Server

To run the generated MCP server:

```bash
jbang GeneratedMcpServer.java
```

### Server Selection

The generated MCP server includes constants for all servers defined in the OpenAPI specification, allowing you to choose which server to use at runtime. By default, the first server in the list is used, but you can select a specific server using environment variables:

```bash
# Select server by index (0-based)
export SERVER_INDEX=1

# Or select server by URL
export SERVER_URL="https://api-example.com/v2"

jbang GeneratedMcpServer.java
```

## Authentication

The generated MCP server supports multiple authentication methods through environment variables:

- **API Key**: Set `API_KEY` and `API_KEY_HEADER` environment variables
- **Bearer Token**: Set `BEARER_TOKEN` environment variable
- **Basic Auth**: Set `API_USERNAME` and `API_PASSWORD` environment variables

Example:

```bash
export API_KEY="your-api-key"
export API_KEY_HEADER="X-API-Key"
jbang GeneratedMcpServer.java
```

## Examples

### Open-Meteo Weather API

The project includes an example OpenAPI specification for the Open-Meteo Weather API in the `examples/open-meteo` directory.

#### Generating the Open-Meteo MCP Server

```bash
cd examples/open-meteo
jbang ../../SwaggerToMcpGenerator.java open-meteo-openapi.yml OpenMeteoMcpServer
```

This will generate `OpenMeteoMcpServer.java` with MCP tools for accessing weather forecast data.

#### Running the Open-Meteo MCP Server

```bash
cd examples/open-meteo
jbang OpenMeteoMcpServer.java
```

#### Using the Open-Meteo MCP Server

The generated MCP server provides tools for accessing weather forecasts. When using the server, pay attention to the parameter descriptions which include valid values and defaults. For example:

- For the `wind_speed_unit` parameter, use `ms` (not "m/s") for meters per second
- Valid values for `wind_speed_unit` are: `kmh` (default), `ms`, `mph`, and `kn`
- For temperature units, use `celsius` (default) or `fahrenheit`

Example query for weather in Sevilla, Spain:

```
latitude: 37.3891
longitude: -5.9845
current_weather: true
wind_speed_unit: ms
```

#### Generating the Clever Cloud MCP Server

```bash
cd examples/clever-cloud
jbang ../../SwaggerToMcpGenerator.java clever-cloud-openapi.yml CleverCloudMcpServer --server-index 1
```

Note that we're using `--server-index 1` (the second server in the list) which is the API Bridge URL required for token authentication. This will generate `CleverCloudMcpServer.java` with MCP tools for managing Clever Cloud resources.

Alternatively, you can specify the server URL directly:

```bash
cd examples/clever-cloud
jbang ../../SwaggerToMcpGenerator.java clever-cloud-openapi.yml CleverCloudMcpServer --server-url https://api-bridge.clever-cloud.com/v2
```

#### Running the Clever Cloud MCP Server

```bash
cd examples/clever-cloud
# Set your Clever Cloud API token
export BEARER_TOKEN=your_api_token
jbang CleverCloudMcpServer.java
```

#### Generating a Clever Cloud API Token

To generate an API token for Clever Cloud, you'll need to use the [Clever Tools CLI](https://github.com/CleverCloud/clever-tools):

```bash
# Install Clever Tools (if not already installed)
npm install -g clever-tools

# Login to Clever Cloud
clever login

# Enable the tokens feature
clever features enable tokens

# Create a token (with optional expiration)
clever tokens create "MCP Server Token"
clever tokens create "Temporary Token" --expiration 24h
```

You can also list and revoke tokens:

```bash
# List existing tokens
clever tokens -F json

# Revoke a token
clever tokens revoke api_tokens_xxx
```

#### Using the Clever Cloud MCP Server

The generated MCP server provides tools for interacting with the Clever Cloud API. Available tools include:

- `get_self`: Get current user information
- `get_summary`: Get user summary
- `get_organisations__organisationId__applications`: List applications for an organization
- `get_organisations__organisationId__applications__applicationId_`: Get application details

Example query to list applications for an organization:

```
organisationId: your_organization_id
```

## How It Works

### MCP Protocol

The Model Context Protocol (MCP) is a standardized way for tools and LLMs to communicate, allowing:

1. Tools to expose their functionality to any MCP-compatible LLM
2. LLMs to discover and use tools without being tied to specific implementations
3. A consistent interface for tool specifications and invocations

### OpenAPI to MCP Conversion

The generator works by:

1. **Parsing the OpenAPI specification** using Swagger Parser
2. **Converting each API endpoint** to an `@Tool` annotated method
3. **Mapping parameters**:
   - Path parameters are incorporated into the URL
   - Query parameters are added to the URL builder
   - Request bodies are properly formatted and attached to the request
4. **Generating HTTP client code** with proper error handling
5. **Formatting responses** based on content type (pretty-printing JSON)
6. **Adding authentication** based on environment variables

### Advanced Features

- **Multiple HTTP Methods**: Support for GET, POST, PUT, DELETE, and PATCH
- **Content Type Handling**: Proper handling of different content types
- **Error Handling**: Detailed error reporting with status codes and response bodies
- **Authentication**: Support for API keys, Bearer tokens, and Basic authentication
- **Timeouts**: Configurable connection, read, and write timeouts
- **Multiple Servers**: Support for selecting from multiple server URLs defined in the OpenAPI specification

## Environment Notes

The `jbang-wrapper.sh` script addresses environment issues when running from AI assistants like [Claude Desktop](https://claude.ai/desktop) on Mac, ensuring the correct PATH and environment variables are available.

## Next Steps

- Add support for form data and multipart requests
- Implement OAuth 2.0 authentication flow
- Add support for custom response transformations
- Create a web UI for uploading OpenAPI specs and generating servers
- Add support for WebSocket endpoints
