package com.todoapp.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

public class UpdateTask implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient db = DynamoDbClient.create();
    private static final String TABLE = System.getenv("TABLE_NAME");
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String userId = extractUserId(event);
            if (userId == null) return response(401, "{\"message\":\"Unauthorized\"}");

            String taskId = event.getPathParameters() != null ? event.getPathParameters().get("taskId") : null;
            if (taskId == null) return response(400, "{\"message\":\"Missing taskId\"}");

            JsonNode body = mapper.readTree(event.getBody());

            // Build a dynamic update over whichever fields were provided.
            StringBuilder expr = new StringBuilder("SET ");
            Map<String, String> names = new HashMap<>();
            Map<String, AttributeValue> values = new HashMap<>();
            boolean first = true;

            if (body.has("description")) {
                expr.append(first ? "" : ", ").append("#d = :d");
                names.put("#d", "Description");
                values.put(":d", AttributeValue.fromS(body.get("description").asText()));
                first = false;
            }
            if (body.has("status")) {
                expr.append(first ? "" : ", ").append("#s = :s");
                names.put("#s", "Status");
                values.put(":s", AttributeValue.fromS(body.get("status").asText()));
                first = false;
            }
            if (body.has("date")) {
                expr.append(first ? "" : ", ").append("#dt = :dt");
                names.put("#dt", "Date");
                values.put(":dt", AttributeValue.fromS(body.get("date").asText()));
                first = false;
            }
            if (first) return response(400, "{\"message\":\"No updatable fields\"}");

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.fromS("USER#" + userId));
            key.put("SK", AttributeValue.fromS("TASK#" + taskId));

            db.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE)
                    .key(key)
                    .updateExpression(expr.toString())
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build());

            return response(200, "{\"message\":\"Updated\",\"taskId\":\"" + taskId + "\"}");

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, "{\"message\":\"Internal error\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractUserId(APIGatewayProxyRequestEvent event) {
        if (event.getRequestContext() == null || event.getRequestContext().getAuthorizer() == null) return null;
        Object claims = event.getRequestContext().getAuthorizer().get("claims");
        if (claims instanceof Map) {
            Object sub = ((Map<String, Object>) claims).get("sub");
            return sub != null ? sub.toString() : null;
        }
        return null;
    }

    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent().withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"))
                .withBody(body);
    }
}
