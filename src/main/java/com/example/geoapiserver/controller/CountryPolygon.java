package com.example.geoapiserver.controller;

import org.locationtech.jts.geom.Polygon;

// A class representing the country and its polygon
class CountryPolygon {
    private String countryName;
    private Polygon polygon;

    // Constructor
    public CountryPolygon(String countryName, Polygon polygon) {
        this.countryName = countryName;
        this.polygon = polygon;
    }

    public String getCountryName() {
        return countryName;
    }

    public Polygon getPolygon() {
        return polygon;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public void setPolygon(Polygon polygon) {
        this.polygon = polygon;
    }
}