/**
 * Saved list views (TZ §9): a named set of filters the user can come back to. Kept in localStorage —
 * the module has no per-user settings store of its own, and view preferences are not case data.
 */
export interface SavedView<F> {
  name: string;
  filters: F;
}

export function loadSavedViews<F>(storageKey: string): SavedView<F>[] {
  try {
    const raw = localStorage.getItem(storageKey);
    return raw ? (JSON.parse(raw) as SavedView<F>[]) : [];
  } catch {
    return [];
  }
}

export function persistSavedViews<F>(storageKey: string, views: SavedView<F>[]): void {
  localStorage.setItem(storageKey, JSON.stringify(views));
}
