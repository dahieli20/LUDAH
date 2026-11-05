import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { finalize, interval, Subscription } from 'rxjs';
import { Router } from '@angular/router';

import {
  AuditFactorResponse,
  DataLakeAuditResponse,
  DatasetService
} from '../../services/dataset.service';

@Component({
  selector: 'app-data-lake-status',
  standalone: false,
  templateUrl: './data-lake-status.component.html',
  styleUrls: ['./data-lake-status.component.css']
})
export class DataLakeStatusComponent implements OnInit, OnDestroy {
  globalAudit: DataLakeAuditResponse | null = null;
  loading = false;
  errorMessage = '';
  selectedMode: 'GLOBAL' | 'REPOSITORY' = 'GLOBAL';
  repositoryPrefix = '';
  availablePrefixes: string[] = [];
  prefixesLoading = false;
  prefixesError = '';
  private prefixRefresh?: Subscription;
  private prefixRequest?: Subscription;
  private refreshingPrefixes = false;

  constructor(
    private datasetService: DatasetService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadGlobalAudit();
  }

  ngOnDestroy(): void {
    this.prefixRefresh?.unsubscribe();
    this.prefixRequest?.unsubscribe();
  }

  onAuditModeChange(mode: 'GLOBAL' | 'REPOSITORY'): void {
    this.selectedMode = mode;
    this.prefixRefresh?.unsubscribe();
    if (mode === 'REPOSITORY') {
      this.loadRepositoryPrefixes();
      this.prefixRefresh = interval(15000).subscribe(() => {
        if (typeof document === 'undefined' || !document.hidden) this.loadRepositoryPrefixes();
      });
    } else {
      this.prefixRequest?.unsubscribe();
    }
  }

  @HostListener('window:focus')
  onWindowFocus(): void {
    if (this.selectedMode === 'REPOSITORY') this.loadRepositoryPrefixes();
  }

  loadRepositoryPrefixes(): void {
    if (this.refreshingPrefixes || this.loading) return;
    this.refreshingPrefixes = true;
    // Las actualizaciones en segundo plano conservan el selector disponible.
    this.prefixesLoading = this.availablePrefixes.length === 0;
    this.prefixesError = '';
    this.prefixRequest = this.datasetService.getRepositoryPrefixes().pipe(
      finalize(() => {
        this.prefixesLoading = false;
        this.refreshingPrefixes = false;
      })
    ).subscribe({
      next: prefixes => {
        this.availablePrefixes = prefixes;
        if (!prefixes.includes(this.repositoryPrefix)) {
          this.repositoryPrefix = prefixes.includes('raw/') ? 'raw/' : (prefixes[0] ?? '');
        }
      },
      error: () => {
        this.prefixesError = 'No se pudieron consultar las rutas de MinIO. Se volverá a intentar automáticamente.';
      }
    });
  }

