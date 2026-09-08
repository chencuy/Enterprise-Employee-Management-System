export function loadClassicScript(src, label) {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = false;
    script.onload = () => {
      window.__bootLog && window.__bootLog(`${label} loaded`);
      resolve();
    };
    script.onerror = () => {
      const error = new Error(`${label} failed to load: ${src}`);
      window.__bootStatus && window.__bootStatus(error.message);
      reject(error);
    };
    document.head.appendChild(script);
  });
}
