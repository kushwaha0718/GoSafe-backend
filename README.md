<div align="center">

# GoSafe — Backend

**RESTful API for Safe Transit Planning**

Spring Boot microservice powering route generation, safety analysis, user authentication, and emergency contact management with zero external paid APIs.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

[Features](#-features) • [Tech Stack](#-tech-stack) • [Quick Start](#-quick-start) • [API Docs](#-api-documentation) • [Docker](#-docker-deployment)

</div>

---

## Features

### Core Services
- **Multi-Route Generation** — Generates 1–3 genuinely different paths using perpendicular via-waypoint strategy
- **Safety Scoring** — Algorithms factoring lighting coverage, crowd density, CCTV availability, emergency access, and incident history
- **Real-Time Geocoding** — Powered by Nominatim (OpenStreetMap) — no API keys, no rate limits beyond fair use
- **POI Integration** — Fetches shops, restaurants, hospitals, ATMs along routes via Overpass API

### Authentication & Security
- **JWT Authentication** — JJWT 0.12 with HS512 signing, 7-day expiration
- **BCrypt Password Hashing** — 12 rounds, salted per-user
- **CORS Configuration** — Whitelisted origins for `localhost:5173`,`localhost:4173` and `https.gosafe.onrender.com`
- **Request Validation** — Jakarta Bean Validation with custom error messages

### Data Management
- **JPA/Hibernate ORM** — Zero raw SQL, automatic DDL updates
- **File Upload Support** — Multipart handling for user avatars (3MB limit)
- **Route History** — Stores last 20 searches per user with timestamps
- **Saved Routes** — JSON storage for complete route objects with waypoints

---

## 🛠 Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Framework** | Spring Boot 3.2.3 | REST API, dependency injection |
| **Language** | Java 17 | LTS version, works with 11/17/21 |
| **ORM** | Spring Data JPA + Hibernate | Database abstraction, entity mapping |
| **Database** | MySQL 8.0 | Persistent storage (UTF-8 charset) |
| **Security** | Spring Security Crypto | BCrypt only — no full Spring Security |
| **Validation** | Jakarta Bean Validation | `@Valid` request bodies |
| **JWT** | JJWT 0.12.5 | Stateless auth tokens |
| **HTTP Client** | RestTemplate | Calls to Nominatim/OSRM/Overpass |
| **Build Tool** | Maven 3.9 | Dependency management, packaging |
| **Container** | Docker + Alpine JRE | Multi-stage build, 150MB image |

---

## 🚀 Quick Start

### Prerequisites

```bash
java --version      # 17 or 21
mysql --version     # 8.0+
mvn --version       # 3.9+ (or use ./mvnw)
```

### 1. Configure Database

Edit **`src/main/resources/application.properties`**:

```properties
spring.datasource.password=your_mysql_root_password

# Change this — must be 32+ chars for HS512
gosafe.jwt.secret=replace_with_long_random_string_min_32_characters
```

The database `gosafe` will be **created automatically** on first run.

### 2. Run the Server

```bash
# Using Maven wrapper (no Maven install needed)
./mvnw spring-boot:run          # Mac/Linux
mvnw.cmd spring-boot:run        # Windows

# Or build and run JAR
./mvnw clean package -DskipTests
java -jar target/gosafe-backend-1.0.0.jar
```

**Output:**
```
🛡  GoSafe API → http://localhost:3001
```

### 3. Test the API

```bash
curl http://localhost:3001/api/health
# {"status":"ok"}
```

---

## Project Structure

```
backend/
├── src/main/java/com/gosafe/
│   ├── GoSafeApplication.java         ← main() entry point
│   │
│   ├── config/
│   │   ├── AppConfig.java             ← CORS, JWT filter registration
│   │   ├── StaticResourceConfig.java  ← Serves /uploads/**
│   │   └── GlobalExceptionHandler.java ← 401/422/500 responses
│   │
│   ├── security/
│   │   ├── JwtUtil.java               ← sign() + verify() tokens
│   │   └── JwtFilter.java             ← Intercepts every request
│   │
│   ├── entity/                        ← JPA-mapped database tables
│   │   ├── User.java
│   │   ├── RouteHistory.java
│   │   ├── SavedRoute.java
│   │   └── EmergencyContact.java
│   │
│   ├── repository/                    ← Spring Data interfaces
│   │   ├── UserRepository.java
│   │   ├── RouteHistoryRepository.java
│   │   ├── SavedRouteRepository.java
│   │   └── EmergencyContactRepository.java
│   │
│   ├── dto/                           ← Request/response POJOs
│   │   ├── SignupRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RouteSearchRequest.java
│   │   └── AddContactRequest.java
│   │
│   ├── service/
│   │   └── RouteService.java          ← OpenStreetMap API integration
│   │
│   └── controller/
│       ├── AuthController.java        ← /api/auth/*
│       ├── ContactsController.java    ← /api/contacts/*
│       └── RouteController.java       ← /api/routes/*, /api/stations
│
├── src/main/resources/
│   └── application.properties         ← Database, JWT, upload config
│
├── uploads/                           ← Avatar storage (mount as volume)
├── pom.xml                            ← Maven dependencies
├── Dockerfile                         ← Multi-stage Docker build
└── mvnw / mvnw.cmd                    ← Maven wrapper scripts
```

---

## API Documentation

Base URL: `https://gosafe-server.onrender.com/api`

### Authentication

#### POST `/auth/signup`
Create a new user account.

**Request:**
```json
{
  "name": "Priya Sharma",
  "email": "priya@example.com",
  "password": "secure123"
}
```

**Response:** `201 Created`
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "user": {
    "id": 1,
    "name": "Priya Sharma",
    "email": "priya@example.com",
    "avatar_url": null,
    "city": null,
    "phone": null,
    "role": "user"
  }
}
```

**Validation:**
- Name: 2–80 chars, letters/spaces/hyphens only
- Email: Valid format
- Password: 6+ chars, must contain letter + number

---

#### POST `/auth/login`
Authenticate existing user.

**Request:**
```json
{
  "email": "priya@example.com",
  "password": "secure123"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "user": { ... }
}
```

---

#### GET `/auth/me`
Get current user profile.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "user": {
    "id": 1,
    "name": "Priya Sharma",
    "email": "priya@example.com",
    "avatar_url": "/uploads/avatar_1708345678901.jpg",
    "city": "Mumbai",
    "phone": "+919876543210",
    "role": "user",
    "created_at": "2024-02-19T10:30:00"
  }
}
```

