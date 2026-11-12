import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '@/api/client';
import { renderWithProviders } from '@/test/utils';

const auth = vi.hoisted(() => ({ login: vi.fn(), sessionExpired: false }));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
const api = vi.hoisted(() => ({ register: vi.fn(), forgotPassword: vi.fn(), resetPassword: vi.fn() }));
vi.mock('./api', () => ({ accountApi: api }));

import { LoginPage, RegisterPage } from './AuthPages';

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    renderWithProviders(<LoginPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('signs in and navigates to the dashboard', async () => {
    auth.login.mockResolvedValueOnce({});
    renderWithProviders(<LoginPage />, { route: '/login', path: '/login', extraRoutes: [{ path: '/dashboard', element: <p>dashboard</p> }] });
    await userEvent.type(screen.getByLabelText(/email/i), 'citizen@example.test');
    await userEvent.type(screen.getByLabelText(/password/i), 'Correct-Horse-42');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(auth.login).toHaveBeenCalledWith('citizen@example.test', 'Correct-Horse-42');
    expect(await screen.findByText('dashboard')).toBeInTheDocument();
  });

  it('shows the generic server error on failure', async () => {
    auth.login.mockRejectedValueOnce(new ApiError({ status: 401, detail: 'Invalid email or password.', errorCode: 'INVALID_CREDENTIALS' }));
    renderWithProviders(<LoginPage />);
    await userEvent.type(screen.getByLabelText(/email/i), 'x@example.test');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
  });

  it('explains an expired session', () => {
    auth.sessionExpired = true;
    renderWithProviders(<LoginPage />);
    expect(screen.getByText(/session has expired/i)).toBeInTheDocument();
    auth.sessionExpired = false;
  });
});

describe('RegisterPage', () => {
  it('enforces the password policy, matching confirmation and consent', async () => {
    renderWithProviders(<RegisterPage />);
    await userEvent.type(screen.getByLabelText(/full name/i), 'Asha Demo');
    await userEvent.type(screen.getByLabelText(/^email/i), 'asha@example.test');
    await userEvent.type(screen.getByLabelText(/^password/i), 'short');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'different');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Use at least 12 characters')).toBeInTheDocument();
    expect(screen.getByText(/understand this is a demo system/i)).toBeInTheDocument();
    // The cross-field "match" rule runs once the individual fields are valid.
    await userEvent.clear(screen.getByLabelText(/^password/i));
    await userEvent.type(screen.getByLabelText(/^password/i), 'Valid-Passphrase-1');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
    expect(api.register).not.toHaveBeenCalled();
  });

  it('maps server field errors next to the right input', async () => {
    api.register.mockRejectedValueOnce(new ApiError({ status: 409, detail: 'An account with this email already exists.', errorCode: 'DUPLICATE_RESOURCE' }));
    renderWithProviders(<RegisterPage />);
    await userEvent.type(screen.getByLabelText(/full name/i), 'Asha Demo');
    await userEvent.type(screen.getByLabelText(/^email/i), 'asha@example.test');
    await userEvent.type(screen.getByLabelText(/^password/i), 'Valid-Passphrase-1');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'Valid-Passphrase-1');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(api.register).toHaveBeenCalled());
    expect(await screen.findByText('An account with this email already exists.')).toBeInTheDocument();
  });
});
