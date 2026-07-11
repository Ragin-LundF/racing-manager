import { Routes } from '@angular/router';
import { DirectorShellComponent } from './shell/director/director.component';
import { SpectatorShellComponent } from './shell/spectator/spectator.component';

export const routes: Routes = [
  { path: '', redirectTo: 'en/director', pathMatch: 'full' },
  { path: 'de/director', component: DirectorShellComponent },
  { path: 'en/director', component: DirectorShellComponent },
  { path: 'de/spectator', component: SpectatorShellComponent },
  { path: 'en/spectator', component: SpectatorShellComponent },
];