---

#### PATCH `/auth/profile`
Update user profile (multipart form).

**Headers:**
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**Form Fields:**
- `name` (optional)
- `city` (optional)
- `phone` (optional)
- `avatar` (file, optional, max 3MB, images only)

**Response:** `200 OK`
```json
{
  "user": { ... }
}
```

---

### Routes & Stations

#### POST `/routes/search`
Generate routes between two locations.

**Request:**
```json
{
  "origin": "Marine Drive Mumbai",
  "destination": "Gateway of India Mumbai"
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "origin": "Marine Drive Mumbai",
  "destination": "Gateway of India Mumbai",
  "routes": [
    {
      "id": "route-0-1708345678901",
      "name": "Fastest Route",
      "description": "Shortest travel time",
      "safetyScore": 87,
      "safetyFactors": [
        { "name": "Lighting Coverage", "score": 96 },
        { "name": "Crowd Density", "score": 90 },
        ...
      ],
      "duration": "8 min",
      "distance": "2.3 km",
      "durationSecs": 480,
      "transfers": 0,
      "totalShops": 18,
      "brands": ["McDonald's", "Cafe Coffee Day", ...],
      "badges": ["Recommended", "Fast"],
      "waypoints": [
        { "lat": 18.9432, "lng": 72.8236 },
        ...
      ],
      "stops": [
        { "lat": 18.9412, "lng": 72.8254, "name": "Marine Drive" }
      ],
      "shops": [
        {
          "name": "Starbucks",
          "category": "CAFE",
          "icon": "☕",
          "color": "#6b3a2a",
          "lat": 18.9428,
          "lng": 72.8243,
          "station": "Churchgate, Mumbai"
        },
        ...
      ],
      "originStation": "Marine Drive",
      "destStation": "Gateway of India"
    }
  ]
}
```

