# Vitalidapp

Vitalidapp is a healthcare web application that connects patients with nearby professionals and supports appointment booking, medical-history management and service ratings.

The original product was built by a six-person team. My main contribution was on the frontend, and I also worked as Scrum Master. The current modernization keeps the existing business logic while making the project easier to run, test and present as a portfolio case study.

## Main flows

- Patient and professional accounts
- Search by province and medical specialty
- Appointment date and time selection
- Patient history and professional diagnoses
- Appointment cancellation and professional ratings
- Role-based areas for patients, professionals and administrators

## Stack

- Java 8 compatible source code
- Spring Boot 2.7 and Spring Security
- Thymeleaf and Bootstrap
- Spring Data JPA
- H2 for the zero-configuration demo profile
- MySQL through an optional production-style profile

## Run the demo locally

The default `demo` profile uses an in-memory H2 database, creates sample content and does not require MySQL.

```bash
cd health-service-app
bash mvnw spring-boot:run
```

Open `http://localhost:8181`.

### Demo accounts

| Role | Email | Password |
| --- | --- | --- |
| Patient | `paciente@vitalidapp.com` | `123456` |
| Professional | `profesional@vitalidapp.com` | `123456` |
| Administrator | `admin@admin.com` | `123456` |

The H2 database is reset whenever the application restarts.

## Run with MySQL

Create the database with `health-service-app/script-mysql/healthservicedb.sql`, then start the application with the `mysql` profile:

```bash
cd health-service-app
SPRING_PROFILES_ACTIVE=mysql \
DB_URL='jdbc:mysql://localhost:3306/healthservicedb' \
DB_USERNAME='root' \
DB_PASSWORD='root' \
bash mvnw spring-boot:run
```

## Tests

```bash
cd health-service-app
bash mvnw test
```

The automated smoke test verifies that the Spring context and public home page load with the demo profile.

## Modernization roadmap

- [x] Add a zero-configuration demo database
- [x] Add deterministic demo accounts and sample professionals
- [x] Make packaged image resources portable
- [ ] Correct authorization rules and protect role-specific routes
- [ ] Validate the patient appointment flow end to end
- [ ] Refresh the visual system and responsive layouts
- [ ] Publish a public demo
