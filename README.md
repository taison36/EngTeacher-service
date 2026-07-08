# English Teacher Backend

When learning English, I write down unfamiliar words and phrases but rarely review them.
This app solves that by automatically generating contextual exercises and tracking my progress.

How it works?
  Add phrases you want to learn
  AI generates practice exercises for each phrase
  Practice by using phrases in natural sentences
  AI evaluates your answers and tracks your progress
  Exercises adapt based on your performance

## Setup

Create a `.env` file in the repository root (next to `docker-compose.yml`) with these keys:

```env
# LLM inference via OpenRouter (create a key at https://openrouter.ai/settings/keys)
OPENROUTER_API_KEY=sk-or-...

# JWT signing secret, e.g. generated with: openssl rand -hex 64
JWT_SECRET=...

# MongoDB credentials (free choice, used to initialize the container)
MONGO_ROOT_USERNAME=admin
MONGO_ROOT_PASSWORD=...
MONGO_DATABASE=engteacher

# Langfuse tracing (Langfuse project settings -> API keys)
# Endpoint: the OTLP traces endpoint of your Langfuse instance
# Auth header: base64("<public key>:<secret key>")
OTEL_EXPORTER_OTLP_ENDPOINT=https://cloud.langfuse.com/api/public/otel/v1/traces
LANGFUSE_AUTH_HEADER=...
```

All keys are required — the service fails to start if one is missing.
When running the service outside Docker, additionally set `MONGO_URL=localhost:27017`
(inside docker-compose it is set automatically).

The LLM model is selected in `src/main/resources/application.yml`
(`spring.ai.openai.chat.options.model`).

## Run

```bash
docker compose up --build
```

| Component     | URL                   |
|---------------|-----------------------|
| Client        | http://localhost:5173 |
| Backend API   | http://localhost:8080 |
| Mongo Express | http://localhost:8081 |
