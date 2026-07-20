package com.todoapp.expiry;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

public class ExpiryHandler implements RequestHandler<Map<String, Object>, String> {

    private static final DynamoDbClient db = DynamoDbClient.create();
    private static final SnsClient sns = SnsClient.create();
    private static final String TABLE = System.getenv("TABLE_NAME");
    private static final String TOPIC_ARN = System.getenv("TOPIC_ARN");

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        String taskId = (String) event.get("taskId");
        String userId = (String) event.get("userId");
        context.getLogger().log("ExpiryHandler fired for task " + taskId + " user " + userId);

        if (taskId == null || userId == null) {
            context.getLogger().log("Missing taskId/userId; skipping.");
            return "SKIP";
        }

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.fromS("USER#" + userId));
        key.put("SK", AttributeValue.fromS("TASK#" + taskId));

        // Look up the task's current state.
        Map<String, AttributeValue> item = db.getItem(GetItemRequest.builder()
                .tableName(TABLE).key(key).build()).item();

        if (item == null || item.isEmpty()) {
            context.getLogger().log("Task no longer exists (deleted); nothing to expire.");
            return "GONE";
        }

        String status = item.containsKey("Status") ? item.get("Status").s() : "";
        if (!"Pending".equals(status)) {
            context.getLogger().log("Task status is " + status + ", not Pending; skipping expiry.");
            return "NOOP";
        }

        // Still Pending -> mark Expired.
        db.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key)
                .updateExpression("SET #s = :s")
                .expressionAttributeNames(Map.of("#s", "Status"))
                .expressionAttributeValues(Map.of(":s", AttributeValue.fromS("Expired")))
                .build());
        context.getLogger().log("Task " + taskId + " marked Expired.");

        // Notify via SNS.
        String description = item.containsKey("Description") ? item.get("Description").s() : "(no description)";
        sns.publish(PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .subject("Task Expired")
                .message("Your task has expired:\n\nTask: " + description + "\nTask ID: " + taskId)
                .build());
        context.getLogger().log("Expiry notification published to SNS for task " + taskId);

        return "EXPIRED";
    }
}
