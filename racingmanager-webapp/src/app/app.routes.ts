import { Routes } from '@angular/router';
import { authGuard, redirectIfAuthenticatedGuard } from './core/auth.guard';
import { SetupComponent } from './pages/setup/setup.component';
import { LoginComponent } from './pages/login/login.component';
import { RaceManagerShellComponent } from './shell/racemanager/racemanager.component';
import { SpectatorShellComponent } from './shell/spectator/spectator.component';
import { EventListComponent } from './events/event-list.component';
import { EventFormComponent } from './events/event-form.component';
import { EventDetailComponent } from './events/event-detail.component';
import { ParticipantListComponent } from './participants/participant-list.component';
import { ParticipantFormComponent } from './participants/participant-form.component';
import { ParticipantImportComponent } from './participants/participant-import.component';
import { ParticipantRandomizeComponent } from './participants/participant-randomize.component';
import { RaceControlComponent } from './races/race-control.component';

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
      {
        path: ':id',
        component: EventDetailComponent,
        children: [
          { path: '', component: ParticipantListComponent },
          { path: 'edit', component: EventFormComponent },
          { path: 'participants/new', component: ParticipantFormComponent },
          { path: 'participants/import', component: ParticipantImportComponent },
          { path: 'participants/randomize', component: ParticipantRandomizeComponent },
          { path: 'participants/:participantId', component: ParticipantFormComponent },
          { path: 'race-control', component: RaceControlComponent },
        ],
      },
    ],
  },
  {
    path: 'en/racemanager',
    component: RaceManagerShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: EventListComponent },
      { path: 'new', component: EventFormComponent },
      {
        path: ':id',
        component: EventDetailComponent,
        children: [
          { path: '', component: ParticipantListComponent },
          { path: 'edit', component: EventFormComponent },
          { path: 'participants/new', component: ParticipantFormComponent },
          { path: 'participants/import', component: ParticipantImportComponent },
          { path: 'participants/randomize', component: ParticipantRandomizeComponent },
          { path: 'participants/:participantId', component: ParticipantFormComponent },
          { path: 'race-control', component: RaceControlComponent },
        ],
      },
    ],
  },

  { path: 'de/spectator', component: SpectatorShellComponent },
  { path: 'en/spectator', component: SpectatorShellComponent },
];
