# Operations Runbook

This guide explains how to start, stop, restart and fix the Essential Butterfly application. Keep it up to date whenever the deployment changes. See also: [ADR-0004](../adr/0004-postgresql-in-docker.md), [ADR-0013](../adr/0013-docker-compose-containers-network-secrets.md) and [SECURITY_REVIEW.md](../../SECURITY_REVIEW.md).

All commands below are run from the **project root** (the folder that contains `docker-compose.yml`), unless a different folder is shown.

## 1. System overview

| Part | What it is | Container | Port on the computer |
|---|---|---|---|
| Frontend | React app served by nginx | `essential-butterfly-frontend` | 5173 |
| Backend | Spring Boot API, Java 17 | `essential-butterfly-backend` | 8080 |
| Database | PostgreSQL 17, data in the `postgres-data` volume | `essential-butterfly-db` | none (private network) |

The database is on a private Docker network. Only the frontend and the backend are reachable from the computer.

GitHub Actions builds the images, tests them with Docker Compose and pushes them to Docker Hub with two tags: `latest` and the commit id (`sha`).

- Backend image: `tirth167/essential-butterfly`
- Frontend image: `tirth167/essential-butterfly-frontend`

## 2. Secrets

The application needs six secret files in a folder named `secrets/` in the project root. This folder must never be committed to GitHub.

| File | What it holds |
|---|---|
| `secrets/postgres_password.txt` | Database password |
| `secrets/gmail_username.txt` | Gmail address that sends newsletters |
| `secrets/gmail_app_password.txt` | Gmail app password for that address |
| `secrets/jwt_secret.txt` | Long random secret that signs login tokens |
| `secrets/admin_email.txt` | Email of the admin account created on first start |
| `secrets/admin_password.txt` | Password of that admin account |

Each file has only the value, with no extra spaces or quotes. Without the Gmail files the site still runs, but newsletters cannot be sent. Without the admin files the backend still starts, but no admin account is created, so nobody can sign in to the admin area. Without `jwt_secret.txt` the backend will not start. If the secret is ever leaked, create a new one and restart the backend. Everyone will have to log in again.

## 3. Start and stop

### Start everything
```
docker compose up -d --build
```
Then check the containers:
```
docker compose ps
```
The `db` service must show `healthy`. Open http://localhost:5173.

### Start for development
- Database only: `docker compose up -d db`
- Backend: `cd essential-butterfly-backend` then `./mvnw spring-boot:run`
- Frontend: `cd frontend`, `npm install`, then `npm run dev`

### Stop
- Stop all containers and keep the data: `docker compose down`
- **Do not** use `docker compose down -v` unless you want to delete all data.
- A backend or frontend started in a terminal: press `Ctrl + C`.

### Check that it works
- Frontend: http://localhost:5173 shows the home page.
- Backend: http://localhost:8080/api/features returns a list of features.
- Database: `docker compose ps` shows `healthy`.

## 4. Restart and recovery

### One service stopped or crashed
1. `docker compose ps` to see which one.
2. `docker compose logs backend` (or `db`, `frontend`) and read the last lines.
3. Restart it: `docker compose restart backend`.
4. If it crashes again right away, check the database (below) and the secret files.

### Database will not start or is not healthy
1. `docker compose logs db`.
2. Check that `secrets/postgres_password.txt` exists.
3. If the password was changed after the first start, the old password is still saved in the volume. Use the old password, or restore from a backup into a new volume.
4. Restart: `docker compose restart db`.
5. The volume `postgres-data` keeps the data. Do not delete it.

### Roll back to the previous version
1. Find the last working commit id on GitHub (Actions tab, green run).
2. Pull the images with that tag:
   ```
   docker pull tirth167/essential-butterfly:<commit-id>
   docker pull tirth167/essential-butterfly-frontend:<commit-id>
   ```
3. Start the database with Compose (`docker compose up -d db`) and run the old images on the same network, or check out that commit in Git and run `docker compose up -d --build`.
4. Tell the team which version is running and why.

