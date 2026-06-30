# Requirements Document

## Introduction

This specification covers the remaining 20% of the GameShift Live sports telemetry dashboard. The application is live at https://dev.gameshift.live with core data pipeline, authentication, and dashboard functionality working. This completion phase addresses: connecting to the live API-Sports account (switching off mock mode), intelligent sport-season filtering, per-game selection to minimize API usage, session-aware polling (stop when no users are watching), missing auth flows (forgot/reset password), middleware activation, frontend utilities, test coverage, production health monitoring, and token lifecycle management.

## Glossary

- **Frontend**: The Next.js 16 application deployed on ECS Fargate that renders the dashboard UI and handles Cognito authentication flows
- **Middleware**: The Next.js middleware function that intercepts requests to enforce auth-based route protection
- **Auth_API**: The Next.js API route handlers under `/api/auth/email/` that interact with AWS Cognito on behalf of the frontend
- **Cognito**: The AWS Cognito User Pool providing email/password and Google OAuth authentication
- **Lambda_Processor**: The Java 21 Lambda function consuming Kinesis events, writing to DynamoDB, and generating Bedrock AI commentary
- **API_Service**: The Spring Boot 3 backend reading DynamoDB and streaming events to the frontend via SSE
- **Producer_Service**: The Spring Boot 3 service polling API-Sports, normalizing data, and publishing to Kinesis
- **Validator**: The frontend utility module responsible for form input validation (email format, password strength)
- **Token_Manager**: The frontend logic responsible for refreshing expired Cognito access tokens using the refresh token
- **Health_Monitor**: CloudWatch alarms and dashboards providing visibility into ECS service health and pipeline throughput
- **ECS_Health_Check**: The ALB target group health check configuration that determines whether a container is healthy
- **Season_Filter**: The logic that determines which sports currently have active seasons and live fixtures available via API-Sports
- **Game_Selector**: The UI component and backend mechanism allowing users to choose which specific fixture (game) to stream
- **Polling_Controller**: The mechanism that starts and stops API-Sports polling based on whether active user sessions are consuming the event stream
- **Subscription_Registry**: The server-side registry tracking which fixtures have active SSE subscribers, used to inform polling decisions

## Requirements

### Requirement 1: Forgot Password Flow

**User Story:** As a registered user, I want to reset my forgotten password via email, so that I can regain access to my account without creating a new one.

#### Acceptance Criteria

1. WHEN a user navigates to `/forgot-password`, THE Frontend SHALL render a form accepting an email address input with validation enforced by the Validator (standard email format: contains @ and a valid domain)
2. WHEN a user submits a valid email on the forgot-password form, THE Auth_API SHALL invoke Cognito's ForgotPassword command with the provided email
3. WHEN Cognito successfully initiates the password reset, THE Frontend SHALL redirect the user to the `/reset-password` page with the email pre-populated
4. WHEN a user navigates to `/reset-password`, THE Frontend SHALL render a form accepting a 6-digit confirmation code and a new password validated by the Validator (minimum 8 characters, at least one uppercase letter, one lowercase letter, one digit, and one special character)
5. WHEN a user submits a valid confirmation code and new password, THE Auth_API SHALL invoke Cognito's ConfirmForgotPassword command with the email, confirmation code, and new password
6. WHEN the password reset succeeds, THE Frontend SHALL redirect the user to `/login` with a success notification indicating the password was changed
7. IF Cognito returns a UserNotFoundException or LimitExceededException during the forgot-password step, THEN THE Auth_API SHALL return a generic success-style response without revealing whether the email exists in the system
8. IF Cognito returns a CodeMismatchException or ExpiredCodeException during the reset-password step, THEN THE Auth_API SHALL return an error message indicating the code is invalid or expired
9. IF the user navigates to `/reset-password` without an email context (no email pre-populated from the forgot-password flow), THEN THE Frontend SHALL redirect the user to `/forgot-password`

### Requirement 2: Resend Confirmation Code

**User Story:** As a user who has not received or has lost their confirmation code, I want to request a new code, so that I can complete account verification.

#### Acceptance Criteria

