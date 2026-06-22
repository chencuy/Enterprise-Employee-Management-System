(function () {
  var logEl = document.getElementById('boot-log');
  var statusEl = document.getElementById('boot-status');

  function write(message) {
    if (logEl) {
      logEl.textContent += '[' + new Date().toLocaleTimeString() + '] ' + message + '\n';
    }
  }

  window.__bootLog = write;
  window.__bootStatus = function (message) {
    if (statusEl) {
      statusEl.textContent = message;
    }
    write(message);
  };

  window.addEventListener('error', function (event) {
    write('JS错误: ' + (event.message || '未知错误') + ' ' + (event.filename || '') + ':' + (event.lineno || 0));
  });
  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason && (event.reason.message || event.reason);
    write('Promise错误: ' + (reason || '未知错误'));
  });

  write('HTML已加载');
  fetch('/api/auth/me', { credentials: 'include' })
    .then(function (response) {
      write('/api/auth/me 状态: HTTP ' + response.status);
      return response.text();
    })
    .then(function (text) {
      write('/api/auth/me 返回: ' + text.slice(0, 180));
    })
    .catch(function (error) {
      write('/api/auth/me 请求失败: ' + error.message);
    });
})();
