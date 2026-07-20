package com.todoapp.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListTasks implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient db = DynamoDbClient.create();
    private static final String TABLE = System.getenv("TABLE_NAME");
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String userId = extractUserId(event);
            if (userId == null) {
                return response(401, "{\"message\":\"Unauthorized\"}");
            }

            // Query: all items where PK = USER#<userId>
            QueryResponse result = db.query(QueryRequest.builder()
                    .tableName(TABLE)
                    .keyConditionExpression("PK = :pk")
                    .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS("USER#" + userId)))
                    .build());

            // Convert each DynamoDB item into a plain map for JSON output.
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (Map<String, AttributeValue> item : result.items()) {
                Map<String, Object> task = new java.util.HashMap<>();
                task.put("taskId", item.getOrDefault("TaskId", AttributeValue.fromS("")).s());
                task.put("description", item.getOrDefault("Description", AttributeValue.fromS("")).s());
                task.put("date", item.getOrDefault("Date", AttributeValue.fromS("")).s());
                task.put("status", item.getOrDefault("Status", AttributeValue.fromS("")).s());
                AttributeValue deadline = item.get("Deadline");
                task.put("deadline", deadline != null ? Long.parseLong(deadline.n()) : null);
                tasks.add(task);
            }

            return response(200, mapper.writeValueAsString(tasks));

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
