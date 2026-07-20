package com.todoapp.auth;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;


import java.util.Map;

public class PostAuth implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static final SnsClient sns = SnsClient.create();
    private static final String TOPIC_ARN = System.getenv("TOPIC_ARN");

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> request = (Map<String, Object>) event.get("request");
        Map<String, String> attrs = (Map<String, String>) request.get("userAttributes");
        String email = attrs.get("email");


        if (email != null && TOPIC_ARN != null) {
            sns.subscribe(SubscribeRequest.builder()
                    .topicArn(TOPIC_ARN)
                    .protocol("email")
                    .endpoint(email)
                    .build());

            context.getLogger().log("Subscribe " + email + "to SNS topic");
        }

        return event;
    }
}