package com.example.geoapiserver.controller;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.GeoPoint;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GeoController {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    // Checking if the server is running like it should
    @GetMapping("/health")
    public String healthCheck() {
        return "Geo API Server is running!";
    }

    // Function for getting the Polygon from Firestore DB
    private Polygon getPolygonFromFirestore(String country) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot document = db.collection("countries").document(country).get().get();

        if (!document.exists()) {
            throw new Exception("Country not found in database: " + country);
        }

        List<Object> rawCoordinates = (List<Object>) document.get("geometry.coordinates");
        if (rawCoordinates == null || rawCoordinates.isEmpty()) {
            throw new Exception("Coordinates field is missing or null for country: " + country);
        }

        // Conversion from GeoPoint / HashMap to an array of coordinates
        Coordinate[] coordsArray = rawCoordinates.stream()
                .map(coord -> {
                    if (coord instanceof GeoPoint) {
                        GeoPoint geoPoint = (GeoPoint) coord;
                        return new Coordinate(geoPoint.getLongitude(), geoPoint.getLatitude());
                    } else if (coord instanceof Map) {
                        Map<String, Double> map = (Map<String, Double>) coord;
                        return new Coordinate(map.get("longitude"), map.get("latitude"));
                    } else {
                        throw new IllegalArgumentException("Unsupported coordinate type: " + coord.getClass());
                    }
                })
                .toArray(Coordinate[]::new);

        return geometryFactory.createPolygon(coordsArray);
    }


    @PostMapping("/find-country")
    public String findCountryByCoordinates(@RequestBody Map<String, Object> request) {
        Double latitude = ((Number) request.get("latitude")).doubleValue();
        Double longitude = ((Number) request.get("longitude")).doubleValue();

        // If no coordinates are given, returns an error message
        if (latitude == null || longitude == null) {
            return "Invalid request! Please provide 'latitude' and 'longitude'.";
        }

        // Creating a test point
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        // Printing the logs to make sure the coordinates arrived correctly
        System.out.println("Received coordinates - Latitude: " + latitude + ", Longitude: " + longitude);

        try {
            // Retrieving all countries from the "countries" collection
            Firestore db = FirestoreClient.getFirestore();
            List<QueryDocumentSnapshot> countries = db.collection("countries").get().get().getDocuments();

            // Print the number of countries retrieved
            System.out.println("Fetched " + countries.size() + " countries from Firestore.");

            // Variable to store result
            String result = "The point is not in any known country.";

            // Go to each country and try to check if the dot is inside it
            for (QueryDocumentSnapshot doc : countries) {

                // Getting the country name from the ID of the document
                String countryName = doc.getId();  // The country name is the ID of the document
                String countryFullName = doc.getString("name");

                // Printing the name of the country being checked
                System.out.println("Checking country: " + countryName);

                // Sending the coordinates and name to your function
                String countryResult = findCountryByCoordinatesHelper(countryName,countryFullName, latitude, longitude);

                // If we found the country, we will change the result
                if (!countryResult.equals("The point is not in any known country.")) {
                    result = countryResult;
                    break;
                }
            }

            // Returns the result if no country is found
            return result;
        } catch (Exception e) {

            // Print the log in case of an error
            System.err.println("Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/check-country-by-name")
    public String findCountryByCoordinatesHelper(String country, String countryFullName, Double latitude, Double longitude) {
        if (country == null || latitude == null || longitude == null) {
            return "Invalid request! Please provide 'country', 'latitude', and 'longitude'.";
        }

        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        try {
            // Printing the log before retrieving the country's polygon
            System.out.println("Fetching polygon for country: " + country);

            // Gets the country boundaries from Firestore DB
            Polygon countryPolygon = getPolygonFromFirestore(country);

            // Print the log if the polygon is found
            if (countryPolygon != null) {
                System.out.println("Polygon for " + country + ": " + countryPolygon);
            } else {
                System.out.println("No polygon found for " + country);
            }

            // Checking if the point is inside the polygon
            if (countryPolygon.contains(point)) {
                System.out.println("The point is inside the country: " + country);
                return "The point is inside " + countryFullName + ".";
            } else {
                System.out.println("The point is outside the country: " + country);
                return "The point is not in any known country.";
            }
        } catch (Exception e) {
            // Print the log in case of an error
            System.err.println("Error while checking country " + country + ": " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    // Function to get a certain country's data from Firestore DB
    @GetMapping("/country/{countryCode}")
    public ResponseEntity<?> getCountryData(@PathVariable String countryCode) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot document = db.collection("countries").document(countryCode).get().get();

            if (!document.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Country with code " + countryCode + " not found.");
            }

            Map<String, Object> countryData = document.getData();
            return ResponseEntity.ok(countryData);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // Function to get all the countries in the Firestore DB
    @GetMapping("/countries")
    public List<String> getAllCountries() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            List<String> countries = new ArrayList<>();
            for (DocumentSnapshot doc : db.collection("countries").get().get().getDocuments()) {
                countries.add(doc.getString("name"));
            }
            return countries;
        } catch (Exception e) {
            return Collections.singletonList("Error: " + e.getMessage());
        }
    }

    // Function for checking if a point is in a certain country
    // We need to send the country's name and to give the coordinates we want to check
    @PostMapping("/check-country")
    public String checkCountry(@RequestBody Map<String, Object> request) {
        String country = (String) request.get("country");
        Double latitude = ((Number) request.get("latitude")).doubleValue();
        Double longitude = ((Number) request.get("longitude")).doubleValue();

        if (country == null || latitude == null || longitude == null) {
            return "Invalid request! Please provide 'country', 'latitude', and 'longitude'.";
        }

        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

        try {
            // Gets the country boundaries from Firestore DB
            Polygon countryPolygon = getPolygonFromFirestore(country);

            if (countryPolygon.contains(point)) {
                return "The point is inside " + country + ".";
            } else {
                return "The point is outside " + country + ".";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}