1. WHEN a user clicks the resend code button on the confirm page, THE Auth_API SHALL validate that the email is present in the request body and invoke Cognito's ResendConfirmationCode command with the user's email
2. IF the resend request is missing the email field, THEN THE Auth_API SHALL return an error message indicating the email is required without invoking Cognito
3. WHEN the resend succeeds, THE Frontend SHALL display a success message indicating a new code was sent and disable the resend button for 60 seconds to prevent repeated requests
4. IF Cognito returns a CodeDeliveryFailureException, THEN THE Auth_API SHALL return an error message indicating the code could not be sent
5. IF Cognito returns a LimitExceededException or UserNotFoundException, THEN THE Auth_API SHALL return a generic error message without revealing whether the email is registered

### Requirement 3: Next.js Middleware Activation

**User Story:** As a developer, I want the authentication middleware to be active in the deployed application, so that protected routes are enforced server-side.

#### Acceptance Criteria

1. THE Frontend SHALL export a named `middleware` function and a `config` object with a `matcher` array from `src/middleware.ts`, as required by the Next.js framework
2. WHILE a user's request does not contain a valid session cookie, THE Middleware SHALL redirect requests to `/dashboard` or any sub-path of `/dashboard` to the `/login` page
3. WHILE a user's request contains a valid session cookie, THE Middleware SHALL redirect requests to `/login`, `/signup`, or `/confirm` to the `/dashboard` page
4. THE Middleware SHALL exclude paths matching `/api/*`, `/_next/static/*`, `/_next/image/*`, and `/favicon.ico` from middleware processing via the exported matcher configuration
5. IF the middleware processes a request that does not match any redirect rule, THEN THE Middleware SHALL allow the request to proceed without modification

### Requirement 4: Form Input Validation Utility

**User Story:** As a frontend developer, I want a shared validation utility, so that all auth forms enforce consistent input rules without duplicating logic.

#### Acceptance Criteria

1. THE Validator SHALL export a function that validates email addresses by verifying the input contains exactly one "@" character, a local part of 1 to 64 characters before the "@", and a domain part after the "@" that contains at least one "." with each label being 1 to 63 characters, with a total maximum email length of 254 characters
2. THE Validator SHALL export a function that validates passwords meet Cognito requirements: minimum 8 characters, maximum 256 characters, at least one uppercase letter (A-Z), one lowercase letter (a-z), one digit (0-9), and one special character from the set ^ $ * . [ ] { } ( ) ? " ! @ # % & / \ , > < ' : ; | _ ~ ` = + -
3. THE Validator SHALL return a validation result object containing a boolean success flag and an array of error objects, where each error object identifies the field name and the specific rule that failed
4. WHEN a validation function is called with an input that violates multiple rules simultaneously, THE Validator SHALL return all failing rules in the errors array, not only the first failure encountered
5. WHEN validation fails on any auth form, THE Frontend SHALL display each error message directly below the relevant input field before submitting to the server
6. WHEN validation succeeds for all fields on an auth form, THE Frontend SHALL permit the form submission to proceed to the server

### Requirement 5: Cognito Token Refresh

**User Story:** As a logged-in user, I want my session to remain active beyond the 1-hour token expiry, so that I am not unexpectedly logged out while watching a live game.

#### Acceptance Criteria

1. WHEN an API request receives a 401 response indicating an expired access token, THE Token_Manager SHALL attempt to refresh the token using the stored refresh token, queuing any additional requests that receive a 401 until the single in-progress refresh completes
2. WHEN the token refresh succeeds, THE Token_Manager SHALL update the session cookie with the new access token and retry the original request and any queued requests using the new token
3. IF the refresh token is expired or invalid (Cognito returns NotAuthorizedException), THEN THE Token_Manager SHALL clear the session cookie and redirect the user to `/login`
4. WHEN the access token's `exp` claim indicates fewer than 5 minutes remain before expiration, THE Token_Manager SHALL initiate a background refresh before any 401 response occurs, so that active requests are not interrupted
5. IF a proactive or reactive token refresh request fails due to a network error, THEN THE Token_Manager SHALL retry the refresh once after a 2-second delay before treating the refresh as failed and clearing the session

### Requirement 6: Lambda Processor Test Coverage

**User Story:** As a developer, I want comprehensive unit tests for the Lambda processor, so that I can refactor and extend the pipeline with confidence.

#### Acceptance Criteria

