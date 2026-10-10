'use strict';

const COLUMNS = [
    { key: 'name', title: 'Назва', fixed: true },
    { key: 'createdAt', title: 'Створено', time: true },
    { key: 'modifiedAt', title: 'Змінено', time: true },
    { key: 'uploadedBy', title: 'Завантажив' },
    { key: 'editedBy', title: 'Редагував' },
];

const state = { token: null, user: null, files: [], selected: null, hidden: new Set() };
const $ = id => document.getElementById(id);

function store(key, value) {
    try {
        if (value === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, value);
    } catch (e) { /* сховище недоступне */ }
}

function load(key) {
    try { return sessionStorage.getItem(key); } catch (e) { return null; }
}

async function api(path, options = {}) {
    const headers = options.headers || {};
    if (state.token) headers.Authorization = 'Bearer ' + state.token;
    const response = await fetch(path, { ...options, headers });
    if (response.status === 401 && state.token) {
        logout();
        throw new Error('Сесія завершилась, увійдіть знову');
    }
    if (!response.ok) {
        let message = 'Помилка сервера: HTTP ' + response.status;
        try { message = (await response.json()).message || message; } catch (e) { /* не JSON */ }
        throw new Error(message);
    }
    return response;
}

function setStatus(text) { $('status').textContent = text; }

function fail(e) { setStatus('Помилка: ' + e.message); }

/* ---------- авторизація ---------- */

async function authenticate(path) {
    $('login-error').textContent = '';
    try {
        const response = await api(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: $('username').value.trim(), password: $('password').value }),
        });
        const session = await response.json();
        startSession(session.token, session.username);
    } catch (e) {
        $('login-error').textContent = e.message === 'Помилка сервера: HTTP 401' ? 'Невірний логін або пароль' : e.message;
    }
}

function startSession(token, user) {
    state.token = token;
    state.user = user;
    store('token', token);
    store('user', user);
    $('user-label').textContent = 'Користувач: ' + user;
    $('login-view').hidden = true;
    $('drive-view').hidden = false;
    refresh();
}

function logout() {
    if (state.token) fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + state.token } });
    state.token = null;
    store('token', null);
    store('user', null);
    $('drive-view').hidden = true;
    $('login-view').hidden = false;
}

/* ---------- список файлів ---------- */

async function refresh() {
    try {
        const params = new URLSearchParams({ order: $('order').value, filter: $('filter').value });
        state.files = await (await api('/api/files?' + params)).json();
        render();
        setStatus('Файлів у списку: ' + state.files.length);
    } catch (e) { fail(e); }
}

function formatTime(iso) {
    return new Date(iso).toLocaleString('uk-UA');
}

function render() {
    const head = $('head-row');
    head.replaceChildren(...COLUMNS.filter(c => !state.hidden.has(c.key)).map(c => {
        const th = document.createElement('th');
        th.textContent = c.title;
        return th;
    }));
    const body = $('rows');
    body.replaceChildren(...state.files.map(file => {
        const tr = document.createElement('tr');
        tr.draggable = true;
        if (state.selected && state.selected.id === file.id) tr.classList.add('selected');
        for (const c of COLUMNS) {
            if (state.hidden.has(c.key)) continue;
            const td = document.createElement('td');
            td.textContent = c.time ? formatTime(file[c.key]) : file[c.key];
            tr.append(td);
        }
        tr.addEventListener('click', () => select(file));
        tr.addEventListener('dblclick', () => download(file));
        // перетягування рядка на робочий стіл (Chrome/Edge) скачує файл
        tr.addEventListener('dragstart', e => {
            e.dataTransfer.setData('DownloadURL',
                'application/octet-stream:' + file.name + ':' + location.origin + contentUrl(file, true));
        });
        return tr;
    }));
    $('empty').hidden = state.files.length > 0;
    if (state.selected && !state.files.some(f => f.id === state.selected.id)) {
        state.selected = null;
        showHint('Оберіть файл, щоб переглянути вміст (.html як текст, .png як зображення)');
    }
}

function setupColumnToggles() {
    const box = $('column-toggles');
    for (const c of COLUMNS.filter(c => !c.fixed)) {
        const label = document.createElement('label');
        const box2 = document.createElement('input');
        box2.type = 'checkbox';
        box2.checked = true;
        box2.addEventListener('change', () => {
            if (box2.checked) state.hidden.delete(c.key); else state.hidden.add(c.key);
            render();
        });
        label.append(box2, ' ' + c.title);
        box.append(label);
    }
}

/* ---------- перегляд вмісту ---------- */

function extension(name) {
    const dot = name.lastIndexOf('.');
    return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
}

function contentUrl(file, download) {
    return '/api/files/' + file.id + '/content?token=' + encodeURIComponent(state.token)
        + (download ? '&download=true' : '');
}

function showHint(text) {
    const p = document.createElement('p');
    p.className = 'hint';
    p.textContent = text;
    $('preview').replaceChildren(p);
}

async function select(file) {
    state.selected = file;
    render();
    const ext = extension(file.name);
    try {
        if (ext === 'html') {
            // .html показується як текст, не як розмітка
            const pre = document.createElement('pre');
            pre.textContent = await (await api('/api/files/' + file.id + '/content')).text();
            $('preview').replaceChildren(pre);
        } else if (ext === 'png') {
            const blob = await (await api('/api/files/' + file.id + '/content')).blob();
            const img = document.createElement('img');
            img.src = URL.createObjectURL(blob);
            img.alt = file.name;
            $('preview').replaceChildren(img);
        } else {
            showHint('Перегляд вмісту доступний для файлів .html і .png');
        }
        setStatus(file.name + ' (' + file.size + ' байт)');
    } catch (e) { fail(e); }
}

/* ---------- операції з файлами ---------- */

