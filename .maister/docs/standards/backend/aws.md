## AWS Standards

### AWS SDK v2 with DynamoDB Enhanced Client
Use AWS SDK v2 (`software.amazon.awssdk`) with the DynamoDB Enhanced Client for all DynamoDB operations. Do not use the low-level `DynamoDbClient` directly for entity operations.

### SQS Integration via Spring Cloud AWS
Use `spring-cloud-aws-starter-sqs` for SQS listener support. Use `@SqsListener` annotations for message consumption.

### Feature-Flag SQS Beans
SQS consumer and related beans must be guarded with `@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")` to allow running without SQS in local/test environments.
