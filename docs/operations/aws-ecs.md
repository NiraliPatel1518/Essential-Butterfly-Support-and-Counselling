# Running the site on Amazon ECS (Fargate)

Professor's feedback: *a single EC2 host is good for development, but
production needs an orchestration tool. Try ECS.* This guide moves the
application containers from the EC2 instance to **Amazon ECS on Fargate**.

Done before this guide (`aws-s3-rds-migration.md`): the data is in **RDS**
and the photos are in **S3**, so the containers keep no state and ECS can
replace them at any time.

```
Browser --HTTPS--> CloudFront --> Load balancer --> ECS task (Fargate)  --> RDS
                                                    |- web: nginx + React    \-> S3
                                                    \- backend: Spring Boot
GitHub merge --> CI --> CD --> EC2 (builds only) --> ECR images --> ECS rolling update
```

| Piece | Why |
|---|---|
| **ECS service, 1 Fargate task** | ECS keeps the task running; if it crashes or fails its health check, ECS starts a new one. Deploys are rolling: the new task must be healthy before the old one stops. |
| **Deployment circuit breaker** | A release that never becomes healthy is rolled back automatically. |
| **Application Load Balancer** | Sends traffic only to healthy tasks. Health check: `/api/features` (nginx -> backend -> database). |
| **CloudFront** | Free HTTPS address (`https://dxxxx.cloudfront.net`); the load balancer only accepts traffic from CloudFront. |
| **ECR** | Stores the images. Keeps the last 10 of each. |
| **SSM Parameter Store** | Holds the secrets (encrypted). The task reads them when it starts; they are not in the code, the images or the task definition. |
| **CloudWatch Logs** | Container logs, kept 7 days: CloudWatch -> Log groups -> `/ecs/essential-butterfly`. |
| **EC2** | Only builds images for CD (it has the lab role, so GitHub needs no AWS keys, which expire in AWS Academy). It serves no users and keeps no data. |

AWS Academy rules followed: Fargate (no EC2 auto scaling group needed) and
**LabRole** as the task role and the task execution role (Learner Lab
Readme). Nothing creates IAM roles.

**Cost** (about $47 of lab credit left on Nov 25): load balancer ~$0.75/day
(always), one task ~$0.70/day (only while on), RDS ~$0.50/day. Turn the site
off when nobody needs it (see "On / off").

**No database or code-behaviour change.** Same images as on EC2 (the web
image is the same React build and the same nginx rules, in one container).

---

## A. Merge the code (no change to the live site)

The deploy workflow only uses ECS when the repository variable
`DEPLOY_TARGET` is `ecs`. Until then CD keeps deploying to EC2 exactly as
before.

## B. Create the AWS pieces (site stays up on EC2)

EC2 Instance Connect:
```
cd ~/app
git log -1 --oneline      # must show the merge from step A
bash scripts/aws/ecs-setup.sh
```
Takes 10-15 minutes (most of it is building the images and waiting for the
first task to be healthy). Each step prints what it did; the secret values
are never printed. It is safe to run again if it stops.

It ends with the new address:
```
ECS is set up. The new address is:
    https://dxxxxxxxxxxxx.cloudfront.net
```
A new CloudFront address can need up to 15 more minutes before it works.

## C. Test the new address (EC2 site is still up)

Both sites use the same RDS database and S3 bucket, so it is the same data.
- Home page, About, Services, Resources load
- Sign in as admin; Appointments, Memory Wall, Website content open
- The Memory Wall photo shows
- Sign in as a client; My bookings shows the bookings
- Password reset email: the link points to the new address

ECS console -> Clusters -> essential-butterfly -> Services: 1 task Running,
target health "healthy".

## D. Switch to ECS

1. GitHub -> Settings -> Secrets and variables -> Actions -> **Variables**:
   - `DEPLOY_TARGET` = `ecs` (new variable)
   - `SITE_URL` = the new `https://....cloudfront.net` address
2. Stop the app containers on EC2 (RDS and S3 are not affected):
   ```
   cd ~/app
   sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml stop backend frontend gateway
   ```
3. Optional: send visitors of the old sslip.io address to the new one:
   ```
   cp Caddyfile Caddyfile.before-ecs
   printf '{$SITE_ADDRESS} {\n\tredir https://dxxxxxxxxxxxx.cloudfront.net{uri} permanent\n}\n' > Caddyfile
   sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml restart caddy
   ```
   (put your CloudFront address in place of `dxxxxxxxxxxxx`)
4. Run Actions -> Deploy to EC2 -> Run workflow (`main`). The log shows
   `Deploy target: ecs` and ends with `Deployed ... to ECS`.

From now on every merge to `main` is tested, then built on EC2, pushed to
ECR and rolled out on ECS.

## On / off (saves credit)

EC2 Instance Connect, from `~/app`:
```
bash scripts/aws/ecs-scale.sh off      # 0 tasks
bash scripts/aws/ecs-scale.sh on       # 1 task, ready in 3-6 minutes
bash scripts/aws/ecs-scale.sh status
```
"off" stops only the containers; the data stays in RDS and S3. A deploy
while the site is off updates ECS and the new version starts with "on".

## Going back to EC2

```
cd ~/app
cp Caddyfile.before-ecs Caddyfile 2>/dev/null || true
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d --no-deps backend frontend
sudo docker compose -f docker-compose.yml -f docker-compose.aws.yml restart gateway caddy
bash scripts/aws/ecs-scale.sh off
```
and set `DEPLOY_TARGET` to `ec2` and `SITE_URL` back to the sslip.io address.
The EC2 containers still use RDS and S3, so no data is lost either way.

## Troubleshooting

| What you see | Where to look |
|---|---|
| Deploy says "did not become healthy" | ECS -> cluster -> service -> **Events** and **Logs**; or CloudWatch -> Log groups -> `/ecs/essential-butterfly` -> newest `backend/...` stream. ECS already rolled back; the site stays up. |
| Task stops right after starting, "ResourceInitializationError" | It could not pull the image or read a secret: check LabRole is the task execution role (ECS -> Task definitions -> latest revision). |
| Site shows 502/503 | Is the site off? `bash scripts/aws/ecs-scale.sh status`. Otherwise target group health (EC2 -> Target groups -> essential-butterfly-tg -> Targets). |
| Deploy says "Cannot reach the server" | The AWS Academy lab is not started (EC2, the build machine, is stopped). ECS keeps running the current version. |

## After the project (to stop all costs)

In this order: ECS service (set 0 tasks, delete), ECS cluster, CloudFront
distribution (Disable, wait, Delete), load balancer, target group, ECR
repositories, RDS (turn off deletion protection; take a final snapshot),
S3 bucket. Only after the client has a copy of the data.
