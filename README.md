# LaunchFleet

LaunchFleet is a LaunchDarkly-inspired feature management product for creating flags, configuring environments, targeting audiences, managing progressive rollouts, approving changes, and measuring experiments.

## Technology stack

- React 19 + TypeScript, TanStack Router, TanStack Query, Vite, Tailwind CSS
- Spring Boot 4 + Java 21 + Gradle
- MongoDB with Spring Data MongoDB
- Cookie-backed Spring Security sessions with CSRF protection
- Bun 1.4+ for the JavaScript workspace

## Product capabilities

1. Feature flag management: create, edit, configure, toggle, and retire boolean or multivariate flags.
2. Environment configuration: manage development, staging, and production environments independently.
3. Segment and user targeting: define attribute-based segments and targeting rules.
4. Progressive rollouts: configure percentage allocations per environment.
5. Approval workflows: submit, approve, reject, schedule, and cancel flag changes.
6. Experimentation: start experiments, assign users, record conversion events, and compare metrics.
7. Change history: inspect activity records for flag, environment, approval, and experiment changes.
8. Workspace administration: manage projects, members, profile settings, and SDK API keys.

## Project structure

```text
frontend/src/features/       Feature API clients, hooks, and types
frontend/src/pages/          Product screens and forms
frontend/src/routes/         TanStack Router route tree
backend/src/main/java/...    Feature-oriented Spring controllers and services
backend/src/main/resources/  Environment-driven application configuration
backend/src/test/             Domain, application, security, and API tests
setup.sh                      Dependency, MongoDB, environment, and seed setup
run.sh                        Seeded backend/frontend start orchestration
hackerrank.yml                Authoritative install/run commands
```

## Prerequisites

- Bun 1.4 or newer
- Java 21 or newer
- MongoDB running as a single-node replica set at `localhost:27018`
- `mongosh` available on `PATH`

Set `MONGODB_URI` to use another MongoDB instance. The backend runs on port `8000`; the Vite frontend runs on port `3000` and proxies `/api` to the backend.

## Install and run

```bash
bun install
bash setup.sh --seed
bash run.sh
```

The full run flow resets application collections to the deterministic demo baseline before starting both servers. Health is available at `http://localhost:8000/actuator/health` and includes MongoDB connectivity details.

## Seeded access

Sign in at `http://localhost:3000/login` with:

- Email: `admin@launchfleet.dev`
- Password: `launchfleet-demo`
- Project: `default`

The seeded user is an administrator for the default project. Seeding clears application collections before recreating the user, project, memberships, environments, and SDK credentials.

## Useful commands

```bash
bun run build                         # Frontend production build
backend/gradlew -p backend build      # Backend compile and tests
backend/gradlew -p backend seed       # Explicit local seed reset
curl http://localhost:8000/actuator/health
```

## Validation and submission

The repository contract is defined by `GUIDELINES.md` and `REQUIREMENTS.md`. The validation procedure is in `skills/validate/SKILL.md`. Before handover, run the exact commands in `hackerrank.yml`, verify live API and MongoDB persistence, copy the external transcript log into `transcripts/`, and create the HackerRank archive from the clean tracked tree.
