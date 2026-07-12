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
import { QualificationComponent } from './qualification/qualification.component';
import { KnockoutComponent } from './knockout/knockout.component';
import { ResultsComponent } from './results/results.component';
import { ExportComponent } from './export/export.component';
import { AuditComponent } from './audit/audit.component';

// Language is a runtime concern (ngx-translate + localStorage), so routes carry
// no locale prefix.
export const routes: Routes = [
  { path: '', redirectTo: 'racemanager', pathMatch: 'full' },

  { path: 'setup', component: SetupComponent, canActivate: [redirectIfAuthenticatedGuard] },
  { path: 'login', component: LoginComponent, canActivate: [redirectIfAuthenticatedGuard] },

  {
    path: 'racemanager',
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
          { path: 'qualification', component: QualificationComponent },
          { path: 'knockout', component: KnockoutComponent },
          { path: 'results', component: ResultsComponent },
          { path: 'export', component: ExportComponent },
          { path: 'audit', component: AuditComponent },
        ],
      },
    ],
  },

  { path: 'spectator', component: SpectatorShellComponent },
];
