// Authentication Service for API calls
const API_BASE_URL = 'http://localhost:8080/api'; // Update this to your backend URL

export const authService = {
  // Signin API call
  signin: async (username, password) => {
    try {
      const response = await fetch(`${API_BASE_URL}/accounts/signin`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username,
          password,
        }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error || 'Signin failed');
      }

      const data = await response.json();
      // Store JWT token
      if (data.token) {
        localStorage.setItem('authToken', data.token);
      }
      // Store user data
      localStorage.setItem('user', JSON.stringify(data));
      return data;
    } catch (error) {
      console.error('Signin error:', error);
      throw error;
    }
  },

  // Signup API call for User
  signupUser: async (accountData, userData) => {
    try {
      const response = await fetch(`${API_BASE_URL}/accounts/signup/user`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username: accountData.username,
          password: accountData.password,
          fullName: userData.fullName,
          companyName: userData.companyName,
          phoneNo: userData.phoneNo,
          companyAddress: userData.address,
        }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error || 'User registration failed');
      }

      const data = await response.json();
      // Store JWT token
      if (data.token) {
        localStorage.setItem('authToken', data.token);
      }
      // Store user data
      localStorage.setItem('user', JSON.stringify(data));
      return data;
    } catch (error) {
      console.error('User signup error:', error);
      throw error;
    }
  },

  // Signup API call for Staff
  signupStaff: async (accountData, staffData) => {
    try {
      const response = await fetch(`${API_BASE_URL}/accounts/signup/staff`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username: accountData.username,
          password: accountData.password,
          fullName: staffData.fullName,
          department: staffData.department,
          position: staffData.position,
        }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error || 'Staff registration failed');
      }

      const data = await response.json();
      // Store JWT token
      if (data.token) {
        localStorage.setItem('authToken', data.token);
      }
      // Store user data
      localStorage.setItem('user', JSON.stringify(data));
      return data;
    } catch (error) {
      console.error('Staff signup error:', error);
      throw error;
    }
  },

  // Logout
  logout: () => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('user');
  },

  // Get current user
  getCurrentUser: () => {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  },

  // Get JWT token
  getToken: () => {
    return localStorage.getItem('authToken');
  },

  // Check if user is logged in
  isLoggedIn: () => {
    return !!localStorage.getItem('authToken');
  },

  // Helper: Get Authorization header for API calls
  getAuthHeaders: () => {
    const token = localStorage.getItem('authToken');
    return {
      'Content-Type': 'application/json',
      ...(token && { 'Authorization': `Bearer ${token}` }),
    };
  },

  // Helper: Make authenticated API call
  fetchWithAuth: async (url, options = {}) => {
    const headers = authService.getAuthHeaders();
    return fetch(url, {
      ...options,
      headers: { ...headers, ...options.headers },
    });
  },
};
