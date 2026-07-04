# URL Shortener — Product Requirements Document

## 1. Overview

A lightweight URL shortening service built with Spring Boot 3.3 and PostgreSQL. Users submit a long URL and receive a compact 7-character short code. Visiting the short code redirects (HTTP 302) to the original URL.

---

## 2. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Runtime | Java (OpenJDK) | 21 |
| Framework | Spring Boot | 3.3.0 |
| Build tool | Apache Maven | 3.9+ |
| Database | PostgreSQL | via Hibernate / JPA |
| ORM | Hibernate ORM | 6.5.2.Final |
| API docs | Springdoc OpenAPI | 2.6.0 |
| Validation | Jakarta Validation | 3.0.2 |
| Code gen | Project Lombok | latest |
| .env support | spring-dotenv | 4.0.0 |
| Frontend | Plain HTML + Vanilla JS | — |

---

## 3. Architecture

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐
│  Browser  │────▶│ UrlController│────▶│   UrlService  │────▶│  UrlRepo   │
│ (UI/API)  │◀────│  (REST)      │◀────│  (Business)   │◀────│  (JPA)     │
└──────────┘     └──────────────┘     └──────┬───────┘     └─────┬──────┘
                                             │                   │
                                             ▼                   ▼
                                      ┌────────────┐      ┌──────────┐
                                      │ HashService │      │  Postgres │
                                      │ (SHA-256)   │      │ Database  │
                                      └────────────┘      └──────────┘
```

### Layers

1. **Controller** (`com.urlshortener.controller`) — REST endpoints, HTTP request/response handling.
2. **Service** (`com.urlshortener.service`) — Business logic: URL creation, short code generation, collision resolution.
3. **Repository** (`com.urlshortener.repository`) — Spring Data JPA interface for database access.
4. **Model** (`com.urlshortener.model`) — JPA entity mapped to the `urls` table.
5. **DTO** (`com.urlshortener.dto`) — Request/response objects for the API.
6. **Exception** (`com.urlshortener.exception`) — Custom exceptions and global error handling.

---

## 4. Features

### 4.1 URL Shortening (`POST /shorten`)

- **Input:** JSON body with `longUrl` field.
- **Validation:**
  - `longUrl` must not be blank.
  - `longUrl` must start with `http://` or `https://` (enforced via regex `^(https?://).+`).
- **Deduplication:** If the exact same long URL already exists in the database, the existing short code is returned instead of creating a duplicate entry.
- **Output:** JSON response with `shortUrl` — the full shortened URL (e.g. `http://localhost:8080/100680a`).

**Request:**
```json
POST /shorten
Content-Type: application/json

{ "longUrl": "https://example.com/very/long/path" }
```

**Response (200):**
```json
{ "shortUrl": "http://localhost:8080/a1b2c3d" }
```

### 4.2 URL Redirect (`GET /{shortCode}`)

- **Path variable:** 7-character hex string (`[a-f0-9]{7}`) — the short code.
- **Behavior:** Issues an HTTP 302 (Found) redirect to the original long URL.
- **Not found:** Returns HTTP 404 with a JSON error body.

**Request:**
```
GET /a1b2c3d
```

**Response (302):**
```
Location: https://example.com/very/long/path
```

**Response (404):**
```json
{ "error": "Short code not found: a1b2c3d" }
```

### 4.3 Short Code Generation (SHA-256 Hashing)

- Uses **SHA-256** to hash the long URL.
- The first **7 hexadecimal characters** of the hash become the short code.
- If a collision occurs (same short code for a different URL), a **salted iteration** appends an incrementing integer to the input before re-hashing until a unique code is found.
- If the same short code exists but points to the **same** long URL, the existing record is reused (collision = dedup hit).

### 4.4 Web UI

- Single-page HTML interface served at `http://localhost:8080/`.
- Built with **plain HTML, CSS, and vanilla JavaScript** — no framework.
- **Features:**
  - Text input for the long URL with autofocus.
  - "Shorten" button that calls `POST /shorten`.
  - "Enter" key support as shortcut.
  - Loading state on the button while the request is in flight.
  - Display of the generated short URL as a clickable link.
  - **"Copy" button** to copy the short URL to clipboard.
  - "Copied!" confirmation state with auto-revert after 2 seconds.
  - Error display for validation failures and network issues.
  - Warm, neutral color palette (`#ece0d1`, `#dbc1ac`, `#967259`, `#634832`, `#38220f`).

### 4.5 Error Handling

All errors return a consistent JSON structure:
```json
{ "error": "<description>" }
```

