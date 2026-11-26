# Memory Wall photos: keeping them in AWS S3

By default the Memory Wall pictures are kept in a folder inside the backend
container (a Docker volume called `memory-wall-data`, so they survive a
rebuild). Moving them to an AWS S3 bucket later is a small, safe change:

* The database never stores the picture, only a short key such as
  `memory-wall/3f2c….jpg`. The same key works in a folder and in S3, so **no
  database change and no screen change** is needed.
* The backend only talks to the `PhotoStorage` interface. The folder version
  is `LocalPhotoStorage`; the S3 version is `S3PhotoStorage`.
* Photos that clients attach to a testimonial stay with the testimonial in the
  database; only photos the administrator uploads use the photo store.
* The bucket stays **private**. The backend reads the pictures and hands them
  to the website, so unpublished photos can never be opened by anyone.

## Status

`S3PhotoStorage` is now part of the backend (with `S3PhotoStorageTest`),
and the AWS SDK is already in `pom.xml`. It is switched off until
`MEMORY_WALL_STORAGE=s3` is set.

The full, tested procedure (bucket, IAM role, copying the existing photos,
switching, rolling back) is in
[aws-s3-rds-migration.md](aws-s3-rds-migration.md).
