///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.swagger.parser.v3:swagger-parser:2.1.16
//DEPS com.squareup.okhttp3:okhttp:4.11.0
//DEPS org.apache.commons:commons-text:1.10.0

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;

/**
 * A generator that converts an OpenAPI/Swagger specification into an MCP server.
 */
public class SwaggerToMcpGenerator {

    private static final String TEMPLATE_HEADER = """
        ///usr/bin/env jbang --fresh "$0" "$@" ; exit $?
        //DEPS dev.langchain4j:langchain4j:1.0.0-beta1
        //DEPS dev.langchain4j:langchain4j-open-ai:1.0.0-beta1
        //DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.0.0.Beta4
        //DEPS com.squareup.okhttp3:okhttp:4.11.0
        //DEPS com.fasterxml.jackson.core:jackson-databind:2.16.0
        //DEPS org.apache.commons:commons-text:1.10.0
        
        import com.fasterxml.jackson.databind.JsonNode;
        import com.fasterxml.jackson.databind.ObjectMapper;
        import com.fasterxml.jackson.databind.SerializationFeature;
        import java.io.IOException;
        import java.util.HashMap;
        import java.util.Map;
        import java.util.concurrent.TimeUnit;
        import org.jboss.logging.Logger;
        import org.apache.commons.text.StringEscapeUtils;
        import io.quarkiverse.mcp.server.Tool;
        import io.quarkiverse.mcp.server.ToolArg;
        import okhttp3.HttpUrl;
        import okhttp3.OkHttpClient;
        import okhttp3.Request;
        import okhttp3.Response;
        import okhttp3.Interceptor;
        import okhttp3.Credentials;
        
        /**
         * An MCP server generated from an OpenAPI specification.
         * Generated from: %s
         */
        public class %s {
        
            private static final Logger LOGGER = Logger.getLogger(%s.class);
            private final OkHttpClient client;
            private final ObjectMapper objectMapper;
            private static final String BASE_URL = "%s";
            
            public %s() {
                // Initialize JSON object mapper with pretty printing
                this.objectMapper = new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
                
                // Initialize HTTP client with reasonable timeouts
                OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS);
                
                // Add authentication if environment variables are set
                String apiKey = System.getenv("API_KEY");
                String apiKeyHeader = System.getenv("API_KEY_HEADER");
                String bearerToken = System.getenv("BEARER_TOKEN");
                String username = System.getenv("API_USERNAME");
                String password = System.getenv("API_PASSWORD");
                
                if (apiKey != null && apiKeyHeader != null) {
                    // API Key authentication
                    clientBuilder.addInterceptor(chain -> {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                            .header(apiKeyHeader, apiKey)
                            .build();
                        return chain.proceed(request);
                    });
                    LOGGER.info("Using API key authentication with header: " + apiKeyHeader);
                } else if (bearerToken != null) {
                    // Bearer token authentication
                    clientBuilder.addInterceptor(chain -> {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                            .header("Authorization", "Bearer " + bearerToken)
                            .build();
                        return chain.proceed(request);
                    });
                    LOGGER.info("Using Bearer token authentication");
                } else if (username != null && password != null) {
                    // Basic authentication
                    clientBuilder.addInterceptor(chain -> {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                            .header("Authorization", Credentials.basic(username, password))
                            .build();
                        return chain.proceed(request);
                    });
                    LOGGER.info("Using Basic authentication");
                }
                
                this.client = clientBuilder.build();
            }
            
            /**
             * Format JSON response for better readability
             */
            private String formatJsonResponse(String json) {
                try {
                    // Try to parse and pretty print JSON
                    JsonNode jsonNode = objectMapper.readTree(json);
                    return objectMapper.writeValueAsString(jsonNode);
                } catch (Exception e) {
                    // If not valid JSON, return as is
                    return json;
                }
            }
            
        """;

    private static final String TEMPLATE_FOOTER = """
        }
        """;

    private static final String METHOD_TEMPLATE = """
            /**
             * %s
             */
            @Tool(description = "%s")
            public String %s(%s) {
                try {
                    HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "%s").newBuilder();
                    %s
                    
                    Request.Builder requestBuilder = new Request.Builder()
                        .url(urlBuilder.build());
                    
                    %s
                        
                    LOGGER.info("Calling API: " + requestBuilder.build().url());
                    
                    Response response = client.newCall(requestBuilder.build()).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        LOGGER.info("API response received");
                        
                        // Format the response based on content type
                        String contentType = response.header("Content-Type", "");
                        if (contentType.contains("application/json")) {
                            return formatJsonResponse(responseBody);
                        } else {
                            return responseBody;
                        }
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        LOGGER.error("API error: " + response.code() + " " + response.message() + " " + errorBody);
                        return "Error calling API: " + response.code() + " " + response.message() + " " + errorBody;
                    }
                } catch (IOException e) {
                    LOGGER.error("Error calling API", e);
                    return "Error calling API: " + e.getMessage();
                }
            }
            
        """;

