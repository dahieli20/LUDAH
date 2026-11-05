import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { DatasetService } from './dataset.service';

describe('DatasetService audit requests', () => {
  let http: jasmine.SpyObj<HttpClient>;
  let service: DatasetService;

  beforeEach(() => {
    http = jasmine.createSpyObj<HttpClient>('HttpClient', ['get']);
    http.get.and.returnValue(of({}));
    service = new DatasetService(http);
  });

  it('preserves the reason when requesting an automatic raw audit', () => {
    service.getRawRepositoryAudit('CAMBIO_UMBRALES').subscribe();
    expect(http.get).toHaveBeenCalledWith(`${environment.apiUrl}/datasets/audit/repository`, {
      params: { prefix: 'raw/', motivo: 'CAMBIO_UMBRALES', t: jasmine.any(String) }
    });
  });

  it('sends the selected prefix and a manual reason for repository audits', () => {
    service.auditRepository('experimentos/escenario 1/').subscribe();
    expect(http.get).toHaveBeenCalledWith(`${environment.apiUrl}/datasets/audit/repository`, {
      params: { prefix: 'experimentos/escenario 1/', motivo: 'MANUAL', t: jasmine.any(String) }
    });
  });

  it('queries prefixes independently from audit requests', () => {
    service.getRepositoryPrefixes().subscribe();
    expect(http.get).toHaveBeenCalledOnceWith(`${environment.apiUrl}/minio/prefixes`);
  });
});
