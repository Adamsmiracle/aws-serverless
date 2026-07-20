package com.todoapp.expiry;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ScheduleManager implements RequestHandler<SQSEvent, String> {

    private static final SchedulerClient scheduler = SchedulerClient.create();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String EXPIRY_LAMBDA_ARN = System.getenv("EXPIRY_LAMBDA_ARN");
    private static final String SCHEDULER_ROLE_ARN = System.getenv("SCHEDULER_ROLE_ARN");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                JsonNode m = mapper.readTree(msg.getBody());
                String eventName = m.path("eventName").asText();
                String taskId = m.path("taskId").asText();
                String userId = m.path("userId").asText();
                String status = m.path("status").asText();
                String deadline = m.path("deadline").asText();

                String scheduleName = "task-" + taskId;

                boolean shouldExist = "INSERT".equals(eventName) && "Pending".equals(status);
                boolean shouldCancel = "REMOVE".equals(eventName)
                        || ("MODIFY".equals(eventName) && !"Pending".equals(status));

                if (shouldExist) {
                    createSchedule(scheduleName, taskId, userId, deadline, context);
                } else if (shouldCancel) {
                    deleteSchedule(scheduleName, context);
                } else {
                    context.getLogger().log("No action for " + eventName + " status=" + status + " task=" + taskId);
                }
            } catch (Exception e) {
                context.getLogger().log("Manager error: " + e.getMessage());
            }
        }
        return "OK";
    }

    private void createSchedule(String name, String taskId, String userId, String deadline, Context ctx) {
        try {
            long epoch = Long.parseLong(deadline);
            String at = "at(" + FMT.format(Instant.ofEpochSecond(epoch)) + ")";
            String payload = String.format("{\"taskId\":\"%s\",\"userId\":\"%s\"}", taskId, userId);

            scheduler.createSchedule(CreateScheduleRequest.builder()
                    .name(name)
                    .scheduleExpression(at)
                    .flexibleTimeWindow(FlexibleTimeWindow.builder().mode(FlexibleTimeWindowMode.OFF).build())
                    .actionAfterCompletion(ActionAfterCompletion.DELETE)
                    .target(Target.builder()
                            .arn(EXPIRY_LAMBDA_ARN)
                            .roleArn(SCHEDULER_ROLE_ARN)
                            .input(payload)
                            .build())
                    .build());
            ctx.getLogger().log("Created schedule " + name + " firing " + at);
        } catch (ConflictException e) {
            ctx.getLogger().log("Schedule " + name + " already exists; skipping (idempotent).");
        }
    }

    private void deleteSchedule(String name, Context ctx) {
        try {
            scheduler.deleteSchedule(DeleteScheduleRequest.builder().name(name).build());
            ctx.getLogger().log("Cancelled schedule " + name);
        } catch (ResourceNotFoundException e) {
            ctx.getLogger().log("Schedule " + name + " not found; nothing to cancel (idempotent).");
        }
    }
}
