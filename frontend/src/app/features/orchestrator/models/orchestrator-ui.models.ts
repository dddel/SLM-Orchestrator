export type PipelineStageId = 'input' | 'router' | 'slm' | 'guards';

export type PipelineStageStatus = 'inactive' | 'running' | 'completed' | 'warning';

export interface PipelineStageState {
  id: PipelineStageId;
  label: string;
  sublabel: string;
  status: PipelineStageStatus;
}

export interface GuardPresentationItem {
  id: string;
  title: string;
  detail: string;
  status: 'passed' | 'warning' | 'error';
}

export interface ParamOption {
  value: string | null;
  label: string;
  badge?: string;
}

export type DemoStep =
  | 'idle'
  | 'select_chip'
  | 'typing'
  | 'submit'
  | 'pipeline'
  | 'stream_json'
  | 'guards'
  | 'holding';

export interface DemoState {
  isRunning: boolean;
  isPaused: boolean;
  currentPresetIndex: number;
  currentPresetId: string | null;
  currentStep: DemoStep;
}
