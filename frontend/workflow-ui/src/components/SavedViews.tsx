// Named filter sets a user can come back to (TZ §9); storage helpers live in savedViewsStorage.ts.
import { useState } from 'react';
import { Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material';
import BookmarkAddOutlinedIcon from '@mui/icons-material/BookmarkAddOutlined';
import { colors } from '../colors';
import { loadSavedViews, persistSavedViews, type SavedView } from './savedViewsStorage';

export function SavedViews<F>({
  storageKey,
  currentFilters,
  onApply,
}: {
  storageKey: string;
  currentFilters: F;
  onApply: (filters: F) => void;
}) {
  const [views, setViews] = useState<SavedView<F>[]>(() => loadSavedViews<F>(storageKey));
  const [saveOpen, setSaveOpen] = useState(false);
  const [name, setName] = useState('');

  const save = () => {
    const next = [...views.filter((view) => view.name !== name.trim()), { name: name.trim(), filters: currentFilters }];
    setViews(next);
    persistSavedViews(storageKey, next);
    setSaveOpen(false);
    setName('');
  };

  const remove = (viewName: string) => {
    const next = views.filter((view) => view.name !== viewName);
    setViews(next);
    persistSavedViews(storageKey, next);
  };

  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
      {views.map((view) => (
        <Chip
          key={view.name}
          label={view.name}
          onClick={() => onApply(view.filters)}
          onDelete={() => remove(view.name)}
          sx={{ color: colors.text.body }}
        />
      ))}
      <Button size="small" startIcon={<BookmarkAddOutlinedIcon />} onClick={() => setSaveOpen(true)}>
        Сохранить представление
      </Button>

      <Dialog open={saveOpen} onClose={() => setSaveOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Сохранить представление</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            label="Название"
            value={name}
            onChange={(event) => setName(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSaveOpen(false)}>Отмена</Button>
          <Button variant="contained" disabled={!name.trim()} onClick={save}>
            Сохранить
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
