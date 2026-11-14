import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { APP_SETTINGS, AppSettings } from './app/settings';

// Settings load at runtime so one built image can move through every environment unchanged.
fetch('/config.json')
  .then((response) => {
    if (!response.ok) throw new Error('config.json could not be loaded');
    return response.json() as Promise<AppSettings>;
  })
  .then((settings) =>
    bootstrapApplication(App, {
      ...appConfig,
      providers: [...appConfig.providers, { provide: APP_SETTINGS, useValue: settings }],
    }),
  )
  .catch((error) => {
    console.error(error);
    document.body.textContent = 'CareConnect could not start. Contact the service desk.';
  });
