# Implementation Plan: Polly Commentary TTS

## Overview

This plan implements text-to-speech for the GameShift Live commentary system. The backend (Spring Boot/Java) gets a new `/api/tts/synthesize` endpoint that proxies Amazon Polly, and the frontend (Next.js/TypeScript) adds a TTS toggle, audio queue, and playback controls to the CommentaryPanel. Infrastructure changes add the Polly IAM permission and SDK dependency.

## Tasks

- [x] 1. Backend: Add Polly dependency and client configuration
  - [x] 1.1 Add AWS SDK Polly dependency to `api/pom.xml`
    - Add `software.amazon.awssdk:polly` dependency under the existing AWS SDK BOM (no version needed)
    - _Requirements: 7.2_

  - [x] 1.2 Add PollyClient bean to `AwsConfig.java`
    - Create a `PollyClient` Spring bean in `api/src/main/java/live/gameshift/api/config/AwsConfig.java`
    - Configure the same region as other AWS clients (`app.aws.region`)
    - Set `apiCallTimeout` to 10 seconds via `overrideConfiguration`
    - _Requirements: 1.7, 6.1_

- [x] 2. Backend: Implement TTS synthesis endpoint
  - [x] 2.1 Create `TtsSynthesizeRequest` DTO
    - Create `api/src/main/java/live/gameshift/api/dto/TtsSynthesizeRequest.java` as a record with `text` and `voiceId` fields
    - _Requirements: 1.1_

  - [x] 2.2 Create `TtsErrorResponse` DTO
    - Create `api/src/main/java/live/gameshift/api/dto/TtsErrorResponse.java` as a record with an `error` field
    - _Requirements: 1.4, 1.5, 1.6, 1.8, 1.9_

  - [x] 2.3 Implement `TtsService`
    - Create `api/src/main/java/live/gameshift/api/service/TtsService.java`
    - Define `SUPPORTED_VOICES = Set.of("Matthew", "Joanna", "Liam", "Ruth")` and `DEFAULT_VOICE = "Matthew"`
    - Validate text is non-null, non-blank, and <= 3000 characters
    - Validate voiceId against supported set; default to Matthew if null
    - Call `pollyClient.synthesizeSpeech()` with Engine=NEURAL, OutputFormat=MP3, SampleRate=24000, TextType=TEXT
    - Read the response InputStream into a byte array and return it
    - Catch `PollyException` and `SdkClientException` (including timeouts) and throw a custom exception or return appropriate error
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 2.4 Implement `TtsController`
    - Create `api/src/main/java/live/gameshift/api/controller/TtsController.java`
    - Map `POST /api/tts/synthesize` with `produces = "audio/mpeg"`
    - Delegate to `TtsService.synthesize()`
    - Return `ResponseEntity<byte[]>` with content type `audio/mpeg` on success
    - Handle validation exceptions → 400 with `TtsErrorResponse`
    - Handle service unavailable exceptions → 503 with `TtsErrorResponse`
    - Handle malformed JSON body → 400 with `TtsErrorResponse`
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 1.8, 1.9, 7.3_

  - [x] 2.5 Write property tests for TtsService
    - **Property 1: Valid request produces correct Polly invocation**
    - **Property 2: Invalid text is rejected**
    - **Property 3: Invalid voiceId is rejected**
    - **Property 4: Polly failure produces service unavailable**
    - Create `api/src/test/java/live/gameshift/api/service/TtsServicePropertyTest.java`
    - Use jqwik with @Tag annotations: `@Tag("Feature: polly-commentary-tts, Property N: title")`
    - Mock PollyClient in all tests; minimum 100 iterations per property
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6, 1.8, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**

  - [x] 2.6 Write unit tests for TtsController
    - Create `api/src/test/java/live/gameshift/api/controller/TtsControllerTest.java`
    - Test successful synthesis returns 200 with audio/mpeg content type
    - Test empty text returns 400
    - Test text exceeding 3000 chars returns 400
    - Test invalid voiceId returns 400
    - Test Polly failure returns 503
    - Test malformed JSON body returns 400
    - Use Spring MockMvc for integration-style controller tests
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.8, 1.9_

- [x] 3. Checkpoint - Backend verification
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Frontend: Implement TTS preference hook and toggle component
  - [x] 4.1 Create `useTtsPreference` hook
    - Create `frontend/src/lib/useTtsPreference.ts`
    - Manage boolean enabled state with `useState`
    - Persist to localStorage under key `gameshift-tts-enabled`
    - Restore stored preference on mount; default to `false` if not found or localStorage unavailable
    - Export `{ enabled, toggle }` interface
    - _Requirements: 2.4, 2.5_

  - [x] 4.2 Create `TtsToggle` component
    - Create `frontend/src/components/dashboard/TtsToggle.tsx`
    - Render a button with speaker icon, `aria-pressed` attribute, and tooltip text
    - When off: unfilled icon, tooltip "Enable voice commentary"
    - When on: filled/highlighted icon, tooltip "Disable voice commentary"
    - On click: call `toggle()` from `useTtsPreference`
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 4.3 Write property test for TTS preference localStorage round-trip
    - **Property 5: TTS preference localStorage round-trip**
    - Create `frontend/src/lib/__tests__/useTtsPreference.property.test.ts`
    - Use fast-check to generate random boolean values, verify persist/read round-trip
    - `// Feature: polly-commentary-tts, Property 5: TTS preference localStorage round-trip`
    - **Validates: Requirements 2.4**

  - [x] 4.4 Write unit tests for TtsToggle
    - Create `frontend/src/components/__tests__/TtsToggle.test.tsx`
    - Test renders with `aria-pressed="false"` when disabled
    - Test renders with `aria-pressed="true"` when enabled
    - Test click toggles state
    - Test tooltip text changes with state
    - _Requirements: 2.1, 2.2, 2.3_