| Scenario | HTTP Status | Error Message |
|----------|------------|---------------|
| Short code not found | 404 | `"Short code not found: {code}"` |
| Validation failure (blank URL) | 400 | `"longUrl: longUrl must not be blank"` |
| Validation failure (bad format) | 400 | `"longUrl: longUrl must start with http:// or https://"` |
| Multiple validation errors | 400 | Semi-colon separated field messages |
| Unhandled server errors | 500 | `"Internal server error"` |

### 4.6 API Documentation (OpenAPI / Swagger)

- Auto-generated API docs via Springdoc OpenAPI.
- Available at `http://localhost:8080/api-docs` (JSON).
- Interactive Swagger UI at `http://localhost:8080/swagger-ui.html`.

### 4.7 Environment Variable Configuration

Sensitive configuration (database credentials) is externalized:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/urlshortener` | JDBC connection URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `changeme` | Database password |

- A `.env` file in the project root is loaded automatically at startup via `spring-dotenv` (`.env` is gitignored).
- `.env.example` is committed as a template for other developers.

---

## 5. Database Schema

### Table: `urls`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `BIGINT` | Primary key, auto-increment |
| `short_code` | `VARCHAR(8)` | NOT NULL, UNIQUE |
| `long_url` | `TEXT` | NOT NULL |
| `created_at` | `TIMESTAMP` | NOT NULL, immutable |

- DDL auto-update is enabled (`spring.jpa.hibernate.ddl-auto=update`), so the table is created automatically on first startup.
- SQL logging is enabled for development visibility.

---

## 6. Project Structure

```
URL-SHORTENER/
├── .env                  # Local env vars (gitignored)
├── .env.example          # Template for other developers
├── .gitignore
├── pom.xml               # Maven build file
├── docs/
│   └── prd.md            # This document
└── src/
    └── main/
        ├── java/com/urlshortener/
        │   ├── UrlShortenerApplication.java    # Entry point
        │   ├── controller/
        │   │   └── UrlController.java          # REST endpoints
        │   ├── dto/
        │   │   ├── ShortenRequest.java         # Input DTO
        │   │   └── ShortenResponse.java        # Output DTO
        │   ├── exception/
        │   │   ├── GlobalExceptionHandler.java # @RestControllerAdvice
        │   │   └── ShortCodeNotFoundException.java
        │   ├── model/
        │   │   └── Url.java                    # JPA entity
        │   ├── repository/
        │   │   └── UrlRepository.java          # JPA repository
        │   └── service/
        │       ├── HashService.java            # SHA-256 hashing
        │       └── UrlService.java             # Business logic
        └── resources/
            ├── application.properties          # Config
            └── static/
                └── index.html                  # Web UI
```

---

## 7. Build & Run

### Prerequisites

- Java 21+
- Apache Maven 3.9+
- PostgreSQL (optional — can use a remote instance)

### Commands

```bash
# Build
mvn clean compile

# Run
mvn spring-boot:run

# Package for production
mvn clean package
java -jar target/url-shortener-0.0.1-SNAPSHOT.jar
```

### Configuration

1. Copy `.env.example` to `.env` and fill in your database credentials (or set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` as environment variables).
2. The app starts on port 8080 by default (configure via `server.port` env var).

---

## 8. API Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| `HEAD` | `/ping` | Health check (returns 200 OK) |
| `POST` | `/shorten` | Create a shortened URL |
| `GET` | `/{shortCode}` | Redirect to the original URL |
| `GET` | `/swagger-ui.html` | Swagger UI (interactive API docs) |
| `GET` | `/api-docs` | OpenAPI JSON spec |
| `GET` | `/` | Web UI |

---

## 9. Dependencies (Maven)

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | REST API + embedded Tomcat |
| `spring-boot-starter-data-jpa` | Hibernate ORM + JPA |
| `spring-boot-starter-validation` | Jakarta Bean Validation |
| `postgresql` | PostgreSQL JDBC driver |
| `lombok` | Boilerplate code reduction |
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI 3 + Swagger UI |
| `spring-dotenv` | `.env` file loading |
| `spring-boot-starter-test` | Testing framework |

---

## 10. Limitations & Future Considerations

- **Short code length:** Fixed at 7 hex characters (≈ 16M combinations). Could be made configurable for scale.
- **No analytics:** No click tracking, expiration, or rate limiting.
- **No user accounts:** Anyone can shorten URLs; no ownership or deletion.
- **Base URL is hardcoded:** The response generates `http://localhost:8080/...` — needs to be configurable per deployment.
- **No tests:** The `src/test` directory does not exist; no unit or integration tests are written.
- **In-memory fallback:** No H2 or embedded database profile for development without PostgreSQL.
