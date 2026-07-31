import { useEffect, useState } from 'react';
import {
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
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import {
  addRoutingRule,
  archiveProcessDefinition,
  createProcessDefinition,
  deleteRoutingRule,
  listProcessDefinitionVersions,
  listRoutingRules,
  publishProcessDefinition,
} from '../api/processDefinitions';
import { listSlaPolicies } from '../api/slaPolicies';
import type { ConditionNode, LeafCondition, ProcessDefinition, RoutingRule, SlaPolicy } from '../api/types';
import { StatusChip } from '../components/StatusChip';
import { DEFINITION_STATUS_LABELS } from '../statusLabels';

interface LeafDraft {
  field: string;
  op: string;
  value: string;
}

const COMPARISON_OPS = ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'CONTAINS', 'EXISTS'];

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

export function TemplatesPage() {
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [slaPolicies, setSlaPolicies] = useState<SlaPolicy[]>([]);
  const [searchCode, setSearchCode] = useState('');
  const [selected, setSelected] = useState<ProcessDefinition | null>(null);
  const [rules, setRules] = useState<RoutingRule[]>([]);

  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newSlaPolicyId, setNewSlaPolicyId] = useState('');

  const [editorMode, setEditorMode] = useState<'visual' | 'json'>('visual');
  const [ruleName, setRuleName] = useState('');
  const [rulePriority, setRulePriority] = useState(0);
  const [targetStepCode, setTargetStepCode] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const [groupOp, setGroupOp] = useState<'AND' | 'OR'>('AND');
  const [leaves, setLeaves] = useState<LeafDraft[]>([{ field: '', op: 'EQ', value: '' }]);
  const [rawJson, setRawJson] = useState('{\n  "type": "condition",\n  "field": "",\n  "op": "EQ",\n  "value": ""\n}');

  useEffect(() => {
    listSlaPolicies().then(setSlaPolicies);
  }, []);

  const loadRules = (definitionId: string) => {
    listRoutingRules(definitionId).then(setRules);
  };

  const searchDefinitions = () => {
    if (!searchCode.trim()) return;
    listProcessDefinitionVersions(searchCode.trim()).then(setDefinitions);
  };

  const selectDefinition = (def: ProcessDefinition) => {
    setSelected(def);
    loadRules(def.id);
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
              <Button onClick={searchDefinitions}>Найти</Button>
            </Stack>
            <List dense>
              {definitions.map((def) => (
                <ListItem key={def.id} disablePadding>
                  <ListItemButton selected={selected?.id === def.id} onClick={() => selectDefinition(def)}>
                    <ListItemText primary={`${def.code} v${def.version}`} secondary={DEFINITION_STATUS_LABELS[def.status].label} />
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
                  const created = await createProcessDefinition({
                    code: newCode,
                    name: newName,
                    slaPolicyId: newSlaPolicyId || null,
                  });
                  setNewCode('');
                  setNewName('');
                  setNewSlaPolicyId('');
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
                      variant="outlined"
                      onClick={async () => {
                        const published = await publishProcessDefinition(selected.id);
                        setSelected(published);
                      }}
                    >
                      Опубликовать
                    </Button>
                  )}
                  {selected.status !== 'ARCHIVED' && (
                    <Button
                      color="warning"
                      variant="outlined"
                      onClick={async () => {
                        const archived = await archiveProcessDefinition(selected.id);
                        setSelected(archived);
                      }}
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
                            loadRules(selected.id);
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
                    <Stack direction="row" spacing={2}>
                      <TextField size="small" label="Название правила" value={ruleName} onChange={(e) => setRuleName(e.target.value)} />
                      <TextField
                        size="small"
                        type="number"
                        label="Приоритет"
                        value={rulePriority}
                        onChange={(e) => setRulePriority(Number(e.target.value))}
                        sx={{ width: 120 }}
                      />
                      <TextField
                        size="small"
                        label="Код целевого шага"
                        value={targetStepCode}
                        onChange={(e) => setTargetStepCode(e.target.value)}
                      />
                      <TextField size="small" label="Целевая роль" value={targetRole} onChange={(e) => setTargetRole(e.target.value)} />
                    </Stack>

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
                        disabled={!ruleName.trim() || !targetStepCode.trim()}
                        onClick={async () => {
                          const conditionTree: ConditionNode =
                            editorMode === 'visual' ? buildConditionTree(groupOp, leaves) : JSON.parse(rawJson);
                          await addRoutingRule(selected.id, {
                            name: ruleName,
                            priority: rulePriority,
                            conditionTree,
                            targetStepCode,
                            targetRole: targetRole || null,
                          });
                          setRuleName('');
                          setRulePriority(0);
                          setTargetStepCode('');
                          setTargetRole('');
                          setLeaves([{ field: '', op: 'EQ', value: '' }]);
                          loadRules(selected.id);
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
