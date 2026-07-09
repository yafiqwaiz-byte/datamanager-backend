# DataManager

A comprehensive Spring Boot REST API application for managing user accounts, staff data, form submissions, file uploads, OCR processing, and data visualization through dashboards. Built with PostgreSQL/Supabase database and OCR service integration.

Version: 1.5.0 (in development) — 2026-07-06

## Features

- **User & Account Management** - User registration, login, and account authentication
- **Staff Management** - Manage staff information, roles, and permissions
- **Form Submission System** - Create, manage, and submit dynamic forms
- **File Upload & Processing** - Upload images and documents with OCR text extraction
- **Data Processing Engine** - Clean, validate, and process extracted data
- **Dashboard System** - Real-time data visualization and insights
- **Export Functionality** - Export processed data and logs
- **JWT Authentication** - Secure token-based authentication
- **Password Management** - Secure password change functionality
- **PostgreSQL Integration** - Persistent data storage with Supabase

## Tech Stack

- **Framework:** Spring Boot
- **Language:** Java
- **Database:** PostgreSQL (Supabase)
- **Build Tool:** Maven
- **Authentication:** JWT
- **ORM:** Hibernate/JPA
- **API:** REST

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- PostgreSQL (or Supabase account)
- Git

## Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yafiqwaiz-byte/datamanager.git
   cd datamanager
   ```

2. **Install dependencies:**
   ```bash
   mvn clean install
   ```

## Configuration

Set the following environment variables before running the application:

```bash
# Database Configuration
DB_URL=jdbc:postgresql://your-host:5432/your-database
DB_USERNAME=your-username
DB_PASSWORD=your-password

# JWT Configuration
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000
```

### For Supabase:
Get your connection string from Supabase dashboard and format it as:
```
DB_URL=jdbc:postgresql://[user]:[password]@[host]:[port]/[database]
```

## Project Architecture

### Architecture Overview

The system follows a three-layer architecture: Presentation, Application, and Data layers. Recent updates introduced a Staff/Admin portal, a PO Aging service and dashboard, a DOCX-based letter generator, tighter token handling with HttpOnly refresh cookies, and expanded OCR and AI-assisted analysis services.

### Presentation Layer

- Auth UI (Sign-in / Google OAuth)
- User portal: forms, OCR uploads, generated letters
- Staff portal: PO Aging dashboard, form submission UI
- Admin portal: approvals, invites, RBAC administration
- UI components: TNB Northern Map, form renderer, OCR upload, letter generator, PO Aging dashboard, `StaffLayout` + topbar
- Authentication: auth service using HttpOnly refresh cookie for token refresh

### Application Layer (Services)

- Auth Service: JWT + Google OAuth support, refresh token flows via HttpOnly cookie
- Form Service: templates, submissions, field mapping (OCR → placeholder mapping)
- OCR Service: Tesseract-based extraction and preprocessing
- Letter Service: DOCX templating and PDF export (DOCX4J)
- File Upload Service: images, Excel, DOCX and PDF handling
- PO Aging Service: percentile and mark calculations; dashboard integration
- RBAC: roles for Admin / Staff / User enforced at service layer
- Login Attempt Service: rate limiting and lockout handling
- Email Service: invite codes and SMTP integration
- Token Store: refresh token persistence (secure store)
- AI Assistant: Gemini-like service for OCR mapping, dashboard analysis and suggestions
- Geocode Service: GPS → address resolution
- PDF converter: DOCX4J-backed conversions and exports
- Security: CORS, JWT filter, SecurityFilterChain, COOP/security headers
- Persistence tuning: batch inserts (batch size 50), Hibernate optimizations

### Data Layer

- Relational database: users, accounts, roles, forms, submissions (PostgreSQL / Supabase)
- File storage: images, Excel, DOCX, PDFs (local or cloud-backed blob storage)
- Document store: OCR results, generated letters, processed documents (for fast search/lookup)
- Token store: secure refresh token storage for HttpOnly cookie flow
- ORM: Hibernate/JPA with batch insert and `ddl-auto=update` configuration

### Notable Changes (latest)

- Implemented HttpOnly refresh token flow and token refresh endpoint
- Added Staff and Admin portals with RBAC controls
- PO Aging service and dashboard are now part of the application layer
- Letter generation service using DOCX templating and PDF export (DOCX4J)
- OCR pipeline using Tesseract with document-store persistence for OCR outputs
- Introduced AI-assisted analysis (Gemini-like) for mapping and dashboard insights
- Hardened security: COOP headers, SecurityFilterChain, CORS and JWT filters
- Persistence and performance: Hibernate batch inserts, ddl-auto updates, batch size tuning

## Project Structure

```
src/main/java/dev/waiz/datamanager/
├── controller/          # REST endpoints (accounts, auth, staff, dashboard, forms, uploads)
├── model/               # JPA entities (Account, User, Staff, Upload, ProcessedData, FormTemplate, etc.)
├── service/             # Business logic (AuthService, OcrService, LetterService, PoAgingService, etc.)
├── repository/          # Spring Data JPA repositories
└── DatamanagerApplication.java
```

## System Workflows (summary)

- Login/Registration: JWT-based authentication, Google OAuth option, refresh tokens via HttpOnly cookie
- Upload & OCR: user uploads files → OCR service (Tesseract) → OCR results persisted to document store → data mapped and validated → processed data saved
- Letter Generation: templates (DOCX) populated with data → exported to DOCX/PDF via DOCX4J
- PO Aging: background calculation service produces metrics consumed by Staff PO Aging dashboard
- AI Assistance: optional analysis step that suggests field mappings or dashboard annotations

## API Endpoints

### Authentication & Account Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/accounts` | Create a new account (Register) |
| POST | `/api/accounts/verify` | Verify credentials (Login) |
| GET | `/api/accounts` | Get all accounts |
| GET | `/api/accounts/{accountId}` | Get account by ID |
| PUT | `/api/accounts/{accountId}` | Update account details |
| PATCH | `/api/accounts/{accountId}/status` | Update account status |
| PATCH | `/api/accounts/{accountId}/password` | Change password |
| DELETE | `/api/accounts/{accountId}` | Delete account |

