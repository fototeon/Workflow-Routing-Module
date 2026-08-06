import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Divider,
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
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import {
  addRoutingRule,
  archiveProcessDefinition,
  createProcessDefinition,
  deleteRoutingRule,
  getProcessDefinitionJournal,
  listAllProcessDefinitions,
  listRoutingRules,
  publishProcessDefinition,
} from '../api/processDefinitions';
import { listSlaPolicies } from '../api/slaPolicies';
import type {
  ConditionNode,
  LeafCondition,
  ProcessDefinition,
  ProcessDefinitionStatus,
  ProcessEventLogEntry,
  RoutingRule,
  SlaPolicy,
} from '../api/types';
import { StatusChip } from '../components/StatusChip';
import { DEFINITION_STATUS_LABELS } from '../statusLabels';
import { describeActionError, describeLoadError } from '../api/errors';
import { colors } from '../colors';

interface LeafDraft {
  field: string;
  op: string;
  value: string;
}

const COMPARISON_OPS = ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'CONTAINS', 'EXISTS'];

/** Suggested priorities, spaced so a rule can always be squeezed between two existing ones later. */
const PRIORITY_SUGGESTIONS = ['10', '20', '30'];

type StatusFilter = 'ALL' | ProcessDefinitionStatus;

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Все' },
  { value: 'DRAFT', label: 'Черновики' },
  { value: 'PUBLISHED', label: 'Опубликованные' },
  { value: 'ARCHIVED', label: 'Архив' },
];

function buildConditionTree(groupOp: 'AND' | 'OR', leaves: LeafDraft[]): ConditionNode {
  const children: ConditionNode[] = leaves.map((leaf) => ({
    type: 'condition',
    field: leaf.field,
    op: leaf.op as LeafCondition['op'],
    value: parseLeafValue(leaf.value),
  }));
  return { type: 'group', op: groupOp, children };
}

function parseLeafValue(raw: string): unknown {
  const trimmed = raw.trim();
  if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
    try {
      return JSON.parse(trimmed);
    } catch {
      return trimmed;
    }
  }
  if (trimmed === 'true' || trimmed === 'false') return trimmed === 'true';
  if (trimmed !== '' && !Number.isNaN(Number(trimmed))) return Number(trimmed);
  return trimmed;
}

function isValidPriority(raw: string): boolean {
  const trimmed = raw.trim();
  return trimmed !== '' && Number.isInteger(Number(trimmed)) && Number(trimmed) >= 0;
}

