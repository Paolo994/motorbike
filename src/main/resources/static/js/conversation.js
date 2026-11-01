/* Pagina di una conversazione: invio via fetch, ricezione in tempo reale, caricamento dei messaggi precedenti. */
(function () {
    'use strict';

    const root = document.getElementById('chat');
    if (!root || !window.MotoRide) {
        return; // senza JS il form funziona comunque con un normale POST
    }

    const conversationId = Number(root.dataset.conversationId);
    const messagesUrl = root.dataset.messagesUrl;
    const readUrl = root.dataset.readUrl;
    const pageSize = Number(root.dataset.pageSize);
    const me = window.MotoRide.me;
    const csrf = window.MotoRide.csrf;

    const list = document.getElementById('chat-messages');
    const scroller = document.getElementById('chat-scroller');
    const form = document.getElementById('chat-form');
    const input = document.getElementById('chat-input');
    const errorBox = document.getElementById('chat-error');
    const olderBtn = document.getElementById('chat-older');
    const emptyNote = document.getElementById('chat-empty');

    function headers(extra) {
        const h = Object.assign({ 'Accept': 'application/json' }, extra || {});
        if (csrf.header && csrf.token) h[csrf.header] = csrf.token;
        return h;
    }

    function scrollToBottom() {
        scroller.scrollTop = scroller.scrollHeight;
    }

    function render(msg) {
        const li = document.createElement('li');
        li.className = 'chat-msg ' + (msg.sender === me ? 'mine' : 'theirs');
        li.dataset.id = msg.id;
        const bubble = document.createElement('div');
        bubble.className = 'chat-bubble';
        bubble.textContent = msg.body;               // textContent: nessun rischio XSS
        const time = document.createElement('time');
        time.className = 'chat-time';
        time.setAttribute('datetime', msg.createdAt);
        time.textContent = window.MotoRide.formatTime(msg.createdAt);
        li.append(bubble, time);
        return li;
    }

    function append(msg) {
        if (list.querySelector('[data-id="' + msg.id + '"]')) return; // già presente (push + risposta fetch)
        const nearBottom = scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < 120;
        list.append(render(msg));
        emptyNote?.remove();
        if (nearBottom || msg.sender === me) scrollToBottom();
    }

    let readTimer = null;
    function markReadSoon() {
        clearTimeout(readTimer);
        readTimer = setTimeout(function () {
            fetch(readUrl, { method: 'POST', headers: headers() });
        }, 400);
    }

    // ------------------------------------------------------------ invio

    if (form && input) {
        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            const body = input.value.trim();
            if (!body) return;
            errorBox.classList.add('d-none');
            input.disabled = true;
            try {
                const res = await fetch(messagesUrl, {
                    method: 'POST',
                    headers: headers({ 'Content-Type': 'application/x-www-form-urlencoded' }),
                    body: new URLSearchParams({ body: body }),
                });
                if (res.status === 201) {
                    append(await res.json());
                    input.value = '';
                } else {
                    const data = await res.json().catch(function () { return {}; });
                    showError(data.error || 'Invio non riuscito. Riprova.');
                }
            } catch (err) {
                showError('Connessione assente. Riprova.');
            } finally {
                input.disabled = false;
                input.focus();
            }
        });

        // Invio = manda, Shift+Invio = a capo
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
                e.preventDefault();
                form.requestSubmit();
            }
        });
    }

    function showError(text) {
        errorBox.textContent = text;
        errorBox.classList.remove('d-none');
    }

    // ------------------------------------------------------------ ricezione

    window.addEventListener('chat:message', function (e) {
        const msg = e.detail;
        if (msg.conversationId !== conversationId) return; // altra conversazione: badge in navbar
        e.preventDefault();
        append(msg);
        if (msg.sender !== me) markReadSoon();
    });

    // ------------------------------------------------------------ messaggi precedenti

    if (olderBtn) {
        olderBtn.addEventListener('click', async function () {
            const first = list.querySelector('[data-id]');
            if (!first) return;
            olderBtn.disabled = true;
            const res = await fetch(messagesUrl + '?before=' + first.dataset.id, { headers: headers() });
            if (!res.ok) { olderBtn.disabled = false; return; }
            const older = await res.json();
            const prevHeight = scroller.scrollHeight;
            const fragment = document.createDocumentFragment();
            older.forEach(function (m) { fragment.append(render(m)); });
            list.prepend(fragment);
            scroller.scrollTop += scroller.scrollHeight - prevHeight; // mantiene la posizione
            if (older.length < pageSize) olderBtn.remove(); else olderBtn.disabled = false;
        });
    }

    scrollToBottom();
    input?.focus();
})();
