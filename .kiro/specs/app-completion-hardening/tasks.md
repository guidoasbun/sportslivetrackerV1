# Implementation Plan: App Completion Hardening

## Overview

This plan implements the remaining 20% of GameShift Live — completing auth flows, middleware activation, validation utilities, token refresh, test coverage, health monitoring, CI/CD hardening, error resilience, live API-Sports connection, season filtering, per-game fixture selection, and session-aware polling. Tasks are ordered so dependencies are satisfied: utilities before consumers, framework setup before tests, infrastructure before features that depend on it.

## Tasks

- [x] 1. Frontend utilities and middleware foundation
  - [x] 1.1 Create validation utility `frontend/src/lib/validation.ts`
    - Implement `validateEmail()` with rules: exactly one "@", local part 1–64 chars, domain with at least one "." and labels 1–63 chars, total ≤254
    - Implement `validatePassword()` with rules: 8–256 chars, at least one uppercase, lowercase, digit, and special character
    - Return `ValidationResult` with `success` boolean and `errors` array of `{field, rule, message}`
    - Report all failing rules simultaneously (not just the first)
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 1.2 Rename `frontend/src/proxy.ts` → `frontend/src/middleware.ts` and activate middleware
    - Rename the file
    - Rename the exported function from `proxy` to `middleware`
    - Keep the existing matcher config and redirect logic unchanged
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 1.3 Add Cognito library extensions to `frontend/src/lib/cognito.ts`
    - Add `forgotPassword(email)` — invokes `ForgotPasswordCommand`
    - Add `confirmForgotPassword(email, code, newPassword)` — invokes `ConfirmForgotPasswordCommand`
    - Add `resendConfirmationCode(email)` — invokes `ResendConfirmationCodeCommand`
    - Add `refreshToken(refreshToken)` — invokes `InitiateAuthCommand` with `REFRESH_TOKEN_AUTH` flow
    - _Requirements: 1.2, 1.5, 2.1, 5.1_

  - [x] 1.4 Create token manager `frontend/src/lib/tokenManager.ts`
    - Implement `createAuthFetch()` wrapping native fetch
    - On 401 response: queue requests, call `/api/auth/refresh`, retry with new token
    - Proactive refresh: decode JWT `exp`, if <5 min remaining trigger background refresh
    - Single `refreshPromise` to prevent concurrent refresh calls
    - On refresh failure: retry once after 2s, then clear session and redirect to `/login`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 1.5 Update `frontend/src/lib/sessions.ts` for token refresh support
    - Change `createSession` to accept `{ accessToken, refreshToken }` JSON-encoded object
    - Update `getSession` to return parsed session data with both tokens
    - Ensure backward compatibility during the transition
    - _Requirements: 5.2_

