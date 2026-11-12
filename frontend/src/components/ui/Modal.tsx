import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { Button } from './Button';
import { TextArea } from './Form';

/** Accessible modal dialog: focus moves in, Tab is trapped, Escape closes, focus returns to the opener. */
export function Modal({ open, title, onClose, children, footer, size = 'md' }: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'md' | 'lg';
}) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);
  // Keep the latest onClose without re-running the focus effect (inline callbacks change every render,
  // which would otherwise steal focus back to the first field while the user types).
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const focusable = () =>
      Array.from(panel.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ) ?? []);
    (focusable()[1] ?? focusable()[0])?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
      if (e.key === 'Tab') {
        const items = focusable();
        if (items.length === 0) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      previouslyFocused?.focus();
    };
  }, [open]);

  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4" onMouseDown={onClose}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onMouseDown={(e) => e.stopPropagation()}
        className={`max-h-[90vh] w-full overflow-y-auto rounded-t-xl bg-white shadow-xl sm:rounded-xl ${size === 'lg' ? 'sm:max-w-2xl' : 'sm:max-w-md'}`}
      >
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <h2 id={titleId} className="text-base font-semibold">
            {title}
          </h2>
          <button type="button" onClick={onClose} className="rounded p-1 text-muted hover:bg-canvas" aria-label="Close dialog">
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>
        <div className="space-y-4 px-4 py-4">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-line px-4 py-3">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

/** Confirmation for irreversible or significant actions. */
export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', danger, loading, onConfirm, onCancel }: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onCancel}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-sm text-ink">{message}</div>
    </Modal>
  );
}

/** Asks for a reason/remarks before an action. `required` enforces a non-empty value (server enforces too). */
export function ReasonDialog({ open, title, label, confirmLabel, required, danger, loading, error, onSubmit, onCancel }: {
  open: boolean;
  title: string;
  label: string;
  confirmLabel: string;
  required?: boolean;
  danger?: boolean;
  loading?: boolean;
  error?: string;
  onSubmit: (reason: string) => void;
  onCancel: () => void;
}) {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (open) {
      setReason('');
      setTouched(false);
    }
  }, [open]);
  const missing = required && reason.trim().length === 0;
  return (
    <Modal
      open={open}
      title={title}
      onClose={onCancel}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            loading={loading}
            onClick={() => {
              setTouched(true);
              if (!missing) onSubmit(reason.trim());
            }}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <TextArea
        label={label}
        required={required}
        value={reason}
        maxLength={2000}
        onChange={(e) => setReason(e.target.value)}
        error={touched && missing ? `${label} is required.` : error}
      />
    </Modal>
  );
}
