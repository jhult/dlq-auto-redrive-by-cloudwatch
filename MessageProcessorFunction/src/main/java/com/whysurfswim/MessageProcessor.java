package com.whysurfswim;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

/**
 * Lambda function to process messages from the primary SQS queue.
 */
public class MessageProcessor implements RequestHandler<SQSEvent, Void> {

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        for (SQSEvent.SQSMessage msg : sqsEvent.getRecords()) {
            try {
                processMessage(msg, context);
            } catch (Exception e) {
                // Log the exception and rethrow it to mark the message batch as failed.
                // SQS will handle the retry logic based on the queue's RedrivePolicy.
                context.getLogger().log("ERROR processing message: " + e.getMessage());
                throw new RuntimeException("Could not process message with ID: " + msg.getMessageId(), e);
            }
        }
        context.getLogger().log("Successfully processed " + sqsEvent.getRecords().size() + " messages.");
        return null;
    }

    private void processMessage(SQSEvent.SQSMessage message, Context context) {
        context.getLogger().log("Processing message ID: " + message.getMessageId());
        context.getLogger().log("Message Body: " + message.getBody());

        // --- Business Logic ---
        // For demonstration, we simulate a failure if the message body contains "fail".
        // In a real application, this is where your message processing logic would go.
        if (message.getBody() != null && message.getBody().contains("fail")) {
            throw new IllegalStateException("Simulated processing failure for message: " + message.getMessageId());
        }

        context.getLogger().log("Successfully processed message: " + message.getMessageId());
    }
}
