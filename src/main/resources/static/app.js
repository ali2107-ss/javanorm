const API = '/api/v1'

function getToken() {
  return localStorage.getItem('jwt_token')
}

function getUserInfo() {
  try {
    const token = getToken()
    if (!token) return { name: 'Пользователь', role: 'USER', initials: 'П' }
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => {
      return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    const payload = JSON.parse(jsonPayload);
    const name = payload.username || payload.name || 
                 payload.email || 'Пользователь'
    return {
      name,
      email: payload.email || '',
      role:  payload.roles || 'USER',
      initials: name.slice(0,2).toUpperCase()
    }
  } catch(e) {
    return { name: 'Пользователь', role: 'USER', initials: 'П' }
  }
}

async function apiCall(url, options = {}) {
  try {
    const res = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + getToken(),
        ...(options.headers || {})
      }
    })
    if (res.status === 401) {
      localStorage.clear()
      window.location.href = '/index.html'
      return null
    }
    if (!res.ok) return null
    const text = await res.text()
    return text ? JSON.parse(text) : {}
  } catch(e) {
    return null
  }
}

async function downloadFile(url, filename) {
  try {
    const res = await fetch(url, {
      headers: { 'Authorization': 'Bearer ' + getToken() }
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      showToast(text || 'Файл не найден или ещё генерируется', 'error')
      return
    }
    const blob = await res.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = getDownloadFilename(res, filename)
    link.click()
    setTimeout(() => URL.revokeObjectURL(link.href), 1000)
  } catch(e) {
    showToast('Ошибка при скачивании', 'error')
  }
}

function getDownloadFilename(response, fallback = 'download') {
  const disposition = response.headers.get('content-disposition') || ''
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match) {
    try { return decodeURIComponent(utf8Match[1].trim().replace(/^"|"$/g, '')) } catch(e) {}
  }
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
  if (plainMatch) {
    return plainMatch[1].trim()
  }
  return fallback
}

function safeFilenamePart(value) {
  return String(value || 'document')
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
}

function stripExtension(filename) {
  const clean = safeFilenamePart(filename)
  const dot = clean.lastIndexOf('.')
  return dot > 0 ? clean.slice(0, dot) : clean
}

function showToast(message, type = 'info') {
  const colors = {
    success: { bg:'#F0FDF4', border:'#86EFAC', text:'#166534' },
    error:   { bg:'#FEF2F2', border:'#FECACA', text:'#991B1B' },
    info:    { bg:'#FFF7ED', border:'#FED7AA', text:'#92400E' }
  }
  const c = colors[type] || colors.info
  const el = document.createElement('div')
  el.style.cssText = `
    position:fixed; bottom:24px; right:24px; z-index:9999;
    background:${c.bg}; border:1px solid ${c.border};
    color:${c.text}; border-radius:12px;
    padding:14px 20px; font-size:14px; font-weight:500;
    box-shadow:0 4px 24px rgba(0,0,0,0.1);
    animation:fadeIn 0.3s ease; max-width:320px;
  `
  el.textContent = message
  document.body.appendChild(el)
  setTimeout(() => el.remove(), 3500)
}

function animateCount(el, target, ms = 800) {
  const step = target / (ms / 16)
  let cur = 0
  const t = setInterval(() => {
    cur = Math.min(cur + step, target)
    el.textContent = Math.round(cur)
    if (cur >= target) clearInterval(t)
  }, 16)
}

function checkAuth() {
  if (!getToken()) {
    window.location.href = '/index.html'
    return false
  }
  return true
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ru-RU', {
    day:'2-digit', month:'short', year:'numeric',
    hour:'2-digit', minute:'2-digit'
  })
}

function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' Б'
  if (bytes < 1048576) return Math.round(bytes/1024) + ' КБ'
  return (bytes/1048576).toFixed(1) + ' МБ'
}

function setActivePage() {
  const path = window.location.pathname
  document.querySelectorAll('.nav-item').forEach(el => {
    if (el.getAttribute('href') === path) {
      el.style.background = 'rgba(255,107,0,0.15)';
      el.style.color = '#FF6B00';
      el.style.fontWeight = '600';
    }
  })
}

