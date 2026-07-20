package com.todoapp.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateTask implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient db = DynamoDbClient.create();
    private static final String TABLE = System.getenv("TABLE_NAME");
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // 1. Extract the user's Cognito sub from the authorizer claims.
            String userId = extractUserId(event);
            if (userId == null) {
                return response(401, "{\"message\":\"Unauthorized\"}");
            }

            // 2. Parse the request body (the task the user wants to create).
            JsonNode body = mapper.readTree(event.getBody());
            String description = body.has("description") ? body.get("description").asText() : "";
            String date = body.has("date") ? body.get("date").asText() : "";

            // 3. Build the task's identity and derived fields.
            String taskId = UUID.randomUUID().toString();
            long deadline = (System.currentTimeMillis() / 1000L) + 300; // now + 5 minutes, in epoch seconds

            // 4. Assemble the DynamoDB item using the single-table key scheme.
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.fromS("USER#" + userId));
            item.put("SK", AttributeValue.fromS("TASK#" + taskId));
            item.put("TaskId", AttributeValue.fromS(taskId));
            item.put("UserId", AttributeValue.fromS(userId));
            item.put("Description", AttributeValue.fromS(description));
            item.put("Date", AttributeValue.fromS(date));
            item.put("Status", AttributeValue.fromS("Pending"));
            item.put("Deadline", AttributeValue.fromN(String.valueOf(deadline)));

            // 5. Write it.
            db.putItem(PutItemRequest.builder().tableName(TABLE).item(item).build());

            return response(201, mapper.writeValueAsString(Map.of(
                    "taskId", taskId,
                    "status", "Pending",
                    "deadline", deadline)));

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return response(500, "{\"message\":\"Internal error\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractUserId(APIGatewayProxyRequestEvent event) {
        if (event.getRequestContext() == null || event.getRequestContext().getAuthorizer() == null) {
            return null;
        }
        Object claims = event.getRequestContext().getAuthorizer().get("claims");
        if (claims instanceof Map) {
            Object sub = ((Map<String, Object>) claims).get("sub");
            return sub != null ? sub.toString() : null;
        }
        return null;
    }

    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*"))
                .withBody(body);
    }
}