### File Upload & Processing (Future)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/uploads` | Upload image/document |
| GET | `/api/uploads/{uploadId}` | Get upload details |
| GET | `/api/uploads/user/{userId}` | Get user uploads |
| POST | `/api/uploads/{uploadId}/process` | Process upload with OCR |
| DELETE | `/api/uploads/{uploadId}` | Delete upload |

### Form Management (Future)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/forms` | Create form template |
| GET | `/api/forms` | Get all form templates |
| GET | `/api/forms/{templateId}` | Get form by ID |
| POST | `/api/submissions` | Submit form |
| GET | `/api/submissions/user/{userId}` | Get user submissions |
| GET | `/api/submissions/{submissionId}` | Get submission details |

### Data Processing (Future)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/processed-data` | Get all processed data |
| GET | `/api/processed-data/{dataId}` | Get processed data by ID |
| POST | `/api/processed-data/export` | Export processed data |
| GET | `/api/export-logs` | Get export history |

### Dashboard (Future)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dashboard` | Get dashboard data |
| GET | `/api/dashboard/{dashboardId}` | Get specific dashboard |
| PUT | `/api/dashboard/{dashboardId}` | Update dashboard |
| POST | `/api/dashboard/generate` | Generate new dashboard |

## Running the Application

**Option 1: Using Maven**
```bash
mvn spring-boot:run
```

**Option 2: Build and run JAR**
```bash
mvn clean package
java -jar target/datamanager-0.0.1-SNAPSHOT.jar
```

**Option 3: Using PowerShell (Windows)**
```powershell
$env:DB_URL = "your-url"
$env:DB_USERNAME = "your-username"
$env:DB_PASSWORD = "your-password"
$env:JWT_SECRET = "your-secret"
$env:JWT_EXPIRATION = "86400000"

mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Testing

Run unit tests:
```bash
mvn test
```

## Usage Example

### Create Account
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "passwordHash": "hashed_password",
    "role": "USER",
    "status": "ACTIVE"
  }'
```