- [x] 2. Auth flows — API routes and pages
  - [x] 2.1 Create `/api/auth/email/forgot-password/route.ts`
    - POST handler: validate email present, call `forgotPassword()` from cognito lib
    - Catch `UserNotFoundException` / `LimitExceededException` → return generic success (don't reveal user existence)
    - Return 200 with success message on normal completion
    - _Requirements: 1.2, 1.7_

  - [x] 2.2 Create `/api/auth/email/reset-password/route.ts`
    - POST handler: validate email, code, newPassword present, call `confirmForgotPassword()`
    - Catch `CodeMismatchException` → return 400 "Invalid or expired code"
    - Catch `ExpiredCodeException` → return 400 "Code has expired, request a new one"
    - _Requirements: 1.5, 1.8_

  - [x] 2.3 Create `/api/auth/email/resend-code/route.ts`
    - POST handler: validate email present (return error if missing), call `resendConfirmationCode()`
    - Catch `CodeDeliveryFailureException` → return 500 "Could not send code"
    - Catch `LimitExceededException` / `UserNotFoundException` → return generic error
    - _Requirements: 2.1, 2.2, 2.4, 2.5_

  - [x] 2.4 Create `/api/auth/refresh/route.ts`
    - POST handler: read refresh token from session cookie, call `refreshToken()`
    - Update session cookie with new access token
    - Catch `NotAuthorizedException` → clear session, return 401
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 2.5 Create forgot-password page `frontend/src/app/(auth)/forgot-password/page.tsx`
    - Email input form with validation via `validateEmail()`
    - Display validation errors below input
    - On submit: call `/api/auth/email/forgot-password`, redirect to `/reset-password?email=...`
    - _Requirements: 1.1, 1.3, 4.5, 4.6_

  - [x] 2.6 Create reset-password page `frontend/src/app/(auth)/reset-password/page.tsx`
    - Guard: if no `email` query param, redirect to `/forgot-password`
    - Form with 6-digit code input + new password input with `validatePassword()`
    - Display validation errors below inputs
    - On submit: call `/api/auth/email/reset-password`, redirect to `/login` with success message
    - _Requirements: 1.4, 1.6, 1.9, 4.5, 4.6_

- [x] 3. Checkpoint — Auth and utilities
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Frontend test framework and test files
  - [x] 4.1 Set up Vitest + React Testing Library in frontend
    - Add `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom` as devDependencies
    - Create `frontend/vitest.config.ts` with jsdom environment and `@/` → `./src/` path alias
    - Add `"test": "vitest --run"` script to package.json
    - _Requirements: 7.1, 7.5_

  - [x] 4.2 Write tests for validation utility `frontend/src/lib/__tests__/validation.test.ts`
    - Test valid email formats accepted
    - Test invalid emails rejected (missing "@", missing domain, local >64 chars)
    - Test passwords <8 chars rejected
    - Test valid passwords accepted (8+ chars with all required character types)
    - _Requirements: 7.3_

  - [x] 4.3 Write property tests for validation `frontend/src/lib/__tests__/validation.property.test.ts`
    - Install `fast-check` as devDependency
    - **Property 1: Email validation correctness** — generate valid/invalid emails, verify accept/reject
    - **Validates: Requirements 4.1**
    - **Property 2: Password validation correctness** — generate compliant/non-compliant passwords
    - **Validates: Requirements 4.2**
    - **Property 3: Validation reports all failures simultaneously** — generate inputs with N rule violations, verify N errors returned
    - **Validates: Requirements 4.4**

  - [x] 4.4 Write tests for useEventBuffer hook `frontend/src/lib/__tests__/useEventBuffer.test.ts`
    - Test events older than offsetSeconds are included in visibleEvents
    - Test events newer than threshold are excluded
    - Test buffer does not exceed 500 stored events
    - _Requirements: 7.2_

  - [x] 4.5 Write property test for event buffer `frontend/src/lib/__tests__/useEventBuffer.property.test.ts`
    - **Property 4: Event buffer time-travel filtering** — generate event lists + offsets, verify only events older than offset are visible
    - **Validates: Requirements 7.2**

  - [x] 4.6 Write tests for auth form components `frontend/src/app/(auth)/__tests__/login.test.tsx`
    - Test login form calls `/api/auth/email/signin` with entered email and password
    - Test error message rendered when API response is not ok
    - Test submit button disabled while submission in progress
    - _Requirements: 7.4_

- [x] 5. Lambda processor tests
  - [x] 5.1 Add JUnit 5 + Mockito dependencies to `lambda/pom.xml`
    - Add `junit-jupiter` (5.10+), `mockito-core`, `mockito-junit-jupiter` as test-scope dependencies
    - Add `maven-surefire-plugin` configured for JUnit 5
    - Add test-visible constructor to `SportEventHandler` for dependency injection
    - _Requirements: 6.6_

  - [x] 5.2 Write Lambda handler unit tests `lambda/src/test/java/live/gameshift/lambda/SportEventHandlerTest.java`
    - Test valid payload deserialization with all SportEvent fields
    - Test Event persisted to DynamoDB with correct field mapping
    - Test Summary persisted with Bedrock-generated commentary
    - Test 6 sport types produce distinct prompts
    - Test Bedrock failure → Event still persisted, fallback commentary used
    - Test malformed JSON → error logged, next record processed
    - Test multi-record batch → independent processing
    - Target ≥80% line coverage on handler, services, repositories
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8_

  - [x] 5.3 Write property test for Lambda batch independence
    - **Property 6: Lambda batch independence** — generate batches with N records (K malformed at arbitrary positions), verify exactly (N-K) events persisted
    - **Validates: Requirements 6.7, 6.8**

- [x] 6. Checkpoint — All test suites pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Producer health and live API-Sports connection
  - [ ] 7.1 Create `KinesisHealthIndicator` in producer
    - File: `producer/src/main/java/live/gameshift/producer/health/KinesisHealthIndicator.java`
    - Implement Spring Boot `HealthIndicator`: try `kinesisClient.describeStream()`, return DOWN on failure
    - Returns HTTP 503 with `{"status": "DOWN"}` when Kinesis unreachable
    - _Requirements: 8.1, 8.3_

  - [ ] 7.2 Update Terraform ECS health check for producer
    - Fix port from 8080 → 8081 in the ECS health check configuration
    - Set interval 30s, timeout 5s, start period 60s, 3 retries
    - _Requirements: 8.2, 8.4_

  - [ ] 7.3 Configure live API-Sports connection in producer
    - Set `app.api.sports.mock-mode=false` via Terraform ECS task environment variable
    - Add HTTP timeouts (10s connect + 10s read) to `ApiSportsClient` RestClient builder
    - Implement rate limiting: catch HTTP 429 → set per-sport `pausedUntil` for 60s
    - Implement error handling: catch 401/403/5xx → log sport + endpoint + status, skip sport this cycle
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

- [ ] 8. Season filtering
  - [ ] 8.1 Create `SeasonFilterService` in producer
    - File: `producer/src/main/java/live/gameshift/producer/service/SeasonFilterService.java`
    - At startup + daily (00:00 UTC via `@Scheduled`), query each sport's fixtures for next 24 hours
    - Maintain `Map<SportType, Boolean> activeSports` (volatile, atomic update)
    - On API failure/timeout: retain previous status, log warning
    - Log INFO: which sports are active/skipped at each check
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_

  - [ ] 8.2 Integrate season filter into `PollingService`
    - Before polling each sport, check `seasonFilterService.isActive(sportType)`
    - Skip inactive sports in the polling cycle
    - _Requirements: 13.2, 13.3_

  - [ ] 8.3 Create active sports API endpoint
    - Add `GET /api/sports/active` endpoint in API service
    - Returns JSON array of currently active sport types
    - Producer exposes its active sports state via an internal endpoint or shared config
    - _Requirements: 13.6_

  - [ ] 8.4 Update frontend sport selector to filter by active sports
    - Fetch `/api/sports/active` on dashboard load
    - Only render sports marked as active in the selector bar
    - Hide sports with no active fixtures
    - _Requirements: 13.7_

  - [ ]* 8.5 Write property test for season filter
    - **Property 7: Season filter partitions sports correctly** — generate sport sets with varying fixture availability, verify active/inactive partition matches expectations
    - **Validates: Requirements 13.1, 13.2, 13.3**

- [ ] 9. Per-game fixture selection
  - [ ] 9.1 Add `fixtureId` to producer SportEvent model and normalizers
    - Add `fixtureId` field to `SportEvent` model in producer
    - Update `SoccerNormalizer` (and other normalizers) to extract fixture ID from API-Sports response
    - _Requirements: 14.4_

  - [ ] 9.2 Add `fixtureId` to API service Event model
    - Add `fixtureId` field to `Event` DynamoDB bean
    - Update EventDto to include fixtureId
    - _Requirements: 14.4_

  - [ ] 9.3 Create `FixtureController` in API service
    - `GET /api/fixtures?sport={sportType}` — returns live + upcoming fixtures
    - Each fixture: participant names, status (live/scheduled/finished), start time, fixture ID
    - _Requirements: 14.3_

  - [ ] 9.4 Update SSE stream endpoint to support fixture filtering
    - Accept optional `?fixtureId=X` query param on `GET /api/events/stream`
    - `SseEmitterService`: store metadata (sport, fixtureId) per emitter
    - `EventService`: send events only to matching emitters
    - _Requirements: 14.2, 14.3_

  - [ ] 9.5 Create `FixtureList` UI component and integrate into dashboard
    - File: `frontend/src/components/dashboard/FixtureList.tsx`
    - Show participants, match status, start time for each fixture
    - Dashboard flow: select sport → show fixture list → select fixture → open SSE with fixtureId
    - No fixture selected → show prompt, no SSE connection
    - Handle fetch errors with retry option
    - _Requirements: 14.1, 14.2, 14.5, 14.6_

  - [ ]* 9.6 Write property test for fixture-filtered SSE
    - **Property 8: Fixture-filtered SSE stream delivers only matching events** — generate events with various fixtureIds, verify only matching events delivered to subscribed connection
    - **Validates: Requirements 14.2, 14.3**

- [ ] 10. Session-aware polling
  - [ ] 10.1 Create `SubscriptionRegistry` in API service
    - File: `api/src/main/java/live/gameshift/api/service/SubscriptionRegistry.java`
    - `ConcurrentHashMap<String, AtomicInteger>` keyed by `sport:fixtureId`
    - Increment on SSE open, decrement on close/timeout/error
    - Count never falls below zero
    - Heartbeat timeout: close emitter and decrement if no ping within 120s
    - _Requirements: 15.1, 15.2, 15.3, 15.8_

  - [ ] 10.2 Create `/api/subscriptions/active` endpoint in API service
    - File: `api/src/main/java/live/gameshift/api/controller/SubscriptionController.java`
    - Returns entries with subscriber count > 0 (sport type + fixture ID)
    - _Requirements: 15.4_

  - [ ] 10.3 Integrate SubscriptionRegistry with SseEmitterService
    - On new SSE connection: increment count for sport + fixture
    - On connection close/timeout/error: decrement count
    - Wire heartbeat timeout logic (120s)
    - _Requirements: 15.2, 15.3, 15.8_

  - [ ] 10.4 Create `PollingController` in producer
    - File: `producer/src/main/java/live/gameshift/producer/service/PollingController.java`
    - Before each poll cycle: call API service `/api/subscriptions/active`
    - Empty response → sleep 30s, check again
    - Endpoint unreachable → use previous cycle's sport/fixture list, log warning
    - Only poll sports/fixtures with active subscribers
    - _Requirements: 15.5, 15.6, 15.7_

  - [ ]* 10.5 Write property test for subscription registry
    - **Property 5: Subscription registry count invariant** — generate connect/disconnect sequences, verify count equals connects minus disconnects and never drops below zero
    - **Validates: Requirements 15.1, 15.2, 15.3**

- [ ] 11. Frontend error boundaries and loading states
  - [ ] 11.1 Create ErrorBoundary component
    - File: `frontend/src/components/ErrorBoundary.tsx`
    - React class component wrapping dashboard
    - Catches render errors, shows fallback UI with error description + "Retry" button (reloads page)
    - _Requirements: 11.1_

  - [ ] 11.2 Create loading skeleton components
    - `frontend/src/components/dashboard/EventFeedSkeleton.tsx` — animated placeholders matching event feed dimensions
    - `frontend/src/components/dashboard/CommentaryPanelSkeleton.tsx` — animated placeholders matching commentary panel
    - Display while SSE connection is being established
    - _Requirements: 11.2_

  - [ ] 11.3 Add SSE reconnection with exponential backoff to `useEventBuffer`
    - Exponential backoff: 1s → 2s → 4s → ... → 30s cap, 10 attempts max
    - Expose `reconnectionState` for UI banner
    - After 10 failures: show persistent error + manual "Reconnect" button that resets counter
    - _Requirements: 11.3, 11.4_

- [ ] 12. CloudWatch monitoring Terraform module
  - [ ] 12.1 Create `infrastructure/modules/monitoring/main.tf`
    - `aws_sns_topic` for alarm notifications
    - Lambda error rate alarm: Errors/Invocations > 5%, 5-min period, 1 datapoint
    - ECS RunningTaskCount == 0 alarm for producer, api, frontend (1-min period, 2 datapoints)
    - Kinesis IteratorAgeMilliseconds max > 60000ms alarm (1-min period)
    - CloudWatch dashboard: Lambda metrics, ECS CPU/memory, Kinesis IncomingRecords, DynamoDB capacity
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ] 12.2 Wire monitoring module into `infrastructure/main.tf`
    - Add `module "monitoring"` block with required variables (Lambda function name, ECS cluster/service names, Kinesis stream name)
    - Pass SNS topic ARN as output for subscription configuration
    - _Requirements: 9.4_

