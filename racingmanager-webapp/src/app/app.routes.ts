import { Routes } from '@angular/router';
import { authGuard, redirectIfAuthenticatedGuard } from './core/auth.guard';
import { SetupComponent } from './pages/setup/setup.component';
import { LoginComponent } from './pages/login/login.component';
import { RaceManagerShellComponent } from './shell/racemanager/racemanager.component';
import { SpectatorShellComponent } from './shell/spectator/spectator.component';

export const routes: Routes = [
  { path: '', redirectTo: 'en/racemanager', pathMatch: 'full' },

  { path: 'de/setup', component: SetupComponent, canActivate: [redirectIfAuthenticatedGuard] },
  { path: 'en/setup', component: SetupComponent, canActivate: [redirectIfAuthenticatedGuard] },

  { path: 'de/login', component: LoginComponent, canActivate: [redirectIfAuthenticatedGuard] },
  { path: 'en/login', component: LoginComponent, canActivate: [redirectIfAuthenticatedGuard] },

  {
    path: 'de/racemanager',
    component: RaceManagerShellComponent,
    canActivate: [authGuard],
  },
  {
    path: 'en/racemanager',
    component: RaceManagerShellComponent,
    canActivate: [authGuard],
  },

  { path: 'de/spectator', component: SpectatorShellComponent },
  { path: 'en/spectator', component: SpectatorShellComponent },
];
