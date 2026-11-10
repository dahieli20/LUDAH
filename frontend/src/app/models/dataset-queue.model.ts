import { UploadDatasetResponse } from '../features/datasets/services/dataset.service';
export type QueueState = 'WAITING' | 'ANALYZING' | 'DONE' | 'ERROR';

export type StatusView = 'ACCEPTED' | 'REVIEW' | 'REJECTED';

export interface FileQueueItem {
  file: File;
  fileSize?: number;
  state: QueueState;
  result: UploadDatasetResponse | null;
  errorMessage: string;
}
