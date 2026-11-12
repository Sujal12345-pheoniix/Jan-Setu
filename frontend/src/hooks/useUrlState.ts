import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/** Filter/pagination state kept in the URL so lists are shareable and survive reloads. */
export function useUrlState<K extends string>(keys: readonly K[]) {
  const [params, setParams] = useSearchParams();
  const values = Object.fromEntries(keys.map((k) => [k, params.get(k) ?? ''])) as Record<K, string>;
  const page = Number(params.get('page') ?? '0') || 0;

  const set = useCallback(
    (updates: Partial<Record<K | 'page', string | number | undefined>>) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          Object.entries(updates).forEach(([k, v]) => {
            if (v === undefined || v === '' || v === null) next.delete(k);
            else next.set(k, String(v));
          });
          if (!('page' in updates)) next.delete('page');
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  return { values, page, set };
}
