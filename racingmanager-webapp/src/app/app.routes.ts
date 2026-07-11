import { Routes } from '@angular/router';
import { authGuard, redirectIfAuthenticatedGuard } from './core/auth.guard';
import { SetupComponent } from './pages/setup/setup.component';
import { LoginComponent } from './pages/login/login.component';
import { RaceManagerShellComponent } from './shell/racemanager/racemanager.component';
import { SpectatorShellComponent } from './shell/spectator/spectator.component';
import { EventListComponent } from './events/event-list.component';
import { EventFormComponent } from './events/event-form.component';

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
    children: [
      { path: '', component: EventListComponent },
      { path: 'new', component: EventFormComponent },
      { path: ':id', component: EventFormComponent },
    ],
  },
  {
    path: 'en/racemanager',
    component: RaceManagerShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: EventListComponent },
      { path: 'new', component: EventFormComponent },
      { path: ':id', component: EventFormComponent },
    ],
  },

  { path: 'de/spectator', component: SpectatorShellComponent },
  { path: 'en/spectator', component: SpectatorShellComponent },
];
