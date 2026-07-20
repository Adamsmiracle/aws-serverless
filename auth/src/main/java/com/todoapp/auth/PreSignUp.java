package com.todoapp.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;

public class PreSignUp implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {

        // Cognito sends a "response" object we mutate and hand back.
        Map<String, Object> response = (Map<String, Object>) event.get("response");
        response.put("autoConfirmUser", true);
        response.put("autoVerifyEmail", true);
        return event;
    }
}