# GameShift Live — Real-Time Multi-Sport Telemetry Dashboard

> Stream live sports events through an AWS data pipeline with AI-generated color commentary and a client-side time-travel buffer to sync your UI to any broadcast delay.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?logo=springboot)
![Next.js](https://img.shields.io/badge/Next.js-16-black?logo=nextdotjs)
![AWS](https://img.shields.io/badge/AWS-Kinesis%20%7C%20Lambda%20%7C%20ECS%20%7C%20DynamoDB-FF9900?logo=amazonaws)
![Terraform](https://img.shields.io/badge/IaC-Terraform-7B42BC?logo=terraform)
![Docker](https://img.shields.io/badge/Container-Docker-2496ED?logo=docker)

---

This application is live at

- https://dev.gameshift.live

---

## What It Does

1. Sign in with Google via AWS Cognito
2. Select a sport (Soccer, Basketball, Football, Baseball, Hockey, Formula 1) and pick a live fixture
3. The **Producer** polls API-Sports for real-time game events, normalizes them into a unified `SportEvent` model, and publishes to Kinesis
4. A **Lambda** consumer reads the stream, timestamps events, writes them to DynamoDB, and calls **Amazon Bedrock** (Claude Haiku 4.5) to generate sport-aware color commentary
5. The **API** layer reads DynamoDB and streams events + AI summaries to the frontend via **Server-Sent Events**
6. The frontend renders a live dashboard with a **broadcast offset slider** — a time-travel buffer that lets you sync the UI to your TV's delay
7. **AWS Polly** provides text-to-speech narration of the AI commentary

---

## AI & Commentary Layer

### Amazon Bedrock — Dynamic Sport Commentary

The Lambda processor calls Bedrock after every event, selecting a sport-specific prompt template based on the `sport_type` field. Commentary for a soccer "goal" and a football "touchdown" are contextually distinct.

| Model | Usage |
| --- | --- |
| Claude Haiku 4.5 | Real-time play-by-play commentary and momentum summaries |

**How it works:**

- Each incoming `SportEvent` carries a `sport_type`, `action`, `participants`, and `timestamp`
- The Lambda selects the matching prompt template and invokes Bedrock
- Generated commentary is stored in the `Summaries` DynamoDB table with the same event ID
- The API layer serves summaries alongside raw events so the frontend renders both in the timeline

### AWS Polly — Text-to-Speech

The API exposes a `/api/tts/synthesize` endpoint that converts commentary text into MP3 audio using AWS Polly. The frontend queues audio clips for hands-free narration of live events.

---

## Architecture

```
API-Sports → Producer (ECS) → Kinesis Data Streams → Lambda → DynamoDB
                                                                   ↓
                                                           Bedrock (AI commentary)
                                                                   ↓
Frontend (ECS) ← SSE ← API layer (ECS) ← DynamoDB reads
                                              ↓
                                         AWS Polly (TTS)
```

### Request Flow

```
GET /api/events/stream?sport=SOCCER&fixtureId=123
  └─ ALB routes to API ECS service
      └─ SseEmitterService creates SSE connection
          └─ EventService polls DynamoDB for new events matching fixture
          └─ SummaryService fetches AI commentary for each event
          └─ Events + summaries pushed to client as SSE messages

POST /api/tts/synthesize
  └─ TtsService invokes AWS Polly
      └─ Returns MP3 audio bytes to frontend
```

---

## Infrastructure

The entire AWS environment is defined in Terraform under [`/infrastructure`](infrastructure/) using a modular structure.

### AWS Services

| Service | Role |
| --- | --- |
| **ECS Fargate** | Runs Producer, API, and Frontend containers (ARM64) |
| **Application Load Balancer** | HTTPS termination, path-based routing (`/api/*` → API, `/*` → Frontend) |
| **Kinesis Data Streams** | Real-time event ingestion from Producer to Lambda |
| **Lambda** | Kinesis consumer: timestamps events, writes DynamoDB, invokes Bedrock |
| **DynamoDB** | `Events` and `Summaries` tables with 7-day TTL auto-expiry |
| **Amazon Bedrock** | AI commentary generation (Claude Haiku 4.5) |
| **AWS Polly** | Text-to-speech synthesis for commentary narration |
| **Cognito** | User pool with Google as federated identity provider; JWT-based auth |
| **ECR** | Private container registries for Producer, API, and Frontend images |
| **Secrets Manager** | Stores API-Sports key; injected at ECS task runtime |
| **CloudWatch** | Container and Lambda logs with 30-day retention |
| **ACM** | TLS certificates for the load balancer |

### Terraform Module Structure

```
infrastructure/
├── main.tf                  # Module orchestration
├── providers.tf             # S3 remote state + native lock
├── variables.tf             # Input variables
├── outputs.tf               # Exported values
├── environments/
│   ├── dev.tfvars
│   └── prod.tfvars
└── modules/
    ├── networking/          # VPC, subnets, IGW, NAT, route tables, security groups
    ├── alb/                 # ALB, target groups, HTTPS listener, path-based rules
    ├── ecs/                 # Cluster, task definitions, Fargate services (Producer, API, Frontend)
    ├── iam/                 # Execution roles, task roles, GitHub Actions OIDC role
    ├── dynamodb/            # Events + Summaries tables with TTL
    ├── kinesis/             # Data stream for event ingestion
    ├── lambda/              # Kinesis consumer function
    ├── cognito/             # User pool, Google IdP, app client
    ├── ecr/                 # Container repositories
    └── monitoring/          # CloudWatch dashboards and alarms
```

**Remote state:** Terraform state is stored in S3 (`sports-tracker-terraform-state`) with native S3 locking.

### Security Design

- **Least-privilege IAM:** Each ECS task role grants only the specific actions needed (Producer → `kinesis:PutRecord`; API → `dynamodb:GetItem/Query`, `polly:SynthesizeSpeech`; Lambda → `dynamodb:PutItem`, `bedrock:InvokeModel`)
- **No static credentials:** GitHub Actions authenticates to AWS via OIDC — no long-lived access keys
- **Secrets at runtime:** API keys are pulled from Secrets Manager by the ECS Execution Role and injected as environment variables — never baked into images
- **JWT validation:** Spring Security validates Cognito-issued JWTs on every protected endpoint

---

## CI/CD Pipeline

**File:** [.github/workflows/deploy.yml](.github/workflows/deploy.yml)

```
Trigger: push to main  OR  manual dispatch (select: dev | prod)
    │
    ├─ Run all tests (Java + Frontend)
    │
    ├─ Configure AWS credentials via OIDC (no static keys)
    │
    ├─ Docker Buildx (linux/arm64)
    │   ├─ Build producer image  → ECR  :latest + :<git-sha>
    │   ├─ Build api image       → ECR  :latest + :<git-sha>
    │   └─ Build frontend image  → ECR  :latest + :<git-sha>
    │
    ├─ Build Lambda shaded JAR → deploy to Lambda function
    │
    └─ Force new ECS deployment
        ├─ sports-tracker-{env}-producer
        ├─ sports-tracker-{env}-api
        └─ sports-tracker-{env}-frontend
```

---

## Tech Stack

| Layer | Technology | Version |
| --- | --- | --- |
| Backend language | Java (Amazon Corretto) | 21 |
| Backend framework | Spring Boot | 3.3.4 |
| Frontend framework | Next.js | 16 |
| Frontend library | React | 19 |
| Styling | Tailwind CSS | 4 |
| Testing (Java) | JUnit 5 + jqwik (property-based) | — |
| Testing (Frontend) | Vitest + fast-check (property-based) | — |
| AI commentary | Amazon Bedrock (Claude Haiku 4.5) | — |
| Text-to-speech | AWS Polly | — |
| Data ingestion | AWS Kinesis Data Streams | — |
| Processing | AWS Lambda (Java 21) | — |
| Database | AWS DynamoDB | — |
| Authentication | AWS Cognito (Google OAuth2) | — |
| Container runtime | AWS ECS Fargate (ARM64) | — |
| Load balancer | AWS ALB | — |
| IaC | Terraform | 1.7+ |
| CI/CD | GitHub Actions (OIDC) | — |
| AWS SDK | AWS SDK for Java v2 | 2.26.31 |

---

## Project Structure

```
sportslivetracker/
├── producer/                         # Spring Boot — polls API-Sports, publishes to Kinesis
│   └── src/main/java/live/gameshift/producer/
│       ├── config/                   # AWS Kinesis + Secrets Manager config
│       ├── service/                  # API polling, event normalization, Kinesis publishing
│       └── model/                    # SportEvent unified data model
├── lambda/                           # AWS Lambda — Kinesis consumer
│   └── src/main/java/live/gameshift/lambda/
│       ├── handler/                  # Kinesis event handler
│       ├── service/                  # DynamoDB writes, Bedrock commentary generation
│       └── model/                    # Event + Summary models
├── api/                              # Spring Boot — serves frontend via SSE
│   └── src/main/java/live/gameshift/api/
│       ├── config/                   # AWS, CORS, Security (Cognito JWT)
│       ├── controller/               # EventController, FixtureController, TtsController, etc.
│       ├── service/                  # SSE emitter, subscriptions, DynamoDB reads, Polly TTS
│       ├── repository/               # DynamoDB event + summary repositories
│       └── model/                    # DTOs, domain models, enums (SportType)
├── frontend/                         # Next.js 16 app
│   └── src/
│       ├── app/(auth)/               # Cognito login flow
│       ├── app/(protected)/dashboard # Live dashboard with sport/fixture selection
│       ├── components/               # UI components (event timeline, offset slider)
│       └── lib/                      # Hooks (useEventBuffer, useAudioQueue, useTtsPreference)
├── infrastructure/                   # Terraform IaC
│   └── modules/                      # networking, alb, ecs, iam, dynamodb, kinesis, lambda, cognito, ecr, monitoring
├── docker/
│   ├── producer.Dockerfile           # Multi-stage Maven → Corretto 21
│   ├── api.Dockerfile                # Multi-stage Maven → Corretto 21
│   └── frontend.Dockerfile           # Multi-stage Node → Next.js standalone
├── scripts/
│   ├── deploy.sh                     # Manual deploy: build images → ECR → ECS
│   └── teardown.sh                   # Destroy infrastructure
└── .github/workflows/deploy.yml      # GitHub Actions CI/CD
```

---

## Local Development

### Prerequisites

- Java 21 (Amazon Corretto recommended)
- Node.js 22 LTS
- Maven 3.9+
- AWS CLI configured with credentials that have DynamoDB, Kinesis, Polly, and Bedrock access
- An API-Sports key ([dashboard.api-football.com](https://dashboard.api-football.com))

### Run the Full Stack (Live Data)

```bash
export API_SPORTS_KEY="your-api-sports-key"
./run-live.sh
```

This starts:
- **API** on `http://localhost:8080`
- **Producer** on `http://localhost:8081`
- **Frontend** on `http://localhost:3000`

Press `Ctrl+C` to stop all services.

### Run in Mock Mode (No API Key Needed)

```bash
# Start API with mock fixtures
./mvnw spring-boot:run -pl api -Dspring-boot.run.arguments="--MOCK_MODE=true"

# Start frontend
cd frontend && npm install && npm run dev
```

### Run All Tests

```bash
./run-all-tests.sh
```

Or individually:

```bash
# Java services (API, Lambda, Producer)
./mvnw test -pl api
./mvnw test -pl lambda
./mvnw test -pl producer

# Frontend (Vitest)
cd frontend && npm run test
```

### Infrastructure

```bash
cd infrastructure
terraform init
terraform plan -var-file=environments/dev.tfvars
terraform apply -var-file=environments/dev.tfvars
```

---

## API Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/events/stream` | SSE stream of live events (query: `sport`, `fixtureId`) |
| `GET` | `/api/fixtures?sport=SOCCER` | List live fixtures for a sport |
| `GET` | `/api/summaries/event/{eventId}` | Get AI commentary for a specific event |
| `GET` | `/api/subscriptions/active` | List active SSE subscriptions |
| `GET` | `/api/sports` | List available sport types |
| `POST` | `/api/tts/synthesize` | Convert text to speech via AWS Polly |
| `GET` | `/actuator/health` | Health check (used by ALB) |

---

## Key Domain Concepts

**Unified `SportEvent` model** — the Producer normalizes all sport-specific API payloads into a single generic schema with fields: `sport_type`, `action`, `participants`, `timestamp`. Every downstream component operates on this model, never on raw API responses.

**Broadcast offset / time-travel buffer** — the frontend holds a sliding window of events and renders only those where `(currentTime - eventTimestamp) > userOffset`. The offset is set via a calibration slider. This is pure client-side logic; no server changes needed.

**Dynamic Bedrock prompting** — the Lambda reads `sport_type` from the event and selects a sport-specific prompt template before calling Bedrock. Commentary adapts per sport context.

**Supported sports:** Soccer, Basketball, Football (American), Baseball, Hockey, Formula 1.
