# Google Calendar for appointments (UC-11)

When the administrator accepts an appointment, the backend adds an event to the
administrator's Google Calendar (with pop-up and e-mail reminders), so Google
notifies them. Nothing else from Google is used.

The backend reads three values from Docker secrets. If they are empty the
appointment is still confirmed and the Manage Appointments page says that no
calendar event was created, so the rest of the system works without them.

| Secret file (in `secrets/`)             | Environment variable             |
|-----------------------------------------|----------------------------------|
| `google_calendar_client_id.txt`         | `GOOGLE_CALENDAR_CLIENT_ID`      |
| `google_calendar_client_secret.txt`     | `GOOGLE_CALENDAR_CLIENT_SECRET`  |
| `google_calendar_refresh_token.txt`     | `GOOGLE_CALENDAR_REFRESH_TOKEN`  |

Optional (application.properties / environment): `GOOGLE_CALENDAR_ID`
(default `primary`), `APPOINTMENTS_TIME_ZONE` (default `America/Toronto`),
`APPOINTMENTS_BOOKING_WINDOW_DAYS` (default `7`).

## Getting the three values (about 10 minutes, free)

1. Open https://console.cloud.google.com and create a project (any name).
2. **APIs & Services > Library**: search for **Google Calendar API** and Enable it.
3. **APIs & Services > OAuth consent screen**: choose **External**, fill the
   required names/e-mails, add the administrator's Google account under
   **Test users**, save. Then press **Publish app** (status "In production").
   Without this step Google expires the refresh token after 7 days.
4. **APIs & Services > Credentials > Create credentials > OAuth client ID**:
   type **Web application**; under *Authorized redirect URIs* add
   `https://developers.google.com/oauthplayground`. Copy the **Client ID** and
   **Client secret**.
5. Open https://developers.google.com/oauthplayground, click the gear icon,
   tick **Use your own OAuth credentials**, paste the Client ID and secret.
6. In Step 1 type the scope `https://www.googleapis.com/auth/calendar.events`
   and press **Authorize APIs**. Sign in as the administrator's Google account
   (click *Advanced > Go to ... (unsafe)* if Google warns that the app is
   unverified; it is your own app) and allow access.
7. In Step 2 press **Exchange authorization code for tokens** and copy the
   **Refresh token**.
8. Put each value on one line, with no quotes and no spaces, in its secret file,
   then run `docker compose up -d --build`.