**Algorithm:**
1. Geocode origin + destination via Nominatim
2. Request 4 routes from OSRM:
    - Direct fastest path
    - Via waypoint 15% perpendicular-left
    - Via waypoint 15% perpendicular-right
    - Via waypoint 25% perpendicular-right
3. Deduplicate (routes within 60s duration)
4. Fetch shops along each route from Overpass API
5. Score safety based on urban density, distance
6. Label routes (Fastest, Shortest, Scenic, Alternate)
7. Sort by safety score descending

---

#### GET `/stations?q=<query>&lat=<lat>&lng=<lng>`
Autocomplete place search.

**Query Params:**
- `q` — search term (min 2 chars)
- `lat`, `lng` — optional, biases results near coordinates

**Response:** `200 OK`
```json
{
  "stations": [
    {
      "id": "123456",
      "name": "Chhatrapati Shivaji Terminus, Mumbai",
      "sub": "Maharashtra",
      "lat": 18.9398,
      "lng": 72.8355,
      "type": "station",
      "line": "station"
    },
    ...
  ]
}
```

---

### Emergency Contacts

#### GET `/contacts`
List user's emergency contacts.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "contacts": [
    {
      "id": 1,
      "name": "Rahul Sharma",
      "phone": "+919876543210",
      "relation": "Brother"
    }
  ]
}
```

---

#### POST `/contacts`
Add new emergency contact (max 5).

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "name": "Anita Desai",
  "phone": "+919123456789",
  "relation": "Mother"
}
```

**Response:** `201 Created`
```json
{
  "contact": {
    "id": 2,
    "name": "Anita Desai",
    "phone": "+919123456789",
    "relation": "Mother"
  }
}
```

---

#### DELETE `/contacts/:id`
Remove emergency contact.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:** `200 OK`
```json
{
  "message": "Contact removed."
}
```

---

### Route History & Saved Routes

#### GET `/auth/history`
Last 20 route searches.

**Response:** `200 OK`
```json
{
  "history": [
    {
      "id": 5,
      "origin": "Marine Drive",
      "destination": "Gateway of India",
      "route_name": "Fastest Route",
      "distance": "2.3 km",
      "duration": "8 min",
      "safety_score": 87,
      "searched_at": "2024-02-19T14:30:00"
    }
  ]
}
```

---

#### POST `/auth/saved-routes`
Save a route for later.

**Request:**
```json
{
  "origin": "CST",
  "destination": "Juhu Beach",
  "route_name": "Scenic Route",
  "route_data": { ... },
  "label": "Weekend Trip"
}
```

**Response:** `201 Created`

---

#### GET `/auth/saved-routes`
List saved routes.

**Response:** `200 OK`
```json
{
  "routes": [
    {
      "id": 3,
      "origin": "CST",
      "destination": "Juhu Beach",
      "route_name": "Scenic Route",
      "label": "Weekend Trip",
      "saved_at": "2024-02-18T09:00:00"
    }
  ]
}
```

---

## Database Schema