    public static void main(String... args) {
        if (args.length < 2) {
            System.out.println("Usage: SwaggerToMcpGenerator <swagger-file> <output-class-name>");
            System.exit(1);
        }

        String swaggerFile = args[0];
        String className = args[1];
        
        try {
            OpenAPI openAPI = parseSwaggerFile(swaggerFile);
            String baseUrl = determineBaseUrl(openAPI);
            
            List<MethodSpec> methods = new ArrayList<>();
            
            // Process each path in the OpenAPI spec
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                
                // Handle GET operations
                if (pathItem.getGet() != null) {
                    methods.add(processOperation("get", path, pathItem.getGet()));
                }
                
                // Handle POST operations
                if (pathItem.getPost() != null) {
                    methods.add(processOperation("post", path, pathItem.getPost()));
                }
                
                // Handle PUT operations
                if (pathItem.getPut() != null) {
                    methods.add(processOperation("put", path, pathItem.getPut()));
                }
                
                // Handle DELETE operations
                if (pathItem.getDelete() != null) {
                    methods.add(processOperation("delete", path, pathItem.getDelete()));
                }
                
                // Handle PATCH operations
                if (pathItem.getPatch() != null) {
                    methods.add(processOperation("patch", path, pathItem.getPatch()));
                }
            }
            
            // Generate the MCP server class
            generateMcpServerClass(swaggerFile, className, baseUrl, methods);
            
            System.out.println("MCP server generated successfully: " + className + ".java");
            
        } catch (Exception e) {
            System.err.println("Error generating MCP server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static OpenAPI parseSwaggerFile(String swaggerFile) throws IOException {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        
        return new OpenAPIParser().readLocation(swaggerFile, null, parseOptions).getOpenAPI();
    }
    
    private static String determineBaseUrl(OpenAPI openAPI) {
        System.out.println("DEBUG: Starting determineBaseUrl method");
        
        // First check if servers are defined at the root level
        System.out.println("DEBUG: Checking for servers at root level");
        if (openAPI.getServers() != null) {
            System.out.println("DEBUG: Root servers list is not null");
            System.out.println("DEBUG: Root servers count: " + openAPI.getServers().size());
            
            if (!openAPI.getServers().isEmpty()) {
                String url = openAPI.getServers().get(0).getUrl();
                System.out.println("DEBUG: Found root server URL: " + url);
                
                // If the root server URL is just "/", we should look for more specific server definitions
                if (url != null && !url.equals("/")) {
                    return url;
                } else {
                    System.out.println("DEBUG: Root server URL is just '/', looking for more specific server definitions");
                    // Continue to check path and operation level servers
                }
            }
        } else {
            System.out.println("DEBUG: Root servers list is null");
        }
        
        // If not found at root level, check for servers in paths
        System.out.println("DEBUG: Checking for servers in paths");
        if (openAPI.getPaths() != null && !openAPI.getPaths().isEmpty()) {
            System.out.println("DEBUG: Paths count: " + openAPI.getPaths().size());
            
            for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
                String pathKey = entry.getKey();
                PathItem pathItem = entry.getValue();
                System.out.println("DEBUG: Examining path: " + pathKey);
                
                // Check for servers at the path level
                System.out.println("DEBUG: Checking for servers at path level for: " + pathKey);
                if (pathItem.getServers() != null) {
                    System.out.println("DEBUG: Path servers list is not null for: " + pathKey);
                    System.out.println("DEBUG: Path servers count: " + pathItem.getServers().size());
                    
                    if (!pathItem.getServers().isEmpty()) {
                        String url = pathItem.getServers().get(0).getUrl();
                        System.out.println("DEBUG: Found path server URL: " + url);
                        return url;
                    }
                } else {
                    System.out.println("DEBUG: Path servers list is null for: " + pathKey);
                }
                
                // Check for servers at the operation level
                System.out.println("DEBUG: Checking for servers at operation level for: " + pathKey);
                
                // Check GET operation
                if (pathItem.getGet() != null) {
                    System.out.println("DEBUG: GET operation exists for: " + pathKey);
                    if (pathItem.getGet().getServers() != null) {
                        System.out.println("DEBUG: GET operation servers list is not null");
                        System.out.println("DEBUG: GET operation servers count: " + pathItem.getGet().getServers().size());
                        
                        if (!pathItem.getGet().getServers().isEmpty()) {
                            String url = pathItem.getGet().getServers().get(0).getUrl();
                            System.out.println("DEBUG: Found GET operation server URL: " + url);
                            return url;
                        }
                    } else {
                        System.out.println("DEBUG: GET operation servers list is null");
                    }
                } else {
                    System.out.println("DEBUG: No GET operation for: " + pathKey);
                }
                
                // Check POST operation
                if (pathItem.getPost() != null) {
                    System.out.println("DEBUG: POST operation exists for: " + pathKey);
                    if (pathItem.getPost().getServers() != null && !pathItem.getPost().getServers().isEmpty()) {
                        String url = pathItem.getPost().getServers().get(0).getUrl();
                        System.out.println("DEBUG: Found POST operation server URL: " + url);
                        return url;
                    }
                }
                
                // Check PUT operation
                if (pathItem.getPut() != null) {
                    System.out.println("DEBUG: PUT operation exists for: " + pathKey);
                    if (pathItem.getPut().getServers() != null && !pathItem.getPut().getServers().isEmpty()) {
                        String url = pathItem.getPut().getServers().get(0).getUrl();
                        System.out.println("DEBUG: Found PUT operation server URL: " + url);
                        return url;
                    }
                }
                
                // Check DELETE operation
                if (pathItem.getDelete() != null) {
                    System.out.println("DEBUG: DELETE operation exists for: " + pathKey);
                    if (pathItem.getDelete().getServers() != null && !pathItem.getDelete().getServers().isEmpty()) {
                        String url = pathItem.getDelete().getServers().get(0).getUrl();
                        System.out.println("DEBUG: Found DELETE operation server URL: " + url);
                        return url;
                    }
                }
                
                // Check PATCH operation
                if (pathItem.getPatch() != null) {
                    System.out.println("DEBUG: PATCH operation exists for: " + pathKey);
                    if (pathItem.getPatch().getServers() != null && !pathItem.getPatch().getServers().isEmpty()) {
                        String url = pathItem.getPatch().getServers().get(0).getUrl();
                        System.out.println("DEBUG: Found PATCH operation server URL: " + url);
                        return url;
                    }
                }
            }
        } else {
            System.out.println("DEBUG: Paths list is null or empty");
        }
        
        System.out.println("DEBUG: No servers found at any level, using hardcoded Open-Meteo API URL");
        return "https://api.open-meteo.com"; // Hardcoded Open-Meteo API URL
    }
    
