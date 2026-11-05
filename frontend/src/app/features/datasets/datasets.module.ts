import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { DatasetStatusComponent } from './pages/dataset-status/dataset-status.component';
import { DatasetUploadComponent } from './pages/dataset-upload/dataset-upload.component';
import { DataLakeStatusComponent } from './pages/data-lake-status/data-lake-status.component';

@NgModule({
  declarations: [
    DatasetUploadComponent,
    DatasetStatusComponent,
    DataLakeStatusComponent
  ],
  imports: [
    CommonModule,
    FormsModule
  ],
  exports: [
    DatasetUploadComponent,
    DatasetStatusComponent,
    DataLakeStatusComponent
  ]
})
export class DatasetsModule { }
