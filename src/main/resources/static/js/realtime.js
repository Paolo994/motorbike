/*
 * Connessione WebSocket/STOMP condivisa da tutte le pagine (utente autenticato).
 * Riceve i nuovi messaggi sulla coda personale e li rilancia come evento "chat:message".
 * Se nessuna pagina gestisce il messaggio (preventDefault), incrementa il badge "Chat" nella navbar.
 */
(function () {
    'use strict';

    const meta = (name) => document.querySelector('meta[name="' + name + '"]')?.content;
    const me = meta('app-user');
    if (!me || !window.StompJs) {
        return;
    }

    const badge = document.getElementById('chat-badge');

    function setChatBadge(count) {
        if (!badge) return;
        badge.textContent = String(count);
        badge.classList.toggle('d-none', count <= 0);
    }

    function bumpChatBadge(delta) {
        const current = parseInt(badge?.textContent || '0', 10) || 0;
        setChatBadge(Math.max(0, current + delta));
    }

    const csrfHeader = meta('_csrf_header');
    const csrfToken = meta('_csrf');
    const context = meta('app-context') || '/';
    const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';

    const connectHeaders = {};
    if (csrfHeader && csrfToken) {
        connectHeaders[csrfHeader] = csrfToken;
    }

    const client = new StompJs.Client({
        brokerURL: scheme + '//' + location.host + context + 'ws',
        connectHeaders: connectHeaders,
        reconnectDelay: 5000,
    });

    client.onConnect = function () {
        client.subscribe('/user/queue/messages', function (frame) {
            const message = JSON.parse(frame.body);
            const event = new CustomEvent('chat:message', { detail: message, cancelable: true });
            const unhandled = window.dispatchEvent(event);
            if (unhandled && message.sender !== me) {
                bumpChatBadge(1);
            }
        });
        client.subscribe('/user/queue/notifications', function (frame) {
            const n = JSON.parse(frame.body);
            const bell = document.getElementById('notif-badge');
            if (bell) {
                const count = (parseInt(bell.textContent, 10) || 0) + 1;
                bell.textContent = String(count);
                bell.classList.remove('d-none');
            }
            showToast(n);
        });
    };

    /** Toast Bootstrap per le notifiche in arrivo (testo inserito con textContent: niente HTML). */
    function showToast(n) {
        const container = document.getElementById('toasts');
        if (!container || !window.bootstrap) return;
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.setAttribute('role', 'status');
        const header = document.createElement('div');
        header.className = 'toast-header';
        const icon = document.createElement('i');
        icon.className = 'bi bi-bell-fill text-warning me-2';
        const title = document.createElement('strong');
        title.className = 'me-auto';
        title.textContent = n.title;
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close';
        close.setAttribute('data-bs-dismiss', 'toast');
        close.setAttribute('aria-label', 'Chiudi');
        header.append(icon, title, close);
        toast.append(header);
        if (n.body || n.link) {
            const body = document.createElement('div');
            body.className = 'toast-body';
            body.textContent = n.body || '';
            if (n.link && n.link.startsWith('/')) {
                const a = document.createElement('a');
                a.href = context.replace(/\/$/, '') + n.link;
                a.className = 'd-block mt-1';
                a.textContent = 'Apri';
                body.append(a);
            }
            toast.append(body);
        }
        container.append(toast);
        toast.addEventListener('hidden.bs.toast', function () { toast.remove(); });
        new bootstrap.Toast(toast, { delay: 8000 }).show();
    }

    client.activate();

    window.MotoRide = Object.assign(window.MotoRide || {}, {
        me: me,
        csrf: { header: csrfHeader, token: csrfToken },
        setChatBadge: setChatBadge,
        bumpChatBadge: bumpChatBadge,
        formatTime: function (iso) {
            const d = new Date(iso);
            const today = new Date();
            const sameDay = d.toDateString() === today.toDateString();
            return sameDay
                ? d.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' })
                : d.toLocaleString('it-IT', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
        },
    });

    // formatta tutte le date renderizzate dal server
    document.querySelectorAll('time[data-local]').forEach(function (el) {
        el.textContent = window.MotoRide.formatTime(el.getAttribute('datetime'));
    });
})();
