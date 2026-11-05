import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { DatasetUploadComponent } from './features/datasets/pages/dataset-upload/dataset-upload.component';
import { DatasetStatusComponent } from './features/datasets/pages/dataset-status/dataset-status.component';
import { DataLakeStatusComponent } from './features/datasets/pages/data-lake-status/data-lake-status.component';
import { HomeComponent } from './features/datasets/pages/home/home.component';

const routes: Routes = [
  {
    path: 'inicio',
    component: HomeComponent
  },
  {
    path: 'datasets/upload',
    component: DatasetUploadComponent
  },
  {
    path: 'datasets/lake-status',
    component: DataLakeStatusComponent
  },
  {
    path: 'datasets/status/:status',
    component: DatasetStatusComponent
  },
  {
    path: '',
    redirectTo: 'inicio',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: 'inicio'
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { scrollPositionRestoration: 'top' })],
  exports: [RouterModule]
})
export class AppRoutingModule {}