1. WHEN a Kinesis event record containing a valid JSON payload with all SportEvent fields (eventId, sportType, action, participants, rawPayload, eventTimestamp) is processed, THEN THE Lambda_Processor test suite SHALL verify that the payload is deserialized into a SportEvent object with each field correctly mapped
2. WHEN the handler processes a valid SportEvent, THEN THE Lambda_Processor test suite SHALL verify that an Event record is persisted to the DynamoDB events table with eventId, sportType, action, participants, rawPayload, and eventTimestamp matching the input SportEvent
3. WHEN the handler processes a valid SportEvent and Bedrock invocation succeeds, THEN THE Lambda_Processor test suite SHALL verify that a Summary record is persisted to the DynamoDB summaries table with a generated summaryId, the source eventId, sportType, the Bedrock-generated commentary text, and a timestamp
4. THE Lambda_Processor test suite SHALL include tests verifying that each of the 6 SportType values (SOCCER, FOOTBALL, BASKETBALL, BASEBALL, HOCKEY, FORMULA_1) results in a distinct sport-specific prompt passed to the Bedrock model
5. IF Bedrock invocation throws an exception, THEN THE Lambda_Processor test suite SHALL verify that the Event record is still persisted to DynamoDB, the exception is logged via the Lambda context logger, and a fallback commentary string is used for the Summary record
6. THE Lambda_Processor test suite SHALL achieve a minimum of 80% line coverage on the SportEventHandler, BedrockCommentaryService, DynamoDbService, EventRepository, and SummaryRepository classes
7. IF the Kinesis event record contains malformed JSON that cannot be deserialized into a SportEvent, THEN THE Lambda_Processor test suite SHALL verify that the error is logged via the Lambda context logger and processing continues to the next record in the batch
8. WHEN a KinesisEvent contains more than one record, THEN THE Lambda_Processor test suite SHALL verify that each record is processed independently and a failure in one record does not prevent processing of subsequent records

### Requirement 7: Frontend Test Setup and Coverage

**User Story:** As a frontend developer, I want an established test framework with baseline coverage, so that UI regressions are caught before deployment.

#### Acceptance Criteria

1. THE Frontend SHALL include Vitest and React Testing Library as dev dependencies in package.json with a vitest.config.ts file that configures the jsdom environment and path aliases matching the project's tsconfig
2. THE Frontend test suite SHALL include at least 3 tests for the `useEventBuffer` hook verifying that: events with an `eventTimestamp` older than the `offsetSeconds` threshold are included in `visibleEvents`, events newer than the threshold are excluded, and the buffer does not exceed 500 stored events
3. THE Frontend test suite SHALL include at least 4 tests for the validation utility verifying: valid email formats are accepted, invalid email formats (missing "@", missing domain) are rejected, passwords shorter than 8 characters are rejected, and valid passwords of 8 or more characters are accepted
4. THE Frontend test suite SHALL include at least 3 tests for auth form components verifying that: submitting the login form calls the signin API endpoint with the entered email and password, an error message element is rendered when the API response is not ok, and the submit button is disabled while the form submission is in progress
5. THE Frontend SHALL include a `test` script in package.json that executes Vitest with the `--run` flag so all tests execute in a single run without entering watch mode, and the script SHALL exit with a non-zero code if any test fails

### Requirement 8: Producer Service Health Endpoint

**User Story:** As an operations engineer, I want the producer ECS service to report accurate health status, so that the ALB and ECS can make correct routing and replacement decisions.

#### Acceptance Criteria

1. THE Producer_Service SHALL expose a `/health` endpoint on the same port as the producer container (port 8081) that returns HTTP 200 with a JSON body containing at minimum a `status` field with value `UP`
2. THE ECS_Health_Check for the producer task SHALL be configured to probe the `/health` endpoint on port 8081 with an interval of 30 seconds, a timeout of 5 seconds, a start period of 60 seconds, and 3 retries before marking the task unhealthy
3. WHEN the producer is unable to connect to Kinesis, THE `/health` endpoint SHALL return HTTP 503 with a JSON body containing a `status` field with value `DOWN`
4. IF the `/health` endpoint does not respond within 5 seconds, THEN THE ECS_Health_Check SHALL treat the check as failed and increment the retry counter

### Requirement 9: CloudWatch Monitoring and Alarms

**User Story:** As an operations engineer, I want CloudWatch alarms and a dashboard, so that I am alerted to pipeline failures and can diagnose issues without manually checking each service.

