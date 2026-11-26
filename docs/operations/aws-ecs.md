# Running the site on Amazon ECS (Fargate)

Professor's feedback: *a single EC2 host is good for development, but
production needs an orchestration tool. Try ECS.* This guide moves the
application containers from the EC2 instance to **Amazon ECS on Fargate**.

Done before this guide (`aws-s3-rds-migration.md`): the data is in **RDS**
and the photos are in **S3**, so the containers keep no state and ECS can
replace them at any time.

```
Browser --HTTPS--> Caddy (EC2) --> Load balancer --> ECS task (Fargate)  --> RDS
                                                     |- web: nginx + React    \-> S3
                                                     \- backend: Spring Boot
GitHub merge --> CI --> CD --> EC2 (builds) --> ECR images --> ECS rolling update
```

| Piece | Why |
|---|---|
| **ECS service, 1 Fargate task** | ECS keeps the task running; if it crashes or fails its health check, ECS starts a new one. Deploys are rolling: the new task must be healthy before the old one stops. |
| **Deployment circuit breaker** | A release that never becomes healthy is rolled back automatically. |
| **Application Load Balancer** | Sends traffic only to healthy tasks. Health check: `/api/features` (nginx -> backend -> database). Accepts traffic **only from the EC2 server's IP**. |
| **Caddy on EC2** | Keeps the existing HTTPS address `https://184-193-202-252.sslip.io` and passes the traffic to the load balancer. |
| **ECR** | Stores the images. Keeps the last 10 of each. |
| **SSM Parameter Store** | Holds the secrets (encrypted). The task reads them when it starts; they are not in the code, the images or the task definition. |
| **CloudWatch Logs** | Container logs, kept 7 days: CloudWatch -> Log groups -> `/ecs/essential-butterfly`. |
| **EC2** | Only passes HTTPS through (Caddy) and builds images for CD. It keeps no data and runs no application code. |

**Why HTTPS stays on EC2:** AWS Academy does not allow CloudFront (neither
LabRole nor the console login: `cloudfront:ListDistributions` denied), and a
load-balancer certificate (ACM) needs a domain we own. If either becomes
available, the setup also supports CloudFront (`CLOUDFRONT_DOMAIN=...`).
In AWS Academy the EC2 instance stops when the lab session ends anyway, and
ECS is turned off at the same time to save credit, so this changes nothing
in practice.

AWS Academy rules followed: Fargate (no EC2 auto scaling group needed) and
**LabRole** as the task role and the task execution role (Learner Lab
Readme). Nothing creates IAM roles.

**Cost** (about $47 of lab credit left on Nov 25): load balancer ~$0.75/day
(always), one task ~$0.70/day (only while on), RDS ~$0.50/day. Turn ECS off
when nobody needs the site (see "On / off").

**No database or code-behaviour change.** The same backend image as on
EC2; the web image is the same React build with the same nginx rules,
in one container.

---

## A. Merge the code (no change to the live site)

CD only deploys to ECS when the repository variable `DEPLOY_TARGET` is
`ecs`. Until then it keeps deploying to EC2 exactly as before.

## B. Create the AWS pieces (site stays up on EC2)

EC2 Instance Connect:
```
cd ~/app
git log -1 --oneline      # must show the merge from step A
PUBLIC_URL=https://184-193-202-252.sslip.io bash scripts/aws/ecs-setup.sh
```
Takes 10-15 minutes (most of it is building the images and waiting for the
first task to be healthy). Each step prints what it did; secret values are
never printed. It is safe to run again; later runs do not need `PUBLIC_URL`.

It ends with:
```
ECS is set up and running (ec2). Public address: https://184-193-202-252.sslip.io
```
At this point ECS runs, but visitors are still served by the EC2 containers.

## C. Check ECS before switching

```
source ~/ecs-config.env
curl -s http://$ALB_DNS/api/features; echo
```
must print the features list (JSON). ECS console -> Clusters ->
essential-butterfly -> Services -> essential-butterfly: 1 task Running,
Health "healthy".

## D. Switch to ECS (about 10 seconds; the address stays the same)

```
cd ~/app
bash scripts/aws/switch-site.sh ecs
```
It checks ECS answers, points Caddy at the load balancer, checks the public
address (and switches back by itself if that fails), then stops the app
containers on EC2.

Then GitHub -> Settings -> Secrets and variables -> Actions -> **Variables**:
`DEPLOY_TARGET` = `ecs`. From now on every merge to `main` is tested, then
built on EC2, pushed to ECR and rolled out on ECS (the deploy log shows
`Deploy target: ecs`).

Test the site: home page, sign in as admin and as a client, Memory Wall
photo, appointments.

## On / off (saves credit)

```
bash scripts/aws/ecs-scale.sh off      # 0 tasks (the site shows an error page)
bash scripts/aws/ecs-scale.sh on       # 1 task, ready in 3-6 minutes
bash scripts/aws/ecs-scale.sh status
```
"off" stops only the containers; the data stays in RDS and S3. A deploy
while ECS is off updates ECS, and the new version starts with "on".
Turn it on before a demo, off at the end of the day.

## Going back to EC2

```
bash scripts/aws/switch-site.sh ec2
bash scripts/aws/ecs-scale.sh off
```
and set `DEPLOY_TARGET` to `ec2`. The EC2 containers also use RDS and S3,
so no data is lost either way.

## Troubleshooting

| What you see | Where to look |
|---|---|
| Deploy says "did not become healthy" | ECS -> cluster -> service -> **Events** and **Logs**; or CloudWatch -> Log groups -> `/ecs/essential-butterfly` -> newest `backend/...` stream. ECS already rolled back; the site stays up. |
| Task stops right after starting, "ResourceInitializationError" | It could not pull the image or read a secret: check LabRole is the task execution role (ECS -> Task definitions -> latest revision). |
| Site shows 502/503 | Is ECS off? `bash scripts/aws/ecs-scale.sh status`. Otherwise target group health (EC2 -> Target groups -> essential-butterfly-tg -> Targets). |
| Deploy says "Cannot reach the server" | The AWS Academy lab is not started (EC2 is stopped). |

## After the project (to stop all costs)

In this order: ECS service (set 0 tasks, delete), ECS cluster, load
balancer, target group, ECR repositories, RDS (turn off deletion protection;
take a final snapshot), S3 bucket. Only after the client has a copy of the
data.
