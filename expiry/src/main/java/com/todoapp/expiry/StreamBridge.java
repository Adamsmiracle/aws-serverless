package com.todoapp.expiry;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

public class StreamBridge implements RequestHandler<DynamodbEvent, String> {

    private static final SqsClient sqs = SqsClient.create();
    private static final String QUEUE_URL = System.getenv("QUEUE_URL");

    @Override
    public String handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            try {
                String eventName = record.getEventName(); // INSERT | MODIFY | REMOVE
                StreamRecord sr = record.getDynamodb();

                // For REMOVE the new image is null, so fall back to the old image.
                Map<String, AttributeValue> image =
                        sr.getNewImage() != null ? sr.getNewImage() : sr.getOldImage();
                if (image == null) continue;

                String taskId = getS(image, "TaskId");
                String userId = getS(image, "UserId");
                String status = getS(image, "Status");
                String deadline = getN(image, "Deadline");
                if (taskId == null) continue; // not a task item; skip

                // Build a compact JSON payload for the manager Lambda.
                String body = String.format(
                        "{\"eventName\":\"%s\",\"taskId\":\"%s\",\"userId\":\"%s\",\"status\":\"%s\",\"deadline\":\"%s\"}",
                        eventName, taskId, userId, status == null ? "" : status, deadline == null ? "" : deadline);

                // FIFO requires MessageGroupId (ordering per task) and a dedup id.
                sqs.sendMessage(SendMessageRequest.builder()
                        .queueUrl(QUEUE_URL)
                        .messageBody(body)
                        .messageGroupId(taskId)
                        .messageDeduplicationId(taskId + "-" + record.getEventID())
                        .build());

                context.getLogger().log("Bridged " + eventName + " for task " + taskId);

            } catch (Exception e) {
                context.getLogger().log("Bridge error: " + e.getMessage());
            }
        }
        return "OK";
    }

    private String getS(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return v != null ? v.getS() : null;
    }

    private String getN(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return v != null ? v.getN() : null;
    }
}
