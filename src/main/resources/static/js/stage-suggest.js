/*
 * "Cerca suggerimenti" nel form della tappa: chiede al server luoghi di una sola categoria
 * (ATTRACTION per "Cosa vedere", LODGING per "Alloggio") vicino ai punti inseriti nel form,
 * e permette di inserirli nel form con un clic. Tutti i testi vengono inseriti con textContent.
 */
(function () {
    'use strict';

    const form = document.getElementById('stage-form');
    const buttons = document.querySelectorAll('.ai-suggest');
    if (!form || !buttons.length) return;

    const url = form.dataset.suggestUrl;
    const meta = (n) => document.querySelector('meta[name="' + n + '"]')?.content;
    const value = (id) => document.getElementById(id)?.value?.trim() || '';

    function el(tag, className, text) {
        const e = document.createElement(tag);
        if (className) e.className = className;
        if (text !== undefined && text !== null) e.textContent = text;
        return e;
    }

    function formatRating(p) {
        if (p.rating == null) return null;
        return p.rating.toLocaleString('it-IT', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
            + ' (' + (p.ratingCount || 0).toLocaleString('it-IT') + ')';
    }

    /** Parametri della richiesta dai campi del form (anche se la tappa non è ancora salvata). */
    function params(category) {
        const p = new URLSearchParams({
            category: category,
            title: value('title'),
            startName: value('startName'), startLat: value('startLat'), startLon: value('startLon'),
            endName: value('endName'), endLat: value('endLat'), endLon: value('endLon'),
            avoidMotorways: document.getElementById('avoidMotorways1')?.checked ? 'true' : 'false',
        });
        document.querySelectorAll('#stops .stop-row').forEach(function (row) {
            const lat = row.querySelector('[data-field="lat"]')?.value;
            const lon = row.querySelector('[data-field="lon"]')?.value;
            if (lat && lon) {
                p.append('stopLat', lat);
                p.append('stopLon', lon);
            }
        });
        return p;
    }

    // ------------------------------------------------------------ azioni sul form

    function addToHighlights(place, button) {
        const area = document.getElementById('highlights');
        const line = '• ' + place.name + (place.reason ? ' — ' + place.reason : '');
        area.value = area.value.trim() ? area.value.trim() + '\n' + line : line;
        button.disabled = true;
        button.textContent = 'Aggiunto ✓';
    }

    function useAsAccommodation(place, button, container) {
        const type = (place.typeLabel || '').toLowerCase();
        const select = document.getElementById('accommodationType');
        select.value = type.includes('ostello') || type.includes('hostel') ? 'HOSTEL'
            : type.includes('b&b') || type.includes('bed') ? 'BNB'
            : type.includes('campeggio') ? 'CAMPING'
            : type.includes('hotel') || type.includes('albergo') ? 'HOTEL' : 'OTHER';
        document.getElementById('accommodationName').value = place.name;
        document.getElementById('accommodationAddress').value = place.address || '';
        document.getElementById('accommodationUrl').value = place.website || place.mapsUrl || '';
        container.querySelectorAll('.use-lodging').forEach(function (b) {
            b.disabled = false;
            b.textContent = 'Usa questo';
        });
        button.disabled = true;
        button.textContent = 'Selezionato ✓';
        document.getElementById('accommodationPrice').focus();
    }

    // ------------------------------------------------------------ risultati

    function render(container, category, data) {
        container.innerHTML = '';
        const box = el('div', 'card card-body bg-light border-0 mb-3 p-2');
        const head = el('div', 'd-flex justify-content-between align-items-center mb-1');
        head.append(el('span', 'small text-muted',
            data.aiGenerated ? 'Scelti dall\'AI tra i luoghi vicini' : 'AI non disponibile: luoghi meglio valutati'));
        const close = el('button', 'btn-close btn-sm');
        close.type = 'button';
        close.setAttribute('aria-label', 'Chiudi suggerimenti');
        close.addEventListener('click', function () { container.innerHTML = ''; });
        head.append(close);
        box.append(head);

        if (!data.places.length) {
            box.append(el('div', 'small text-muted', 'Nessun luogo adatto nei dintorni.'));
        }
        data.places.forEach(function (place) {
            const card = el('div', 'bg-white rounded border p-2 mb-2 place-card');
            const top = el('div', 'd-flex justify-content-between gap-2');
            const name = el('a', 'fw-semibold text-dark text-decoration-none small', place.name);
            if (place.mapsUrl) {
                name.href = place.mapsUrl;
                name.target = '_blank';
                name.rel = 'noopener noreferrer';
            }
            top.append(name);
            const rating = formatRating(place);
            if (rating) {
                const r = el('span', 'rating small');
                r.append(el('i', 'bi bi-star-fill'), document.createTextNode(' ' + rating));
                top.append(r);
            }
            card.append(top);

            const info = [place.typeLabel, place.price,
                place.distanceKm.toLocaleString('it-IT', { maximumFractionDigits: 1 }) + ' km da ' + place.near]
                .filter(Boolean).join(' · ');
            card.append(el('div', 'small text-muted', info));
            if (place.reason) {
                const reason = el('div', 'small mt-1');
                reason.append(el('i', 'bi bi-stars text-primary'), document.createTextNode(' ' + place.reason));
                card.append(reason);
            }
            if (place.review) {
                card.append(el('div', 'review text-muted', '“' + place.review + '” — ' + (place.reviewAuthor || '')));
            }
            const action = el('button', 'btn btn-sm btn-outline-dark mt-2',
                category === 'LODGING' ? 'Usa questo' : 'Aggiungi a "Cosa vedere"');
            action.type = 'button';
            if (category === 'LODGING') {
                action.classList.add('use-lodging');
                action.addEventListener('click', function () { useAsAccommodation(place, action, container); });
            } else {
                action.addEventListener('click', function () { addToHighlights(place, action); });
            }
            card.append(action);
            box.append(card);
        });
        box.append(el('div', 'small text-muted', 'Luoghi, valutazioni e recensioni: Google Maps.'));
        container.append(box);
    }

    buttons.forEach(function (button) {
        button.addEventListener('click', async function () {
            const container = document.getElementById(button.dataset.target);
            const category = button.dataset.category;
            if (!value('endLat') || !value('startLat')) {
                container.innerHTML = '';
                container.append(el('div', 'alert alert-warning small py-2',
                    'Scegli prima partenza e arrivo della tappa: i suggerimenti si basano sul percorso.'));
                return;
            }
            const original = button.innerHTML;
            button.disabled = true;
            button.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Sto cercando…';
            try {
                const headers = { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json' };
                if (meta('_csrf_header')) headers[meta('_csrf_header')] = meta('_csrf');
                const res = await fetch(url, { method: 'POST', headers: headers, body: params(category) });
                const data = await res.json().catch(function () { return {}; });
                if (!res.ok) {
                    container.innerHTML = '';
                    container.append(el('div', 'alert alert-warning small py-2', data.error || 'Suggerimenti non disponibili.'));
                    return;
                }
                render(container, category, data);
            } catch (e) {
                container.innerHTML = '';
                container.append(el('div', 'alert alert-warning small py-2', 'Connessione non riuscita. Riprova.'));
            } finally {
                button.disabled = false;
                button.innerHTML = original;
            }
        });
    });
})();
