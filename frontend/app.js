/**
 * NormaControl Frontend Application Logic
 */
const CONFIG = {
    API_BASE_URL: 'http://localhost:8080/api/v1',
    POLL_INTERVAL: 3000
};

class NormaControlApp {
    constructor() {
        this.state = {
            user: null,
            token: localStorage.getItem('access_token'),
            currentView: 'dashboard',
            documents: [],
            selectedFile: null,
            isPolling: false
        };

        this.init();
    }

    init() {
        this.cacheDOM();
        this.bindEvents();
        this.checkAuth();
        this.updateApiStatus();
    }

    cacheDOM() {
        this.nodes = {
            app: document.getElementById('app'),
            authOverlay: document.getElementById('authOverlay'),
            loginForm: document.getElementById('loginForm'),
            registerForm: document.getElementById('registerForm'),
            viewTitle: document.getElementById('viewTitle'),
            viewContainer: document.getElementById('viewContainer'),
            navLinks: document.querySelectorAll('.nav-links li'),
            views: document.querySelectorAll('.view'),
            userName: document.getElementById('userName'),
            logoutBtn: document.getElementById('logoutBtn'),
            apiStatus: document.getElementById('apiStatus'),
            dropZone: document.getElementById('dropZone'),
            fileInput: document.getElementById('fileInput'),
            selectedFile: document.getElementById('selectedFile'),
            fileName: document.getElementById('fileName'),
            startCheckBtn: document.getElementById('startCheckBtn'),
            recentDocsTable: document.getElementById('recentDocsTable'),
            totalDocs: document.getElementById('totalDocs'),
            cleanDocs: document.getElementById('cleanDocs'),
            totalViolations: document.getElementById('totalViolations'),
            notification: document.getElementById('notification'),
            violationsList: document.getElementById('violationsList'),
            violationCount: document.getElementById('violationCount'),
            resultDocName: document.getElementById('resultDocName'),
            resultScore: document.getElementById('resultScore'),
            aiSuggestion: document.getElementById('aiSuggestion'),
            docA: document.getElementById('docA'),
            docB: document.getElementById('docB'),
            compareBtn: document.getElementById('compareBtn'),
            compareResults: document.getElementById('compareResults')
        };
    }

