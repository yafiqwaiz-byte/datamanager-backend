# DataManager

A comprehensive Spring Boot REST API application for managing user accounts, staff data, form submissions, file uploads, OCR processing, and data visualization through dashboards. Built with PostgreSQL/Supabase database and OCR service integration.

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

### System Components
- **Web App (Frontend)** - User and Staff interfaces
- **Backend API** - Spring Boot REST API server
- **Database** - PostgreSQL (Supabase)
- **OCR Service** - Text extraction from images
- **Data Processing Engine** - Data cleaning and validation
- **Dashboard Engine** - Real-time data visualization

### Entity Relationship Diagram

The system manages the following entities:

```
User (with profile, company info, contact details)
├── Account (login credentials, role, status)
├── Form_Submission (user submissions)
│   ├── Form_Template (form structure and fields)
│   └── Form_Field (individual form fields)
└── Upload (file management)
    └── Processed_data (cleaned and validated data)

Staff (manages system)
├── Account (staff credentials)
├── Upload (handles file uploads)
└── Dashboard (views and manages data visualizations)

Export_log (tracks data exports)
```

## Project Structure

```
src/main/java/dev/waiz/datamanager/
├── controller/
│   ├── accountcontroller.java      # Account REST endpoints
│   ├── staffcontroller.java        # Staff REST endpoints
│   ├── usercontroller.java         # User REST endpoints
│   ├── uploadcontroller.java       # File upload endpoints (future)
│   ├── formsubmissioncontroller.java # Form submission endpoints (future)
│   └── dashboardcontroller.java    # Dashboard endpoints (future)
├── model/
│   ├── account.java                # Account entity
│   ├── staff.java                  # Staff entity
│   ├── user.java                   # User entity
│   ├── upload.java                 # Upload entity (future)
│   ├── form_submission.java        # Form submission entity (future)
│   ├── form_template.java          # Form template entity (future)
│   ├── form_field.java             # Form field entity (future)
│   ├── processed_data.java         # Processed data entity (future)
│   ├── dashboard.java              # Dashboard entity (future)
│   └── export_log.java             # Export log entity (future)
├── service/
│   ├── accountservice.java         # Account business logic
│   ├── uploadservice.java          # File upload service (future)
│   ├── ocrservice.java             # OCR processing service (future)
│   ├── dataprocessingservice.java  # Data processing logic (future)
│   └── dashboardservice.java       # Dashboard service (future)
├── repository/
│   ├── accountrepository.java      # Account database access
│   ├── uploadrepository.java       # Upload repository (future)
│   ├── formsubmissionrepository.java # Form submission repository (future)
│   └── processed_datarepository.java # Processed data repository (future)
└── DatamanagerApplication.java     # Main application class
```

## System Workflows

### 1. Login/Registration Flow
- User/Staff registers with credentials
- Backend validates and stores account in database
- JWT token generated for authentication
- User gains access to home page

### 2. User Flow
- User inputs form data and uploads images
- Form data stored in Form_Submission table
- Images sent to OCR service for text extraction
- Extracted data stored in database
- Data processed and cleaned
- Cleaned data displayed in user dashboard
- User can export processed data

### 3. Staff Flow
- Staff requests user data from backend
- Backend fetches data records from database
- Staff can filter/sort data
- Staff can export data table to file
- Export tracked in Export_log

### 4. File Upload Flow
- User uploads image/document
- File sent to OCR service
- OCR extracts text and returns data
- Data validation and formatting
- Valid data stored in Processed_data table
- Invalid data tracked with error logs
- Dashboard generated with processed data

### 5. Data Processing
- Clean and format extracted data
- Validate data against requirements
- Handle errors and log issues
- Store in Processed_data table
- Generate dashboard configuration

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

✅ **Completed:**
- Account Management (CRUD operations)
- Account Service Layer
- Account Repository
- User Model
- Staff Model
- Database configuration with PostgreSQL

⏳ **In Progress:**
- Staff Controller & Service
- User Controller & Service

📋 **Upcoming:**
- Upload Management & File Processing
- Form Management System
- Data Processing Engine
- OCR Service Integration
- Dashboard System
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