- [ ] 5. Frontend: Implement audio queue hook
  - [ ] 5.1 Create `useAudioQueue` hook
    - Create `frontend/src/lib/useAudioQueue.ts`
    - Manage `AudioQueueState`: queue (max 5 pending), currentlyPlaying, isPlaying
    - Implement `enqueue(text, audioBlob)`: deduplicate by text match, enforce capacity (drop oldest if full)
    - Implement `skip()`: stop current, play next within 100ms
    - Implement `stopAll()`: stop current and clear queue within 100ms
    - Auto-advance to next segment when current finishes (within 200ms)
    - Handle decoding/playback errors: discard failed segment, log warning, advance to next
    - When `enabled` becomes false: stop and clear immediately
    - Use `HTMLAudioElement` and `URL.createObjectURL` for playback
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ] 5.2 Write property tests for audio queue
    - **Property 6: Audio queue FIFO ordering**
    - **Property 7: Audio queue deduplication**
    - **Property 8: Audio queue capacity invariant**
    - Create `frontend/src/lib/__tests__/useAudioQueue.property.test.ts`
    - Use fast-check with `fc.assert(property, { numRuns: 100 })`
    - `// Feature: polly-commentary-tts, Property 6/7/8`
    - **Validates: Requirements 3.2, 3.5, 4.1, 4.3**

  - [ ] 5.3 Write unit tests for useAudioQueue
    - Create `frontend/src/lib/__tests__/useAudioQueue.test.ts`
    - Test FIFO playback order
    - Test queue capacity limit of 5
    - Test deduplication of identical text
    - Test skip advances to next
    - Test stopAll clears queue
    - Test disabled state stops and clears
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 6. Frontend: Implement playback controls and CommentaryPanel integration
  - [ ] 6.1 Create `PlaybackControls` component
    - Create `frontend/src/components/dashboard/PlaybackControls.tsx`
    - When audio is playing: show "Skip" and "Stop All" buttons with accessible labels
    - When not playing but queue has items: show "Play Next" and "Stop All" buttons
    - When not playing and queue is empty: hide all controls
    - Wire buttons to `skip()`, `stopAll()`, and a `playNext()` callback
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ] 6.2 Integrate TTS into CommentaryPanel
    - Modify `frontend/src/components/dashboard/CommentaryPanel.tsx`
    - Add `TtsToggle` to the panel header
    - Add `PlaybackControls` below the toggle or in the panel footer
    - Use `useTtsPreference` and `useAudioQueue` hooks
    - When TTS enabled and new commentary arrives: call `/api/tts/synthesize` with 10s timeout, enqueue audio on success
    - On synthesis failure: skip commentary, log warning to console
    - Do not send synthesis requests when TTS is disabled
    - _Requirements: 2.1, 3.1, 3.3, 3.4_

  - [ ] 6.3 Write unit tests for PlaybackControls
    - Create `frontend/src/components/__tests__/PlaybackControls.test.tsx`
    - Test button visibility in each state (playing, queued, empty)
    - Test button click handlers
    - Test accessible labels on buttons
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ] 6.4 Write integration test for CommentaryPanel with TTS
    - Create `frontend/src/components/__tests__/CommentaryPanel.tts.test.tsx`
    - Test TTS toggle renders in panel header
    - Test new commentary triggers synthesis when TTS enabled
    - Test no synthesis when TTS disabled
    - Mock fetch for `/api/tts/synthesize`
    - _Requirements: 2.1, 3.1, 3.3_

- [ ] 7. Checkpoint - Frontend verification
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Infrastructure: Add Polly IAM permissions
  - [ ] 8.1 Add Polly IAM policy to Terraform IAM module
    - Modify `infrastructure/modules/iam/main.tf`
    - Add a new `aws_iam_policy` resource granting only `polly:SynthesizeSpeech` with Resource `"*"`
    - Attach the policy to the API Service ECS task role
    - Follow least privilege: only `polly:SynthesizeSpeech`, no other Polly actions
    - _Requirements: 7.1, 7.4, 7.5_

- [ ] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Backend uses Java 21 with Spring Boot 3 and jqwik for property tests
- Frontend uses TypeScript with Next.js and fast-check for property tests
- PollyClient is mocked in all backend tests (no real AWS calls)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "2.2", "4.1"] },
    { "id": 1, "tasks": ["1.2", "2.3", "4.2"] },
    { "id": 2, "tasks": ["2.4", "4.3", "4.4", "5.1"] },
    { "id": 3, "tasks": ["2.5", "2.6", "5.2", "5.3"] },
    { "id": 4, "tasks": ["6.1", "8.1"] },
    { "id": 5, "tasks": ["6.2"] },
    { "id": 6, "tasks": ["6.3", "6.4"] }
  ]
}
```