export function TemplatesPage() {
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [slaPolicies, setSlaPolicies] = useState<SlaPolicy[]>([]);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [searchText, setSearchText] = useState('');
  const [selected, setSelected] = useState<ProcessDefinition | null>(null);
  const [rules, setRules] = useState<RoutingRule[]>([]);
  const [journal, setJournal] = useState<ProcessEventLogEntry[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newSlaPolicyId, setNewSlaPolicyId] = useState('');

  const [editorMode, setEditorMode] = useState<'visual' | 'json'>('visual');
  const [ruleName, setRuleName] = useState('');
  const [rulePriority, setRulePriority] = useState('');
  const [targetStepCode, setTargetStepCode] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const [groupOp, setGroupOp] = useState<'AND' | 'OR'>('AND');
  const [leaves, setLeaves] = useState<LeafDraft[]>([{ field: '', op: 'EQ', value: '' }]);
  const [rawJson, setRawJson] = useState('{\n  "type": "condition",\n  "field": "",\n  "op": "EQ",\n  "value": ""\n}');

  /** The catalogue is loaded whole and filtered in the browser: the registry of templates is small. */
  const reloadCatalogue = useCallback(async () => {
    try {
      const all = await listAllProcessDefinitions();
      setDefinitions(all);
      setLoadError(null);
      return all;
    } catch (error) {
      setLoadError(describeLoadError(error, 'шаблоны процессов'));
      return [];
    }
  }, []);

  useEffect(() => {
    reloadCatalogue();
    listSlaPolicies().then(setSlaPolicies).catch(() => setSlaPolicies([]));
  }, [reloadCatalogue]);

  const visibleDefinitions = useMemo(() => {
    const needle = searchText.trim().toLowerCase();
    return definitions.filter((def) => {
      if (statusFilter !== 'ALL' && def.status !== statusFilter) return false;
      if (!needle) return true;
      return def.code.toLowerCase().includes(needle) || def.name.toLowerCase().includes(needle);
    });
  }, [definitions, statusFilter, searchText]);

  /** Rules and journal of the opened template; the journal grows with every change (TZ §10, REQ-02-002). */
  const loadDetails = (definitionId: string) => {
    listRoutingRules(definitionId).then(setRules).catch(() => setRules([]));
    getProcessDefinitionJournal(definitionId).then(setJournal).catch(() => setJournal([]));
  };

  const selectDefinition = (def: ProcessDefinition) => {
    setSelected(def);
    setActionError(null);
    loadDetails(def.id);
  };

  /** Keeps the catalogue row and the opened template in sync after publish/archive/rule changes. */
  const applyDefinitionUpdate = (updated: ProcessDefinition) => {
    setSelected(updated);
    setDefinitions((prev) => prev.map((def) => (def.id === updated.id ? updated : def)));
  };

  const canPublish = selected?.status === 'DRAFT' && rules.length > 0;

  /** Placeholder for the priority field: the next slot after the rules already in the template. */
  const nextFreePriority = String(rules.reduce((max, rule) => Math.max(max, rule.priority), 0) + 10);

  return (
    <Box>
      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline', mb: 1 }}>
              <Typography variant="subtitle1">Каталог шаблонов</Typography>
              <Typography variant="caption" sx={{ color: colors.text.caption }}>
                {visibleDefinitions.length} из {definitions.length}
              </Typography>
            </Stack>
            <ToggleButtonGroup
              size="small"
              exclusive
              value={statusFilter}
              onChange={(_, v: StatusFilter | null) => v && setStatusFilter(v)}
              sx={{ mb: 1, flexWrap: 'wrap' }}
            >
              {STATUS_FILTERS.map((filter) => (
                <ToggleButton key={filter.value} value={filter.value}>
                  {filter.label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <TextField
              fullWidth
              size="small"
              label="Фильтр по коду или названию"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
            <List dense>
              {visibleDefinitions.map((def) => (
                <ListItem key={def.id} disablePadding>
                  <ListItemButton selected={selected?.id === def.id} onClick={() => selectDefinition(def)}>
                    <ListItemText
                      disableTypography
                      primary={
                        <Typography variant="body2" sx={{ color: colors.text.body, fontWeight: 600 }}>
                          {def.code}{' '}
                          <Box component="span" sx={{ color: colors.text.caption, fontWeight: 400 }}>
                            v{def.version}
                          </Box>
                        </Typography>
                      }
                      secondary={
                        <Box>
                          <Typography variant="caption" sx={{ color: colors.text.caption, display: 'block' }}>
                            {def.name}
                          </Typography>
                          <Stack direction="row" spacing={1} sx={{ mt: 0.5, alignItems: 'center' }}>
                            <StatusChip
                              label={DEFINITION_STATUS_LABELS[def.status].label}
                              tone={DEFINITION_STATUS_LABELS[def.status].tone}
                            />
                            <Typography variant="caption" sx={{ color: colors.text.caption }}>
                              правил: {def.routingRuleCount}
                            </Typography>
                          </Stack>
                        </Box>
                      }
                    />
                  </ListItemButton>
                </ListItem>
              ))}
              {visibleDefinitions.length === 0 && (
                <ListItem>
                  <ListItemText
                    primary={
                      <Typography variant="body2" sx={{ color: colors.text.secondary }}>
                        {definitions.length === 0
                          ? 'Шаблонов пока нет — создайте первый черновик ниже.'
                          : 'Под фильтр ничего не подошло.'}
                      </Typography>
                    }
                  />
                </ListItem>
              )}
            </List>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" gutterBottom>
              Новый шаблон (черновик)
            </Typography>
            <Stack spacing={2}>
              <TextField size="small" label="Код" value={newCode} onChange={(e) => setNewCode(e.target.value)} />
              <TextField size="small" label="Название" value={newName} onChange={(e) => setNewName(e.target.value)} />
              <TextField
                select
                size="small"
                label="SLA политика"
                value={newSlaPolicyId}
                onChange={(e) => setNewSlaPolicyId(e.target.value)}
              >
                <MenuItem value="">Нет</MenuItem>
                {slaPolicies.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.name}
                  </MenuItem>
                ))}
              </TextField>
              <Button
                variant="contained"
                disabled={!newCode.trim() || !newName.trim()}
                onClick={async () => {
                  try {
                    const created = await createProcessDefinition({
                      code: newCode,
                      name: newName,
                      slaPolicyId: newSlaPolicyId || null,
                    });
                    setNewCode('');
                    setNewName('');
                    setNewSlaPolicyId('');
                    setStatusFilter('ALL');
                    setSearchText('');
                    await reloadCatalogue();
                    selectDefinition(created);
                  } catch (error) {
                    setActionError(describeActionError(error, 'Не удалось создать шаблон.'));
                  }
                }}
              >
                Создать
              </Button>
            </Stack>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          {!selected && <Typography color="text.secondary">Выберите шаблон в каталоге или создайте новый черновик.</Typography>}
          {selected && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              {actionError && (
                <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError(null)}>
                  {actionError}
                </Alert>
              )}
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Box>
                  <Typography variant="h6">
                    {selected.code} v{selected.version}
                  </Typography>
                  <Typography variant="body2" sx={{ color: colors.text.secondary }}>
                    {selected.name}
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
                    <Tooltip
                      title={
                        canPublish
                          ? 'Опубликовать: шаблон станет доступен для запуска процессов, правила больше не редактируются.'
                          : 'Сначала добавьте хотя бы одно правило маршрутизации — без него процесс не сможет определить следующий шаг.'
                      }
                    >
                      <span>
                        <Button
                          variant="contained"
                          disabled={!canPublish}
                          onClick={async () => {
                            try {
                              applyDefinitionUpdate(await publishProcessDefinition(selected.id));
                              loadDetails(selected.id);
                              setActionError(null);
                            } catch (error) {
                              setActionError(describeActionError(error, 'Не удалось опубликовать шаблон.'));
                            }
                          }}
                        >
                          Опубликовать
                        </Button>
                      </span>
                    </Tooltip>
                  )}
                  {selected.status !== 'ARCHIVED' && (
                    <Button
                      color="warning"
                      variant="outlined"
                      onClick={async () => {
                        try {
                          applyDefinitionUpdate(await archiveProcessDefinition(selected.id));
                          loadDetails(selected.id);
                          setActionError(null);
                        } catch (error) {
                          setActionError(describeActionError(error, 'Не удалось отправить шаблон в архив.'));
                        }
                      }}
                    >
                      В архив
                    </Button>
                  )}
                </Stack>
              </Stack>

              {selected.status === 'DRAFT' && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  Черновик собирается в три шага: <b>1)</b> создать шаблон, <b>2)</b> добавить правила маршрутизации —
                  каждое кнопкой «Добавить правило», их может быть сколько угодно, <b>3)</b> нажать «Опубликовать»,
                  чтобы по шаблону можно было запускать процессы. Пока правил нет, публикация недоступна: процессу
                  нечем будет выбрать следующий шаг. После публикации правила менять нельзя — новая версия создаётся
                  отдельным шаблоном с тем же кодом.
                </Alert>
              )}

              <Typography variant="subtitle1" gutterBottom>
                Правила маршрутизации ({rules.length})
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
                            try {
                              await deleteRoutingRule(selected.id, rule.id);
                              loadDetails(selected.id);
                              await reloadCatalogue();
                            } catch (error) {
                              setActionError(describeActionError(error, 'Не удалось удалить правило.'));
                            }
                          }}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      )
                    }
                  >
                    <ListItemText
                      primary={`[${rule.priority}] ${rule.name} → ${rule.targetStepCode} (${rule.targetRole ?? 'без роли'})`}
                      secondary={JSON.stringify(rule.conditionTree)}
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
                    <Grid container spacing={2}>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField
                          fullWidth
                          required
                          size="small"
                          label="Название правила"
                          value={ruleName}
                          onChange={(e) => setRuleName(e.target.value)}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <Autocomplete
                          freeSolo
                          size="small"
                          options={PRIORITY_SUGGESTIONS}
                          value={rulePriority}
                          onChange={(_, value) => setRulePriority(value ?? '')}
                          onInputChange={(_, value) => setRulePriority(value)}
                          renderInput={(params) => (
                            <TextField
                              {...params}
                              required
                              label="Приоритет"
                              placeholder={nextFreePriority}
                              helperText="Меньше — правило проверяется раньше"
                            />
                          )}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField
                          fullWidth
                          required
                          size="small"
                          label="Код целевого шага"
                          value={targetStepCode}
                          onChange={(e) => setTargetStepCode(e.target.value)}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField
                          fullWidth
                          size="small"
                          label="Целевая роль"
                          value={targetRole}
                          onChange={(e) => setTargetRole(e.target.value)}
                        />
                      </Grid>
                    </Grid>

                    <ToggleButtonGroup
                      size="small"
                      exclusive
                      value={editorMode}
                      onChange={(_, v) => v && setEditorMode(v)}
                    >
                      <ToggleButton value="visual">Визуальный конструктор</ToggleButton>
                      <ToggleButton value="json">Сырой JSON</ToggleButton>
                    </ToggleButtonGroup>

                    {editorMode === 'visual' ? (
                      <Box>
                        <TextField
                          select
                          size="small"
                          label="Объединить через"
                          value={groupOp}
                          onChange={(e) => setGroupOp(e.target.value as 'AND' | 'OR')}
                          sx={{ width: 120, mb: 1 }}
                        >
                          <MenuItem value="AND">AND</MenuItem>
                          <MenuItem value="OR">OR</MenuItem>
                        </TextField>
                        {leaves.map((leaf, idx) => (
                          <Stack direction="row" spacing={1} key={idx} sx={{ mb: 1 }}>
                            <TextField
                              size="small"
                              label="Поле"
                              value={leaf.field}
                              onChange={(e) =>
                                setLeaves((prev) => prev.map((l, i) => (i === idx ? { ...l, field: e.target.value } : l)))
                              }
                            />
                            <TextField
                              select
                              size="small"
                              label="Оператор"
                              value={leaf.op}
                              sx={{ width: 130 }}
                              onChange={(e) =>
                                setLeaves((prev) => prev.map((l, i) => (i === idx ? { ...l, op: e.target.value } : l)))
                              }
                            >
                              {COMPARISON_OPS.map((op) => (
                                <MenuItem key={op} value={op}>
                                  {op}
                                </MenuItem>
                              ))}
                            </TextField>
                            <TextField
                              size="small"
                              label="Значение"
                              value={leaf.value}
                              onChange={(e) =>
                                setLeaves((prev) => prev.map((l, i) => (i === idx ? { ...l, value: e.target.value } : l)))
                              }
                            />
                            <IconButton onClick={() => setLeaves((prev) => prev.filter((_, i) => i !== idx))}>
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Stack>
                        ))}
                        <Button
                          startIcon={<AddIcon />}
                          onClick={() => setLeaves((prev) => [...prev, { field: '', op: 'EQ', value: '' }])}
                        >
                          Добавить условие
                        </Button>
                      </Box>
                    ) : (
                      <TextField
                        fullWidth
                        multiline
                        minRows={6}
                        value={rawJson}
                        onChange={(e) => setRawJson(e.target.value)}
                        sx={{ fontFamily: 'monospace' }}
                      />
                    )}

                    <Box>
                      <Button
                        variant="contained"
                        disabled={!ruleName.trim() || !targetStepCode.trim() || !isValidPriority(rulePriority)}
                        onClick={async () => {
                          try {
                            const conditionTree: ConditionNode =
                              editorMode === 'visual' ? buildConditionTree(groupOp, leaves) : JSON.parse(rawJson);
                            await addRoutingRule(selected.id, {
                              name: ruleName,
                              priority: Number(rulePriority),
                              conditionTree,
                              targetStepCode,
                              targetRole: targetRole || null,
                            });
                            setRuleName('');
                            setRulePriority('');
                            setTargetStepCode('');
                            setTargetRole('');
                            setLeaves([{ field: '', op: 'EQ', value: '' }]);
                            loadDetails(selected.id);
                            await reloadCatalogue();
                            setActionError(null);
                          } catch (error) {
                            setActionError(describeActionError(error, 'Не удалось добавить правило.'));
                          }
                        }}
                      >
                        Добавить правило
                      </Button>
                    </Box>
                  </Stack>
                </Box>
              )}

              <Divider sx={{ my: 3, borderColor: colors.glass.panelStroke }} />
              <Typography variant="subtitle1" sx={{ color: colors.text.heading, mb: 1 }}>
                Журнал изменений шаблона
              </Typography>
              <List dense>
                {journal.map((entry) => (
                  <ListItem key={entry.id} disableGutters>
                    <ListItemText
                      primary={
                        <Typography variant="body2" sx={{ color: colors.text.body }}>
                          {entry.eventType}
                          {entry.actorId ? ` — ${entry.actorId}` : ''}
                        </Typography>
                      }
                      secondary={
                        <Typography variant="caption" sx={{ color: colors.text.caption }}>
                          {new Date(entry.occurredAt).toLocaleString('ru-RU')}
                        </Typography>
                      }
                    />
                  </ListItem>
                ))}
                {journal.length === 0 && (
                  <ListItem disableGutters>
                    <ListItemText
                      primary={
                        <Typography variant="body2" sx={{ color: colors.text.secondary }}>
                          Записей пока нет.
                        </Typography>
                      }
                    />
                  </ListItem>
                )}
              </List>
            </Paper>
          )}
        </Grid>
      </Grid>
    </Box>
  );
}
