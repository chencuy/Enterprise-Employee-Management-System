export function startBootDiagnostics() {
  const logEl = document.getElementById('boot-log');
  const statusEl = document.getElementById('boot-status');

  function write(message) {
    if (logEl) {
      logEl.textContent += `[${new Date().toLocaleTimeString()}] ${message}\n`;
    }
  }

  window.__bootLog = write;
  window.__bootStatus = function updateBootStatus(message) {
    if (statusEl) {
      statusEl.textContent = message;
    }
    write(message);
  };

  window.addEventListener('error', (event) => {
    write(`JS error: ${event.message || 'unknown'} ${event.filename || ''}:${event.lineno || 0}`);
  });

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason && (event.reason.message || event.reason);
    write(`Promise error: ${reason || 'unknown'}`);
  });

  write('Frontend module bootstrap loaded');
}