### Login (Verify Credentials)
```bash
curl -X POST http://localhost:8080/api/accounts/verify \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "password": "hashed_password"
  }'
```

### Get All Accounts
```bash
curl http://localhost:8080/api/accounts
```

## Database Schema

### Account Table
```sql
CREATE TABLE Account (
  account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### User Table
```sql
CREATE TABLE "User" (
  user_id INT PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES Account(account_id),
  full_name VARCHAR(255) NOT NULL,
  company_name VARCHAR(255),
  phone_no VARCHAR(20),
  address TEXT
);
```

### Staff Table
```sql
CREATE TABLE Staff (
  staff_id INT PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES Account(account_id),
  full_name VARCHAR(255) NOT NULL,
  department VARCHAR(255),
  position VARCHAR(255)
);
```

### Upload Table
```sql
CREATE TABLE upload (
  upload_id INT PRIMARY KEY,
  staff_id INT NOT NULL REFERENCES Staff(staff_id),
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500),
  uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Processed Data Table
```sql
CREATE TABLE Processed_data (
  processed_id INT PRIMARY KEY,
  upload_id INT NOT NULL REFERENCES upload(upload_id),
  cleaned_data TEXT,
  validation_status VARCHAR(50),
  error_log TEXT,
  processed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Form Template Table
```sql
CREATE TABLE Form_Template (
  template_id INT PRIMARY KEY,
  staff_id INT NOT NULL REFERENCES Staff(staff_id),
  template_name VARCHAR(255) NOT NULL,
  description TEXT,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Form Submission Table
```sql
CREATE TABLE Form_Submission (
  submission_id INT PRIMARY KEY,
  user_id INT NOT NULL REFERENCES "User"(user_id),
  template_id INT NOT NULL REFERENCES Form_Template(template_id),
  input_method VARCHAR(50),
  deletion_date TIMESTAMP,
  status VARCHAR(50)
);
```

### Dashboard Table
```sql
CREATE TABLE Dashboard (
  dashboard_id INT PRIMARY KEY,
  staff_id INT NOT NULL REFERENCES Staff(staff_id),
  processed_id INT NOT NULL REFERENCES Processed_data(processed_id),
  title VARCHAR(255),
  chart_config TEXT,
  filter_config TEXT,
  generated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  last_edited TIMESTAMP WITH TIME ZONE
);
```

## Implementation Status

✅ **Completed / Available:**
- Account Management (CRUD), JWT authentication, account service & repository
- Basic User and Staff models and database configuration (PostgreSQL)

🔧 **Recently Added / Updated:**
- HttpOnly refresh token flow and token-store support
- OCR pipeline (Tesseract) with document-store persistence
- Letter generation service (DOCX templates + DOCX4J export)
- PO Aging service and Staff PO Aging dashboard
- Security enhancements: SecurityFilterChain, COOP headers, CORS, JWT filter

⏳ **In Progress / Planned:**
- Frontend pages for some workflows (form renderer, advanced dashboards)
- Integration tests and end-to-end validation for new services
- Scaling and deployment automation (containerization / cloud storage)

## Implemented Backend Components (selected)

The following controllers, services, and repositories are implemented in the backend and reflected in the current codebase (see `src/main/java/dev/waiz/datamanager`):

- Controllers:
  - `AuthController`, `accountcontroller`, `usercontroller`, `staffcontroller`, `AdminController`
  - `FormTemplateController`, `FormSubmissionController`, `FileUploadController`, `LetterController`
  - `POAgingController`, `ProcessedRowsController`, `DataPreprocessingController`, `GeocodingController`, `GeminiController`, `UserFormController`

- Services:
  - `accountservice`, `userservice`, `staffservice`, `GoogleAuthService`, `RefreshTokenService`, `LoginAttemptService`
  - `OcrService`, `FileUploadService`, `FormSubmissionService`, `FormTemplateService`, `FieldMappingService`, `DataPreprocessingService`
  - `POAgingService`, `POAgingCacheService`, `ProcessedRowService`, `LetterGeneratorService`, `GeminiService`, `GeocodingService`
  - `EmailService`, `TemplateUploadService`, `StaffInviteService`, `BusinessValidationService`, `RegexValidationService`

- Repositories:
  - `accountrepository`, `userrepository`, `staffrepository`, `StaffInviteRepository`, `RefreshTokenRepository`
  - `FormSubmissionRepository`, `formtemplaterepository`, `FormFieldRepository`, `FormAnswerRepository`, `FieldMappingRepository`
  - `FileUploadRepository`, `OcrResultRepository`, `ProcessedRowsRepository`, `ExcelDataRepository`
  - `POAgingReportRepository`, `POAgingRawRepository`, `POAgingCacheRepository`, `LetterTemplateRepository`, `GeneratedLetterRepository`

These components are used by the updated architecture: the README's Application Layer and System Workflows sections reference these implementations.

## Upcoming

- Complete frontend pages and integrate with the new endpoints (forms, PO Aging dashboard, letter generation).
- Add integration and E2E tests for `OcrService`, `POAgingService`, `LetterGeneratorService`, and refresh-token flows.
- Add containerization scripts and storage migration for blob/file storage.
- Improve monitoring and metrics for batch processing and OCR throughput.

If you'd like, I can run the test suite and create a git commit for the README changes next.
- Staff Controller & Service
- User Controller & Service

📋 **Upcoming:**
- Export Functionality
- Error Handling & Validation
- Unit Tests

## Important Notes

### Security Considerations
- **Password Hashing:** Implement proper password hashing (bcrypt, Argon2) in production instead of storing plain hashes
- **JWT Tokens:** Implement JWT token generation and validation for authentication
- **HTTPS:** Use HTTPS in production for secure communication
- **Database Credentials:** Never expose DB credentials in code or README - use environment variables (.env files in .gitignore)

### Development Guidelines
- **Request Validation:** Add @Valid annotations for request body validation
- **Error Handling:** Implement global exception handlers for better error messages
- **Logging:** Add comprehensive logging for debugging and monitoring
- **API Documentation:** Consider using Swagger/OpenAPI for API documentation
- **Testing:** Implement unit tests and integration tests for all services
- **File Upload Security:** Validate file types, sizes, and scan for malicious content before processing
- **OCR Integration:** Ensure OCR service is properly integrated and handles errors gracefully
- **Data Privacy:** Ensure GDPR/data privacy compliance for user data storage and processing

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

- **GitHub:** [yafiqwaiz-byte](https://github.com/yafiqwaiz-byte)
- **Repository:** [datamanager](https://github.com/yafiqwaiz-byte/datamanager)

## Troubleshooting

### Common Issues

#### Port Already in Use
```bash
# Change port in application.yaml
server:
  port: 8081
```

#### Database Connection Issues
- Verify DB credentials are correctly set in environment variables
- Check if PostgreSQL is running
- Check firewall/network access to Supabase
- Verify Supabase project is active
- Test connection: `psql -h your-host -U your-username -d your-database`

#### Build Fails
```bash
mvn clean install -DskipTests
```

#### No Email Configuration Error
```bash
git config --global user.email "your-email@example.com"
git config --global user.name "Your Name"
```

#### JWT/Authentication Issues
- Verify JWT_SECRET is set correctly
- Check if token expiration time is appropriate
- Ensure Bearer token is included in Authorization header

### Getting Help

1. Check logs: `tail -f logs/application.log`
2. Enable debug mode in `application.yaml`:
   ```yaml
   logging:
     level:
       root: DEBUG
   ```
3. Review error messages and stack traces
4. Check GitHub issues or create a new one

---

**Last Updated:** April 2026  
**Version:** 1.0.0 (In Development)  
**Status:** 🔶 Core Features Implemented - Awaiting Extended Features
