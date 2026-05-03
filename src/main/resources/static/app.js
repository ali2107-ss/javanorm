const DEMO_TOKEN_KEY = 'jwt_token';

function getToken() {
  return localStorage.getItem(DEMO_TOKEN_KEY);
}

function setToken(token) {
  localStorage.setItem(DEMO_TOKEN_KEY, token);
}

function logout() {
  localStorage.removeItem(DEMO_TOKEN_KEY);
  localStorage.removeItem('user_info');
  window.location.href = '/index.html';
}

function parseJwt(token) {
  if (!token || !token.includes('.')) return {};
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(atob(base64).split('').map(c =>
      '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
    ).join(''));
    return JSON.parse(json);
  } catch (e) {
    return {};
  }
}

function getUserName() {
  try {
    const token = localStorage.getItem('jwt_token');
    if (!token) return 'Пользователь';
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.name || payload.email || payload.sub || 'Пользователь';
  } catch(e) {
    return 'Пользователь';
  }
}

function requireAuth() {
  if (!getToken()) {
    window.location.href = '/index.html';
    return false;
  }
  return true;
}

function initPage() {
  if (!requireAuth()) return;
  const user = document.getElementById('menuUserName');
  if (user) user.textContent = getUserName();
  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.toggle('active', link.getAttribute('href') === window.location.pathname);
  });
}

async function apiCall(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;
  if (!(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(url, { ...options, headers });
  if (!response.ok) throw new Error('API error ' + response.status);
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function pageShell(active) {
  const items = [
    ['📊', 'Дашборд', '/dashboard.html'],
    ['📄', 'Документы', '/documents.html'],
    ['🔍', 'Проверка', '/check.html'],
    ['📈', 'Статистика', '/stats.html'],
    ['👤', 'Профиль', '/profile.html'],
    ['ℹ️', 'О проекте', '/about.html']
  ];
  const userName = getUserName();
  const initial = userName ? userName.charAt(0).toUpperCase() : '👤';
  
  return \`
    <aside class="sidebar">
      <div class="brand"><div class="brand-icon">📄</div><div><b>НормаКонтроль</b><span>АВТОМАТИЗАЦИЯ ГОСТ</span></div></div>
      <nav class="nav">\${items.map(([icon, text, href]) =>
        \`<a class="nav-link \${active === href ? 'active' : ''}" href="\${href}"><span>\${icon}</span>\${text}</a>\`
      ).join('')}</nav>
      <div class="sidebar-bottom">
        <div class="user-chip"><div class="avatar">\${initial}</div><div><small>Пользователь</small><b id="menuUserName">\${userName}</b></div></div>
        <button class="logout" onclick="logout()"><span>🚪</span> Выйти</button>
      </div>
    </aside>\`;
}

function showModal(title, body) {
  closeModal();
  const wrap = document.createElement('div');
  wrap.className = 'modal-backdrop';
  wrap.id = 'appModal';
  wrap.innerHTML = `
    <div class="modal">
      <button class="modal-close" onclick="closeModal()">×</button>
      <h2>${title}</h2>
      <div>${body}</div>
    </div>`;
  document.body.appendChild(wrap);
}

function closeModal() {
  const modal = document.getElementById('appModal');
  if (modal) modal.remove();
}

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
  }[c]));
}
