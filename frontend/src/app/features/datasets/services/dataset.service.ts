import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

export interface CsvQualityResult {
  totalRows: number;
  totalColumns: number;
  nullPercentage: number;
  duplicateRowsPercentage: number;
  hasDuplicateColumns: boolean;
  hasDataRows: boolean;
}

export interface UploadDatasetResponse {
  fileName: string;
  path: string;
  status: string;
  message: string;
  fileHash: string;
  partialMatchPercentage: number;
  matchedWith: string;
  quality: CsvQualityResult;
}

export interface AuditFactorResponse {
  name: string;
  value: number;
  weight: number;
  contribution: number;
  description: string;
}

export interface DataLakeAuditResponse {
  totalFiles: number;
  rawFiles: number;
  reviewFiles: number;
  rejectedFiles: number;
  ingestionRiskPercentage: number;
  qualityRiskPercentage: number;
  exactDuplicationRiskPercentage: number;
  redundancyRiskPercentage: number;
  dataSwampIndexPercentage: number;
  globalRiskPercentage: number;
  classification: string;
  message: string;
  factors: AuditFactorResponse[];
  auditMode: 'GLOBAL' | 'REPOSITORY';
  auditedPrefix: string;
  cleanMax: number | null;
  frontierMax: number | null;
}
@Injectable({
  providedIn: 'root'
})
export class DatasetService {

  private readonly apiUrl = `${environment.apiUrl}/datasets`;

  constructor(private http: HttpClient) {}

  uploadDataset(file: File): Observable<UploadDatasetResponse> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<UploadDatasetResponse>(`${this.apiUrl}/upload`, formData);
  }

  approveDataset(dataset: UploadDatasetResponse): Observable<UploadDatasetResponse> {
    return this.http.post<UploadDatasetResponse>(
      `${this.apiUrl}/review/approve`,
      dataset
    );
  }

  rejectDataset(dataset: UploadDatasetResponse): Observable<UploadDatasetResponse> {
    return this.http.post<UploadDatasetResponse>(
      `${this.apiUrl}/review/reject`,
      dataset
    );
  }

  getRepositoryPrefixes(): Observable<string[]> {
    return this.http.get<string[]>(`${environment.apiUrl}/minio/prefixes`);
  }

  getRawRepositoryAudit(motivo: string = 'NO_REGISTRADO'): Observable<DataLakeAuditResponse> {
    return this.auditRepository('raw/', motivo);
  }

  auditRepository(prefix: string, motivo: string = 'MANUAL'): Observable<DataLakeAuditResponse> {
    return this.http.get<DataLakeAuditResponse>(`${this.apiUrl}/audit/repository`, {
      params: { prefix, motivo, t: Date.now().toString() }
    });
  }

  getGlobalAudit(): Observable<DataLakeAuditResponse> {
    const timestamp = new Date().getTime();

    return this.http.get<DataLakeAuditResponse>(
      `${this.apiUrl}/audit/global?t=${timestamp}`
    );
  }

  getRiskThresholdConfig(): Observable<RiskThresholdConfigResponse> {
    return this.http.get<RiskThresholdConfigResponse>(
      `${this.apiUrl}/audit/config/thresholds`
    );
  }

  updateRiskThresholdConfig(
    request: RiskThresholdConfigRequest
  ): Observable<RiskThresholdConfigResponse> {
    return this.http.put<RiskThresholdConfigResponse>(
      `${this.apiUrl}/audit/config/thresholds`,
      request
    );
  }
  
}
export interface RiskThresholdConfigRequest {
  cleanMax: number;
  frontierMax: number;
}

export interface RiskThresholdConfigResponse {
  cleanMax: number;
  frontierMax: number;
  cleanRange: string;
  frontierRange: string;
  swampRange: string;
}