# Operations Runbook

This guide explains how to start, stop, restart and fix the Essential Butterfly application. Keep it up to date whenever the deployment changes. See also: [ADR-0004](../adr/0004-postgresql-in-docker.md) and [SECURITY_REVIEW.md](../../SECURITY_REVIEW.md).

## 1. System overview

| Part | What it is | Port |
|---|---|---|
| Frontend | React app (Vite) | 5173 (development) |
| Backend | Spring Boot API, Java 17 | 8080 |
| Database | PostgreSQL 17 in Docker, data in the `postgres-data` volume | 5432 |

The backend image is built by GitHub Actions and pushed to Docker Hub with two tags: `latest` and the commit id (`sha`).

## 2. Start and stop

### Start (development)
1. Start the database. Set the database password in your environment first, then:
   ```
   cd backend
   docker compose up -d
   ```
2. Check that the database is healthy:
   ```
   docker compose ps
   ```
   The `db` service must show `healthy`.
3. Start the backend:
   ```
   cd backend
   ./mvnw spring-boot:run
   ```
4. Start the frontend:
   ```
   cd frontend
   npm install
   npm run dev
   ```
5. Open http://localhost:5173.

### Start the backend from the Docker image
```
docker pull <dockerhub-user>/essential-butterfly:latest
docker run -p 8080:8080 <dockerhub-user>/essential-butterfly:latest
```
The database address and password are passed with environment variables (for example `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Never write real passwords in this file.

### Stop
- Backend or frontend started in a terminal: press `Ctrl + C`.
- Database: `docker compose down` (keeps the data).
- **Do not** use `docker compose down -v` unless you want to delete all data.

### Check that it works
- Backend answers: open the login page in the browser and sign in.
- Database: `docker compose ps` shows `healthy`.

## 3. Restart and recovery

### Backend stopped or crashed
1. Read the last lines of the backend log.
2. Start it again (section 2).
3. If it crashes again right away, check the database (below) and the settings.

### Database will not start or is not healthy
1. Run `docker compose ps` and `docker compose logs db`.
2. Check that the password variable is set and that port 5432 is free.
3. Restart: `docker compose restart db`.
4. The volume `postgres-data` keeps the data. Do not delete it.

### Roll back to the previous version
1. Find the last working commit id on GitHub (Actions tab, green run).
2. Run the image with that tag instead of `latest`:
   ```
   docker run -p 8080:8080 <dockerhub-user>/<image-name>:<commit-id>
   ```
3. Tell the team which version is running and why.

### Restore data
The database has no automatic backup yet (see Planned). To make a manual backup:
```
docker compose exec db pg_dump -U postgres essential_butterfly > backup.sql
```
To restore it into an empty database:
```
docker compose exec -T db psql -U postgres essential_butterfly < backup.sql
```
Do not commit backup files to GitHub. They contain personal data.

## 4. Troubleshooting

| What you see | Likely cause | What to do |
|---|---|---|
| "Unable to connect to the server" in the browser | Backend is not running or the frontend uses a wrong address | Start the backend. Check the address in the frontend code or settings. |
| Browser console shows a CORS error | The frontend runs on a different address than the backend allows | Open the frontend on http://localhost:5173 or update the allowed origin. |
| Backend fails at start with a database error | Database is down, password is wrong or settings are missing | Check `docker compose ps`, the password variable and `application.properties`. |
| Login says "Invalid email or password" for a correct password | Email typed with a different spelling, or the account does not exist | Emails are saved in lower case. Check the database. |
| Admin sees "temporarily locked" (HTTP 423) | 5 wrong passwords. The lock lasts 15 minutes | Wait 15 minutes or use the admin password reset. |
| Password reset email does not arrive | Email sending is not built. The link is printed in the backend console | Copy the link from the backend log. |
| Logged out after a while | 30 minutes of inactivity, or the 24-hour token expired | Log in again. |
| Page shows 401 or 403 after login | Token missing or expired, or a client tries an admin page | Log in again with the correct account type. |
| Intake form refuses a new submission | The client already has an intake with status `NEW` (ADR-0005) | The admin must change the status of the first one. |
| Tests fail only on GitHub | Different environment than your computer | Open the failed run in the Actions tab and read the first error. |

## 5. Incident response

An incident is anything that stops visitors or Casey from using the site, or that may expose personal data.

1. **Notice and report.** Whoever sees the problem writes it in the team chat with the time, what they saw and a screenshot.
2. **Choose a lead.** The team picks one person to coordinate. Others help but do not change things at the same time.
3. **Protect first.** If personal data may be exposed, stop the service or the exposed feature (for example turn the feature off or stop the container) and change any secret that may have leaked.
4. **Find the cause.** Check recent commits, the backend log and the database health.
5. **Fix or roll back.** Prefer a roll back to the last working image (section 3) when the fix is not quick.
6. **Tell the client.** Inform Casey in plain words what happened, what was affected and when it is fixed. If personal data was affected, tell her clearly so she can decide what to do next.
7. **Write it down.** Within a few days add a short note to the team's records: what happened, the cause, how it was fixed and what we change so it does not happen again. If the fix changes a design decision, add an ADR.

## 6. Security reminders
- Never commit passwords, the JWT secret or `.env` files to GitHub. The repository is public.
- Change the default admin password before any real use.
- Do not put personal data (emails, messages) in logs, issues or screenshots shared outside the team.

## 7. Planned (not built yet)
- Separate containers for database, backend and frontend on a private Docker network (Docker Compose).
- Docker secrets for the database password and JWT secret.
- Frontend API address in an environment variable.
- Integration tests that start the containers in GitHub Actions, then publish images and deploy.
- Automatic database backups and a health-check endpoint.
