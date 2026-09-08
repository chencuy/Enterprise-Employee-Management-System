import { startBootDiagnostics } from './runtime/bootDiagnostics.js';
import { loadClassicScript } from './runtime/scriptLoader.js';
import { API_DEFAULT_TIMEOUT, API_STATUS } from './shared/api.js';

window.EMS_FRONTEND = {
  version: '2026.06-security-migration',
  api: {
    defaultTimeout: API_DEFAULT_TIMEOUT,
    status: API_STATUS
  }
};

startBootDiagnostics();

bootstrap();

async function bootstrap() {
  await loadClassicScript('/vendor/vue.global.prod.js?v=3.4.38', 'Vue');
  await loadClassicScript('/vendor/lucide.min.js?v=0.468.0', 'Lucide');
  await loadClassicScript('/app/app.js?v=20260626-renderfix2', 'Application');
}
