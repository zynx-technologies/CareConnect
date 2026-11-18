import { InjectionToken } from '@angular/core';

export interface AppSettings {
  authMode: 'dev' | 'entra' | 'database';
  clientId?: string;
  tenantId?: string;
  apiScope?: string;
}

export const APP_SETTINGS = new InjectionToken<AppSettings>('APP_SETTINGS');