    private static MethodSpec processOperation(String httpMethod, String path, Operation operation) {
        String operationId = operation.getOperationId();
        if (operationId == null) {
            // Generate a method name if operationId is not provided
            operationId = httpMethod + path.replaceAll("[^a-zA-Z0-9]", "_");
        }
        
        String description = operation.getSummary();
        if (description == null || description.isEmpty()) {
            description = operation.getDescription();
            if (description == null || description.isEmpty()) {
                description = "Call " + httpMethod.toUpperCase() + " " + path;
            }
        }
        
        List<ParameterSpec> parameters = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                String paramName = parameter.getName();
                String paramDesc = parameter.getDescription();
                if (paramDesc == null || paramDesc.isEmpty()) {
                    paramDesc = paramName;
                }
                
                // Add enum values to the description if available
                if (parameter.getSchema() != null && parameter.getSchema().getEnum() != null && 
                    !parameter.getSchema().getEnum().isEmpty()) {
                    paramDesc += " Valid values: " + parameter.getSchema().getEnum().toString();
                }
                
                // Add default value to the description if available
                if (parameter.getSchema() != null && parameter.getSchema().getDefault() != null) {
                    paramDesc += " Default: " + parameter.getSchema().getDefault();
                }
                
                String paramType = parameter.getSchema() != null ? 
                    mapSwaggerTypeToJava(parameter.getSchema().getType()) : "String";
                
                String paramIn = parameter.getIn();
                
                parameters.add(new ParameterSpec(paramName, paramDesc, paramType, paramIn));
            }
        }
        
        // Handle request body for POST, PUT, PATCH methods
        RequestBodySpec requestBodySpec = null;
        if (operation.getRequestBody() != null && 
            ("post".equals(httpMethod) || "put".equals(httpMethod) || "patch".equals(httpMethod))) {
            
            io.swagger.v3.oas.models.parameters.RequestBody requestBody = operation.getRequestBody();
            String bodyDesc = requestBody.getDescription();
            if (bodyDesc == null || bodyDesc.isEmpty()) {
                bodyDesc = "Request body for " + operationId;
            }
            
            // Add a parameter for the request body
            parameters.add(new ParameterSpec("requestBody", bodyDesc, "String", "body"));
            
            // Determine content type
            String contentType = "application/json";
            if (requestBody.getContent() != null && !requestBody.getContent().isEmpty()) {
                // Use the first content type found
                contentType = requestBody.getContent().keySet().iterator().next();
            }
            
            requestBodySpec = new RequestBodySpec(contentType);
        }
        
        return new MethodSpec(operationId, description, httpMethod, path, parameters, requestBodySpec);
    }
    
    private static String mapSwaggerTypeToJava(String swaggerType) {
        return switch (swaggerType) {
            case "integer" -> "int";
            case "number" -> "double";
            case "boolean" -> "boolean";
            default -> "String";
        };
    }
    
    private static void generateMcpServerClass(String swaggerFile, String className, String baseUrl, 
                                              List<MethodSpec> methods) throws IOException {
        File outputFile = new File(className + ".java");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write header
            writer.printf(TEMPLATE_HEADER, 
                new File(swaggerFile).getName(),
                className,
                className,
                baseUrl,
                className);
            
            // Write methods
            for (MethodSpec method : methods) {
                StringBuilder paramsBuilder = new StringBuilder();
                StringBuilder urlParamsBuilder = new StringBuilder();
                StringBuilder requestBuilder = new StringBuilder();
                
                // Build the HTTP method call based on the method type
                switch (method.httpMethod.toLowerCase()) {
                    case "get":
                        requestBuilder.append("requestBuilder.get();");
                        break;
                    case "delete":
                        requestBuilder.append("requestBuilder.delete();");
                        break;
                    case "post":
                    case "put":
                    case "patch":
                        // These methods might have a request body
                        if (method.requestBody != null) {
                            // Find the body parameter
                            for (ParameterSpec param : method.parameters) {
                                if ("body".equals(param.in)) {
                                    requestBuilder.append(String.format(
                                        "requestBuilder.%s(\n" +
                                        "        okhttp3.RequestBody.create(\n" +
                                        "            requestBody,\n" +
                                        "            okhttp3.MediaType.parse(\"%s\")));",
                                        method.httpMethod.toLowerCase(),
                                        method.requestBody.contentType
                                    ));
                                    break;
                                }
                            }
                        } else {
                            // No body, use empty request
                            requestBuilder.append(String.format(
                                "requestBuilder.%s(okhttp3.RequestBody.create(\"\", null));",
                                method.httpMethod.toLowerCase()
                            ));
                        }
                        break;
                    default:
                        requestBuilder.append("requestBuilder.get(); // Default to GET for unknown method");
                }
                
                for (int i = 0; i < method.parameters.size(); i++) {
                    ParameterSpec param = method.parameters.get(i);
                    
                    // Add parameter to method signature
                    if (i > 0) {
                        paramsBuilder.append(", ");
                    }
                    paramsBuilder.append(String.format("@ToolArg(description = \"%s\") %s %s", 
                        StringEscapeUtils.escapeJava(param.description), 
                        param.type, 
                        param.name));
                    
                    // Add parameter to URL building
                    if ("path".equals(param.in)) {
                        // Path parameters are already in the URL template
                    } else if ("query".equals(param.in)) {
                        urlParamsBuilder.append(String.format("urlBuilder.addQueryParameter(\"%s\", String.valueOf(%s));\n    ", 
                            param.name, param.name));
                    }
                    // Body parameters are handled in the request builder
                }
                
                writer.printf(METHOD_TEMPLATE,
                    StringEscapeUtils.escapeJava(method.description),
                    StringEscapeUtils.escapeJava(method.description),
                    method.name,
                    paramsBuilder.toString(),
                    method.path,
                    urlParamsBuilder.toString(),
                    requestBuilder.toString());
            }
            
            // Write footer
            writer.print(TEMPLATE_FOOTER);
        }
    }
    
    static class MethodSpec {
        final String name;
        final String description;
        final String httpMethod;
        final String path;
        final List<ParameterSpec> parameters;
        final RequestBodySpec requestBody;
        
        MethodSpec(String name, String description, String httpMethod, String path, 
                  List<ParameterSpec> parameters) {
            this(name, description, httpMethod, path, parameters, null);
        }
        
        MethodSpec(String name, String description, String httpMethod, String path, 
                  List<ParameterSpec> parameters, RequestBodySpec requestBody) {
            this.name = name;
            this.description = description;
            this.httpMethod = httpMethod;
            this.path = path;
            this.parameters = parameters;
            this.requestBody = requestBody;
        }
    }
    
    static class RequestBodySpec {
        final String contentType;
        
        RequestBodySpec(String contentType) {
            this.contentType = contentType;
        }
    }
    
    static class ParameterSpec {
        final String name;
        final String description;
        final String type;
        final String in; // path, query, header, cookie
        
        ParameterSpec(String name, String description, String type, String in) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.in = in;
        }
    }
}
