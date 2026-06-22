package dev.waiz.datamanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.dto.ReverseGeocodeResponseDTO;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeocodingService {

    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── 1. Reverse geocode (GPS coordinates → address) ────────────────────────
    private static final String GEOCODE_URL =
        "https://maps.googleapis.com/maps/api/geocode/json?latlng=%s,%s&key=%s";

    public ReverseGeocodeResponseDTO reverseGeocode(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Both lat and lng are required for reverse geocoding");
        }

        String url = String.format(GEOCODE_URL, lat, lng, googleMapsApiKey);

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = OBJECT_MAPPER.readTree(rawResponse);

            String status = root.path("status").asText();
            if (!"OK".equals(status)) {
                log.warn("Google geocoding returned status '{}' for ({}, {})", status, lat, lng);
                return new ReverseGeocodeResponseDTO(lat, lng, lat + ", " + lng);
            }

            JsonNode results = root.path("results");
            String formattedAddress = results.isArray() && results.size() > 0
                ? results.get(0).path("formatted_address").asText()
                : (lat + ", " + lng);

            return new ReverseGeocodeResponseDTO(lat, lng, formattedAddress);

        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {})", lat, lng, e);
            return new ReverseGeocodeResponseDTO(lat, lng, lat + ", " + lng);
        }
    }

    // ── 2. Places Autocomplete (input text → list of suggestions) ────────────
    // Returns a list of { placeId, description } maps so the frontend can show
    // a dropdown. The API key never leaves the server.
    private static final String AUTOCOMPLETE_URL =
        "https://maps.googleapis.com/maps/api/place/autocomplete/json";

    public List<Map<String, String>> getAutocompleteSuggestions(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        String url = UriComponentsBuilder.fromUri(URI.create(AUTOCOMPLETE_URL))
            .queryParam("input", input)
            .queryParam("key", googleMapsApiKey)
            // Bias results toward Malaysia — remove or change if you need global results
            .queryParam("components", "country:my")
            .queryParam("language", "ms")
            .build()
            .toUriString();

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = OBJECT_MAPPER.readTree(rawResponse);

            String status = root.path("status").asText();
            if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
                log.warn("Places Autocomplete returned status '{}' for input '{}'", status, input);
                return List.of();
            }

            List<Map<String, String>> suggestions = new ArrayList<>();
            JsonNode predictions = root.path("predictions");
            if (predictions.isArray()) {
                for (JsonNode prediction : predictions) {
                    String placeId = prediction.path("place_id").asText();
                    String description = prediction.path("description").asText();
                    if (!placeId.isBlank() && !description.isBlank()) {
                        suggestions.add(Map.of("placeId", placeId, "description", description));
                    }
                }
            }
            return suggestions;

        } catch (Exception e) {
            log.warn("Places Autocomplete failed for input '{}'", input, e);
            return List.of();
        }
    }

    // ── 3. Place Details (placeId → lat, lng, formattedAddress) ──────────────
    // Called when the user selects a suggestion from the dropdown.
    private static final String PLACE_DETAILS_URL =
        "https://maps.googleapis.com/maps/api/place/details/json";

    public ReverseGeocodeResponseDTO getPlaceDetails(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId is required");
        }

        String url = UriComponentsBuilder.fromUri(URI.create(PLACE_DETAILS_URL))
            .queryParam("place_id", placeId)
            .queryParam("fields", "geometry,formatted_address")
            .queryParam("key", googleMapsApiKey)
            .build()
            .toUriString();

        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = OBJECT_MAPPER.readTree(rawResponse);

            String status = root.path("status").asText();
            if (!"OK".equals(status)) {
                log.warn("Place Details returned status '{}' for placeId '{}'", status, placeId);
                throw new IllegalArgumentException("Could not resolve place details for: " + placeId);
            }

            JsonNode result = root.path("result");
            String formattedAddress = result.path("formatted_address").asText();
            JsonNode location = result.path("geometry").path("location");
            double lat = location.path("lat").asDouble();
            double lng = location.path("lng").asDouble();

            return new ReverseGeocodeResponseDTO(lat, lng, formattedAddress);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Place Details failed for placeId '{}'", placeId, e);
            throw new IllegalArgumentException("Failed to fetch place details: " + e.getMessage());
        }
    }
}