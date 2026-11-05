import { of, Subject, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { DataLakeStatusComponent } from './data-lake-status.component';
import { DatasetService, DataLakeAuditResponse } from '../../services/dataset.service';

describe('DataLakeStatusComponent audit scope', () => {
  let api: jasmine.SpyObj<DatasetService>;
  let component: DataLakeStatusComponent;
  const result = (mode: 'GLOBAL' | 'REPOSITORY'): DataLakeAuditResponse => ({
    totalFiles: 0, rawFiles: 0, reviewFiles: 0, rejectedFiles: 0,
    ingestionRiskPercentage: 0, qualityRiskPercentage: 0, exactDuplicationRiskPercentage: 0,
    redundancyRiskPercentage: 0, dataSwampIndexPercentage: 0, globalRiskPercentage: 0,
    classification: 'SIN_DATOS', message: '', factors: [], auditMode: mode,
    auditedPrefix: 'raw/', cleanMax: 25, frontierMax: 50
  });
  beforeEach(() => {
    api = jasmine.createSpyObj<DatasetService>('DatasetService', ['getGlobalAudit', 'auditRepository', 'getRepositoryPrefixes']);
    component = new DataLakeStatusComponent(api, {} as Router);
  });
  afterEach(() => {
    component.ngOnDestroy();
    jasmine.clock().uninstall();
  });
  it('uses global ingestion risk only for the global response', () => {
    api.getGlobalAudit.and.returnValue(of(result('GLOBAL')));
    component.loadGlobalAudit();
    expect(api.getGlobalAudit).toHaveBeenCalledTimes(1);
    expect(component.isRepositoryAudit).toBeFalse();
  });
  it('audits the entered prefix and treats raw as repository mode too', () => {
    component.selectedMode = 'REPOSITORY';
    component.repositoryPrefix = ' raw/ ';
    api.auditRepository.and.returnValue(of(result('REPOSITORY')));
    component.loadGlobalAudit();
    expect(api.auditRepository).toHaveBeenCalledWith('raw/');
    expect(component.isRepositoryAudit).toBeTrue();
    component.selectedMode = 'GLOBAL';
    expect(component.isRepositoryAudit).toBeTrue(); // The displayed result retains its own mode.
  });
  it('rejects the bucket root before making a request', () => {
    component.selectedMode = 'REPOSITORY';
    for (const prefix of ['', ' ', '/']) {
      component.repositoryPrefix = prefix;
      component.loadGlobalAudit();
      expect(component.errorMessage).not.toBe('');
    }
    expect(api.auditRepository).not.toHaveBeenCalled();
  });
  it('clears the previous result when the next request fails', () => {
    component.globalAudit = result('GLOBAL');
    api.getGlobalAudit.and.returnValue(throwError(() => new Error('offline')));
    component.loadGlobalAudit();
    expect(component.globalAudit).toBeNull();
    expect(component.loading).toBeFalse();
    expect(component.errorMessage).not.toBe('');
  });
  it('shows the percentage multiplier for exact duplication', () => {
    expect(component.getFactorFormula({ name: 'D', value: 30, weight: 0.3, contribution: 9, description: '' }))
      .toBe('D = 100 × (N - H) / N');
  });
  it('loads MinIO routes when repository mode is selected and keeps the selection on refresh', () => {
    api.getRepositoryPrefixes.and.returnValue(of(['pruebas/', 'raw/']));
    component.onAuditModeChange('REPOSITORY');
    expect(component.availablePrefixes).toEqual(['pruebas/', 'raw/']);
    expect(component.repositoryPrefix).toBe('raw/');
    component.repositoryPrefix = 'pruebas/';
    component.loadRepositoryPrefixes();
    expect(component.repositoryPrefix).toBe('pruebas/');
    expect(api.auditRepository).not.toHaveBeenCalled();
    expect(api.getGlobalAudit).not.toHaveBeenCalled();
  });
  it('clears a removed selection and handles an empty bucket', () => {
    component.repositoryPrefix = 'old/';
    api.getRepositoryPrefixes.and.returnValue(of(['pruebas/']));
    component.loadRepositoryPrefixes();
    expect(component.repositoryPrefix).toBe('pruebas/');
    api.getRepositoryPrefixes.and.returnValue(of([]));
    component.loadRepositoryPrefixes();
    expect(component.repositoryPrefix).toBe('');
    expect(component.prefixesLoading).toBeFalse();
  });
  it('reports route listing failures without launching an audit', () => {
    api.getRepositoryPrefixes.and.returnValue(throwError(() => new Error('offline')));
    component.onAuditModeChange('REPOSITORY');
    expect(component.prefixesError).not.toBe('');
    expect(component.availablePrefixes).toEqual([]);
    expect(component.repositoryPrefix).toBe('');
    expect(component.prefixesLoading).toBeFalse();
    expect(api.auditRepository).not.toHaveBeenCalled();
  });

  it('refreshes every 15 seconds and stops when leaving repository mode', () => {
    jasmine.clock().install();
    if (typeof document !== 'undefined') spyOnProperty(document, 'hidden', 'get').and.returnValue(false);
    api.getRepositoryPrefixes.and.returnValue(of(['raw/']));
    component.onAuditModeChange('REPOSITORY');
    expect(api.getRepositoryPrefixes).toHaveBeenCalledTimes(1);
    api.getRepositoryPrefixes.and.returnValue(of(['nueva/', 'raw/']));
    jasmine.clock().tick(15000);
    expect(component.availablePrefixes).toEqual(['nueva/', 'raw/']);
    expect(component.repositoryPrefix).toBe('raw/');
    component.onAuditModeChange('GLOBAL');
    jasmine.clock().tick(30000);
    expect(api.getRepositoryPrefixes).toHaveBeenCalledTimes(2);
    expect(api.auditRepository).not.toHaveBeenCalled();
    expect(api.getGlobalAudit).not.toHaveBeenCalled();
  });
  it('cancels pending requests and the timer when the screen is destroyed', () => {
    jasmine.clock().install();
    const pending = new Subject<string[]>();
    api.getRepositoryPrefixes.and.returnValue(pending);
    component.onAuditModeChange('REPOSITORY');
    component.ngOnDestroy();
    pending.next(['tardia/']);
    jasmine.clock().tick(30000);
    expect(component.availablePrefixes).toEqual([]);
    expect(api.getRepositoryPrefixes).toHaveBeenCalledTimes(1);
    expect(component.prefixesLoading).toBeFalse();
  });
  it('keeps existing routes on a background failure and retries on window focus', () => {
    api.getRepositoryPrefixes.and.returnValue(of(['raw/']));
    component.onAuditModeChange('REPOSITORY');
    api.getRepositoryPrefixes.and.returnValue(throwError(() => new Error('offline')));
    component.onWindowFocus();
    expect(component.repositoryPrefix).toBe('raw/');
    expect(component.availablePrefixes).toEqual(['raw/']);
    expect(component.prefixesError).not.toBe('');
    api.getRepositoryPrefixes.and.returnValue(of(['nueva/', 'raw/']));
    component.onWindowFocus();
    expect(component.prefixesError).toBe('');
    expect(component.availablePrefixes).toEqual(['nueva/', 'raw/']);
  });

});
