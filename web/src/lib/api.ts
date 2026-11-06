import axios from 'axios';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '/api/v1';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor for auth token
api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Interceptor for 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('access_token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

// Auth API
export const authApi = {
  login: (tenantCode: string, email: string, password: string) =>
    api.post('/auth/login', { tenantCode, email, password }),
};

// Students API
export const studentsApi = {
  list: (params?: { page?: number; limit?: number; search?: string; status?: string; classId?: string }) =>
    api.get('/students', { params }),
  getById: (id: string) => api.get(`/students/${id}`),
  create: (data: any) => api.post('/students', data),
  update: (id: string, data: any) => api.patch(`/students/${id}`, data),
  delete: (id: string) => api.delete(`/students/${id}`),
  import: (data: { students: any[] }) => api.post('/students/import', data),
};

// Classes API
export const classesApi = {
  list: (academicYear?: string) => api.get('/classes', { params: { academicYear } }),
  getById: (id: string) => api.get(`/classes/${id}`),
  create: (data: any) => api.post('/classes', data),
  update: (id: string, data: any) => api.patch(`/classes/${id}`, data),
  delete: (id: string) => api.delete(`/classes/${id}`),
};

// Transactions API
export const transactionsApi = {
  list: (params?: { page?: number; limit?: number; type?: string; status?: string; studentId?: string; startDate?: string; endDate?: string }) =>
    api.get('/transactions', { params }),
  getById: (id: string) => api.get(`/transactions/${id}`),
  listByStudent: (studentId: string, params?: { page?: number; limit?: number }) =>
    api.get(`/transactions/student/${studentId}`, { params }),
  void: (id: string, reason: string) => api.post(`/transactions/${id}/void`, { reason }),
};

// Ledger API
export const ledgerApi = {
  recordCashPayment: (data: { studentId: string; amountCents: string; idempotencyKey?: string; note?: string }) =>
    api.post('/ledger/record-cash-payment', data),
  getReceipt: (transactionId: string) => api.get(`/ledger/receipts/${transactionId}`),
};

// Dashboard API
export const dashboardApi = {
  getSummary: () => api.get('/dashboard/summary'),
};

// Users API
export const usersApi = {
  list: () => api.get('/users'),
  getById: (id: string) => api.get(`/users/${id}`),
  create: (data: any) => api.post('/users', data),
  update: (id: string, data: any) => api.patch(`/users/${id}`, data),
  suspend: (id: string) => api.post(`/users/${id}/suspend`),
  activate: (id: string) => api.post(`/users/${id}/activate`),
};

// Reports API
export const reportsApi = {
  getCollections: (params?: { startDate?: string; endDate?: string }) =>
    api.get('/dashboard/reports/collections', { params }),
  getStudentDebts: () => api.get('/dashboard/reports/students-debt'),
};

// Tenant API
export const tenantApi = {
  getMe: () => api.get('/tenants/me'),
};

// Finance API (trésorerie : solde, retraits, grand livre)
export const financeApi = {
  getBalance: () => api.get('/schools/balance'),
  listWithdrawals: (params?: { page?: number; limit?: number }) =>
    api.get('/schools/withdrawals', { params }),
  listLedgerEntries: (params?: { page?: number; limit?: number }) =>
    api.get('/schools/ledger', { params }),
  requestWithdrawal: (data: { amountCents: number; method: string; destination: string }) =>
    api.post('/schools/withdraw', data),
};