### Back up and restore data
The database has no automatic backup yet (see Planned). To make a manual backup:
```
docker compose exec db pg_dump -U postgres essential_butterfly > backup.sql
```
To restore it into an empty database:
```
docker compose exec -T db psql -U postgres essential_butterfly < backup.sql
```
Do not commit backup files to GitHub. They contain personal data.

## 5. Troubleshooting

| What you see | Likely cause | What to do |
|---|---|---|
| "Unable to connect to the server" in the browser | Backend is not running or the frontend uses a wrong address | Run `docker compose ps`. Start the backend. The address is set at build time with `VITE_API_BASE_URL`. |
| Browser console shows a CORS error | The frontend runs on an address the backend does not allow | Open the frontend on http://localhost:5173. Other addresses must be added in `SecurityConfig`. |
| Backend fails at start with a database error | Database is down, or the password in `secrets/postgres_password.txt` is wrong | Check `docker compose ps` and `docker compose logs backend`. |
| Compose says a secret file is missing | A file in `secrets/` does not exist | Create the three files in section 2. |
| Login says "Invalid email or password" for a correct password | Email typed with a different spelling, or the account does not exist | Emails are saved in lower case. Check the database. |
| Admin sees "temporarily locked" (HTTP 423) | 5 wrong passwords. The lock lasts 15 minutes | Wait 15 minutes or use the admin password reset. |
| Password reset email does not arrive | Reset links are not emailed yet. The link is printed in the backend log | Run `docker compose logs backend` and copy the link. It works for 30 minutes. |
| Newsletter send fails with "Gmail is not configured" | The Gmail secret files are missing or wrong | Check the two Gmail files in `secrets/`, then restart the backend. |
| Some newsletter emails show as failed | Wrong address or Gmail limit | Open the newsletter details and use "retry failed". |
| Logged out after a while | 30 minutes of inactivity, or the 24-hour token expired | Log in again. |
| Page shows 401 or 403 after login | Token missing or expired, or a client tries an admin page | Log in again with the correct account type. |
| Intake form refuses a new submission | The client already has an intake that is `SUBMITTED` or `IN_PROGRESS` (ADR-0005) | Casey must accept or reject the first one. |
| A public page shows "feature unavailable" | The admin turned that feature off | Turn it on in the admin feature settings. |
| Tests fail only on GitHub | Different environment than your computer | Open the failed run in the Actions tab and read the first error. |

## 6. Incident response

An incident is anything that stops visitors or Casey from using the site, or that may expose personal data.

1. **Notice and report.** Whoever sees the problem writes it in the team chat with the time, what they saw and a screenshot.
2. **Choose a lead.** The team picks one person to coordinate. Others help but do not change things at the same time.
3. **Protect first.** If personal data may be exposed, stop the service or the exposed feature (turn the feature off in the admin settings or stop the container) and change any secret that may have leaked.
4. **Find the cause.** Check recent commits, `docker compose logs` and the database health.
5. **Fix or roll back.** Prefer a roll back to the last working image (section 4) when the fix is not quick.
6. **Tell the client.** Inform Casey in plain words what happened, what was affected and when it is fixed. If personal data was affected, tell her clearly so she can decide what to do next.
7. **Write it down.** Within a few days add a short note to the team's records: what happened, the cause, how it was fixed and what we change so it does not happen again. If the fix changes a design decision, add an ADR.

## 7. Security reminders
- Never commit passwords, the JWT secret, `.env` files or the `secrets/` folder to GitHub. The repository is public.
- If a secret is committed by mistake, change it at once. Removing the file from Git is not enough, because the old value stays in the history.
- Change the default admin password before any real use.
- Do not put personal data (emails, messages) in logs, issues or screenshots shared outside the team.

## 8. Planned (not built yet)
- Move the JWT signing key and the default admin account out of the source code and into secrets.
- Integration tests that call the API in the running containers, and an automatic deploy step after publishing.
- Automatic database backups and a health-check endpoint.
- Send password reset links by email.
