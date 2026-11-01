/*
 * Mappa dei road trip (Leaflet + OpenStreetMap). Legge i dati dall'attributo data-map dell'elemento #trip-map:
 * { focus?, stages: [{id, position, title, url, shape, estimated, start, end, stops: [...]}], stations: [...] }
 */
(function () {
    'use strict';

    const el = document.getElementById('trip-map');
    if (!el || !window.L) return;
    const data = JSON.parse(el.dataset.map || '{"stages":[],"stations":[]}');

    /** Decodifica una polyline Valhalla (precisione 6). */
    function decode(str) {
        const points = [];
        let index = 0, lat = 0, lon = 0;
        while (index < str.length) {
            for (const coord of [0, 1]) {
                let result = 0, shift = 0, b;
                do {
                    b = str.charCodeAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                const delta = (result & 1) ? ~(result >> 1) : (result >> 1);
                if (coord === 0) lat += delta; else lon += delta;
            }
            points.push([lat / 1e6, lon / 1e6]);
        }
        return points;
    }

    function pin(color, label) {
        return L.divIcon({
            className: 'map-pin',
            html: '<span style="background:' + color + '">' + (label || '') + '</span>',
            iconSize: [26, 26],
            iconAnchor: [13, 13],
        });
    }

    function text(s) {                       // per i popup: testo, mai HTML
        const span = document.createElement('span');
        span.textContent = s;
        return span;
    }

    const map = L.map(el, { scrollWheelZoom: false });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);

    const bounds = L.latLngBounds([]);
    const palette = ['#f0a500', '#0d6efd', '#198754', '#d63384', '#6f42c1', '#fd7e14', '#20c997'];

    data.stages.forEach(function (s, i) {
        const focused = data.focus === undefined || data.focus === s.id;
        const color = data.focus !== undefined ? '#f0a500' : palette[i % palette.length];
        if (s.shape) {
            const line = L.polyline(decode(s.shape), {
                color: focused ? color : '#adb5bd',
                weight: focused ? 5 : 3,
                opacity: 0.9,
                dashArray: s.estimated ? '8 8' : null,
            }).addTo(map);
            line.bindTooltip(text((s.position ? s.position + '. ' : '') + s.title), { sticky: true });
            if (s.url && data.focus === undefined) {
                line.on('click', function () { location.href = s.url; });
            }
            bounds.extend(line.getBounds());
        }
        L.marker([s.start.lat, s.start.lon], { icon: pin('#198754', i === 0 || data.focus !== undefined ? 'A' : '') })
            .bindPopup(text('Partenza: ' + s.start.name)).addTo(map);
        L.marker([s.end.lat, s.end.lon], { icon: pin('#dc3545', data.focus !== undefined ? 'B' : String(s.position || '')) })
            .bindPopup(text('Arrivo: ' + s.end.name)).addTo(map);
        bounds.extend([s.start.lat, s.start.lon]).extend([s.end.lat, s.end.lon]);
        (s.stops || []).forEach(function (st) {
            L.circleMarker([st.lat, st.lon], { radius: 7, color: '#212529', weight: 2, fillColor: '#ffffff', fillOpacity: 1 })
                .bindPopup(text(st.label + ': ' + st.name)).addTo(map);
            bounds.extend([st.lat, st.lon]);
        });
    });

    (data.stations || []).forEach(function (st) {
        L.marker([st.lat, st.lon], { icon: pin('#fd7e14', '€') })
            .bindPopup(text(st.name + ' · ' + st.price + ' €/l · ' + st.km + ' km')).addTo(map);
        bounds.extend([st.lat, st.lon]);
    });

    if (bounds.isValid()) {
        map.fitBounds(bounds, { padding: [30, 30] });
    } else {
        map.setView([42.5, 12.5], 5);                      // Italia
    }
    // la mappa è dentro layout flessibili: ricalcola le dimensioni dopo il rendering
    setTimeout(function () { map.invalidateSize(); }, 200);
})();
