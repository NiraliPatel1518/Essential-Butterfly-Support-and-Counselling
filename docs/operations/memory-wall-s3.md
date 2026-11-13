# Memory Wall photos: keeping them in AWS S3

Today the Memory Wall pictures are kept in a folder inside the backend
container (a Docker volume called `memory-wall-data`, so they survive a
rebuild). Moving them to an AWS S3 bucket later is a small, safe change:

* The database never stores the picture, only a short key such as
  `memory-wall/3f2c….jpg`. The same key works in a folder and in S3, so **no
  database change and no screen change** is needed.
* The backend only talks to the `PhotoStorage` interface. The folder version
  is `LocalPhotoStorage`; the S3 version is one extra class,
  `S3PhotoStorage` (ready to copy from `S3PhotoStorage.java.txt`).
* Photos that clients attach to a testimonial stay with the testimonial in the
  database; only photos the administrator uploads use the photo store.
* The bucket stays **private**. The backend reads the pictures and hands them
  to the website, so unpublished photos can never be opened by anyone.

## Steps (AWS Academy Learner Lab)

1. Start the lab and open the AWS console (click the green dot next to "AWS").
2. Open **S3** → **Create bucket**.
   * Name: for example `essential-butterfly-memory-wall-<your-initials>`
     (bucket names are global, so make it unique).
   * Region: `us-east-1` (the Learner Lab allows `us-east-1` and `us-west-2`).
   * Leave **Block all public access** ON.
3. Decide where the backend will run:
   * **On an EC2 instance** in the same lab: launch the instance with the
     instance profile **LabInstanceProfile**. The backend then gets its
     permissions by itself; no keys are needed.
   * **Anywhere else** (your laptop, Docker): in the lab page choose
     **AWS Details → AWS CLI → Show** and copy the three values. They are
     temporary and change every time the lab restarts.
4. Add the SDK to `essential-butterfly-backend/pom.xml`. Inside `<dependencies>`:

   ```xml
   <dependency>
       <groupId>software.amazon.awssdk</groupId>
       <artifactId>s3</artifactId>
   </dependency>
   ```

   and, if the file has no `<dependencyManagement>` section yet, add this
   next to `<dependencies>` (use the newest 2.x version from Maven Central):

   ```xml
   <dependencyManagement>
       <dependencies>
           <dependency>
               <groupId>software.amazon.awssdk</groupId>
               <artifactId>bom</artifactId>
               <version>2.29.0</version>
               <type>pom</type>
               <scope>import</scope>
           </dependency>
       </dependencies>
   </dependencyManagement>
   ```
5. Copy `S3PhotoStorage.java.txt` to
   `essential-butterfly-backend/src/main/java/ca/sheridancollege/shtirthb/service/S3PhotoStorage.java`
   (rename it to end in `.java`).
6. Tell the backend to use S3. In `docker-compose.yml`, under the backend's
   `environment:` add:

   ```yaml
   MEMORY_WALL_STORAGE: s3
   MEMORY_WALL_S3_BUCKET: essential-butterfly-memory-wall-xx
   MEMORY_WALL_S3_REGION: us-east-1
   # only when NOT running on EC2 with LabInstanceProfile:
   AWS_ACCESS_KEY_ID: ...
   AWS_SECRET_ACCESS_KEY: ...
   AWS_SESSION_TOKEN: ...
   ```
7. Move the photos that already exist (once, before switching):

   ```
   docker cp essential-butterfly-backend:/app/data/memory-wall/memory-wall ./photos
   aws s3 sync ./photos s3://essential-butterfly-memory-wall-xx/memory-wall
   ```
8. Rebuild: `docker compose up -d --build`. Open Manage Memory Wall and check
   that the pictures still show.

To go back to the folder, set `MEMORY_WALL_STORAGE: local` (or remove the
line) and copy the files back the same way.
