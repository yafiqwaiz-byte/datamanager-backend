package dev.waiz.datamanager.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.dto.ReverseGeocodeRequestDTO;
import dev.waiz.datamanager.dto.ReverseGeocodeResponseDTO;
import dev.waiz.datamanager.service.GeocodingService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;

    // GPS coordinates → formatted address
    @PostMapping("/reverse")
    public ResponseEntity<ReverseGeocodeResponseDTO> reverseGeocode(
            @RequestBody ReverseGeocodeRequestDTO request) {
        ReverseGeocodeResponseDTO result = geocodingService.reverseGeocode(
                request.getLat(), request.getLng());
        return ResponseEntity.ok(result);
    }

    // Text input → list of { placeId, description } suggestions
    // Called on every keystroke (debounced on the frontend) as the user types
    @GetMapping("/autocomplete")
    public ResponseEntity<List<Map<String, String>>> autocomplete(
            @RequestParam String input) {
        return ResponseEntity.ok(geocodingService.getAutocompleteSuggestions(input));
    }

    // placeId → { lat, lng, formattedAddress }
    // Called once when the user selects a suggestion from the dropdown
    @GetMapping("/place-details")
    public ResponseEntity<ReverseGeocodeResponseDTO> placeDetails(
            @RequestParam String placeId) {
        return ResponseEntity.ok(geocodingService.getPlaceDetails(placeId));
    }
}