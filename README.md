# Enterprise FAQ Chatbot (RAG Backend)

### Overview
A production-ready **Spring Boot 3 + Couchbase 8.0 + Python Embedding Service** backend implementing vector search–based FAQ retrieval.

---

## Architecture Overview

```

User → /api/ask → Spring Boot → Python Embedding Service → Couchbase FTS Vector Search
↘→ KV fetch (faq document)

```

### Key Technologies
- **Java 21**, **Spring Boot 3.3+**
- **Couchbase Java SDK 3.9.2**
- **Docker & Compose**
- **Vector Search + FTS**
- **Python microservice** for embeddings (HuggingFace model)

---

## Project Structure

```

src/
├── main/java/io/github/jdeeplearn/rag
│   ├── config/CouchbaseConfig.java
│   ├── controller/AskController.java
│   ├── dto/{AskRequest,FaqResponse}.java
│   ├── repository/FaqRepository.java
│   └── service/FaqService.java
└── resources/
├── application.yml
└── logback-spring.xml

````

---

## Running Locally

### Build and run (Docker Compose)

```bash
docker compose up --build
```

This launches:

* Couchbase 8.0 (on ports 8091–8096)
* Python embedding service (port 8000)
* Spring Boot app (port 8080)

---

## Example Query

```bash
curl -s -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"How can I reset my password?"}' | jq
```

### Sample Response

```json
{
  "answer": "Go to Settings > Account > Reset Password.",
  "image": "https://example.com/images/reset.png",
  "link": "https://help.example.com/reset-password"
}
```
 
---

## Build Locally (no Docker)

```bash
./mvnw clean package
java -jar target/faq-api.jar
```

---