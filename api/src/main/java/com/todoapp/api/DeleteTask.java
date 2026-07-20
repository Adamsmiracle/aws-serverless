package com.todoapp.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

import java.util.HashMap;
import java.util.Map;

public class DeleteTask implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient db = DynamoDbClient.create();
    private static final String TABLE = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String userId = extractUserId(event);
            if (userId == null) return response(401, "{\"message\":\"Unauthorized\"}");

            String taskId = event.getPathParameters() != null ? event.getPathParameters().get("taskId") : null;
            if (taskId == null) return response(400, "{\"message\":\"Missing taskId\"}");

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.fromS("USER#" + userId));
            key.put("SK", AttributeValue.fromS("TASK#" + taskId));

            db.deleteItem(DeleteItemRequest.builder().tableName(TABLE).key(key).build());

            return response(200, "{\"message\":\"Deleted\",\"taskId\":\"" + taskId + "\"}");

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
