/* ═══════════════════════════════════════════
   НормаКонтроль — Shared JS (app.js)
   ═══════════════════════════════════════════ */

const API_BASE = '';          // Same origin — Spring Boot serves static files

// ── Token helpers ──────────────────────────────────
function getToken()     { return localStorage.getItem('jwt_token'); }
function setToken(t)    { localStorage.setItem('jwt_token', t); }
function removeToken()  { localStorage.removeItem('jwt_token'); }

function getUserInfo() {
  try { return JSON.parse(localStorage.getItem('user_info') || '{}'); }
  catch { return {}; }
}
function setUserInfo(u) { localStorage.setItem('user_info', JSON.stringify(u)); }

function logout() {
  removeToken();
  localStorage.removeItem('user_info');
  window.location.href = '/index.html';
}

// ── Auth guard (call on every protected page) ──────
function requireAuth() {
  if (!getToken()) {
    window.location.href = '/index.html';
    return false;
  }
  return true;
}

// ── Base fetch with JWT ────────────────────────────
async function apiCall(url, options = {}) {
  const token = getToken();
  const headers = {
    ...options.headers,
  };

  // Don't set Content-Type for FormData (browser sets multipart boundary)
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  const resp = await fetch(API_BASE + url, { ...options, headers });

  if (resp.status === 401) {
    logout();
    return null;
  }

  if (resp.status === 204) return null;   // No content

  const text = await resp.text();
  if (!text) return null;

  let data;
  try { data = JSON.parse(text); }
  catch { data = { message: text }; }

  if (!resp.ok) {
    throw new Error(data?.message || data?.error || `Ошибка ${resp.status}`);
  }

  return data;
}

// ── Download helper ────────────────────────────────
async function apiDownload(url, filename) {
  const token = getToken();
  const resp = await fetch(API_BASE + url, {
    headers: token ? { 'Authorization': 'Bearer ' + token } : {}
  });

  if (!resp.ok) {
    showError('Не удалось скачать файл');
    return;
  }

  const blob = await resp.blob();
  const a    = document.createElement('a');
  a.href     = URL.createObjectURL(blob);
  a.download = filename || 'report.pdf';
  a.click();
  URL.revokeObjectURL(a.href);
}

// ── Toast notifications ────────────────────────────
(function initToasts() {
  const container = document.createElement('div');
  container.className = 'toast-container';
  container.id = 'toastContainer';
  document.addEventListener('DOMContentLoaded', () => {
    document.body.appendChild(container);
  });
})();

function showToast(message, type = 'info', duration = 3500) {
  const container = document.getElementById('toastContainer') ||
    (() => {
      const c = document.createElement('div');
      c.className = 'toast-container';
      c.id = 'toastContainer';
      document.body.appendChild(c);
      return c;
    })();

  const icons = { success: '✅', error: '❌', info: 'ℹ️' };
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${icons[type] || ''}</span><span>${message}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    toast.style.transition = 'all .3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

function showSuccess(msg) { showToast(msg, 'success'); }
function showError(msg)   { showToast(msg, 'error'); }
function showInfo(msg)    { showToast(msg, 'info'); }

// ── Formatting helpers ─────────────────────────────
function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d)) return iso;
  return d.toLocaleDateString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  }).replace(',', '');
}

function formatDateShort(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d)) return iso;
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function formatFileSize(bytes) {
  if (bytes == null) return '—';
  if (bytes < 1024)        return bytes + ' Б';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' КБ';
  return (bytes / (1024 * 1024)).toFixed(1) + ' МБ';
}

function getScoreColor(score) {
  if (score == null) return '#aaa';
  if (score >= 80) return '#27AE60';
  if (score >= 60) return '#F39C12';
  return '#E74C3C';
}

function getScoreLabel(score) {
  if (score == null) return '—';
  if (score >= 80) return 'ПРОШЁЛ';
  return 'НЕ ПРОШЁЛ';
}

function statusBadge(status) {
  const map = {
    'UPLOADED':  ['badge-grey',   '📁 Загружен'],
    'PENDING':   ['badge-blue',   '🕐 В очереди'],
    'CHECKING':  ['badge-yellow', '🔍 Проверяется<span class="dots"></span>'],
    'DONE':      ['badge-green',  '✅ Готово'],
    'FAILED':    ['badge-red',    '❌ Нарушения'],
    'ERROR':     ['badge-red',    '⚠️ Ошибка'],
  };
  const [cls, label] = map[status] || ['badge-grey', status || '—'];
  return `<span class="badge ${cls}">${label}</span>`;
}

function auditBadge(action) {
  const map = {
    'LOGIN':       'badge-blue',
    'LOGOUT':      'badge-grey',
    'UPLOAD':      'badge-green',
    'START_CHECK': 'badge-yellow',
    'DELETE':      'badge-red',
  };
  return `<span class="badge ${map[action] || 'badge-grey'}">${action}</span>`;
}

// ── Sidebar active link ────────────────────────────
function markActivePage() {
  const path = window.location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav-item').forEach(el => {
    const href = el.getAttribute('href') || '';
    el.classList.toggle('active', href === path || href.endsWith('/' + path));
  });
}

// ── Fill sidebar user info ─────────────────────────
function fillSidebarUser() {
  const u = getUserInfo();
  const nameEl  = document.getElementById('sidebarUserName');
  const roleEl  = document.getElementById('sidebarUserRole');
  const avEl    = document.getElementById('sidebarAvatar');

  if (nameEl) nameEl.textContent = u.name || u.email || 'Пользователь';
  if (roleEl) roleEl.textContent = u.role === 'ADMIN' ? 'Администратор' : 'Пользователь';
  if (avEl)   avEl.textContent   = (u.name || u.email || 'U')[0].toUpperCase();
}

// ── On DOMContentLoaded for protected pages ────────
function initProtectedPage() {
  if (!requireAuth()) return;
  markActivePage();
  fillSidebarUser();
}

// ── Empty state helper ─────────────────────────────
function emptyState(icon, title, text) {
  return `<div class="empty-state">
    <div class="empty-icon">${icon}</div>
    <div class="empty-title">${title}</div>
    <div class="empty-text">${text}</div>
  </div>`;
}

// ── Skeleton rows ──────────────────────────────────
function skeletonRows(cols, count = 4) {
  return Array.from({ length: count }, () =>
    `<tr>${Array.from({ length: cols }, () =>
      `<td><div class="skeleton" style="height:14px;width:${60 + Math.random()*30}%"></div></td>`
    ).join('')}</tr>`
  ).join('');
}
