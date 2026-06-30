# Requirements Document

## Introduction

This specification covers adding text-to-speech (TTS) capabilities to the GameShift Live commentary system using Amazon Polly. The application already generates AI commentary via Amazon Bedrock (in the Lambda Processor) and displays it in a commentary panel on the frontend dashboard. This feature extends the commentary experience by reading commentary text aloud using Polly's neural voices, giving users an immersive sports broadcast feel without requiring them to watch the screen continuously.

## Glossary

- **TTS_Service**: The Spring Boot service component in the API Service responsible for accepting commentary text, invoking Amazon Polly, and returning synthesized audio data
- **Polly_Client**: The AWS SDK client configured in the API Service that communicates with the Amazon Polly SynthesizeSpeech API
- **Audio_Queue**: The frontend queue mechanism that buffers synthesized audio segments and plays them sequentially without overlap
- **TTS_Toggle**: The frontend UI control that enables or disables automatic text-to-speech playback of new commentary
- **Playback_Controller**: The frontend component responsible for managing audio playback state (play, stop, skip) and exposing controls to the user
- **API_Service**: The Spring Boot 3 backend deployed on ECS Fargate that serves events, summaries, and now TTS audio to the frontend
- **Frontend**: The Next.js 16 application deployed on ECS Fargate that renders the dashboard UI including the commentary panel and TTS controls
- **Commentary_Panel**: The existing frontend component that displays AI-generated commentary text for each sports event
- **Neural_Voice**: An Amazon Polly voice engine that uses deep learning to produce natural-sounding speech with lifelike intonation

## Requirements

### Requirement 1: TTS Synthesis API Endpoint

**User Story:** As a frontend client, I want an API endpoint that converts commentary text into audio, so that the dashboard can play commentary aloud to the user.

#### Acceptance Criteria

1. THE API_Service SHALL expose a POST endpoint at `/api/tts/synthesize` that accepts a JSON request body containing a `text` field (the commentary string) and an optional `voiceId` field
2. WHEN the TTS_Service receives a valid synthesis request with a non-empty text field, THE TTS_Service SHALL invoke the Polly_Client to synthesize speech using the NEURAL engine and return HTTP 200 with the audio data as a binary response with content type `audio/mpeg`
3. WHEN no `voiceId` is provided in the request, THE TTS_Service SHALL use a default neural voice of `Matthew` (US English male, suitable for sports commentary)
4. IF the `text` field is empty or missing in the request body, THEN THE API_Service SHALL return HTTP 400 with an error message indicating that commentary text is required
5. IF the `text` field exceeds 3000 characters, THEN THE API_Service SHALL return HTTP 400 with an error message indicating the text exceeds the maximum allowed length
6. IF the Polly_Client invocation fails due to a service error or timeout, THEN THE API_Service SHALL return HTTP 503 with an error message indicating that speech synthesis is temporarily unavailable
7. THE TTS_Service SHALL set a request timeout of 10 seconds when invoking the Polly_Client, so that a slow Polly response does not block the API_Service indefinitely
8. IF the provided `voiceId` is not a valid Polly neural voice identifier, THEN THE API_Service SHALL return HTTP 400 with an error message indicating the specified voice is not supported
9. IF the request body is not valid JSON or cannot be parsed, THEN THE API_Service SHALL return HTTP 400 with an error message indicating the request body is malformed

### Requirement 2: TTS Toggle Control

**User Story:** As a user watching live sports, I want a toggle button to enable or disable commentary being read aloud, so that I can choose whether to listen to commentary or read it silently.

#### Acceptance Criteria

1. THE Frontend SHALL render a TTS_Toggle button in the Commentary_Panel header area with a speaker icon, an `aria-pressed` attribute reflecting the current state, and tooltip text indicating "Enable voice commentary" when off and "Disable voice commentary" when on
2. WHEN the user clicks the TTS_Toggle while TTS is disabled, THE Frontend SHALL enable TTS mode, set `aria-pressed` to `true`, and visually distinguish the active state from the inactive state using a filled or highlighted icon variant
3. WHEN the user clicks the TTS_Toggle while TTS is enabled, THE Frontend SHALL disable TTS mode, stop any currently playing audio, clear the Audio_Queue, set `aria-pressed` to `false`, and visually revert the button to the inactive icon variant
4. THE Frontend SHALL persist the TTS_Toggle preference in browser localStorage and restore the stored preference on page load, so that the user's choice survives page reloads within the same browser session
5. IF no stored TTS preference exists in localStorage or localStorage is unavailable, THEN THE Frontend SHALL default the TTS_Toggle to the disabled (off) state, so that audio does not play unexpectedly on first visit or in restricted browsing contexts

### Requirement 3: Automatic Commentary Playback

**User Story:** As a user with TTS enabled, I want new commentary to be read aloud automatically as it arrives, so that I can hear the AI commentary without manually triggering playback for each event.

#### Acceptance Criteria

