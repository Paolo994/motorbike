package com.motorbike.user;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public class ProfileForm {

    @Size(max = 60)
    private String displayName;

    @Size(max = 500)
    private String bio;

    @Size(max = 80)
    private String city;

    @Size(max = 80)
    private String bikeModel;

    @DecimalMin(value = "1.0", message = "Valore minimo 1 km/l")
    @DecimalMax(value = "100.0", message = "Valore massimo 100 km/l")
    private BigDecimal fuelConsumptionKmPerL;

    @DecimalMin(value = "1.0", message = "Valore minimo 1 litro")
    @DecimalMax(value = "100.0", message = "Valore massimo 100 litri")
    private BigDecimal tankCapacityL;

    public static ProfileForm from(User user) {
        ProfileForm f = new ProfileForm();
        f.displayName = user.getDisplayName();
        f.bio = user.getBio();
        f.city = user.getCity();
        f.bikeModel = user.getBikeModel();
        f.fuelConsumptionKmPerL = user.getFuelConsumptionKmPerL();
        f.tankCapacityL = user.getTankCapacityL();
        return f;
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getBikeModel() { return bikeModel; }
    public void setBikeModel(String bikeModel) { this.bikeModel = bikeModel; }
    public BigDecimal getFuelConsumptionKmPerL() { return fuelConsumptionKmPerL; }
    public void setFuelConsumptionKmPerL(BigDecimal v) { this.fuelConsumptionKmPerL = v; }
    public BigDecimal getTankCapacityL() { return tankCapacityL; }
    public void setTankCapacityL(BigDecimal v) { this.tankCapacityL = v; }
}
