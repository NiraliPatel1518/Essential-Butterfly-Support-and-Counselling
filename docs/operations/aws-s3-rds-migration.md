# Moving photos to Amazon S3 and the database to Amazon RDS

QAP feedback: *use Amazon S3 for photo and media storage* and *migrate the
database to Amazon RDS*. Professor: *EBS is tied to the EC2 instance; what
happens if it fails?* After this guide the EC2 instance keeps **no data**:
photos are in S3, data is in RDS (with automatic daily backups). That is
also what the move to ECS needs.

**No database change.** No Flyway migration, no table or column change. The
database is copied as it is, and the Memory Wall already stores only a key
(`memory-wall/<uuid>.jpg`) that works the same in a folder and in S3.

**The old copies are kept.** The local `db` container and the
`memory-wall-data` volume are only *read*. Going back is one file change
(see "Roll back").

| Part | Downtime | Where |
|---|---|---|
| A. Merge the code (S3 is off by default) | none | GitHub |
| B. Create the bucket, role and RDS | none | AWS console |
| C. Copy and switch | about 5-10 min | EC2 Instance Connect |

---

## A. Merge the code

Merge the pull request. CD deploys it. Nothing changes on the site:
`MEMORY_WALL_STORAGE` is still `local`, the database is still the container.
The two copy scripts are now on the server in `~/app/scripts/aws/`.

## B. AWS console (no downtime)

### B1. S3 bucket
S3 → **Create bucket**
- Bucket type: General purpose. Region: **US East (N. Virginia) us-east-1**
- Name: e.g. `essential-butterfly-photos-ts1518` (must be unique worldwide; lower case, digits, `-`)
- Object Ownership: ACLs disabled
- **Block all public access: ON** (the backend serves the photos; the bucket stays private)
- Bucket Versioning: **Enable** (a deleted or overwritten photo can be recovered)
- Default encryption: SSE-S3 → **Create bucket**

### B2. Give the EC2 instance the lab role
EC2 → Instances → select the instance → **Actions → Security → Modify IAM role**
→ **LabInstanceProfile** → Update IAM role.

### B3. Let containers use that role
Same instance → **Actions → Instance settings → Modify instance metadata options**
- Instance metadata service: Enable
- IMDSv2: Required
- **Metadata response hop limit: 2** → Save

