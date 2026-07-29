// Smart Attendance Console JavaScript Logic

document.addEventListener('DOMContentLoaded', () => {
    // Global State
    let jwtToken = localStorage.getItem('jwt_token') || '';

    // DOM Elements
    const backendUrlInput = document.getElementById('backend-url');
    const btnHealthCheck = document.getElementById('btn-health-check');
    const btnRunAllTests = document.getElementById('btn-run-all-tests');
    const connectionStatus = document.getElementById('connection-status');

    const jsonViewer = document.getElementById('json-viewer');
    const eventLogs = document.getElementById('event-logs');
    const btnClearLogs = document.getElementById('btn-clear-logs');

    const resStatusCode = document.getElementById('res-status-code');
    const resLatency = document.getElementById('res-latency');
    const resMethod = document.getElementById('res-method');

    const storedJwtInput = document.getElementById('stored-jwt');

    if (jwtToken) {
        storedJwtInput.value = jwtToken;
    }

    // --- Tab Switching Logic ---
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));

            btn.classList.add('active');
            const targetTab = document.getElementById(btn.dataset.tab);
            if (targetTab) targetTab.classList.add('active');
        });
    });

    // --- Helper Functions ---
    function getBackendUrl() {
        return backendUrlInput.value.replace(/\/$/, '');
    }

    function addLog(message, type = 'info') {
        const entry = document.createElement('div');
        entry.className = `log-entry log-${type}`;
        const timestamp = new Date().toLocaleTimeString();
        entry.textContent = `[${timestamp}] ${message}`;
        eventLogs.appendChild(entry);
        eventLogs.scrollTop = eventLogs.scrollHeight;
    }

    function updateInspector(method, status, latencyMs, data) {
        resMethod.textContent = method;
        resStatusCode.textContent = status ? `${status}` : 'ERR';

        if (status >= 200 && status < 300) {
            resStatusCode.className = 'status-code';
        } else {
            resStatusCode.className = 'status-code error';
        }

        resLatency.textContent = `${latencyMs} ms`;
        jsonViewer.textContent = JSON.stringify(data, null, 2);
    }

    async function apiRequest(endpoint, method = 'GET', body = null, useAuth = false) {
        const baseUrl = getBackendUrl();
        const url = `${baseUrl}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json'
        };

        if (useAuth && jwtToken) {
            headers['Authorization'] = `Bearer ${jwtToken}`;
        }

        const startTime = performance.now();
        addLog(`Sending ${method} request to ${endpoint}...`, 'info');

        try {
            const options = { method, headers };
            if (body && (method === 'POST' || method === 'PUT')) {
                options.body = JSON.stringify(body);
            }

            const res = await fetch(url, options);
            const latency = Math.round(performance.now() - startTime);

            let data;
            const contentType = res.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                data = await res.json();
            } else {
                data = { raw: await res.text() };
            }

            updateInspector(method, res.status, latency, data);

            if (res.ok) {
                addLog(`Success ${res.status} from ${endpoint} (${latency}ms)`, 'success');
                connectionStatus.className = 'status-badge status-online';
                connectionStatus.innerHTML = '<span class="status-dot"></span> Online';
            } else {
                addLog(`HTTP ${res.status} from ${endpoint}: ${data?.message || data?.error || 'Failed'}`, 'error');
            }

            return { status: res.status, ok: res.ok, data };
        } catch (err) {
            const latency = Math.round(performance.now() - startTime);
            updateInspector(method, 0, latency, { error: err.message, note: 'Failed to connect to backend server' });
            addLog(`Network Error: ${err.message}`, 'error');
            connectionStatus.className = 'status-badge status-offline';
            connectionStatus.innerHTML = '<span class="status-dot"></span> Offline';
            return { status: 0, ok: false, data: null };
        }
    }

    // --- Architecture Visualizer Pulse Animation ---
    function animateFlow(step) {
        const nodes = ['teacher', 'backend', 'fcm', 'mdns', 'student'];
        nodes.forEach(id => {
            const el = document.getElementById(`node-${id}`);
            if (el) el.classList.remove('active-pulse');
        });

        if (step >= 0 && step < nodes.length) {
            const el = document.getElementById(`node-${nodes[step]}`);
            if (el) el.classList.add('active-pulse');
        }
    }

    // Quick Login Helper
    async function ensureTeacherLogin() {
        if (jwtToken) return true;
        addLog('No active JWT found. Auto-authenticating as Teacher...', 'info');
        const loginRes = await apiRequest('/api/auth/teacher/login', 'POST', {
            email: 'teacher@rit.ac.in',
            password: 'TeacherPass@123'
        });
        if (loginRes.ok && loginRes.data && loginRes.data.access_token) {
            jwtToken = loginRes.data.access_token;
            localStorage.setItem('jwt_token', jwtToken);
            storedJwtInput.value = jwtToken;
            addLog('Auto-authentication successful! JWT token stored.', 'success');
            return true;
        }
        return false;
    }

    // --- Event Listeners ---

    // Clear Logs
    btnClearLogs.addEventListener('click', () => {
        eventLogs.innerHTML = '';
        jsonViewer.textContent = '// Console cleared';
    });

    // Ping Server Root Health Check
    btnHealthCheck.addEventListener('click', async () => {
        animateFlow(1); // Backend node
        await apiRequest('/');
    });

    // Run Automated E2E Flow Button
    btnRunAllTests.addEventListener('click', async () => {
        addLog('=== STARTING AUTOMATED END-TO-END FLOW TEST ===', 'info');
        
        // 1. Root Health Ping
        animateFlow(1);
        const healthRes = await apiRequest('/');
        if (!healthRes.ok) {
            addLog('E2E Flow aborted: Server health ping failed.', 'error');
            return;
        }

        // 2. Ensure Authentication
        const authed = await ensureTeacherLogin();
        if (!authed) {
            addLog('E2E Flow aborted: Teacher authentication failed.', 'error');
            return;
        }

        // 3. Trigger Attendance Session (Teacher)
        animateFlow(0); // Teacher node
        setTimeout(() => animateFlow(1), 300); // Backend
        setTimeout(() => animateFlow(2), 600); // FCM Push

        const sessionRes = await apiRequest('/api/teacher/start-session', 'POST', {
            classroom_id: 1,
            subject_id: 1,
            teacher_id: 2
        }, true);

        if (!sessionRes.ok || !sessionRes.data || !sessionRes.data.session_id) {
            addLog('E2E Flow aborted: Session creation failed.', 'error');
            return;
        }

        const sessionId = sessionRes.data.session_id;
        document.getElementById('student-session-id').value = sessionId;
        addLog(`Session #${sessionId} created successfully!`, 'success');

        // 4. Mark Attendance Attestation (Student)
        setTimeout(async () => {
            animateFlow(3); // mDNS Discovery
            setTimeout(() => animateFlow(4), 400); // Student App

            const attendanceRes = await apiRequest('/api/student/mark-attendance', 'POST', {
                session_id: sessionId,
                student_id: 3
            });

            if (attendanceRes.ok) {
                addLog('=== ALL E2E FLOW STEPS COMPLETED 100% SUCCESSFULLY! ===', 'success');
            } else {
                addLog('E2E Flow failed at student attendance step.', 'error');
            }
        }, 800);
    });

    // 1. Teacher: Start Attendance Session Form
    document.getElementById('form-start-session').addEventListener('submit', async (e) => {
        e.preventDefault();
        const classroom_id = parseInt(document.getElementById('teacher-classroom-id').value);
        const subject_id = parseInt(document.getElementById('teacher-subject-id').value);
        const teacher_id = parseInt(document.getElementById('teacher-id').value);

        const authed = await ensureTeacherLogin();
        if (!authed) {
            addLog('Cannot start session: Authentication failed.', 'error');
            return;
        }

        animateFlow(0);
        setTimeout(() => animateFlow(1), 300);
        setTimeout(() => animateFlow(2), 600);

        const res = await apiRequest('/api/teacher/start-session', 'POST', {
            classroom_id,
            subject_id,
            teacher_id
        }, true);

        if (res.ok && res.data && res.data.session_id) {
            document.getElementById('student-session-id').value = res.data.session_id;
            addLog(`Session ${res.data.session_id} created! Student Session ID field auto-populated.`, 'success');
        }
    });

    // 2. Student: Mark Attendance Form
    document.getElementById('form-mark-attendance').addEventListener('submit', async (e) => {
        e.preventDefault();
        const session_id = parseInt(document.getElementById('student-session-id').value);
        const student_id = parseInt(document.getElementById('student-id').value);

        animateFlow(3);
        setTimeout(() => animateFlow(4), 400);

        await apiRequest('/api/student/mark-attendance', 'POST', {
            session_id,
            student_id
        });
    });

    // 3. Teacher Health Ping
    document.getElementById('btn-teacher-health').addEventListener('click', async () => {
        await ensureTeacherLogin();
        await apiRequest('/api/teacher/health', 'GET', null, true);
    });

    // 4. Student Health Ping
    document.getElementById('btn-student-health').addEventListener('click', async () => {
        await apiRequest('/api/student/health', 'GET', null, true);
    });

    // 5. Auth Login Simulator
    document.getElementById('form-login').addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email').value;
        const password = document.getElementById('login-password').value;

        let endpoint = '/api/auth/login';
        if (email.includes('teacher')) {
            endpoint = '/api/auth/teacher/login';
        } else if (email.includes('student')) {
            endpoint = '/api/auth/student/login';
        } else if (email.includes('admin')) {
            endpoint = '/api/auth/admin/login';
        }

        const res = await apiRequest(endpoint, 'POST', { email, password });

        if (res.ok && res.data && res.data.access_token) {
            jwtToken = res.data.access_token;
            localStorage.setItem('jwt_token', jwtToken);
            storedJwtInput.value = jwtToken;
            addLog(`Stored JWT token for subsequent authenticated API requests.`, 'success');
        }
    });

    // 6. Fetch Profile (/api/auth/me)
    document.getElementById('btn-auth-me').addEventListener('click', async () => {
        if (!jwtToken) {
            const authed = await ensureTeacherLogin();
            if (!authed) return;
        }
        await apiRequest('/api/auth/me', 'GET', null, true);
    });

    // Initial Health Check on Load
    setTimeout(() => {
        btnHealthCheck.click();
    }, 500);
});
