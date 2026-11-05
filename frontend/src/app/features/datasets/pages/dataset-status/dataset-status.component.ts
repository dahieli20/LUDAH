import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { DatasetService } from '../../services/dataset.service';
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

  private subscriptions = new Subscription();

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
      this.filterItemsByStatus();
    });

    const queueSubscription = this.datasetStateService.fileQueue$.subscribe(queue => {
      this.fileQueue = queue;
      this.filterItemsByStatus();
    });

    this.subscriptions.add(routeSubscription);
    this.subscriptions.add(queueSubscription);
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

  private filterItemsByStatus(): void {
    this.statusItems = this.fileQueue.filter(
      item => item.result?.status === this.status
    );

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
      next: result => this.datasetStateService.updateItemStatus(item, result),
      error: () => {
        item.errorMessage = 'No se pudo aprobar el documento en el backend.';
        this.datasetStateService.setFileQueue(this.fileQueue);
      }
    });
  }

  rejectSelectedItem(): void {
    const item = this.selectedItem;

    if (!item || !item.result || item.result.status !== 'REVIEW') {
      return;
    }

    this.datasetService.rejectDataset(item.result).subscribe({
      next: result => this.datasetStateService.updateItemStatus(item, result),
      error: () => {
        item.errorMessage = 'No se pudo rechazar el documento en el backend.';
        this.datasetStateService.setFileQueue(this.fileQueue);
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
}