- [ ] 13. CI/CD pipeline hardening
  - [ ] 13.1 Restructure `deploy.yml` with test gates
    - Add `test` job: run `mvn test` for lambda, api, producer; `npm run test` for frontend
    - Add `plan` job: run `terraform plan -var-file=dev.tfvars`, output to workflow summary
    - Make `build-deploy` job depend on `test` job via `needs: [test]`
    - If any test fails → pipeline halts, no deployment
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [ ] 14. Final checkpoint — Full integration
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The frontend uses TypeScript with Next.js 16 / React 19
- The backend services use Java 21 with Spring Boot 3
- Lambda tests use JUnit 5 + Mockito; frontend tests use Vitest + React Testing Library + fast-check

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "1.5", "4.1", "5.1"] },
    { "id": 2, "tasks": ["2.1", "2.2", "2.3", "2.4", "7.1", "7.2"] },
    { "id": 3, "tasks": ["2.5", "2.6", "4.2", "4.3", "7.3"] },
    { "id": 4, "tasks": ["4.4", "4.5", "4.6", "5.2", "5.3", "8.1"] },
    { "id": 5, "tasks": ["8.2", "8.3", "9.1"] },
    { "id": 6, "tasks": ["8.4", "8.5", "9.2", "9.3"] },
    { "id": 7, "tasks": ["9.4", "9.5", "10.1"] },
    { "id": 8, "tasks": ["9.6", "10.2", "10.3"] },
    { "id": 9, "tasks": ["10.4", "10.5", "11.1", "11.2"] },
    { "id": 10, "tasks": ["11.3", "12.1"] },
    { "id": 11, "tasks": ["12.2", "13.1"] }
  ]
}
```