#### Acceptance Criteria

1. THE Health_Monitor SHALL include a CloudWatch alarm that enters ALARM state when the Lambda_Processor error count divided by invocation count exceeds 5% over a 5-minute evaluation period, using 1 consecutive datapoint to trigger
2. THE Health_Monitor SHALL include a CloudWatch alarm that enters ALARM state when any of the Producer_Service, API_Service, or Frontend ECS services has zero running tasks for more than 2 minutes (2 consecutive 1-minute datapoints at zero)
3. THE Health_Monitor SHALL include a CloudWatch alarm that enters ALARM state when the Kinesis GetRecords.IteratorAgeMilliseconds maximum exceeds 60,000 milliseconds over a 1-minute period
4. WHEN any CloudWatch alarm transitions to ALARM state, THE Health_Monitor SHALL publish a notification to an SNS topic so that subscribed operations engineers receive an alert
5. THE Health_Monitor SHALL include a CloudWatch dashboard displaying widgets for: Lambda invocation count and error count (1-minute period), ECS service CPU and memory utilization percentage for each service, Kinesis IncomingRecords count, and DynamoDB ConsumedReadCapacityUnits and ConsumedWriteCapacityUnits

### Requirement 10: CI/CD Branch Merge and Pipeline Hardening

**User Story:** As a developer, I want the CI/CD pipeline running from the main branch with quality gates, so that deployments are automated and broken code cannot reach production.

#### Acceptance Criteria

1. WHEN code is pushed to the main branch, THE CI/CD pipeline SHALL execute all unit tests for the Lambda, API, Producer, and Frontend projects before building Docker images
2. IF any test suite fails, THEN THE CI/CD pipeline SHALL halt and report the failure without deploying
3. THE CI/CD pipeline SHALL run `terraform plan` against the dev environment using the dev.tfvars file and include the plan output in the workflow summary for review
4. WHEN all tests pass and Docker images are built, THE CI/CD pipeline SHALL deploy to ECS using force-new-deployment and update the Lambda function code

### Requirement 11: Frontend Error Boundaries and Loading States

**User Story:** As a user, I want graceful error recovery and clear loading indicators, so that I understand the application state and am not presented with a blank screen on failure.

#### Acceptance Criteria

1. THE Frontend SHALL wrap the dashboard page in a React error boundary component that catches JavaScript render errors and displays a fallback UI containing an error description and a "Retry" button that reloads the dashboard
2. WHILE the SSE connection to the API_Service is being established, THE Frontend SHALL display animated placeholder elements (loading skeletons) in the event feed and commentary panel areas that match the dimensions of the real content
3. IF the SSE connection fails or disconnects, THEN THE Frontend SHALL display a reconnection banner and attempt to reconnect with exponential backoff starting at 1 second, doubling each attempt, capped at a maximum interval of 30 seconds, for up to 10 attempts
4. IF all 10 reconnection attempts fail, THEN THE Frontend SHALL display a persistent error message with a manual "Reconnect" button that resets the attempt counter and begins the reconnection sequence again

### Requirement 12: Live API-Sports Connection

**User Story:** As the application operator, I want the producer to connect to the real API-Sports account instead of mock mode, so that users see actual live sports data.

#### Acceptance Criteria

1. THE Producer_Service SHALL read the API-Sports API key from AWS Secrets Manager at startup using the configured secret ARN, and IF the secret retrieval fails, THEN THE Producer_Service SHALL fail to start and log an error message indicating the secret ARN that could not be retrieved
2. WHILE mock mode is disabled, THE Producer_Service SHALL make authenticated HTTP requests to the API-Sports endpoints using the retrieved API key in the `x-apisports-key` header with a connection timeout of 10 seconds and a read timeout of 10 seconds
3. WHEN an API-Sports response is received with HTTP status 200 and a non-empty response body, THE Producer_Service SHALL normalize the response into a SportEvent using the sport-specific normalizer matching the polled sport type
4. IF the API-Sports API returns a rate limit error (HTTP 429), THEN THE Producer_Service SHALL pause polling for that sport for 60 seconds and log a warning
5. THE Producer_Service configuration SHALL set `app.api.sports.mock-mode` to `false` for the production deployment
6. IF the API-Sports API returns an authentication error (HTTP 401 or 403) or a server error (HTTP 5xx), THEN THE Producer_Service SHALL log an error message indicating the sport, endpoint, and HTTP status code, skip that sport for the current polling cycle, and continue polling on the next scheduled cycle

