import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '@/api/client';
import { ErrorState } from '@/components/ui';
import { precheckFile } from '@/features/documents/DocumentComponents';
import { renderWithProviders } from '@/test/utils';
import { render } from '@testing-library/react';

const api = vi.hoisted(() => ({
  list: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  create: vi.fn(),
}));
vi.mock('./api', () => ({ departmentsApi: api }));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ can: (p: string) => p === 'DEPARTMENT:CREATE' }) }));

import { DepartmentListPage } from './DepartmentPages';

describe('department administration', () => {
  it('creates a department with an idempotency key and shows server validation errors', async () => {
    api.create.mockRejectedValueOnce(new ApiError({
      status: 409, detail: 'A department with code TRANSPORT already exists.', errorCode: 'DUPLICATE_RESOURCE',
    }));
    api.create.mockResolvedValueOnce({ id: 'd9', name: 'Transport' });
    renderWithProviders(<DepartmentListPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New department' }));
    const dialog = await screen.findByRole('dialog', { name: 'New department' });
    await userEvent.type(within(dialog).getByLabelText(/code/i), 'TRANSPORT');
    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Transport');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByText('A department with code TRANSPORT already exists.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(api.create).toHaveBeenCalledTimes(2));
    const [body, key] = api.create.mock.calls[0];
    expect(body).toMatchObject({ code: 'TRANSPORT', name: 'Transport' });
    expect(key).toEqual(api.create.mock.calls[1][1]); // same key for the retried command
  });
});

describe('error and upload handling', () => {
  it('shows the request id for server errors and hides retry for 403', () => {
    const { rerender } = render(<ErrorState error={new ApiError({ status: 500, detail: 'Boom', requestId: 'req-1' })} onRetry={() => {}} />);
    expect(screen.getByText('Reference: req-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    rerender(<ErrorState error={new ApiError({ status: 403, detail: 'nope' })} onRetry={() => {}} />);
    expect(screen.getByText('You do not have permission to view this.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('pre-checks file type and size before uploading', () => {
    const pdf = new File(['%PDF-1.4'], 'id.pdf', { type: 'application/pdf' });
    expect(precheckFile(pdf)).toBeNull();
    expect(precheckFile(new File(['x'], 'malware.exe'))).toMatch(/Unsupported file type/);
    expect(precheckFile(new File([new Uint8Array(2048)], 'big.png'), 'image/png', 1024)).toMatch(/too large/);
    expect(precheckFile(pdf, 'image/png')).toMatch(/Unsupported/);
  });
});
