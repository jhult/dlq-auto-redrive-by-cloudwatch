package com.whysurfswim;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskResponse;

/**
 * Lambda function triggered by an SNS notification from a CloudWatch alarm.
 * This function initiates a redrive of messages from the DLQ back to the primary queue.
 */
public class RedriveProcessor implements RequestHandler<SNSEvent, Void> {

    // Retrieve environment variables defined in the SAM template
    private static final String DLQ_ARN = System.getenv("DLQ_ARN");
    private static final String PRIMARY_QUEUE_URL = System.getenv("PRIMARY_QUEUE_URL");

    // Create an SQS client. It's best practice to initialize this outside the handler
    // to allow for connection reuse.
    private final SqsClient sqsClient;

    public RedriveProcessor() {
        // Initialize the SQS client. It will use the default credentials chain
        // and the region from the Lambda environment.
        sqsClient = SqsClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.of(System.getenv("DEPLOYED_AWS_REGION")))
                .build();
    }

    @Override
    public Void handleRequest(SNSEvent snsEvent, Context context) {
        context.getLogger().log("Redrive function triggered by SNS event.");

        // Basic validation to ensure environment variables are set
        if (DLQ_ARN == null || PRIMARY_QUEUE_URL == null || DLQ_ARN.isEmpty() || PRIMARY_QUEUE_URL.isEmpty()) {
            context.getLogger().log("ERROR: Environment variables DLQ_ARN and/or PRIMARY_QUEUE_URL are not set.");
            // Throwing an exception will cause Lambda to retry the invocation if configured.
            throw new IllegalStateException("Environment variables for queue ARNs/URLs are missing.");
        }

        context.getLogger().log("Attempting to start message move task from DLQ: " + DLQ_ARN + " to Primary Queue: " + PRIMARY_QUEUE_URL);

        try {
            // Build the request to start moving messages.
            // This operation is asynchronous. AWS SQS manages the move in the background.
            StartMessageMoveTaskRequest request = StartMessageMoveTaskRequest.builder()
                    .sourceArn(DLQ_ARN)
                    .destinationArn(null) // Destination is inferred if the DLQ has a configured RedrivePolicy
                    .build();

            // Note: If the DLQ itself does not have a RedrivePolicy pointing back to the source,
            // you must specify the .destinationArn() pointing to the Primary Queue ARN.
            // For this setup, AWS can infer it.

            StartMessageMoveTaskResponse response = sqsClient.startMessageMoveTask(request);

            context.getLogger().log("Successfully started message move task. Task Handle: " + response.taskHandle());

        } catch (SqsException e) {
            // Log any errors from the SQS API call
            context.getLogger().log("ERROR starting message move task: " + e.awsErrorDetails().errorMessage());
            // Rethrow to indicate failure for potential Lambda retries
            throw e;
        }

        return null;
    }
}
