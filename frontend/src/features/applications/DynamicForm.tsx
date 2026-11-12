import { z } from 'zod';
import type { UseFormReturn } from 'react-hook-form';
import { Checkbox, Select, TextArea, TextInput } from '@/components/ui';
import type { FormField } from '@/types/api';

export type FormValues = Record<string, string | boolean | undefined>;

/**
 * Builds a Zod schema from the service's versioned form definition. Drafts allow empty required fields;
 * submission requires them. The backend (FormDataValidator) applies the same rules authoritatively.
 */
export function buildFormSchema(fields: FormField[], forSubmission: boolean) {
  const shape: Record<string, z.ZodTypeAny> = {};
  for (const field of fields) {
    const requiredMsg = `${field.label} is required`;
    let schema: z.ZodTypeAny;
    switch (field.type) {
      case 'checkbox':
        schema = z.boolean().optional();
        if (forSubmission && field.required) schema = z.literal(true, { errorMap: () => ({ message: requiredMsg }) });
        shape[field.key] = schema;
        continue;
      case 'number':
        schema = z.string().trim().refine((v) => v === '' || !Number.isNaN(Number(v)), 'Enter a number');
        break;
      case 'date':
        schema = z.string().trim().refine((v) => v === '' || /^\d{4}-\d{2}-\d{2}$/.test(v), 'Enter a valid date');
        break;
      case 'email':
        schema = z.string().trim().refine((v) => v === '' || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v), 'Enter a valid email');
        break;
      case 'phone':
        schema = z.string().trim().refine((v) => v === '' || /^\+?[0-9][0-9 -]{6,18}$/.test(v), 'Enter a valid phone number');
        break;
      case 'select':
        schema = z.string().refine((v) => v === '' || (field.options ?? []).includes(v), 'Choose one of the options');
        break;
      case 'textarea':
        schema = z.string().max(field.maxLength ?? 4000, 'Too long');
        break;
      default:
        schema = z.string().max(field.maxLength ?? 500, 'Too long');
    }
    if (forSubmission && field.required) {
      schema = (schema as z.ZodString).refine((v: string) => v.trim() !== '', requiredMsg);
    }
    shape[field.key] = schema.optional().transform((v) => v ?? '');
  }
  return z.object(shape);
}

/** Converts server form data to input values (strings/booleans). */
export function toFormValues(fields: FormField[], data: Record<string, unknown>): FormValues {
  return Object.fromEntries(
    fields.map((f) => {
      const v = data[f.key];
      if (f.type === 'checkbox') return [f.key, v === true];
      return [f.key, v === undefined || v === null ? '' : String(v)];
    }),
  );
}

/** Converts input values to typed JSON for the API (numbers as numbers, empty fields omitted). */
export function toPayload(fields: FormField[], values: FormValues, declarations: string[]): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  for (const f of fields) {
    const v = values[f.key];
    if (f.type === 'checkbox') {
      if (v === true) payload[f.key] = true;
      continue;
    }
    if (typeof v !== 'string' || v.trim() === '') continue;
    payload[f.key] = f.type === 'number' ? Number(v) : v.trim();
  }
  if (declarations.length > 0) payload._declarations = declarations;
  return payload;
}

export function DynamicForm({ fields, form, disabled }: { fields: FormField[]; form: UseFormReturn<FormValues>; disabled?: boolean }) {
  const errors = form.formState.errors;
  if (fields.length === 0) return <p className="text-sm text-muted">This service has no form fields.</p>;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {fields.map((field) => {
        const error = errors[field.key]?.message as string | undefined;
        const common = { label: field.label, required: field.required, hint: field.helpText, error, disabled };
        switch (field.type) {
          case 'textarea':
            return <TextArea key={field.key} className="sm:col-span-2" {...common} {...form.register(field.key)} />;
          case 'select':
            return (
              <Select
                key={field.key}
                {...common}
                placeholder="Select…"
                options={(field.options ?? []).map((o) => ({ value: o, label: o }))}
                {...form.register(field.key)}
              />
            );
          case 'checkbox':
            return (
              <Checkbox key={field.key} className="sm:col-span-2" label={field.label} error={error} disabled={disabled} {...form.register(field.key)} />
            );
          default:
            return (
              <TextInput
                key={field.key}
                {...common}
                type={field.type === 'number' ? 'number' : field.type === 'date' ? 'date' : field.type === 'email' ? 'email' : field.type === 'phone' ? 'tel' : 'text'}
                inputMode={field.type === 'number' ? 'decimal' : undefined}
                {...form.register(field.key)}
              />
            );
        }
      })}
    </div>
  );
}

export function AnswerList({ fields, data }: { fields: FormField[]; data: Record<string, unknown> }) {
  return (
    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
      {fields.map((f) => (
        <div key={f.key}>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted">{f.label}</dt>
          <dd className="mt-0.5 text-sm">
            {data[f.key] === undefined || data[f.key] === '' ? '—' : f.type === 'checkbox' ? (data[f.key] ? 'Yes' : 'No') : String(data[f.key])}
          </dd>
        </div>
      ))}
    </dl>
  );
}
