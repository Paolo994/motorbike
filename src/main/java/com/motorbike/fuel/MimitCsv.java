package com.motorbike.fuel;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser dei CSV open data MIMIT (Osservaprezzi carburanti).
 * Formato: prima riga "Estrazione del AAAA-MM-GG", seconda riga intestazione, separatore '|' (in passato ';').
 */
final class MimitCsv {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.8");
    private static final BigDecimal MAX_PRICE = new BigDecimal("4.0");

    record Station(long id, String name, String brand, String address, String comune, String provincia,
                   double lat, double lon) {
    }

    record PetrolPrice(BigDecimal self, BigDecimal served, Instant updatedAt) {
    }

    private MimitCsv() {
    }

    /** UTF-8 se valido, altrimenti ISO-8859-1 (i file storici non sono sempre UTF-8). */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine */
    static List<Station> parseStations(String csv) {
        List<Station> result = new ArrayList<>();
        String[] lines = csv.split("\r?\n");
        String sep = separator(lines);
        for (int i = 2; i < lines.length; i++) {
            String[] f = lines[i].split(java.util.regex.Pattern.quote(sep), -1);
            if (f.length < 10) {
                continue;
            }
            try {
                long id = Long.parseLong(f[0].strip());
                // se un campo di testo contiene il separatore, le coordinate restano comunque le ultime due colonne
                int n = f.length;
                double lat = Double.parseDouble(f[n - 2].strip().replace(',', '.'));
                double lon = Double.parseDouble(f[n - 1].strip().replace(',', '.'));
                if (!inItaly(lat, lon)) {
                    continue;
                }
                result.add(new Station(id, clean(f[4]), clean(f[2]), clean(f[5]), clean(f[n - 4]), clean(f[n - 3]),
                    lat, lon));
            } catch (NumberFormatException e) {
                // riga malformata: ignorata
            }
        }
        return result;
    }

    /**
     * idImpianto|descCarburante|prezzo|isSelf|dtComu — solo la "Benzina" (non Plus/Blue/Special),
     * self e servito separati, ultima comunicazione per impianto.
     */
    static Map<Long, PetrolPrice> parsePetrolPrices(String csv) {
        Map<Long, PetrolPrice> result = new HashMap<>();
        String[] lines = csv.split("\r?\n");
        String sep = separator(lines);
        for (int i = 2; i < lines.length; i++) {
            String[] f = lines[i].split(java.util.regex.Pattern.quote(sep), -1);
            if (f.length < 5 || !"benzina".equalsIgnoreCase(f[1].strip())) {
                continue;
            }
            try {
                long id = Long.parseLong(f[0].strip());
                BigDecimal price = new BigDecimal(f[2].strip().replace(',', '.'));
                if (price.compareTo(MIN_PRICE) < 0 || price.compareTo(MAX_PRICE) > 0) {
                    continue;
                }
                boolean self = "1".equals(f[3].strip());
                Instant at = LocalDateTime.parse(f[4].strip(), DATE).atZone(ROME).toInstant();
                result.merge(id, self ? new PetrolPrice(price, null, at) : new PetrolPrice(null, price, at),
                    MimitCsv::merge);
            } catch (RuntimeException e) {
                // riga malformata: ignorata
            }
        }
        return result;
    }

    private static PetrolPrice merge(PetrolPrice a, PetrolPrice b) {
        Instant latest = a.updatedAt().isAfter(b.updatedAt()) ? a.updatedAt() : b.updatedAt();
        BigDecimal self = pick(a.self(), a.updatedAt(), b.self(), b.updatedAt());
        BigDecimal served = pick(a.served(), a.updatedAt(), b.served(), b.updatedAt());
        return new PetrolPrice(self, served, latest);
    }

    private static BigDecimal pick(BigDecimal a, Instant at, BigDecimal b, Instant bt) {
        if (a == null) return b;
        if (b == null) return a;
        return bt.isAfter(at) ? b : a;
    }

    private static String separator(String[] lines) {
        return lines.length > 1 && lines[1].contains(";") && !lines[1].contains("|") ? ";" : "|";
    }

    /** Coordinate plausibili per l'Italia (scarta 0,0 e valori invertiti). */
    private static boolean inItaly(double lat, double lon) {
        return lat > 35 && lat < 48 && lon > 6 && lon < 19;
    }

    private static String clean(String s) {
        String v = s.replaceAll("\\s+", " ").strip();
        return v.isEmpty() ? null : v;
    }
}