    bindEvents() {
        // Navigation
        this.nodes.navLinks.forEach(link => {
            link.addEventListener('click', () => {
                const view = link.getAttribute('data-view');
                this.switchView(view);
            });
        });

        // Auth Tabs
        document.querySelectorAll('.auth-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                const type = tab.getAttribute('data-tab');
                document.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                
                this.nodes.loginForm.classList.toggle('active', type === 'login');
                this.nodes.registerForm.classList.toggle('active', type === 'register');
            });
        });

        // Forms
        this.nodes.loginForm.addEventListener('submit', (e) => this.handleLogin(e));
        this.nodes.registerForm.addEventListener('submit', (e) => this.handleRegister(e));
        this.nodes.logoutBtn.addEventListener('click', () => this.handleLogout());

        // File Upload
        this.nodes.dropZone.addEventListener('click', () => this.nodes.fileInput.click());
        this.nodes.fileInput.addEventListener('change', (e) => this.handleFileSelect(e));
        this.nodes.dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            this.nodes.dropZone.classList.add('drag-over');
        });
        this.nodes.dropZone.addEventListener('dragleave', () => this.nodes.dropZone.classList.remove('drag-over'));
        this.nodes.dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            this.nodes.dropZone.classList.remove('drag-over');
            if (e.dataTransfer.files.length) {
                this.handleFileSelect({ target: { files: e.dataTransfer.files } });
            }
        });

        this.nodes.startCheckBtn.addEventListener('click', () => this.handleUpload());
        
        // Compare
        this.nodes.compareBtn.addEventListener('click', () => this.handleCompare());
    }

    // --- Core Logic ---

    async checkAuth() {
        if (!this.state.token) {
            this.showAuth(true);
            return;
        }

        try {
            const response = await this.apiFetch('/users/me');
            if (response.ok) {
                this.state.user = await response.json();
                this.updateUserUI();
                this.showAuth(false);
                this.loadDashboardData();
            } else {
                this.handleLogout();
            }
        } catch (e) {
            this.showNotification('Сервер недоступен', 'error');
        }
    }

    async handleLogin(e) {
        e.preventDefault();
        const login = document.getElementById('loginEmail').value;
        const password = document.getElementById('loginPass').value;

        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ login, password })
            });

            const data = await response.json();
            if (response.ok) {
                this.state.token = data.access_token;
                localStorage.setItem('access_token', data.access_token);
                this.checkAuth();
                this.showNotification('Вход выполнен!', 'success');
            } else {
                this.showNotification(data.message || 'Ошибка входа', 'error');
            }
        } catch (e) {
            this.showNotification('Ошибка сети', 'error');
        }
    }

    async handleRegister(e) {
        e.preventDefault();
        const username = document.getElementById('regUser').value;
        const email = document.getElementById('regEmail').value;
        const password = document.getElementById('regPass').value;

        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/auth/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password, fullName: username })
            });

            if (response.ok) {
                this.showNotification('Успешная регистрация! Теперь войдите.', 'success');
                document.querySelector('.auth-tab[data-tab="login"]').click();
            } else {
                const data = await response.json();
                this.showNotification(data.message || 'Ошибка регистрации', 'error');
            }
        } catch (e) {
            this.showNotification('Ошибка сети', 'error');
        }
    }

    handleLogout() {
        localStorage.removeItem('access_token');
        this.state.token = null;
        this.state.user = null;
        this.showAuth(true);
    }

    // --- Navigation & View Logic ---

    switchView(viewId) {
        this.state.currentView = viewId;
        
        // Update Nav
        this.nodes.navLinks.forEach(link => {
            link.classList.toggle('active', link.getAttribute('data-view') === viewId);
        });

        // Update Views
        this.nodes.views.forEach(view => {
            view.classList.toggle('active', view.id === `${viewId}View`);
        });

        // Update Title
        const titles = {
            dashboard: 'Панель управления',
            upload: 'Новая проверка',
            history: 'История проверок',
            results: 'Результаты анализа',
            compare: 'Сравнение документов'
        };
        this.nodes.viewTitle.innerText = titles[viewId] || 'NormaControl';

        // Load View Data
        if (viewId === 'dashboard') this.loadDashboardData();
        if (viewId === 'compare') this.loadCompareOptions();
    }

    showAuth(show) {
        this.nodes.authOverlay.classList.toggle('hidden', !show);
    }

    // --- Dashboard ---

    async loadDashboardData() {
        try {
            const response = await this.apiFetch('/documents');
            if (response.ok) {
                this.state.documents = await response.json();
                this.renderDocuments();
                this.updateStats();
            }
        } catch (e) {
            console.error(e);
        }
    }

    renderDocuments() {
        const tbody = this.nodes.recentDocsTable;
        tbody.innerHTML = '';

        if (this.state.documents.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 40px; color: var(--text-muted)">Документов пока нет</td></tr>';
            return;
        }

        this.state.documents.slice(0, 10).forEach(doc => {
            const tr = document.createElement('tr');
            const date = new Date(doc.createdAt).toLocaleDateString();
            const statusClass = doc.status === 'PROCESSED' ? 'processed' : 'pending';
            const statusText = doc.status === 'PROCESSED' ? 'Проверен' : 'В обработке';
            
            tr.innerHTML = `
                <td style="font-weight: 500">${doc.originalName}</td>
                <td>${date}</td>
                <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                <td>${doc.violationCount || 0}</td>
                <td>
                    <button class="btn btn-secondary btn-sm" onclick="app.viewResults('${doc.id}')"><i class="fas fa-eye"></i></button>
                    <button class="btn btn-secondary btn-sm" onclick="app.deleteDoc('${doc.id}')"><i class="fas fa-trash"></i></button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    }

    updateStats() {
        const docs = this.state.documents;
        this.nodes.totalDocs.innerText = docs.length;
        this.nodes.cleanDocs.innerText = docs.filter(d => d.violationCount === 0 && d.status === 'PROCESSED').length;
        this.nodes.totalViolations.innerText = docs.reduce((acc, d) => acc + (d.violationCount || 0), 0);
    }

    // --- Upload ---

    handleFileSelect(e) {
        const file = e.target.files[0];
        if (!file) return;

        this.state.selectedFile = file;
        this.nodes.fileName.innerText = file.name;
        this.nodes.selectedFile.classList.remove('hidden');
        this.nodes.startCheckBtn.disabled = false;
    }

    async handleUpload() {
        if (!this.state.selectedFile) return;

        const formData = new FormData();
        formData.append('file', this.state.selectedFile);

        this.nodes.startCheckBtn.disabled = true;
        this.nodes.startCheckBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Загрузка...';

        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/documents`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${this.state.token}` },
                body: formData
            });

            if (response.ok) {
                const doc = await response.json();
                this.showNotification('Документ загружен и отправлен на проверку', 'success');
                this.viewResults(doc.id);
            } else {
                this.showNotification('Ошибка при загрузке', 'error');
            }
        } catch (e) {
            this.showNotification('Ошибка сети', 'error');
        } finally {
            this.nodes.startCheckBtn.disabled = false;
            this.nodes.startCheckBtn.innerText = 'Запустить анализ';
        }
    }

    // --- Results ---

    async viewResults(docId) {
        this.switchView('results');
        this.nodes.violationsList.innerHTML = '<div class="loader"><i class="fas fa-spinner fa-spin"></i> Загрузка результатов...</div>';
        
        try {
            // Get doc info
            const docResponse = await this.apiFetch(`/documents/${docId}`);
            const doc = await docResponse.json();
            this.nodes.resultDocName.innerText = doc.originalName;

            // Get results
            const resultsResponse = await this.apiFetch(`/check-results/document/${docId}`);
            const results = await resultsResponse.json();
            
            this.renderResults(results[0] || { violations: [] }); // Assuming latest check result
        } catch (e) {
            console.error(e);
            this.nodes.violationsList.innerHTML = 'Ошибка при загрузке данных';
        }
    }

    renderResults(result) {
        const list = this.nodes.violationsList;
        list.innerHTML = '';
        
        const violations = result.violations || [];
        this.nodes.violationCount.innerText = violations.length;
        
        const score = violations.length === 0 ? 100 : Math.max(0, 100 - (violations.length * 5));
        this.nodes.resultScore.innerText = `Счёт: ${score}%`;
        this.nodes.resultScore.style.background = score > 80 ? 'var(--success)' : score > 50 ? 'var(--warning)' : 'var(--error)';

        if (violations.length === 0) {
            list.innerHTML = '<div class="empty-state">Нарушений не обнаружено. Документ соответствует ГОСТ 19.201-78!</div>';
            return;
        }

        violations.forEach((v, index) => {
            const item = document.createElement('div');
            item.className = `violation-item ${v.severity === 'WARNING' ? 'warning' : 'error'}`;
            item.innerHTML = `
                <h4><i class="fas fa-exclamation-circle"></i> ${v.ruleName || 'Нарушение'}</h4>
                <p>${v.message}</p>
                <div style="margin-top: 8px; font-size: 11px; color: var(--primary-light)">Раздел: ${v.section || 'Не указан'}</div>
            `;
            item.onclick = () => {
                document.querySelectorAll('.violation-item').forEach(el => el.classList.remove('active'));
                item.classList.add('active');
                this.showAiRecommendation(v);
            };
            list.appendChild(item);
        });
    }

    showAiRecommendation(violation) {
        const aiBox = this.nodes.aiSuggestion;
        aiBox.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ИИ анализирует нарушение...';
        
        // In a real app, this might be a separate API call or part of the violation object
        // Here we simulate the AI recommendation based on the violation
        setTimeout(() => {
            const recommendation = violation.aiRecommendation || 
                `Основываясь на ГОСТ 19.201-78, данное нарушение (${violation.ruleName}) требует корректировки. \n\n**Рекомендация:** Проверьте структуру раздела "${violation.section}". Текст должен быть изложен четко, без двусмысленностей. Используйте терминологию, соответствующую государственным стандартам.`;
            
            aiBox.innerHTML = recommendation.replace(/\n/g, '<br>');
        }, 600);
    }

    // --- Utils ---

    async apiFetch(endpoint, options = {}) {
        const url = `${CONFIG.API_BASE_URL}${endpoint}`;
        const headers = {
            'Authorization': `Bearer ${this.state.token}`,
            'Content-Type': 'application/json',
            ...options.headers
        };

        return fetch(url, { ...options, headers });
    }

    updateUserUI() {
        if (this.state.user) {
            this.nodes.userName.innerText = this.state.user.fullName || this.state.user.username;
            this.nodes.userAvatar.innerText = (this.state.user.username || 'U')[0].toUpperCase();
            this.nodes.userRole.innerText = this.state.user.roles?.[0]?.name?.replace('ROLE_', '') || 'User';
        }
    }

    updateApiStatus() {
        fetch(`${CONFIG.API_BASE_URL}/auth/login`, { method: 'OPTIONS' })
            .then(() => {
                this.nodes.apiStatus.innerText = 'В сети';
                this.nodes.apiStatus.parentElement.querySelector('.dot').style.backgroundColor = 'var(--success)';
            })
            .catch(() => {
                this.nodes.apiStatus.innerText = 'Ошибка';
                this.nodes.apiStatus.parentElement.querySelector('.dot').style.backgroundColor = 'var(--error)';
            });
    }

    showNotification(message, type = 'info') {
        const n = this.nodes.notification;
        n.innerText = message;
        n.className = `notification show ${type}`;
        setTimeout(() => n.classList.remove('show'), 3000);
    }

    async deleteDoc(id) {
        if (!confirm('Удалить этот документ?')) return;
        try {
            const res = await this.apiFetch(`/documents/${id}`, { method: 'DELETE' });
            if (res.ok) {
                this.showNotification('Документ удален', 'success');
                this.loadDashboardData();
            }
        } catch (e) { console.error(e); }
    }
}

// Global instance
const app = new NormaControlApp();
