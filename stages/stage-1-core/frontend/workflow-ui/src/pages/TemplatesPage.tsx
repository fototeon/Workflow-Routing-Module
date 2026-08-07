import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import {
  addRoutingRule,
  archiveProcessDefinition,
  createProcessDefinition,
  deleteRoutingRule,
  listProcessDefinitionVersions,
  listRoutingRules,
  publishProcessDefinition,
} from '../api/processDefinitions';
import type { LeafCondition, ProcessDefinition, RoutingRule } from '../api/types';
import { StatusChip } from '../components/StatusChip';
import { DEFINITION_STATUS_LABELS } from '../statusLabels';

const COMPARISON_OPS: LeafCondition['op'][] = ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE'];

/** Numbers and booleans are sent as such; everything else stays a string. */
function parseValue(raw: string): unknown {
  const trimmed = raw.trim();
  if (trimmed === 'true' || trimmed === 'false') return trimmed === 'true';
  if (trimmed !== '' && !Number.isNaN(Number(trimmed))) return Number(trimmed);
  return trimmed;
}

export function TemplatesPage() {
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [searchCode, setSearchCode] = useState('');
  const [selected, setSelected] = useState<ProcessDefinition | null>(null);
  const [rules, setRules] = useState<RoutingRule[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');

  const [ruleName, setRuleName] = useState('');
  const [rulePriority, setRulePriority] = useState('10');
  const [targetStepCode, setTargetStepCode] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const [field, setField] = useState('');
  const [op, setOp] = useState<LeafCondition['op']>('EQ');
  const [value, setValue] = useState('');

  const selectDefinition = (def: ProcessDefinition) => {
    setSelected(def);
    setError(null);
    listRoutingRules(def.id).then(setRules).catch(() => setRules([]));
  };

  return (
    <Box>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              Поиск по коду
            </Typography>
            <Stack direction="row" spacing={1}>
              <TextField
                size="small"
                label="Код процесса"
                value={searchCode}
                onChange={(e) => setSearchCode(e.target.value)}
              />
              <Button
                onClick={() => {
                  if (searchCode.trim()) {
                    listProcessDefinitionVersions(searchCode.trim()).then(setDefinitions);
                  }
                }}
              >
                Найти
              </Button>
            </Stack>
            <List dense>
              {definitions.map((def) => (
                <ListItem key={def.id} disablePadding>
                  <ListItemButton selected={selected?.id === def.id} onClick={() => selectDefinition(def)}>
                    <ListItemText
                      primary={`${def.code} v${def.version}`}
                      secondary={DEFINITION_STATUS_LABELS[def.status].label}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              Новый шаблон (черновик)
            </Typography>
            <Stack spacing={2}>
              <TextField size="small" label="Код" value={newCode} onChange={(e) => setNewCode(e.target.value)} />
              <TextField size="small" label="Название" value={newName} onChange={(e) => setNewName(e.target.value)} />
              <Button
                variant="contained"
                disabled={!newCode.trim() || !newName.trim()}
                onClick={async () => {
                  const created = await createProcessDefinition({ code: newCode, name: newName });
                  setNewCode('');
                  setNewName('');
                  setSearchCode(created.code);
                  listProcessDefinitionVersions(created.code).then(setDefinitions);
                  selectDefinition(created);
                }}
              >
                Создать
              </Button>
            </Stack>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          {!selected && <Typography color="text.secondary">Выберите или создайте шаблон процесса.</Typography>}
          {selected && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              {error && (
                <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
                  {error}
                </Alert>
              )}
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Box>
                  <Typography variant="h6">
                    {selected.code} v{selected.version}
                  </Typography>
                  <Box sx={{ mt: 0.5 }}>
                    <StatusChip
                      label={DEFINITION_STATUS_LABELS[selected.status].label}
                      tone={DEFINITION_STATUS_LABELS[selected.status].tone}
                    />
                  </Box>
                </Box>
                <Stack direction="row" spacing={1}>
                  {selected.status === 'DRAFT' && (
                    <Button
                      variant="contained"
                      onClick={async () => {
                        try {
                          setSelected(await publishProcessDefinition(selected.id));
                        } catch {
                          setError('Опубликовать не удалось: у шаблона должно быть хотя бы одно правило маршрутизации.');
                        }
                      }}
                    >
                      Опубликовать
                    </Button>
                  )}
                  {selected.status !== 'ARCHIVED' && (
                    <Button
                      color="warning"
                      variant="outlined"
                      onClick={async () => setSelected(await archiveProcessDefinition(selected.id))}
                    >
                      В архив
                    </Button>
                  )}
                </Stack>
              </Stack>

              <Typography variant="subtitle1" gutterBottom>
                Правила маршрутизации
              </Typography>
              <List dense sx={{ mb: 2 }}>
                {rules.map((rule) => (
                  <ListItem
                    key={rule.id}
                    secondaryAction={
                      selected.status === 'DRAFT' && (
                        <IconButton
                          edge="end"
                          onClick={async () => {
                            await deleteRoutingRule(selected.id, rule.id);
                            listRoutingRules(selected.id).then(setRules);
                          }}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      )
                    }
                  >
                    <ListItemText
                      primary={`[${rule.priority}] ${rule.name} → ${rule.targetStepCode} (${rule.targetRole ?? 'без роли'})`}
                      secondary={`${rule.conditionTree.field} ${rule.conditionTree.op} ${String(rule.conditionTree.value)}`}
                    />
                  </ListItem>
                ))}
                {rules.length === 0 && (
                  <ListItem>
                    <ListItemText primary="Правил маршрутизации пока нет." />
                  </ListItem>
                )}
              </List>

              {selected.status === 'DRAFT' && (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Добавить правило маршрутизации
                  </Typography>
                  <Stack spacing={2}>
                    <Stack direction="row" spacing={2}>
                      <TextField size="small" label="Название правила" value={ruleName} onChange={(e) => setRuleName(e.target.value)} />
                      <TextField
                        size="small"
                        label="Приоритет"
                        value={rulePriority}
                        onChange={(e) => setRulePriority(e.target.value)}
                        sx={{ width: 120 }}
                      />
                      <TextField size="small" label="Код целевого шага" value={targetStepCode} onChange={(e) => setTargetStepCode(e.target.value)} />
                      <TextField size="small" label="Целевая роль" value={targetRole} onChange={(e) => setTargetRole(e.target.value)} />
                    </Stack>
                    <Stack direction="row" spacing={2}>
                      <TextField size="small" label="Поле" value={field} onChange={(e) => setField(e.target.value)} />
                      <TextField
                        select
                        size="small"
                        label="Оператор"
                        value={op}
                        sx={{ width: 130 }}
                        onChange={(e) => setOp(e.target.value as LeafCondition['op'])}
                      >
                        {COMPARISON_OPS.map((o) => (
                          <MenuItem key={o} value={o}>
                            {o}
                          </MenuItem>
                        ))}
                      </TextField>
                      <TextField size="small" label="Значение" value={value} onChange={(e) => setValue(e.target.value)} />
                    </Stack>
                    <Box>
                      <Button
                        variant="contained"
                        disabled={!ruleName.trim() || !targetStepCode.trim() || !field.trim()}
                        onClick={async () => {
                          await addRoutingRule(selected.id, {
                            name: ruleName,
                            priority: Number(rulePriority) || 0,
                            conditionTree: { type: 'condition', field, op, value: parseValue(value) },
                            targetStepCode,
                            targetRole: targetRole || null,
                          });
                          setRuleName('');
                          setTargetStepCode('');
                          setTargetRole('');
                          setField('');
                          setValue('');
                          listRoutingRules(selected.id).then(setRules);
                        }}
                      >
                        Добавить правило
                      </Button>
                    </Box>
                  </Stack>
                </Box>
              )}
            </Paper>
          )}
        </Grid>
      </Grid>
    </Box>
  );
}
