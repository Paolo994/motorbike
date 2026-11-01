package com.motorbike.trip;

/** Enum del modulo road trip. */
public final class TripEnums {

    private TripEnums() {
    }

    public enum TripRole {
        OWNER("Organizzatore"),
        EDITOR("Può modificare"),
        VIEWER("Sola lettura");

        private final String label;

        TripRole(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean canEdit() {
            return this != VIEWER;
        }
    }

    public enum RouteStatus {
        PENDING,
        OK,
        /** routing non disponibile: distanza e tempo stimati in linea d'aria */
        ESTIMATED
    }

    public enum StopType {
        FUEL("Carburante", "bi-fuel-pump"),
        FOOD("Pranzo / ristoro", "bi-cup-hot"),
        VIEWPOINT("Panorama", "bi-binoculars"),
        ATTRACTION("Da visitare", "bi-camera"),
        REST("Pausa", "bi-pause-circle"),
        OTHER("Altro", "bi-geo-alt");

        private final String label;
        private final String icon;

        StopType(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }

        public String getLabel() { return label; }
        public String getIcon() { return icon; }
    }

    public enum AccommodationType {
        HOSTEL("Ostello"),
        HOTEL("Hotel"),
        BNB("B&B"),
        CAMPING("Campeggio"),
        APARTMENT("Appartamento"),
        OTHER("Altro");

        private final String label;

        AccommodationType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
