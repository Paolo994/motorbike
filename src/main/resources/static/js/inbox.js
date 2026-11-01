/* Lista conversazioni: ricerca istantanea e aggiornamento in tempo reale dei messaggi in arrivo. */
(function () {
    'use strict';

    if (!window.MotoRide) return;

    const results = document.getElementById('chat-results');
    const search = document.getElementById('chat-search');
    const form = document.getElementById('chat-search-form');

    function formatTimes(root) {
        root.querySelectorAll('time[data-local]').forEach(function (el) {
            el.textContent = window.MotoRide.formatTime(el.getAttribute('datetime'));
        });
    }

    // ------------------------------------------------------------ ricerca istantanea

    let timer = null;
    let controller = null;

    async function refresh(query) {
        controller?.abort();
        controller = new AbortController();
        const url = form.getAttribute('action') + (query ? '?q=' + encodeURIComponent(query) : '');
        try {
            const res = await fetch(url, { signal: controller.signal, headers: { 'Accept': 'text/html' } });
            if (!res.ok) return;
            const doc = new DOMParser().parseFromString(await res.text(), 'text/html');
            const fresh = doc.getElementById('chat-results');
            if (fresh) {
                results.innerHTML = fresh.innerHTML;
                formatTimes(results);
                history.replaceState(null, '', url);
            }
        } catch (e) {
            if (e.name !== 'AbortError') console.warn('Ricerca chat non riuscita', e);
        }
    }

    if (search && form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            refresh(search.value.trim());
        });
        search.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(function () { refresh(search.value.trim()); }, 250);
        });
    }

    // ------------------------------------------------------------ messaggi in tempo reale

    window.addEventListener('chat:message', function (e) {
        const msg = e.detail;
        const list = document.getElementById('inbox');
        const row = list?.querySelector('[data-conversation-id="' + msg.conversationId + '"]');
        if (!row) {
            // conversazione non in lista (nuova o esclusa dal filtro): ricarico con la ricerca corrente
            refresh(search ? search.value.trim() : '');
            return;
        }
        const mine = msg.sender === window.MotoRide.me;
        row.querySelector('.inbox-preview').textContent = (mine ? 'Tu: ' : '') + msg.body;
        const time = row.querySelector('time');
        time.setAttribute('datetime', msg.createdAt);
        time.textContent = window.MotoRide.formatTime(msg.createdAt);
        if (!mine) {
            const badge = row.querySelector('.inbox-unread');
            const count = (parseInt(badge.textContent, 10) || 0) + 1;
            badge.textContent = String(count);
            badge.classList.remove('d-none');
            row.classList.add('fw-semibold');
        }
        list.prepend(row);
    });
})();