(Docker containers are one network "hop" further away than the instance. With
hop limit 1 the backend cannot get the role's credentials.)

### B4. RDS PostgreSQL
First read the current database password on the server, to use the same
one for RDS (then no secret file changes):
```
cat ~/app/secrets/postgres_password.txt; echo
```
RDS does not accept `/`, `@`, `"` or spaces in the password. If it has any,
see "Different password" at the end.

Aurora and RDS → **Create database**
- **Standard create**, Engine: **PostgreSQL**, Engine version: the newest **17.x**
- Templates: **Free tier** (or Dev/Test), Availability: **Single-AZ DB instance**
- DB instance identifier: `essential-butterfly-db`
- Master username: **postgres**
- Credentials management: **Self managed** → type the password from above twice
- Instance class: **db.t3.micro** (or db.t4g.micro)
- Storage: gp3, 20 GiB, *storage autoscaling* off
- Connectivity: **Connect to an EC2 compute resource** → choose the app instance
  (AWS creates the two security groups so only that instance can reach port 5432)
- Public access: **No**
- Monitoring: **turn OFF Enhanced Monitoring** (AWS Academy blocks the role it needs; creation fails otherwise). Performance Insights: off
- **Additional configuration → Initial database name: `essential_butterfly`** (required)
- Backup retention: 7 days, Encryption: on, **Deletion protection: on**
- **Create database**, wait until Status is **Available** (about 10 minutes)
- Open it → **Connectivity & security** → copy the **Endpoint**
  (`essential-butterfly-db.xxxxxxxx.us-east-1.rds.amazonaws.com`)

## C. Copy and switch (EC2 Instance Connect)

### C0. Pause automatic deploys
GitHub → Settings → Secrets and variables → Actions → Variables →
`AUTO_DEPLOY` → set to `false`. (A deploy in the middle would restart the
backend on the old database.) Do not merge anything until C7.

```
cd ~/app
git log -1 --oneline          # must show the merge from part A
ls scripts/aws/               # copy-db-to-rds.sh  copy-photos-to-s3.sh
```

### C1. Copy the photos (site stays up)
```
bash scripts/aws/copy-photos-to-s3.sh essential-butterfly-photos-ts1518
```
Ends with `OK: N file(s) in the volume, all present in s3://...`. Safe to repeat.

### C2. Copy the database (site shows "We will be right back")
```
bash scripts/aws/copy-db-to-rds.sh essential-butterfly-db.xxxxxxxx.us-east-1.rds.amazonaws.com
```
It refuses to write into a non-empty database, restores in one transaction,
and compares the row count of every table. If anything fails, the backend is
started again on the local database. On success it ends with
`OK: all N tables match exactly` and leaves the backend **stopped**.

### C3. Point the backend at RDS and S3
```
cp docker-compose.aws.yml docker-compose.aws.yml.before-rds
nano docker-compose.aws.yml
```
Under `backend:` → `environment:` add these four lines, indented exactly
like `SPRING_JPA_SHOW_SQL` (6 spaces), with your endpoint and bucket:
```yaml
      SPRING_DATASOURCE_URL: jdbc:postgresql://essential-butterfly-db.xxxxxxxx.us-east-1.rds.amazonaws.com:5432/essential_butterfly?sslmode=require
      MEMORY_WALL_STORAGE: s3
      MEMORY_WALL_S3_BUCKET: essential-butterfly-photos-ts1518
      MEMORY_WALL_S3_REGION: us-east-1
```
Save: `Ctrl+O`, `Enter`, `Ctrl+X`. Check it:
```
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml config | grep -E "SPRING_DATASOURCE_URL|MEMORY_WALL"
```
It must show the RDS URL and the three `MEMORY_WALL_` lines.

### C4. Start the backend
```
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d --no-deps backend
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml restart gateway
sleep 40
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml logs --tail 300 backend | grep -E "Flyway|up to date|S3 bucket|Started Essential|ERROR"
```
Expected:
- `Schema "public" is up to date. No migration necessary.`
- `Memory Wall photos are stored in S3 bucket essential-butterfly-photos-...`
- `Started EssentialButterflyBackendApplication`

### C5. Test the site
- Sign in as admin, open Appointments, Memory Wall, Website content
- The existing Memory Wall photos show (they now come from S3)
- Upload one new photo → S3 console → the bucket → `memory-wall/` shows a new file
- Book a test appointment as a client → it appears in the admin list

### C6. Turn deploys back on
`AUTO_DEPLOY` → `true`. From now on every deploy backs up **RDS**
(the deploy log says `Database: ... (Amazon RDS)`).

### C7. Later (after 1-2 weeks without problems)
Stop the old database container to free memory. **Do not delete the
volume** until the team agrees:
```
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml stop db
```

---

## Roll back
```
cd ~/app
cp docker-compose.aws.yml.before-rds docker-compose.aws.yml
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d --no-deps backend
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml restart gateway
```
The site is back on the local database and the photo volume exactly as they
were before C2. Anything saved while running on RDS/S3 is **not** in the old
copies.

## Different password
If the current password cannot be used for RDS, create RDS with a new one and
put it in its own file:
```
nano ~/app/secrets/rds_password.txt            # only the password, one line
RDS_PASSWORD_FILE=secrets/rds_password.txt bash scripts/aws/copy-db-to-rds.sh <endpoint>
```
Before C4, make the backend use it:
```
cp secrets/postgres_password.txt secrets/postgres_password.local-db.txt
cp secrets/rds_password.txt secrets/postgres_password.txt
```
(The local `db` container only reads that file when it is created for the
first time, so it keeps working with its old password. For a roll back, copy
`postgres_password.local-db.txt` back.)

## What stays in the database
Testimonial photos and website-update photos (stored as text in
`photo_data`) and resource-library files (`bytea`) are kept inside
PostgreSQL, so after this guide they are in RDS, not on EC2. Moving them to S3 as well would need a schema change and is left for
a later sprint.