  loadGlobalAudit(): void {
    if (this.loading || (this.selectedMode === 'REPOSITORY' && this.prefixesLoading)) return;
    const prefix = this.repositoryPrefix.trim();
    if (this.selectedMode === 'REPOSITORY' && (!prefix || prefix === '/')) {
      this.errorMessage = 'Seleccioná una ruta de MinIO para ejecutar la auditoría.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.globalAudit = null;
    const request = this.selectedMode === 'GLOBAL'
      ? this.datasetService.getGlobalAudit()
      : this.datasetService.auditRepository(prefix);
    request.subscribe({
      next: response => {
        this.globalAudit = response;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'No se pudo auditar el ámbito seleccionado.';
      }
    });
  }

  get isRepositoryAudit(): boolean {
    return this.globalAudit?.auditMode === 'REPOSITORY';
  }

  goBack(): void {
    this.router.navigate(['/datasets/upload']);
  }

  getAuditClassificationLabel(classification: string): string {
    switch (classification) {
      case 'DATA_LAKE_SALUDABLE':
      case 'DATA_LAKE_LIMPIO':
        return 'Data Lake saludable';
      case 'ZONA_FRONTERA':
      case 'FRONTERA':
        return 'Zona frontera';
      case 'DATA_SWAMP':
        return 'Data Swamp';
      case 'SIN_DATOS':
        return 'Sin datos';
      default:
        return classification;
    }
  }

  getAuditClassificationDescription(classification: string): string {
    switch (classification) {
      case 'DATA_LAKE_SALUDABLE':
      case 'DATA_LAKE_LIMPIO':
        return 'Bajo nivel de riesgo medido en el conjunto auditado.';
      case 'ZONA_FRONTERA':
      case 'FRONTERA':
        return 'El conjunto auditado presenta señales de degradación.';
      case 'DATA_SWAMP':
        return 'Alto riesgo de pérdida de utilidad, confiabilidad y reutilización.';
      case 'SIN_DATOS':
        return 'Todavía no hay datos suficientes para calcular el indicador.';
      default:
        return 'Clasificación no reconocida.';
    }
  }

  getIriLevel(value: number): string {
    if (value <= 30) {
      return 'Ingreso estable';
    }

    if (value <= 60) {
      return 'Ingreso con alertas';
    }

    return 'Ingreso riesgoso';
  }

  getIriDescription(value: number): string {
    if (value <= 30) {
      return 'La mayoría de los archivos procesados fue aceptada o generó pocas alertas.';
    }

    if (value <= 60) {
      return 'Una parte importante de los archivos necesitó revisión o fue rechazada.';
    }

    return 'Muchos archivos intentaron ingresar con problemas y fueron filtrados por el sistema.';
  }

  getTopFactor(): AuditFactorResponse | null {
    if (!this.globalAudit || this.globalAudit.factors.length === 0) {
      return null;
    }

    return this.globalAudit.factors.reduce((highest, current) =>
      current.value > highest.value ? current : highest
    );
  }

  getFactorFriendlyName(factor: AuditFactorResponse): string {
    if (factor.name.startsWith('Q')) {
      return 'Calidad de los datos';
    }

    if (factor.name.startsWith('D')) {
      return 'Archivos repetidos';
    }

    if (factor.name.startsWith('R')) {
      return 'Archivos muy parecidos';
    }

    return factor.name;
  }

  getFactorFriendlyDescription(factor: AuditFactorResponse): string {
    if (factor.name.startsWith('Q')) {
      return 'Mira si los CSV auditados tienen celdas vacías, filas repetidas dentro del mismo archivo o columnas duplicadas.';
    }

    if (factor.name.startsWith('D')) {
      return 'Identifica archivos exactamente iguales en el conjunto auditado, aunque tengan nombres distintos.';
    }

    if (factor.name.startsWith('R')) {
      return 'Identifica datasets del conjunto auditado que comparten filas sin ser idénticos.';
    }

    return factor.description;
  }

  getFactorShortCode(factor: AuditFactorResponse): string {
    if (factor.name.startsWith('Q')) {
      return 'Q';
    }

    if (factor.name.startsWith('D')) {
      return 'D';
    }

    if (factor.name.startsWith('R')) {
      return 'R';
    }

    return '';
  }

  getRiskLevel(value: number): string {
    if (value <= 30) {
      return 'Bajo';
    }

    if (value <= 60) {
      return 'Medio';
    }

    return 'Alto';
  }

  getFactorMeasuredExplanation(factor: AuditFactorResponse): string {
    const level = this.getRiskLevel(factor.value).toLowerCase();

    if (factor.name.startsWith('Q')) {
      return `Nivel ${level}: mide cuántos problemas de calidad aparecen en los CSV auditados.`;
    }

    if (factor.name.startsWith('D')) {
      return `Nivel ${level}: mide cuánto contenido repetido exacto existe en el conjunto auditado.`;
    }

    if (factor.name.startsWith('R')) {
      return `Nivel ${level}: mide cuánto se parecen entre sí los datasets aceptados.`;
    }

    return `Nivel ${level}: mide la presencia de este problema en el Data Lake.`;
  }

  getFactorWeightExplanation(factor: AuditFactorResponse): string {
    const weight = Math.round(factor.weight * 100);

    if (factor.name.startsWith('Q')) {
      return `El modelo le da ${weight}% de importancia porque la calidad define si los datos se pueden usar.`;
    }

    if (factor.name.startsWith('D')) {
      return `El modelo le da ${weight}% de importancia porque los duplicados ocupan espacio y confunden el análisis.`;
    }

    if (factor.name.startsWith('R')) {
      return `El modelo le da ${weight}% de importancia porque datasets muy parecidos reducen la utilidad del Data Lake.`;
    }

    return `El modelo le da ${weight}% de importancia dentro del índice final.`;
  }

  getFactorContributionExplanation(factor: AuditFactorResponse): string {
    return `Este factor agrega ${factor.contribution} puntos al DSI final.`;
  }

  getFactorFormula(factor: AuditFactorResponse): string {
    if (factor.name.startsWith('Q')) {
      return 'Qi = 0,50Ni + 0,30Fi + 0,20Ci';
    }

    if (factor.name.startsWith('D')) {
      return 'D = 100 × (N - H) / N';
    }

    if (factor.name.startsWith('R')) {
      return 'Ri = max Riesgo(Similitud(i,j))';
    }

    return '';
  }

  formatPercentage(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '0';
    }

    const hasDecimals = value % 1 !== 0;

    return new Intl.NumberFormat('es-PY', {
      minimumFractionDigits: hasDecimals ? 2 : 0,
      maximumFractionDigits: 2
    }).format(value);
  }

  formatAuditMessage(message: string | null | undefined): string {
  if (!message) {
    return '';
  }

  return message.replace(/DSI-v1/gi, 'DSI').replace(/(\d+)\.(\d+)%/g, '$1,$2%');
}
}