async function uploadFiles(files) {
    let n = 0;
    try {
        for (const file of files) {
            await uploadOne(file);
            n++;
        }
        setStatus('Завантажено файлів: ' + n);
    } catch (e) { fail(e); }
    refresh();
}

async function uploadOne(file, name) {
    const form = new FormData();
    form.append('file', file, name || file.name);
    return (await api('/api/files', { method: 'POST', body: form })).json();
}

function download(file) {
    const a = document.createElement('a');
    a.href = contentUrl(file, true);
    a.download = file.name;
    document.body.append(a);
    a.click();
    a.remove();
}

async function deleteSelected() {
    const file = state.selected;
    if (!file || !confirm('Видалити ' + file.name + '?')) return;
    try {
        await api('/api/files/' + file.id, { method: 'DELETE' });
        state.selected = null;
        setStatus('Видалено: ' + file.name);
    } catch (e) { fail(e); }
    refresh();
}

function setupDropZone() {
    const zone = $('drop-zone');
    zone.addEventListener('dragover', e => {
        if (e.dataTransfer.types.includes('Files')) {
            e.preventDefault();
            zone.classList.add('dragging');
        }
    });
    zone.addEventListener('dragleave', () => zone.classList.remove('dragging'));
    zone.addEventListener('drop', e => {
        e.preventDefault();
        zone.classList.remove('dragging');
        if (e.dataTransfer.files.length) uploadFiles([...e.dataTransfer.files]);
    });
}

/* ---------- синхронізація папки (File System Access API, Chrome/Edge) ---------- */

const TOLERANCE_MS = 2000;

function journalKey(dirName) { return 'sync:' + state.user + ':' + dirName; }

function readJournal(dirName) {
    try { return JSON.parse(localStorage.getItem(journalKey(dirName))) || {}; } catch (e) { return {}; }
}

function writeJournal(dirName, journal) {
    try { localStorage.setItem(journalKey(dirName), JSON.stringify(journal)); } catch (e) { /* без журналу */ }
}

async function syncFolder() {
    if (!window.showDirectoryPicker) {
        setStatus('Синхронізація папки у браузері потребує Chrome або Edge (File System Access API)');
        return;
    }
    let dir;
    try {
        dir = await window.showDirectoryPicker({ mode: 'readwrite' });
    } catch (e) { return; }
    setStatus('Синхронізація…');
    try {
        const remote = await (await api('/api/files')).json();
        const remoteByName = new Map(remote.map(f => [f.name, f]));
        // журнал запам'ятовує стан після попередньої синхронізації, бо браузер не може змінити час файлу
        const journal = readJournal(dir.name);
        const local = new Map();
        for await (const [name, handle] of dir.entries()) {
            if (handle.kind === 'file' && !name.startsWith('.')) local.set(name, handle);
        }
        let up = 0;
        let down = 0;
        for (const [name, handle] of local) {
            const file = await handle.getFile();
            const r = remoteByName.get(name);
            const known = journal[name];
            let direction = null;
            if (!r) {
                direction = 'up';
            } else {
                const localChanged = !known || known.local !== file.lastModified;
                const remoteChanged = !known || known.remote !== r.modifiedAt;
                const remoteTime = Date.parse(r.modifiedAt);
                if (localChanged && remoteChanged) {
                    // конфлікт або перша синхронізація: перемагає новіша версія
                    if (file.lastModified > remoteTime + TOLERANCE_MS) direction = 'up';
                    else if (remoteTime > file.lastModified + TOLERANCE_MS) direction = 'down';
                    else journal[name] = { local: file.lastModified, remote: r.modifiedAt };
                } else if (localChanged) {
                    direction = 'up';
                } else if (remoteChanged) {
                    direction = 'down';
                }
            }
            if (direction === 'up') {
                const info = await uploadOne(file, name);
                journal[name] = { local: file.lastModified, remote: info.modifiedAt };
                up++;
            } else if (direction === 'down') {
                journal[name] = { local: await saveToFolder(dir, r), remote: r.modifiedAt };
                down++;
            }
        }
        for (const r of remote) {
            if (!local.has(r.name)) {
                const lastModified = await saveToFolder(dir, r);
                journal[r.name] = { local: lastModified, remote: r.modifiedAt };
                down++;
            }
        }
        writeJournal(dir.name, journal);
        setStatus('Синхронізовано папку ' + dir.name + ': на сервер ' + up + ', з сервера ' + down);
    } catch (e) { fail(e); }
    refresh();
}

async function saveToFolder(dir, file) {
    const blob = await (await api('/api/files/' + file.id + '/content?download=true')).blob();
    const handle = await dir.getFileHandle(file.name, { create: true });
    const writable = await handle.createWritable();
    await writable.write(blob);
    await writable.close();
    return (await handle.getFile()).lastModified;
}

/* ---------- запуск ---------- */

$('login-form').addEventListener('submit', e => { e.preventDefault(); authenticate('/api/auth/login'); });
$('register').addEventListener('click', () => authenticate('/api/auth/register'));
$('upload-btn').addEventListener('click', () => $('file-input').click());
$('file-input').addEventListener('change', e => { uploadFiles([...e.target.files]); e.target.value = ''; });
$('download-btn').addEventListener('click', () => state.selected && download(state.selected));
$('delete-btn').addEventListener('click', deleteSelected);
$('refresh-btn').addEventListener('click', refresh);
$('order').addEventListener('change', refresh);
$('filter').addEventListener('change', refresh);
$('sync-btn').addEventListener('click', syncFolder);
$('logout-btn').addEventListener('click', logout);
setupColumnToggles();
setupDropZone();

if (load('token')) {
    state.token = load('token');
    api('/api/files').then(() => startSession(load('token'), load('user'))).catch(() => logout());
}