function setupCanvas(canvas) {
  const rect = canvas.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  const width = Math.max(320, Math.floor(rect.width || canvas.clientWidth || 600))
  const height = Math.max(220, Math.floor(rect.height || canvas.clientHeight || 300))
  canvas.width = width * dpr
  canvas.height = height * dpr
  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  return { ctx, width, height }
}

function drawEmptyCanvas(canvas, message = 'Нет данных') {
  const { ctx, width, height } = setupCanvas(canvas)
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#6E6E73'
  ctx.font = '600 14px Inter, Arial, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(message, width / 2, height / 2)
}

function drawLineChart(canvas, labels, values, options = {}) {
  if (!canvas || !values || values.length === 0) {
    if (canvas) drawEmptyCanvas(canvas)
    return
  }
  const { ctx, width, height } = setupCanvas(canvas)
  const pad = { left: 42, right: 18, top: 18, bottom: 34 }
  const chartW = width - pad.left - pad.right
  const chartH = height - pad.top - pad.bottom
  const color = options.color || '#FF6B00'
  const max = options.max ?? 100

  ctx.clearRect(0, 0, width, height)
  ctx.strokeStyle = '#E5E5EA'
  ctx.lineWidth = 1
  ctx.fillStyle = '#6E6E73'
  ctx.font = '12px Inter, Arial, sans-serif'
  ctx.textAlign = 'right'

  for (let i = 0; i <= 4; i++) {
    const y = pad.top + chartH - (chartH * i / 4)
    const val = Math.round(max * i / 4)
    ctx.beginPath()
    ctx.moveTo(pad.left, y)
    ctx.lineTo(width - pad.right, y)
    ctx.stroke()
    ctx.fillText(val, pad.left - 8, y + 4)
  }

  const points = values.map((value, i) => {
    const x = pad.left + (values.length === 1 ? chartW / 2 : chartW * i / (values.length - 1))
    const y = pad.top + chartH - (Math.max(0, Math.min(max, Number(value) || 0)) / max) * chartH
    return { x, y }
  })

  ctx.beginPath()
  points.forEach((p, i) => i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y))
  ctx.strokeStyle = color
  ctx.lineWidth = 3
  ctx.stroke()

  const gradient = ctx.createLinearGradient(0, pad.top, 0, height - pad.bottom)
  gradient.addColorStop(0, options.fill || 'rgba(255,107,0,0.18)')
  gradient.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.lineTo(points[points.length - 1].x, height - pad.bottom)
  ctx.lineTo(points[0].x, height - pad.bottom)
  ctx.closePath()
  ctx.fillStyle = gradient
  ctx.fill()

  points.forEach(p => {
    ctx.beginPath()
    ctx.arc(p.x, p.y, 4, 0, Math.PI * 2)
    ctx.fillStyle = '#FFFFFF'
    ctx.fill()
    ctx.strokeStyle = color
    ctx.lineWidth = 2
    ctx.stroke()
  })

  ctx.fillStyle = '#6E6E73'
  ctx.textAlign = 'center'
  labels.forEach((label, i) => {
    if (values.length > 8 && i % Math.ceil(values.length / 8) !== 0) return
    const x = pad.left + (values.length === 1 ? chartW / 2 : chartW * i / (values.length - 1))
    ctx.fillText(String(label), x, height - 10)
  })
}

