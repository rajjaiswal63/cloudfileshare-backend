# CloudShare Backend API

This repository contains the REST API for CloudShare, the file-sharing application whose main project and user interface live in the [frontend repository](https://github.com/rajjaiswal63/cloudfileshare). Start with the frontend README for the complete product overview; use this repository for backend setup, API routes, and storage configuration.

## Responsibilities

- Authenticate protected requests using Clerk JWTs and enforce user-specific file access.
- Accept file uploads, save file contents, and store file metadata.
- List, download, delete, and change the public/private visibility of files.
- Manage user profiles, upload credits, and payment transaction records.
- Create Razorpay payment orders, verify payment signatures, and add purchased credits.
- Provide public file access for files the owner has marked as public.

## Architecture and storage

- The API is served under /api/v1.0 (default local address: http://localhost:8080/api/v1.0).
- MongoDB stores user profiles, file metadata, credit balances, and payment transactions.
- File contents are written to the directory configured by FILE_UPLOAD_DIR (default: upload). MongoDB stores the file metadata and location, not the binary content.
- Keep the upload directory on persistent storage when deploying the API on an ephemeral or containerized host.
- Clerk provides user sign-in in the frontend. Protected API requests carry a Clerk Bearer token; file ownership is checked for private operations.
- Razorpay powers credit-plan checkout. Payment credentials are required to enable purchases.

## API routes

All routes below are relative to /api/v1.0.

| Method | Route | Access | Purpose |
| --- | --- | --- | --- |
| GET | /health | Public | Health check |
| POST | /files/upload | Signed-in user | Upload one or more files; consumes one credit per file |
| GET | /files/my | Signed-in user | List the current user's files |
| PATCH | /files/{id}/toggle-public | File owner | Switch a file between private and public |
| DELETE | /files/{id} | File owner | Delete a file and its metadata |
| GET | /files/public/{id} | Public | Get details for a public file |
| GET | /files/download/{id} | Public file, or owner for private file | Download a file |
| GET | /users/credits | Signed-in user | Get the current credit balance and plan |
| POST | /payments/create-order | Signed-in user | Create a Razorpay order for a credit plan |
| POST | /payments/verify-payment | Signed-in user | Verify payment and add plan credits |
| GET | /transactions | Signed-in user | List successful transactions for the current user |
| POST | /webhooks/clerk | Clerk webhook | Receive Clerk user lifecycle events for profile synchronization |

File uploads use multipart form data with the field name files. The default upload limits are 5 MB per file and 25 MB per request; these can be configured with MAX_FILE_SIZE and MAX_REQUEST_SIZE.

## Run locally

Requirements: Java 21 and a MongoDB instance or Atlas database. Configure the environment variables before starting the service.

1. Clone the backend repository:

       git clone https://github.com/rajjaiswal63/cloudfileshare-backend.git
       cd cloudfileshare-backend

2. Use .env.example as a reference and set the variables in your local shell or hosting provider. Spring Boot does not load .env files automatically. Provide your own values for at least:

   - MONGODB_URI — MongoDB connection URI.
   - CLERK_ISSUER and CLERK_JWKS_URL — Clerk issuer and JSON Web Key Set endpoint.
   - CORS_ALLOWED_ORIGINS — frontend origin(s), such as http://localhost:5173.
   - FILE_UPLOAD_DIR — directory for uploaded file contents (optional; defaults to upload).
   - RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET — required for credit purchases.
   - CLERK_WEBHOOK_SECRET — Clerk webhook configuration.
   - PORT, MAX_FILE_SIZE, and MAX_REQUEST_SIZE — optional server and upload settings.

Never commit real database, authentication, or payment credentials.

3. Start the API:

       Windows: .\mvnw.cmd spring-boot:run
       macOS/Linux: ./mvnw spring-boot:run

The API is then available at http://localhost:8080/api/v1.0. Check GET /health to confirm that the service is responding.

## Build, tests, and Docker

- Build the application with .\mvnw.cmd clean package on Windows or ./mvnw clean package on macOS/Linux.
- Run backend tests with .\mvnw.cmd test or ./mvnw test.
- A two-stage Java 21 Dockerfile is included for building and running the API. Supply the required environment variables and persistent file storage when deploying the container.

## Related documentation

- [Main CloudShare project and frontend](https://github.com/rajjaiswal63/cloudfileshare)
- [Frontend setup and user-flow documentation](https://github.com/rajjaiswal63/cloudfileshare)

