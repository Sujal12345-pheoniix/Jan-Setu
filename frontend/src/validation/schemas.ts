import { z } from 'zod';

/**
 * Client-side mirrors of backend validation for immediate feedback. The server re-validates everything
 * (PasswordPolicy, Bean Validation); never rely on these alone.
 */
export const emailSchema = z.string().trim().min(1, 'Email is required').max(254).email('Enter a valid email address');

export const passwordSchema = z
  .string()
  .min(12, 'Use at least 12 characters')
  .max(64, 'Use at most 64 characters')
  .refine((v) => /\p{L}/u.test(v) && /\d/.test(v), 'Include at least one letter and one digit');

export const phoneSchema = z
  .string()
  .trim()
  .regex(/^(\+?[0-9][0-9 -]{6,18})?$/, 'Enter a valid phone number')
  .optional()
  .or(z.literal(''));

export const nameSchema = z.string().trim().min(2, 'Enter at least 2 characters').max(150);

export const registerSchema = z
  .object({
    fullName: nameSchema,
    email: emailSchema,
    phone: phoneSchema,
    password: passwordSchema,
    confirmPassword: z.string(),
    consent: z.literal(true, { errorMap: () => ({ message: 'Confirm that you understand this is a demo system' }) }),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Password is required'),
});

export const resetSchema = z
  .object({ password: passwordSchema, confirmPassword: z.string() })
  .refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });

export const grievanceSchema = z.object({
  departmentId: z.string().optional(),
  applicationId: z.string().optional(),
  category: z.string().min(1, 'Choose a category'),
  subject: z.string().trim().min(5, 'Use at least 5 characters').max(200),
  description: z.string().trim().min(20, 'Describe the issue in at least 20 characters').max(4000),
});