function drawBarChart(canvas, labels, values, options = {}) {
  if (!canvas || !values || values.length === 0) {
    if (canvas) drawEmptyCanvas(canvas)
    return
  }
  const { ctx, width, height } = setupCanvas(canvas)
  const pad = { left: 44, right: 16, top: 18, bottom: 52 }
  const chartW = width - pad.left - pad.right
  const chartH = height - pad.top - pad.bottom
  const max = Math.max(1, ...values.map(v => Number(v) || 0))
  const gap = 12
  const barW = Math.max(18, (chartW - gap * (values.length - 1)) / values.length)

  ctx.clearRect(0, 0, width, height)
  ctx.strokeStyle = '#E5E5EA'
  ctx.fillStyle = '#6E6E73'
  ctx.font = '12px Inter, Arial, sans-serif'
  ctx.textAlign = 'right'
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + chartH - (chartH * i / 4)
    ctx.beginPath()
    ctx.moveTo(pad.left, y)
    ctx.lineTo(width - pad.right, y)
    ctx.stroke()
    ctx.fillText(Math.round(max * i / 4), pad.left - 8, y + 4)
  }

  values.forEach((value, i) => {
    const h = ((Number(value) || 0) / max) * chartH
    const x = pad.left + i * (barW + gap)
    const y = pad.top + chartH - h
    ctx.fillStyle = (options.colors || ['#FF6B00', '#007AFF', '#34C759', '#FF9500', '#FF3B30'])[i % 5]
    ctx.fillRect(x, y, barW, h)
    ctx.fillStyle = '#1C1C1E'
    ctx.textAlign = 'center'
    ctx.font = '600 12px Inter, Arial, sans-serif'
    ctx.fillText(value, x + barW / 2, y - 6)
    ctx.save()
    ctx.translate(x + barW / 2, height - 12)
    ctx.rotate(-Math.PI / 8)
    ctx.fillStyle = '#6E6E73'
    ctx.font = '11px Inter, Arial, sans-serif'
    ctx.fillText(String(labels[i]).slice(0, 14), 0, 0)
    ctx.restore()
  })
}

document.addEventListener('DOMContentLoaded', () => {
  const user = getUserInfo()
  const nameEl = document.getElementById('userName')
  const roleEl = document.getElementById('userRole')
  const initEl = document.getElementById('userInitials')
  if (nameEl) nameEl.textContent = user.name
  if (roleEl) roleEl.textContent = user.role
  if (initEl) initEl.textContent = user.initials

  const logoutBtn = document.getElementById('logoutBtn')
  if (logoutBtn) logoutBtn.onclick = () => {
    localStorage.clear()
    window.location.href = '/index.html'
  }

  setActivePage()

  setInterval(() => {
    const el = document.getElementById('currentTime')
    if (el) el.textContent = new Date().toLocaleString('ru-RU')
  }, 1000)
})

function renderSidebar() {
  const sidebar = `
    <div style="background: var(--sidebar); width: 240px; height: 100vh; position: fixed; left: 0; top: 0; display: flex; flex-direction: column;">
      <div style="padding: 24px; display: flex; align-items: center; gap: 12px; margin-bottom: 24px;">
        <div style="width: 32px; height: 32px; background: var(--accent); border-radius: 8px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; font-size: 14px;">НК</div>
        <div style="color: white; font-weight: 700; font-size: 18px;">НормаКонтроль</div>
      </div>
      <nav style="flex: 1; padding: 0 16px; display: flex; flex-direction: column; gap: 8px;">
        <a href="/dashboard.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>
          Дашборд
        </a>
        <a href="/documents.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
          Документы
        </a>
        <a href="/check.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          Проверка
        </a>
        <a href="/stats.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></svg>
          Статистика
        </a>
        <a href="/database.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"></ellipse><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path></svg>
          База данных
        </a>
        <a href="/profile.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
          Профиль
        </a>
        <a href="/about.html" class="nav-item" style="padding: 10px 16px; border-radius: 8px; color: var(--sidebar-text); font-size: 14px; display: flex; align-items: center; gap: 10px; transition: 0.15s all ease;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
          О программе
        </a>
      </nav>
      <div style="padding: 16px; border-top: 1px solid rgba(255,255,255,0.1);">
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px;">
          <div id="userInitials" style="width: 32px; height: 32px; background: var(--accent); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; font-size: 12px;"></div>
          <div style="flex: 1; overflow: hidden;">
            <div id="userName" style="color: white; font-size: 14px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"></div>
            <div id="userRole" style="color: var(--sidebar-text); font-size: 12px;"></div>
          </div>
        </div>
        <button id="logoutBtn" style="width: 100%; padding: 8px; background: rgba(255,255,255,0.06); border: none; border-radius: 8px; color: #98989A; font-size: 13px; cursor: pointer; transition: 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px;" onmouseover="this.style.background='var(--danger)'; this.style.color='white'" onmouseout="this.style.background='rgba(255,255,255,0.06)'; this.style.color='#98989A'">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
          Выйти
        </button>
      </div>
      <style>
        .nav-item:hover:not(.active) { background: rgba(255,255,255,0.06); color: white !important; }
      </style>
    </div>
  `;
  document.write(sidebar);
}