1. WHILE TTS is enabled and the Commentary_Panel state updates with a new commentary text (i.e., a commentary string not already present in the Audio_Queue or currently playing), THE Frontend SHALL send the commentary text to the `/api/tts/synthesize` endpoint with a request timeout of 10 seconds and, upon receiving a successful response, enqueue the returned audio for playback
2. WHILE TTS is enabled and audio is already playing from a previous commentary, THE Audio_Queue SHALL hold newly received audio segments and play them sequentially in FIFO arrival order after the current segment finishes, up to the maximum queue capacity defined by the Audio_Queue
3. WHILE TTS is disabled, THE Frontend SHALL not send synthesis requests to the API_Service and SHALL not enqueue or play any audio
4. IF a synthesis request fails (network error, request timeout, or non-2xx response), THEN THE Frontend SHALL skip that commentary segment without interrupting any currently playing audio or the remaining queue, and log a warning to the browser console including the HTTP status code or error type
5. WHEN the Commentary_Panel receives commentary text that is identical (exact string match) to the text currently playing or any text already enqueued in the Audio_Queue, THE Frontend SHALL not enqueue a duplicate synthesis request
6. IF audio playback of a queued segment fails (decoding error or browser playback restriction), THEN THE Frontend SHALL discard that segment, log a warning to the browser console, and advance to the next segment in the Audio_Queue without interrupting the remaining queue

### Requirement 4: Audio Queue Management

**User Story:** As a user, I want commentary audio to play sequentially without overlapping, so that I can clearly hear each commentary segment.

#### Acceptance Criteria

1. THE Audio_Queue SHALL play audio segments one at a time in first-in-first-out (FIFO) order, beginning the next segment only after the current segment finishes playing or is skipped
2. WHEN an audio segment finishes playing or encounters a playback error, THE Audio_Queue SHALL automatically advance to the next queued segment without user intervention within 200 milliseconds
3. THE Audio_Queue SHALL hold a maximum of 5 pending audio segments excluding the currently playing segment; WHEN the queue is full and a new segment arrives, THE Audio_Queue SHALL discard the oldest unplayed segment to make room for the new one
4. WHEN TTS is disabled via the TTS_Toggle, THE Audio_Queue SHALL stop the current audio and discard all pending segments within 100 milliseconds, resetting to an empty state
5. IF an audio segment fails to play due to a decoding error or corrupted audio data, THEN THE Audio_Queue SHALL skip the failed segment, advance to the next queued segment, and log a warning to the browser console without interrupting the remaining queue

### Requirement 5: Playback Controls

**User Story:** As a user, I want to stop or skip the currently playing commentary audio, so that I can control what I hear without disabling TTS entirely.

#### Acceptance Criteria

1. WHILE audio is playing, THE Playback_Controller SHALL display a "Skip" button and a "Stop All" button, each with an accessible label describing its action
2. WHILE no audio is playing and the Audio_Queue contains at least one pending segment, THE Playback_Controller SHALL display a "Play Next" button that begins playback of the next queued segment, and a "Stop All" button that clears the Audio_Queue
3. WHILE no audio is playing and the Audio_Queue is empty, THE Playback_Controller SHALL hide all playback control buttons
4. WHEN the user clicks the Skip button, THE Playback_Controller SHALL stop the current audio segment within 100 milliseconds and begin playing the next queued segment; IF no next segment exists in the Audio_Queue, THEN THE Playback_Controller SHALL stop the current audio within 100 milliseconds and transition to the hidden-controls state
5. WHEN the user clicks the Stop All button, THE Playback_Controller SHALL stop the current audio segment within 100 milliseconds and remove all pending segments from the Audio_Queue without disabling the TTS_Toggle, so that new commentary arriving after the action will still be queued for playback

### Requirement 6: Neural Voice Configuration

**User Story:** As a developer, I want Polly to use a high-quality neural voice appropriate for sports commentary, so that the synthesized speech sounds natural and engaging.

#### Acceptance Criteria

1. THE TTS_Service SHALL use the Amazon Polly `neural` engine for all synthesis requests
2. THE TTS_Service SHALL accept the following voiceId values as valid: `Matthew`, `Joanna`, `Liam`, `Ruth`; IF no voiceId is provided in the request, THEN THE TTS_Service SHALL default to `Matthew`
3. IF an unsupported voiceId is provided, THEN THE TTS_Service SHALL return HTTP 400 with an error message indicating the supported voice values
4. THE TTS_Service SHALL request audio output in MP3 format (OutputFormat `mp3`) with a sample rate of 24000 Hz from Polly
5. THE TTS_Service SHALL set the Polly `TextType` to `text` (plain text input), not SSML
6. IF the Polly service returns an error or is unreachable, THEN THE TTS_Service SHALL return HTTP 503 with an error message indicating speech synthesis failed

### Requirement 7: Infrastructure and IAM Permissions

**User Story:** As a DevOps engineer, I want the necessary IAM permissions and infrastructure configuration managed in Terraform, so that the API Service can invoke Polly in production without manual AWS Console changes.

#### Acceptance Criteria

1. THE Terraform IAM module SHALL include a policy attached to the API_Service ECS task role that grants `polly:SynthesizeSpeech` permission with Resource set to `"*"` (Polly does not support resource-level ARNs)
2. THE API_Service build configuration (pom.xml) SHALL declare the AWS SDK Polly client dependency under the existing AWS SDK BOM so that the Polly client class is available at runtime
3. IF the API_Service ECS task role does not have the `polly:SynthesizeSpeech` permission, THEN THE API_Service SHALL return HTTP 503 with a response body indicating that speech synthesis is unavailable when a text-to-speech request is attempted
4. THE Terraform IAM policy for Polly access SHALL follow the principle of least privilege by granting only the `polly:SynthesizeSpeech` action and no other Polly actions
5. WHEN `terraform plan` is executed, THE Terraform configuration SHALL show the Polly IAM policy resource as attached to the same role used by the API_Service ECS task definition (`api_task_role_arn`) with no changes required to the ECS task definition itself
