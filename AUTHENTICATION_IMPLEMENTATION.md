# Backend Authentication Implementation Guide

## Overview
This document outlines all the changes made to implement secure JWT authentication with password hashing in your Spring Boot backend.

## Changes Made

### 1. **Added JWT Dependency (pom.xml)**
Added JJWT library for JWT token generation and validation:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

### 2. **Created JWT Utility Class** (`JwtUtil.java`)
- Generates JWT tokens with username and role claims
- Validates tokens and extracts claims
- Uses BCrypt algorithm (HS256) for signing
- Configurable expiration time via environment variables

### 3. **Created DTOs** (Data Transfer Objects)
Four new DTOs in `/dto` directory:
- `SigninRequest.java` - For login requests (username, password)
- `SignupUserRequest.java` - For user registration
- `SignupStaffRequest.java` - For staff registration
- `AuthResponse.java` - Response with JWT token, username, role, and user data

### 4. **Updated AccountService** (`accountservice.java`)
**Key Changes:**
- Added BCryptPasswordEncoder for secure password hashing
- `createAccount()` - Now hashes passwords before storing
- `verifyCredentials()` - Uses BCrypt password matching instead of plain text comparison
- `updateAccountPassword()` - Hashes new passwords before storing
- All existing methods remain backward compatible

### 5. **Updated AccountController** (`accountcontroller.java`)
**New Endpoints:**

#### `/api/accounts/signin` (POST)
```json
Request:
{
  "username": "user123",
  "password": "password123"
}

Response (200 OK):
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "user123",
  "role": "USER",
  "user": { /* User or Staff object */ }
}
```

#### `/api/accounts/signup/user` (POST)
```json
Request:
{
  "username": "newuser",
  "password": "securepassword",
  "fullName": "John Doe",
  "companyName": "Acme Corp",
  "phoneNo": "123-456-7890",
  "companyAddress": "123 Main St"
}

Response (201 Created):
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "newuser",
  "role": "USER",
  "user": { /* User object */ }
}
```

#### `/api/accounts/signup/staff` (POST)
```json
Request:
{
  "username": "newstaff",
  "password": "securepassword",
  "fullName": "Jane Smith",
  "department": "IT",
  "position": "Developer"
}

Response (201 Created):
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "newstaff",
  "role": "STAFF",
  "user": { /* Staff object */ }
}
```

### 6. **Updated application.yaml**
Added JWT configuration:
```yaml
jwt:
  secret: ${JWT_SECRET:mySecretKeyThatIsAtLeast256BitsLongForHS256Algorithm}
  expiration: ${JWT_EXPIRATION:3600000}
```

### 7. **CORS Configuration Already Present**
The `DatamanagerApplication.java` already has CORS configured to allow:
- Origins: http://localhost:3000, http://localhost:8080
- Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Headers: All headers allowed
- Exposed Headers: Authorization, Content-Type

## Updated React Frontend Service

The `auth.service.js` has been updated with:

### New Features:
1. **JWT Token Storage** - Stores and retrieves `authToken` in localStorage
2. **Updated API Endpoints**:
   - Signin: `/api/accounts/signin`
   - User Signup: `/api/accounts/signup/user`
   - Staff Signup: `/api/accounts/signup/staff`

3. **Helper Methods**:
   - `getToken()` - Retrieves stored JWT token
   - `getAuthHeaders()` - Returns headers with Authorization bearer token
   - `fetchWithAuth()` - Makes authenticated API calls automatically

### Usage Example:
```javascript
// Signin
const response = await authService.signin('username', 'password');
console.log(response.token); // JWT token

// User Signup
const newUser = await authService.signupUser(
  { username: 'newuser', password: 'pass123' },
  { fullName: 'John', companyName: 'Company', phoneNo: '123', address: '...' }
);

// Make authenticated request
const headers = authService.getAuthHeaders();
const result = await authService.fetchWithAuth(`${API_BASE_URL}/users`, {
  method: 'GET'
});
```

## Environment Setup

### 1. Create `.env` file in project root:
```
DB_URL=jdbc:postgresql://localhost:5432/datamanager
DB_USERNAME=postgres
DB_PASSWORD=your_password

JWT_SECRET=myVerySecureSecretKeyThatIsAtLeast32CharactersLongForHS256
JWT_EXPIRATION=3600000
```

### 2. Update Frontend Configuration:
Set correct API base URL in `auth.service.js`:
```javascript
const API_BASE_URL = 'http://localhost:8080/api';
```

## Building & Running

### Build Backend:
```bash
cd datamanager
./mvnw.cmd clean package
```

### Run Backend:
```bash
./mvnw.cmd spring-boot:run
```

### Run Frontend:
```bash
npm start  # from your React project directory
```

## Security Notes

1. **Password Hashing**: All passwords are hashed using BCrypt (strength: 10) before storage
2. **JWT Secret**: Change the default JWT_SECRET to a secure, random value (min 32 characters)
3. **Token Expiration**: Default is 1 hour (3600000 ms), configurable via JWT_EXPIRATION
4. **CORS**: Currently allows localhost:3000 and 8080. Update for production domains
5. **HTTPS**: Always use HTTPS in production for authentication endpoints

## Testing the Endpoints

### Using cURL:

**Signin:**
```bash
curl -X POST http://localhost:8080/api/accounts/signin \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'
```

**User Signup:**
```bash
curl -X POST http://localhost:8080/api/accounts/signup/user \
  -H "Content-Type: application/json" \
  -d '{
    "username":"newuser",
    "password":"pass123",
    "fullName":"John Doe",
    "companyName":"Acme",
    "phoneNo":"555-1234",
    "companyAddress":"123 Main St"
  }'
```

**Authenticated Request:**
```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

## Backward Compatibility

All existing endpoints remain unchanged:
- GET /api/accounts - Get all accounts
- GET /api/accounts/{id} - Get account by ID
- PUT /api/accounts/{id} - Update account
- DELETE /api/accounts/{id} - Delete account
- PATCH /api/accounts/{id}/status - Update status
- PATCH /api/accounts/{id}/password - Update password
- POST /api/accounts/verify - Verify credentials (legacy)

## Next Steps (Optional)

1. Implement JWT filter to validate tokens on protected endpoints
2. Add role-based access control (RBAC)
3. Implement refresh token mechanism
4. Add email verification for new registrations
5. Implement password reset functionality
6. Add rate limiting for login attempts
