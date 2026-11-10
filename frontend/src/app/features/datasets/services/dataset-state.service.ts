import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

import { FileQueueItem } from '../../../models/dataset-queue.model';

@Injectable({
  providedIn: 'root'
})
export class DatasetStateService {
  private readonly fileQueueSubject = new BehaviorSubject<FileQueueItem[]>([]);

  fileQueue$ = this.fileQueueSubject.asObservable();

  setFileQueue(items: FileQueueItem[]): void {
    this.fileQueueSubject.next([...items]);
  }

  getFileQueueSnapshot(): FileQueueItem[] {
    return this.fileQueueSubject.value;
  }

  updateItemStatus(
    targetItem: FileQueueItem,
    result: FileQueueItem['result']
  ): void {
    const updatedQueue = this.fileQueueSubject.value.map(item => {
      const sameItem = item === targetItem
        || (!!item.result?.path && item.result.path === targetItem.result?.path);

      if (!sameItem || !result) {
        return item;
      }

      return {
        ...item,
        state: 'DONE' as const,
        result
      };
    });

    this.fileQueueSubject.next(updatedQueue);
  }
}