### Requirement 13: Seasonal Sport Filtering

**User Story:** As the application operator, I want the producer to only poll sports that are currently in-season, so that I do not waste API calls on sports with no live fixtures.

#### Acceptance Criteria

1. WHEN the Producer_Service starts and at the beginning of each day (00:00 UTC), THE Season_Filter SHALL query API-Sports for each configured sport to determine whether live or upcoming fixtures exist within the next 24 hours, completing the check within 10 seconds per sport
2. WHILE a sport has no fixtures within the next 24 hours, THE Producer_Service SHALL skip polling that sport during its regular polling cycle
3. WHEN a previously inactive sport becomes active (fixtures detected on the next season check), THE Producer_Service SHALL resume polling that sport on the next polling cycle
4. THE Producer_Service SHALL log at INFO level which sports are active and which are skipped at each season check, listing every configured sport with its determined status
5. IF the API-Sports query for a sport fails or times out during the season check, THEN THE Season_Filter SHALL retain that sport's previous active/inactive status and log a warning indicating the failed check
6. THE API_Service SHALL expose an endpoint that returns the list of currently active sports as determined by the Season_Filter
7. WHEN the Frontend renders the sport selector bar, THE Frontend SHALL fetch the active sports list from the API_Service and display only sports marked as active, hiding sports that have no active fixtures from user selection

### Requirement 14: Per-Game Selection

**User Story:** As a user, I want to choose which specific game to stream from a list of live fixtures, so that I see only the events relevant to the game I am watching and I help reduce unnecessary API calls.

#### Acceptance Criteria

1. WHEN the user selects a sport, THE Frontend SHALL display a list of currently live and upcoming (starting within 24 hours) fixtures for that sport fetched from the API_Service, showing for each fixture the two participant names, current match status (live, scheduled, or finished), and scheduled start time
2. WHEN the user selects a specific fixture, THE Frontend SHALL subscribe to events only for that fixture by passing the fixture ID to the SSE stream endpoint
3. THE API_Service SHALL expose a fixtures endpoint that returns the list of live and upcoming fixtures for a given sport type and SHALL accept an optional fixture ID query parameter on the events stream endpoint, filtering DynamoDB queries to return only events matching that fixture
4. THE Producer_Service SHALL store the fixture ID (as provided by API-Sports) in the SportEvent model so that events are filterable by fixture downstream
5. WHILE no fixture is selected, THE Frontend SHALL display a prompt instructing the user to choose a game and SHALL NOT render the event feed or open an SSE connection
6. IF the API_Service fixture list request fails or returns an error, THEN THE Frontend SHALL display an error message indicating fixtures could not be loaded and provide a retry option

### Requirement 15: Session-Aware Polling (Stop on No Viewers)

**User Story:** As the application operator, I want the producer to stop polling API-Sports when no users are actively watching, so that I do not consume API quota when the app has no active viewers.

#### Acceptance Criteria

1. THE API_Service SHALL maintain a Subscription_Registry that tracks the count of active SSE connections grouped by sport type and fixture ID, ensuring the count never falls below zero
2. WHEN a new SSE connection is established, THE API_Service SHALL increment the subscriber count for the requested sport and fixture
3. WHEN an SSE connection is closed (user closes browser, navigates away, or connection drops), THE API_Service SHALL decrement the subscriber count for the associated sport and fixture
4. THE API_Service SHALL expose a `/api/subscriptions/active` endpoint that returns the list of sport types and fixture IDs with at least one active subscriber
5. WHEN the Producer_Service polling cycle begins, THE Polling_Controller SHALL query the API_Service subscriptions endpoint and only poll sports and fixtures that have active subscribers
6. WHILE no subscribers exist for any sport, THE Producer_Service SHALL pause all API-Sports polling and check for new subscribers every 30 seconds
7. IF the API_Service subscriptions endpoint is unreachable or returns an error during a polling cycle, THEN THE Producer_Service SHALL continue polling the same sports and fixtures as the previous successful cycle and log a warning
8. IF an SSE connection has not sent a heartbeat acknowledgment within 120 seconds, THEN THE API_Service SHALL treat the connection as stale, close it, and decrement the subscriber count
