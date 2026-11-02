export type DocumentType = 'EMAIL' | 'FATTURA' | 'ANAGRAFICA' | 'ALTRO';

export type RouterType = 'local-ollama' | 'reflex';

export type OrchestratorStatus = 'OK' | 'INDETERMINATE';

export interface FieldError {
  path: string;
  code: string;
  message: string;
}

export interface StepMetric {
  stepName: string;
  durationMs: number;
  model?: string;
  promptTokens?: number;
  evalTokens?: number;
}

export interface ExecutionMetrics {
  totalLatencyMs?: number;
  promptTokens?: number;
  evalTokens?: number;
  totalTokens?: number;
  tokensPerSecond?: number;
  steps?: StepMetric[];
}

export interface OrchestratorRequest {
  type?: DocumentType | null;
  payload: string;
  routerType?: RouterType | null;
  [customParam: string]: unknown;
}

export interface OrchestratorResponse {
  status: OrchestratorStatus;
  label: DocumentType;
  router: string;
  result: string | null;
  unvalidatedResult?: string | null;
  errors: FieldError[];
  actionErrors: string[];
  latencyMs: number;
  metrics?: ExecutionMetrics;
}

export interface CustomParam {
  id: string;
  key: string;
  value: string;
  enabled: boolean;
}

export interface RequestPreset {
  id: string;
  title: string;
  docType: DocumentType;
  routerType?: RouterType;
  description: string;
  payload: string;
}

export interface HistoryEntry {
  id: string;
  timestamp: string;
  request: OrchestratorRequest;
  response?: OrchestratorResponse;
  errorMessage?: string;
  latencyMs: number;
  status: OrchestratorStatus | 'ERROR';
}
