/*
 * Form della tappa: ricerca luoghi (via /api/geocode), scelta del punto sulla mappa, gestione delle fermate.
 * Ogni luogo è un blocco .loc con: input nome, input hidden lat/lon, pulsanti "Cerca" e "Sulla mappa".
 */
(function () {
    'use strict';

    const form = document.getElementById('stage-form');
    const mapEl = document.getElementById('stage-form-map');
    if (!form || !mapEl || !window.L) return;

    const context = (document.querySelector('meta[name="app-context"]')?.content || '/').replace(/\/$/, '');
    const map = L.map(mapEl);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);
    map.setView([42.5, 12.5], 5);

    const markers = new Map();               // blocco .loc -> marker
    let armed = null;                        // blocco .loc in attesa di un clic sulla mappa

    function colorOf(loc) {
        return loc.dataset.loc === 'start' ? '#198754' : loc.dataset.loc === 'end' ? '#dc3545' : '#212529';
    }

    function labelOf(loc) {
        if (loc.dataset.loc === 'start') return 'A';
        if (loc.dataset.loc === 'end') return 'B';
        const rows = Array.from(document.querySelectorAll('#stops .stop-row'));
        return String(rows.indexOf(loc.closest('.stop-row')) + 1);
    }

    function field(loc, suffix) {
        return loc.querySelector('[data-field="' + suffix + '"]');
    }

    function refreshMarker(loc) {
        const lat = parseFloat(field(loc, 'lat').value);
        const lon = parseFloat(field(loc, 'lon').value);
        markers.get(loc)?.remove();
        markers.delete(loc);
        const status = loc.querySelector('.loc-status');
        if (isNaN(lat) || isNaN(lon)) {
            if (status) status.textContent = '';
            return;
        }
        const icon = L.divIcon({
            className: 'map-pin',
            html: '<span style="background:' + colorOf(loc) + '">' + labelOf(loc) + '</span>',
            iconSize: [26, 26], iconAnchor: [13, 13],
        });
        markers.set(loc, L.marker([lat, lon], { icon: icon }).addTo(map));
        if (status) status.textContent = '✓ ' + lat.toFixed(4) + ', ' + lon.toFixed(4);
    }

    function fitAll() {
        const pts = Array.from(markers.values()).map(function (m) { return m.getLatLng(); });
        if (pts.length === 1) map.setView(pts[0], 11);
        else if (pts.length > 1) map.fitBounds(L.latLngBounds(pts), { padding: [30, 30] });
    }

    function setPlace(loc, name, lat, lon) {
        const nameInput = field(loc, 'name');
        if (name) nameInput.value = name;
        field(loc, 'lat').value = lat.toFixed(6);
        field(loc, 'lon').value = lon.toFixed(6);
        refreshMarker(loc);
        fitAll();
    }

    // ------------------------------------------------------------ ricerca

    async function search(loc) {
        const q = field(loc, 'name').value.trim();
        const results = loc.querySelector('.loc-results');
        results.innerHTML = '';
        if (q.length < 3) {
            results.append(item('Scrivi almeno 3 caratteri', null));
            return;
        }
        results.append(item('Ricerca in corso…', null));
        try {
            const res = await fetch(context + '/api/geocode?q=' + encodeURIComponent(q), { headers: { Accept: 'application/json' } });
            const places = res.ok ? await res.json() : [];
            results.innerHTML = '';
            if (!places.length) {
                results.append(item('Nessun risultato. Prova a scegliere il punto sulla mappa.', null));
                return;
            }
            places.forEach(function (p) {
                results.append(item(p.label, function () {
                    setPlace(loc, p.name, p.lat, p.lon);
                    results.innerHTML = '';
                }));
            });
        } catch (e) {
            results.innerHTML = '';
            results.append(item('Ricerca non disponibile. Scegli il punto sulla mappa.', null));
        }
    }

    function item(label, onClick) {
        const el = document.createElement(onClick ? 'button' : 'div');
        el.className = 'list-group-item small' + (onClick ? ' list-group-item-action' : ' text-muted');
        if (onClick) {
            el.type = 'button';
            el.addEventListener('click', onClick);
        }
        el.textContent = label;
        return el;
    }

    // ------------------------------------------------------------ scelta sulla mappa

    function arm(loc) {
        document.querySelectorAll('.loc.armed').forEach(function (l) { l.classList.remove('armed'); });
        armed = loc;
        loc.classList.add('armed');
        mapEl.classList.add('picking');
        mapEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    map.on('click', function (e) {
        if (!armed) return;
        const loc = armed;
        armed = null;
        loc.classList.remove('armed');
        mapEl.classList.remove('picking');
        const nameInput = field(loc, 'name');
        const name = nameInput.value.trim() ? null : 'Punto ' + e.latlng.lat.toFixed(4) + ', ' + e.latlng.lng.toFixed(4);
        setPlace(loc, name, e.latlng.lat, e.latlng.lng);
    });

    function wire(loc) {
        loc.querySelector('.loc-search').addEventListener('click', function () { search(loc); });
        loc.querySelector('.loc-pick').addEventListener('click', function () { arm(loc); });
        field(loc, 'name').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); search(loc); }
        });
        refreshMarker(loc);
    }

    // ------------------------------------------------------------ fermate

    const stops = document.getElementById('stops');
    const template = document.getElementById('stop-template');
    const addBtn = document.getElementById('add-stop');
    const maxStops = parseInt(stops.dataset.max, 10);

    /** Gli indici stops[i] devono restare consecutivi perché Spring li leghi alla lista. */
    function reindex() {
        stops.querySelectorAll('.stop-row').forEach(function (row, i) {
            row.querySelectorAll('[name]').forEach(function (input) {
                input.name = input.name.replace(/stops\[\d+]/, 'stops[' + i + ']');
            });
            row.querySelector('.stop-number').textContent = String(i + 1);
            refreshMarker(row.querySelector('.loc'));
        });
        addBtn.disabled = stops.querySelectorAll('.stop-row').length >= maxStops;
    }

    function wireRow(row) {
        wire(row.querySelector('.loc'));
        row.querySelector('.stop-remove').addEventListener('click', function () {
            const loc = row.querySelector('.loc');
            markers.get(loc)?.remove();
            markers.delete(loc);
            row.remove();
            reindex();
        });
    }

    addBtn.addEventListener('click', function () {
        const count = stops.querySelectorAll('.stop-row').length;
        if (count >= maxStops) return;
        const html = template.innerHTML.replace(/__i__/g, String(count));
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html.trim();
        const row = wrapper.firstElementChild;
        stops.append(row);
        wireRow(row);
        reindex();
        field(row.querySelector('.loc'), 'name').focus();
    });

    document.querySelectorAll('.loc[data-loc="start"], .loc[data-loc="end"]').forEach(wire);
    stops.querySelectorAll('.stop-row').forEach(wireRow);
    reindex();
    fitAll();
    setTimeout(function () { map.invalidateSize(); }, 200);

    // il salvataggio calcola il percorso: evita doppi invii e mostra che sta lavorando
    form.addEventListener('submit', function () {
        const btn = form.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Calcolo del percorso…';
    });
})();
