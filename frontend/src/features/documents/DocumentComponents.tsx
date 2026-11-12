import { useRef, useState } from 'react';
import { Download, Eye, FileText, RotateCcw, Upload, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import { Alert, Badge, Button, StatusBadge } from '@/components/ui';
import type { DocumentView } from '@/types/api';
import { formatBytes, formatDateTime } from '@/utils/format';

const DEFAULT_TYPES = ['application/pdf', 'image/png', 'image/jpeg'];
const EXTENSIONS: Record<string, string[]> = { 'application/pdf': ['pdf'], 'image/png': ['png'], 'image/jpeg': ['jpg', 'jpeg'] };
const DEFAULT_MAX = 5 * 1024 * 1024;

/** Client-side pre-check for fast feedback. The server re-validates type (magic bytes) and size. */
export function precheckFile(file: File, allowedCsv?: string, maxBytes?: number): string | null {
  const allowed = allowedCsv ? allowedCsv.split(',').map((t) => t.trim()) : DEFAULT_TYPES;
  const max = Math.min(maxBytes ?? DEFAULT_MAX, 10 * 1024 * 1024);
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  const extOk = allowed.some((t) => EXTENSIONS[t]?.includes(ext));
  if (!extOk) return `Unsupported file type. Allowed: ${allowed.flatMap((t) => EXTENSIONS[t] ?? []).join(', ').toUpperCase()}.`;
  if (file.size === 0) return 'The file is empty.';
  if (file.size > max) return `The file is too large (${formatBytes(file.size)}). Maximum ${formatBytes(max)}.`;
  return null;
}

export function DocumentUploader({ label, allowedTypes, maxBytes, onUpload, disabled }: {
  label: string;
  allowedTypes?: string;
  maxBytes?: number;
  disabled?: boolean;
  onUpload: (file: File, onProgress: (p: number) => void, signal: AbortSignal) => Promise<unknown>;
}) {
  const input = useRef<HTMLInputElement>(null);
  const controller = useRef<AbortController | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const start = async (selected: File) => {
    const problem = precheckFile(selected, allowedTypes, maxBytes);
    setFile(selected);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    setProgress(0);
    controller.current = new AbortController();
    try {
      await onUpload(selected, setProgress, controller.current.signal);
      setFile(null);
      if (input.current) input.current.value = '';
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') setError('Upload cancelled.');
      else setError(e instanceof ApiError ? e.message : 'Upload failed. Please retry.');
    } finally {
      setProgress(null);
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={input}
          type="file"
          className="sr-only"
          accept={(allowedTypes ?? DEFAULT_TYPES.join(',')).toString()}
          aria-label={label}
          disabled={disabled || progress !== null}
          onChange={(e) => e.target.files?.[0] && start(e.target.files[0])}
        />
        <Button
          variant="secondary"
          size="sm"
          icon={<Upload className="h-4 w-4" aria-hidden />}
          disabled={disabled || progress !== null}
          onClick={() => input.current?.click()}
        >
          {label}
        </Button>
        {error && file && !error.startsWith('Unsupported') && !error.includes('too large') && (
          <Button variant="ghost" size="sm" icon={<RotateCcw className="h-4 w-4" aria-hidden />} onClick={() => start(file)}>
            Retry
          </Button>
        )}
      </div>
      {progress !== null && (
        <div className="flex items-center gap-2">
          <div className="h-2 flex-1 overflow-hidden rounded bg-line" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100} aria-label={`Uploading ${file?.name}`}>
            <div className="h-full bg-brand-600 transition-all" style={{ width: `${progress}%` }} />
          </div>
          <span className="w-10 text-xs text-muted">{progress}%</span>
          <button type="button" aria-label="Cancel upload" onClick={() => controller.current?.abort()} className="text-muted hover:text-ink">
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}

/** Opens a downloaded blob: inline preview for PDF/images (new tab), otherwise a file download. */
export async function openBlob(fetcher: () => Promise<{ blob: Blob; fileName?: string }>, preview: boolean) {
  const { blob, fileName } = await fetcher();
  const url = URL.createObjectURL(blob);
  if (preview && (blob.type === 'application/pdf' || blob.type.startsWith('image/'))) {
    window.open(url, '_blank', 'noopener');
  } else {
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName ?? 'document';
    a.click();
  }
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export function DocumentCard({ document, title, onDownload, actions }: {
  document: DocumentView;
  title: string;
  onDownload: (version?: number) => Promise<{ blob: Blob; fileName?: string }>;
  actions?: React.ReactNode;
}) {
  const [error, setError] = useState<string | null>(null);
  const run = async (preview: boolean, version?: number) => {
    setError(null);
    try {
      await openBlob(() => onDownload(version), preview);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not open the document.');
    }
  };
  return (
    <div className="rounded-md border border-line p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex gap-2">
          <FileText className="mt-0.5 h-5 w-5 text-brand-600" aria-hidden />
          <div>
            <p className="text-sm font-medium">{title}</p>
            <p className="text-xs text-muted">
              {document.fileName} · {formatBytes(document.sizeBytes)} · v{document.currentVersion} · {formatDateTime(document.uploadedAt)}
            </p>
          </div>
        </div>
        <StatusBadge status={document.status} />
      </div>
      {document.status === 'REJECTED' && document.rejectionReason && (
        <Alert tone="warning" className="mt-2">
          Rejected: {document.rejectionReason}
        </Alert>
      )}
      <div className="mt-2 flex flex-wrap gap-2">
        <Button size="sm" variant="ghost" icon={<Eye className="h-4 w-4" aria-hidden />} onClick={() => run(true)}>
          Preview
        </Button>
        <Button size="sm" variant="ghost" icon={<Download className="h-4 w-4" aria-hidden />} onClick={() => run(false)}>
          Download
        </Button>
        {actions}
      </div>
      {document.versions.length > 1 && (
        <details className="mt-2 text-xs">
          <summary className="cursor-pointer text-muted">Version history ({document.versions.length})</summary>
          <ul className="mt-1 space-y-1">
            {document.versions.map((v) => (
              <li key={v.versionNumber} className="flex items-center gap-2">
                <Badge>v{v.versionNumber}</Badge>
                <span>{v.fileName}</span>
                <span className="text-muted">{formatDateTime(v.uploadedAt)}</span>
                <button type="button" className="text-brand-700 hover:underline" onClick={() => run(false, v.versionNumber)}>
                  Download
                </button>
              </li>
            ))}
          </ul>
        </details>
      )}
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}
    </div>
  );
}
