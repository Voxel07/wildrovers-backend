# Upload storage and recovery

`backend_uploads` is mounted at `/work/uploads`. It contains forum images, user
photos and profile backgrounds. Forum full-size images and thumbnails share a
2 GiB budget by default; every uploader has a separate 50 MiB budget per UTC day.
Set `FORUM_IMAGES_MAX_STORAGE_BYTES` and `FORUM_IMAGES_MAX_USER_DAILY_BYTES` to
positive byte counts to adjust them. HTTP 507 means the forum store is full;
HTTP 429 means the user's daily budget is exhausted. Both editor uploads and
pasted base64 images count. The disk-backed `forum/upload-usage` counters survive
restarts and moving uploads into posts. Failed writes may still consume daily
budget. The storage check scans the forum directory, so large installations
should monitor upload latency as well as disk space.

The nightly cleanup removes unused files after its grace period. It does not
replace backups. User photos/backgrounds are one file per user and are outside
the forum budget. Immich needs its own database and media backup.

Use a daily encrypted off-host backup, keep 7 daily and 4 weekly copies, and test
a restore at least quarterly. Back up the database and uploads from the same
maintenance window. Keep `FORUM_IMAGE_SIGNING_KEY`, the notification encryption
key, JWT keys and deployment configuration in the separate secrets backup.

## Consistent backup with Docker Compose

Run from the backend Compose directory. Resolve the actual volume name rather
than assuming Compose's project prefix. The following commands use PowerShell:

```powershell
$backupDir = Join-Path (Get-Location) 'backups'
New-Item -ItemType Directory -Path $backupDir -Force
$containerId = docker compose ps -q backend
$uploadVolume = docker inspect $containerId --format '{{range .Mounts}}{{if eq .Destination "/work/uploads"}}{{.Name}}{{end}}{{end}}'
if (-not $uploadVolume) { throw 'Upload volume not found' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
docker compose stop backend
```

While the backend is stopped, dump MariaDB using the credentials and database
name of the external database service. For example, inside its container, use
`mariadb-dump --single-transaction --routines --triggers --events` with a protected
client option file, and save the result alongside the upload archive. Avoid
putting database passwords on the command line. Then archive the volume:

```powershell
docker run --rm --mount "type=volume,source=$uploadVolume,target=/uploads,readonly" --mount "type=bind,source=$backupDir,target=/backup" alpine:3.21 tar -czf "/backup/uploads-$stamp.tgz" -C /uploads .
if ($LASTEXITCODE -ne 0) { throw 'Upload backup failed; keep the backend stopped until checked' }
docker compose start backend
Get-FileHash -Algorithm SHA256 (Join-Path $backupDir "uploads-$stamp.tgz")
```

Verify the archive listing and database dump, then copy both to encrypted off-host
storage. Automate these steps in the deployment's backup system; no backup job is
installed by the application. Monitor the backing Docker filesystem's free space
because it also contains other volumes and logs.

## Restore

Stop the backend. Restore the matching database dump and archive into a **new,
empty** upload volume. Mount that volume at `/work/uploads`, verify permissions
for the backend's container user, restore secrets, and start the backend. Check a
forum thumbnail and full-size image, an avatar/background, and an event's forum
link. Keep the previous volume until the restore is verified. The backup includes
daily usage counters, which prevents resetting an uploader's current-day budget
by restarting or restoring the service.