### `users`
```sql
id            INT PRIMARY KEY AUTO_INCREMENT
name          VARCHAR(120)
email         VARCHAR(180) UNIQUE
password_hash VARCHAR(255)
phone         VARCHAR(20)
city          VARCHAR(100)
avatar_url    VARCHAR(500)
role          VARCHAR(20) DEFAULT 'user'
is_verified   TINYINT(1) DEFAULT 0
created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

### `route_history`
```sql
id           INT PRIMARY KEY AUTO_INCREMENT
user_id      INT, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
origin       VARCHAR(300)
destination  VARCHAR(300)
route_name   VARCHAR(120)
distance     VARCHAR(30)
duration     VARCHAR(30)
safety_score INT
searched_at  DATETIME DEFAULT CURRENT_TIMESTAMP
```

### `saved_routes`
```sql
id          INT PRIMARY KEY AUTO_INCREMENT
user_id     INT, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
origin      VARCHAR(300)
destination VARCHAR(300)
route_name  VARCHAR(120)
route_data  JSON
label       VARCHAR(100)
saved_at    DATETIME DEFAULT CURRENT_TIMESTAMP
```

### `emergency_contacts`
```sql
id         INT PRIMARY KEY AUTO_INCREMENT
user_id    INT, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
name       VARCHAR(120)
phone      VARCHAR(25)
relation   VARCHAR(60)
created_at DATETIME DEFAULT CURRENT_TIMESTAMP
```

**All tables use:**
- Engine: InnoDB
- Charset: UTF-8 (`utf8mb4`)
- Collation: `utf8mb4_unicode_ci`

---

## Docker Deployment

### Build Image

```bash
cd backend
docker build -t gosafe-backend .
```

**Multi-stage build:**
- Stage 1: Maven + JDK 17 (builds JAR)
- Stage 2: Eclipse Temurin JRE 17 Alpine (runtime, 150MB)

### Run Container

```bash
docker run -p 3001:3001 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://host.docker.internal:3306/gosafe?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e SPRING_DATASOURCE_PASSWORD="yourpassword" \
  -e GOSAFE_JWT_SECRET="your_32_char_secret" \
  -v gosafe-uploads:/app/uploads \
  gosafe-backend
```

### Docker Compose (Backend + MySQL)

**Root directory `docker-compose.yml`:**

```bash
# Copy .env.example to .env and fill in secrets
cp .env.example .env

# Start everything
docker compose up -d

# View logs
docker compose logs -f backend

# Stop everything
docker compose down
```

**Services:**
- `db` — MySQL 8.0 on port 3306
- `backend` — GoSafe API on port 3001

**Volumes:**
- `gosafe-db-data` — MySQL data
- `gosafe-uploads` — Avatar images

---

## 🔧 Configuration

### Environment Variables

All `application.properties` values can be overridden:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:mysql://...
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=secret

# JWT
GOSAFE_JWT_SECRET=your_secret_here
GOSAFE_JWT_EXPIRATION=604800000  # 7 days in ms

# File uploads
GOSAFE_UPLOAD_DIR=/app/uploads
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=3MB

# Logging
LOGGING_LEVEL_COM_GOSAFE=DEBUG
```

### CORS Origins

Edit **`AppConfig.java`** to add production domains:

```java
config.setAllowedOrigins(List.of(
    "http://localhost:5173",
    "http://localhost:4173",
    "https://gosafe.yourdomain.com"
));
```

---

## 🧪 Testing

### Health Check

```bash
curl http://localhost:3001/api/health
```

### Create Test User

```bash
curl -X POST http://localhost:3001/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "test123"
  }'
```

### Search Routes

```bash
TOKEN="eyJhbGciOiJIUzUxMiJ9..."  # from login response

curl -X POST http://localhost:3001/api/routes/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "origin": "Mumbai Central",
    "destination": "Bandra"
  }'
```


---

## Security Best Practices

**Implemented:**
- Passwords stored as BCrypt hashes (12 rounds)
- JWT tokens with expiration (7 days)
- CORS whitelist (no `*` wildcard)
- SQL injection prevention via JPA/Hibernate
- Input validation on all endpoints
- File upload MIME type checking

**Production Additions Needed:**
- HTTPS only (terminate SSL at load balancer)
- Rate limiting (use Spring `bucket4j` or Nginx)
- Refresh tokens (separate endpoint for token renewal)
- CSRF protection if using cookie-based auth
- Helmet.js equivalent for Spring (security headers)
- Database connection encryption (SSL)



<div align="center">


</div>
