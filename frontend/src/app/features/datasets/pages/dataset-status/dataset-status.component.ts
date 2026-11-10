import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { DatasetService, UploadDatasetResponse } from '../../services/dataset.service';
import { DatasetStateService } from '../../services/dataset-state.service';
import { FileQueueItem, StatusView } from '../../../../models/dataset-queue.model';

@Component({
  selector: 'app-dataset-status',
  standalone: false,
  templateUrl: './dataset-status.component.html',
  styleUrls: ['./dataset-status.component.css']
})
export class DatasetStatusComponent {
  status: StatusView = 'REVIEW';

  fileQueue: FileQueueItem[] = [];
  statusItems: FileQueueItem[] = [];
  selectedIndex: number | null = null;
  loading = false;
  loadError = '';

  private subscriptions = new Subscription();
  private statusRequestSubscription: Subscription | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private datasetService: DatasetService,
    private datasetStateService: DatasetStateService
  ) {}

  ngOnInit(): void {
    const routeSubscription = this.route.paramMap.subscribe(params => {
      const statusParam = params.get('status')?.toUpperCase();

      if (!this.isValidStatus(statusParam)) {
        this.router.navigate(['/datasets/status', 'REVIEW']);
        return;
      }

      this.status = statusParam;
      this.loadStatusItems();
    });

    this.subscriptions.add(routeSubscription);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  get selectedItem(): FileQueueItem | null {
    if (this.selectedIndex === null) {
      return null;
    }

    return this.statusItems[this.selectedIndex] ?? null;
  }

  get pageTitle(): string {
    switch (this.status) {
      case 'ACCEPTED':
        return 'Documentos aceptados';
      case 'REVIEW':
        return 'Documentos en revisión';
      case 'REJECTED':
        return 'Documentos rechazados';
      default:
        return 'Documentos';
    }
  }

  get pageDescription(): string {
    switch (this.status) {
      case 'ACCEPTED':
        return 'Estos son los datasets que fueron aceptados para ingresar al Data Lake.';
      case 'REVIEW':
        return 'Estos son los datasets que necesitan validación manual antes de ingresar al Data Lake.';
      case 'REJECTED':
        return 'Estos son los datasets que fueron rechazados y no deben ingresar al Data Lake.';
      default:
        return '';
    }
  }

  get emptyTitle(): string {
    switch (this.status) {
      case 'ACCEPTED':
        return 'No hay documentos aceptados';
      case 'REVIEW':
        return 'No hay documentos en revisión';
      case 'REJECTED':
        return 'No hay documentos rechazados';
      default:
        return 'No hay documentos';
    }
  }

  get emptyDescription(): string {
    switch (this.status) {
      case 'ACCEPTED':
        return 'Cuando un CSV quede con estado Aceptado, va a aparecer en esta pantalla.';
      case 'REVIEW':
        return 'Cuando un CSV quede con estado En revisión, va a aparecer en esta pantalla.';
      case 'REJECTED':
        return 'Cuando un CSV quede con estado Rechazado, va a aparecer en esta pantalla.';
      default:
        return '';
    }
  }

  private isValidStatus(status: string | undefined): status is StatusView {
    return status === 'ACCEPTED' || status === 'REVIEW' || status === 'REJECTED';
  }

  private loadStatusItems(): void {
    this.statusRequestSubscription?.unsubscribe();
    this.loading = true;
    this.loadError = '';
    this.fileQueue = [];
    this.statusItems = [];
    this.selectedIndex = null;

    this.statusRequestSubscription = this.datasetService
      .getDatasetsByStatus(this.status)
      .subscribe({
        next: datasets => {
          this.fileQueue = datasets.map(dataset => this.toQueueItem(dataset));
          this.statusItems = this.fileQueue;
          this.loading = false;
          this.selectFirstAvailableItem();
        },
        error: () => {
          this.fileQueue = [];
          this.statusItems = [];
          this.selectedIndex = null;
          this.loading = false;
          this.loadError = 'No se pudieron recuperar los documentos almacenados.';
        }
      });

    this.subscriptions.add(this.statusRequestSubscription);
  }

  private toQueueItem(result: UploadDatasetResponse): FileQueueItem {
    const fileName = result.fileName || this.extractFileName(result.path);

    return {
      file: new File([], fileName, { type: 'text/csv' }),
      fileSize: result.size ?? 0,
      state: 'DONE',
      result,
      errorMessage: ''
    };
  }

  private extractFileName(path: string | null): string {
    if (!path) {
      return 'archivo.csv';
    }

    return path.split('/').pop() || 'archivo.csv';
  }

  private selectFirstAvailableItem(): void {

    if (this.statusItems.length === 0) {
      this.selectedIndex = null;
      return;
    }

    if (this.selectedIndex === null || this.selectedIndex >= this.statusItems.length) {
      this.selectedIndex = 0;
    }
  }

  selectStatusItem(index: number): void {
    this.selectedIndex = index;
  }

  goBack(): void {
    this.router.navigate(['/datasets/upload']);
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACCEPTED':
        return 'Aceptado';
      case 'REVIEW':
        return 'En revisión';
      case 'REJECTED':
        return 'Rechazado';
      default:
        return status;
    }
  }

  getStatusClass(status: string): string {
    return status.toLowerCase();
  }

  getRecommendation(status: string): string {
    switch (status) {
      case 'ACCEPTED':
        return 'El dataset puede ser almacenado en la zona raw del Data Lake.';
      case 'REVIEW':
        return 'El dataset debe ser revisado antes de almacenarse definitivamente.';
      case 'REJECTED':
        return 'El dataset no debe ser incorporado al Data Lake.';
      default:
        return 'No se pudo determinar una recomendación.';
    }
  }

  approveSelectedItem(): void {
    const item = this.selectedItem;

    if (!item || !item.result || item.result.status !== 'REVIEW') {
      return;
    }

    this.datasetService.approveDataset(item.result).subscribe({
      next: result => {
        this.datasetStateService.updateItemStatus(item, result);
        this.loadStatusItems();
      },
      error: () => {
        item.errorMessage = 'No se pudo aprobar el documento en el backend.';
      }
    });
  }

  rejectSelectedItem(): void {
    const item = this.selectedItem;

    if (!item || !item.result || item.result.status !== 'REVIEW') {
      return;
    }

    this.datasetService.rejectDataset(item.result).subscribe({
      next: result => {
        this.datasetStateService.updateItemStatus(item, result);
        this.loadStatusItems();
      },
      error: () => {
        item.errorMessage = 'No se pudo rechazar el documento en el backend.';
      }
    });
  }

  formatFileSize(size: number): string {
    if (size < 1024) {
      return `${size} B`;
    }

    if (size < 1024 * 1024) {
      return `${(size / 1024).toFixed(2)} KB`;
    }

    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  }

  getFileSize(item: FileQueueItem | null): number {
    return item?.fileSize ?? item?.file.size ?? 0;
  }
}
