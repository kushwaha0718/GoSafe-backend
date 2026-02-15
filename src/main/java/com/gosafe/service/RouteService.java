package com.gosafe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Service
public class RouteService {

    private static final String NOMINATIM = "https://nominatim.openstreetmap.org";
    private static final String OSRM      = "https://router.project-osrm.org";
    private static final String OVERPASS  = "https://overpass-api.de/api/interpreter";

    private final RestTemplate  http;
    private final ObjectMapper  mapper = new ObjectMapper();

    public RouteService() {
        this.http = new RestTemplate();
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent",       "GoSafe-IndiaTransit/1.0");
        h.set("Accept-Language",  "en");
        return h;
    }

    // ─── Nominatim geocode ────────────────────────────────────────────────────
    public Map<String, Object> geocode(String query) throws Exception {
        String url = NOMINATIM + "/search?q=" + enc(query)
                   + "&format=json&limit=1&countrycodes=in&addressdetails=1";
        ResponseEntity<String> res = http.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers()), String.class);
        JsonNode arr = mapper.readTree(res.getBody());
        if (arr.isEmpty())
            throw new RuntimeException("Could not find \"" + query + "\" in India. Try a more specific name.");
        JsonNode d = arr.get(0);
        return Map.of(
            "lat",     d.get("lat").asDouble(),
            "lng",     d.get("lon").asDouble(),
            "display", d.get("display_name").asText()
        );
    }

    // ─── Nominatim autocomplete ───────────────────────────────────────────────
    public List<Map<String, Object>> autocomplete(String query) {
        return autocompleteNearby(query, null);
    }

    public List<Map<String, Object>> autocompleteNearby(String query, Map<String, Double> coords) {
        try {
            StringBuilder url = new StringBuilder(NOMINATIM + "/search?q=" + enc(query)
                    + "&format=json&limit=7&countrycodes=in&addressdetails=1");
            if (coords != null) {
                double span = 1.0;
                url.append("&viewbox=").append(coords.get("lng") - span).append(",")
                   .append(coords.get("lat") - span).append(",")
                   .append(coords.get("lng") + span).append(",")
                   .append(coords.get("lat") + span)
                   .append("&bounded=0");
            }
            ResponseEntity<String> res = http.exchange(url.toString(), HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class);
            JsonNode arr = mapper.readTree(res.getBody());
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode d : arr) results.add(formatPlace(d));
            return results;
        } catch (Exception e) { return List.of(); }
    }

    private Map<String, Object> formatPlace(JsonNode d) {
        JsonNode a = d.has("address") ? d.get("address") : mapper.createObjectNode();
        String specific = firstNonNull(a,
            "amenity","building","railway","aeroway","road","neighbourhood","suburb");
        if (specific == null) specific = d.get("display_name").asText().split(",")[0];
        String city = firstNonNull(a, "city","town","village","county","state_district");
        String name = (city != null && !specific.toLowerCase().contains(city.toLowerCase()))
                      ? specific + ", " + city : specific;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",   d.get("place_id").asText());
        m.put("name", name.trim());
        m.put("sub",  a.has("state") ? a.get("state").asText() : "");
        m.put("lat",  d.get("lat").asDouble());
        m.put("lng",  d.get("lon").asDouble());
        m.put("type", d.has("type") ? d.get("type").asText() : d.has("class") ? d.get("class").asText() : "");
        m.put("line", m.get("type"));
        return m;
    }

    // ─── OSRM single route (optionally via a waypoint) ────────────────────────
    private JsonNode fetchOSRMOne(Map<String, Object> origin, Map<String, Object> dest,
                                   Map<String, Double> via) {
        try {
            String coords;
            if (via != null) {
                coords = String.format(Locale.US, "%f,%f;%f,%f;%f,%f",
                    dbl(origin,"lng"), dbl(origin,"lat"),
                    via.get("lng"),    via.get("lat"),
                    dbl(dest,"lng"),   dbl(dest,"lat"));
            } else {
                coords = String.format(Locale.US, "%f,%f;%f,%f",
                    dbl(origin,"lng"), dbl(origin,"lat"),
                    dbl(dest,"lng"),   dbl(dest,"lat"));
            }
            String url = OSRM + "/route/v1/driving/" + coords
                       + "?overview=full&geometries=geojson&steps=true";
            ResponseEntity<String> res = http.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class);
            JsonNode json = mapper.readTree(res.getBody());
            if (!json.path("code").asText().equals("Ok")) return null;
            JsonNode routes = json.path("routes");
            return routes.isEmpty() ? null : routes.get(0);
        } catch (Exception e) { return null; }
    }

    // ─── Build up to 3 genuinely different routes (perpendicular via strategy) ─
    private List<JsonNode> buildThreeRoutes(Map<String, Object> originGeo,
                                             Map<String, Object> destGeo) throws Exception {
        double lat1 = dbl(originGeo,"lat"), lng1 = dbl(originGeo,"lng");
        double lat2 = dbl(destGeo,"lat"),   lng2 = dbl(destGeo,"lng");

        double midLat = (lat1 + lat2) / 2, midLng = (lng1 + lng2) / 2;
        double dLat = lat2 - lat1, dLng = lng2 - lng1;
        double len  = Math.sqrt(dLat*dLat + dLng*dLng);
        if (len == 0) len = 1;

        double perpLat = -dLng / len, perpLng = dLat / len;
        double dist = Math.sqrt(dLat*dLat + dLng*dLng);

        Map<String, Double> via1 = Map.of("lat", midLat + perpLat*dist*0.15, "lng", midLng + perpLng*dist*0.15);
        Map<String, Double> via2 = Map.of("lat", midLat - perpLat*dist*0.15, "lng", midLng - perpLng*dist*0.15);
        Map<String, Double> via3 = Map.of("lat", midLat + perpLat*dist*0.25, "lng", midLng + perpLng*dist*0.25);

        // Fire all 4 requests in parallel
        ExecutorService pool = Executors.newFixedThreadPool(4);
        Future<JsonNode> f1 = pool.submit(() -> fetchOSRMOne(originGeo, destGeo, null));
        Future<JsonNode> f2 = pool.submit(() -> fetchOSRMOne(originGeo, destGeo, via1));
        Future<JsonNode> f3 = pool.submit(() -> fetchOSRMOne(originGeo, destGeo, via2));
        Future<JsonNode> f4 = pool.submit(() -> fetchOSRMOne(originGeo, destGeo, via3));
        pool.shutdown();

        List<JsonNode> candidates = new ArrayList<>();
        for (Future<JsonNode> f : List.of(f1,f2,f3,f4)) {
            try { JsonNode r = f.get(15, TimeUnit.SECONDS); if (r != null) candidates.add(r); }
            catch (Exception ignored) {}
        }

        if (candidates.isEmpty())
            throw new RuntimeException("No drivable route found between these locations.");

        // De-duplicate (same duration ±60s)
        List<JsonNode> unique = new ArrayList<>();
        for (JsonNode r : candidates) {
            double dur = r.path("duration").asDouble();
            boolean dup = unique.stream().anyMatch(u ->
                Math.abs(u.path("duration").asDouble() - dur) < 60);
            if (!dup) unique.add(r);
        }
        unique.sort(Comparator.comparingDouble(r -> r.path("duration").asDouble()));
        return unique.subList(0, Math.min(3, unique.size()));
    }

    // ─── Overpass shops ───────────────────────────────────────────────────────
    private List<Map<String, Object>> fetchShops(List<double[]> waypoints) {
        try {
            // Determine bounding box
            List<double[]> useWpts = waypoints;
            double latSpan = waypoints.stream().mapToDouble(w->w[0]).max().orElse(0)
                           - waypoints.stream().mapToDouble(w->w[0]).min().orElse(0);
            double lngSpan = waypoints.stream().mapToDouble(w->w[1]).max().orElse(0)
                           - waypoints.stream().mapToDouble(w->w[1]).min().orElse(0);
            if (latSpan > 1.0 || lngSpan > 1.0) {
                int s = waypoints.size() / 3;
                useWpts = waypoints.subList(s, s * 2);
            }
            final List<double[]> wp = useWpts;
            double pad   = 0.008;
            double south = wp.stream().mapToDouble(w->w[0]).min().orElse(0) - pad;
            double north = wp.stream().mapToDouble(w->w[0]).max().orElse(0) + pad;
            double west  = wp.stream().mapToDouble(w->w[1]).min().orElse(0) - pad;
            double east  = wp.stream().mapToDouble(w->w[1]).max().orElse(0) + pad;

            String bbox  = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", south, west, north, east);
            String query = "[out:json][timeout:18];" +
                "(node[\"name\"][\"shop\"](" + bbox + ");" +
                "node[\"name\"][\"amenity\"~\"restaurant|cafe|fast_food|bank|atm|pharmacy|supermarket|cinema|fuel|hospital|mall\"](" + bbox + ");" +
                "node[\"name\"][\"brand\"](" + bbox + "););" +
                "out 150;";

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String body = "data=" + enc(query);
            ResponseEntity<String> res = http.exchange(OVERPASS, HttpMethod.POST,
                    new HttpEntity<>(body, h), String.class);

            JsonNode json = mapper.readTree(res.getBody());
            Set<String> seen = new LinkedHashSet<>();
            List<Map<String, Object>> shops = new ArrayList<>();

            for (JsonNode el : json.path("elements")) {
                JsonNode tags = el.path("tags");
                String name = tags.has("brand") ? tags.get("brand").asText()
                            : tags.has("name")  ? tags.get("name").asText() : null;
                if (name == null || seen.contains(name)) continue;
                seen.add(name);

                String rawCat = tags.has("shop")    ? tags.get("shop").asText()
                              : tags.has("amenity") ? tags.get("amenity").asText() : "shop";
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name",     name);
                s.put("category", rawCat.replace("_", " ").toUpperCase());
                s.put("icon",     ICON.getOrDefault(rawCat, "🏪"));
                s.put("color",    COLOR.getOrDefault(rawCat, "#2e3450"));
                s.put("lat",      el.path("lat").asDouble());
                s.put("lng",      el.path("lon").asDouble());

                List<String> parts = new ArrayList<>();
                if (tags.has("addr:street"))  parts.add(tags.get("addr:street").asText());
                if (tags.has("addr:suburb"))  parts.add(tags.get("addr:suburb").asText());
                else if (tags.has("addr:city")) parts.add(tags.get("addr:city").asText());
                s.put("station", parts.isEmpty() ? "Along route" : String.join(", ", parts));
                shops.add(s);
            }
            return shops;
        } catch (Exception e) { return List.of(); }
    }

    // ─── Safety scoring ───────────────────────────────────────────────────────
    private Map<String, Object> scoreSafety(JsonNode r, int rank) {
        double distKm      = r.path("distance").asDouble() / 1000;
        double durationMin = r.path("duration").asDouble() / 60;
        double urbanFactor = Math.min(15, (durationMin / Math.max(distKm, 0.1)) * 3);
        int[] bases = {82, 71, 60};
        double base  = (rank < bases.length ? bases[rank] : 55) + urbanFactor * 0.5;
        int score    = (int) Math.round(Math.max(30, Math.min(96, base - Math.min(8, distKm / 60))));

        List<Map<String, Object>> factors = List.of(
            factor("Lighting Coverage", clamp(score + 9)),
            factor("Crowd Density",     clamp(score + 3)),
            factor("CCTV Coverage",     clamp(score - 4)),
            factor("Emergency Access",  clamp(score + 6)),
            factor("Incident History",  clamp(score - 7))
        );
        return Map.of("score", score, "factors", factors);
    }
    private int clamp(int v) { return Math.min(98, Math.max(28, v)); }
    private Map<String, Object> factor(String name, int score) {
        return Map.of("name", name, "score", score);
    }

    // ─── Route labelling ──────────────────────────────────────────────────────
    private Map<String, Object> labelRoute(JsonNode r, List<JsonNode> all) {
        JsonNode fastest = all.get(0);
        boolean isFastest  = r == fastest;
        double myDist      = r.path("distance").asDouble();
        double minDist     = all.stream().mapToDouble(x -> x.path("distance").asDouble()).min().orElse(0);
        double maxDist     = all.stream().mapToDouble(x -> x.path("distance").asDouble()).max().orElse(0);
        double fastDur     = fastest.path("duration").asDouble();
        double pct         = fastDur > 0 ? ((r.path("duration").asDouble() - fastDur) / fastDur) * 100 : 0;

        if (isFastest)          return meta("Fastest Route",  "Shortest travel time",              List.of("Recommended","Fast"));
        if (myDist == minDist)  return meta("Shortest Route", "Least distance travelled",          List.of("Efficient"));
        if (myDist == maxDist)  return meta("Scenic Route",   "Longer but less congested",         List.of("Scenic"));
        return meta("Alternate Route", "~" + (int)pct + "% longer, different path",                List.of("Alternate"));
    }
    private Map<String, Object> meta(String name, String desc, List<String> badges) {
        return Map.of("name", name, "desc", desc, "badges", badges);
    }

    // ─── Format duration ──────────────────────────────────────────────────────
    private String fmtDur(double secs) {
        int m = (int) Math.round(secs / 60);
        return m < 60 ? m + " min" : (m/60) + "h " + (m%60) + "m";
    }

    // ─── Extract waypoints from GeoJSON ──────────────────────────────────────
    private List<double[]> geojsonToWaypoints(JsonNode geometry) {
        List<double[]> pts = new ArrayList<>();
        for (JsonNode c : geometry.path("coordinates"))
            pts.add(new double[]{ c.get(1).asDouble(), c.get(0).asDouble() }); // [lat, lng]
        return pts;
    }

    // ─── PUBLIC: generate routes ──────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> generateRoutes(String origin, String destination) throws Exception {
        Map<String, Object> originGeo = geocode(origin);
        Map<String, Object> destGeo   = geocode(destination);
        List<JsonNode> osrmRoutes     = buildThreeRoutes(originGeo, destGeo);

        // Fetch shops for all routes in parallel
        ExecutorService pool = Executors.newFixedThreadPool(osrmRoutes.size());
        List<Future<List<Map<String, Object>>>> shopFutures = new ArrayList<>();
        for (JsonNode r : osrmRoutes) {
            List<double[]> wpts = geojsonToWaypoints(r.path("geometry"));
            shopFutures.add(pool.submit(() -> fetchShops(wpts)));
        }
        pool.shutdown();

        List<Map<String, Object>> routes = new ArrayList<>();
        for (int i = 0; i < osrmRoutes.size(); i++) {
            JsonNode r = osrmRoutes.get(i);
            Map<String, Object> safety = scoreSafety(r, i);
            Map<String, Object> label  = labelRoute(r, osrmRoutes);

            List<Map<String, Object>> shops = List.of();
            try { shops = shopFutures.get(i).get(20, TimeUnit.SECONDS); } catch (Exception ignored) {}
            shops = shops.subList(0, Math.min(30, shops.size()));

            // Stops from steps
            List<Map<String, Object>> stops = new ArrayList<>();
            JsonNode legs = r.path("legs");
            int stepIdx = 0;
            outer:
            for (JsonNode leg : legs) {
                for (JsonNode step : leg.path("steps")) {
                    String name = step.path("name").asText("").trim();
                    if (!name.isEmpty() && !name.equals("undefined") && stepIdx > 0 && stepIdx % 4 == 0) {
                        JsonNode loc = step.path("maneuver").path("location");
                        stops.add(Map.of("lat", loc.get(1).asDouble(), "lng", loc.get(0).asDouble(), "name", name));
                        if (stops.size() >= 8) break outer;
                    }
                    stepIdx++;
                }
            }

            // Brands
            Set<String> brandSet = new LinkedHashSet<>();
            for (Map<String, Object> s : shops) brandSet.add((String) s.get("name"));
            List<String> brands = new ArrayList<>(brandSet);
            if (brands.size() > 12) brands = brands.subList(0, 12);

            // Waypoints as list of {lat, lng} maps
            List<Map<String, Object>> waypointMaps = new ArrayList<>();
            for (double[] w : geojsonToWaypoints(r.path("geometry")))
                waypointMaps.add(Map.of("lat", w[0], "lng", w[1]));

            Map<String, Object> route = new LinkedHashMap<>();
            route.put("id",            "route-" + i + "-" + System.currentTimeMillis());
            route.put("name",          label.get("name"));
            route.put("description",   label.get("desc"));
            route.put("safetyScore",   safety.get("score"));
            route.put("safetyFactors", safety.get("factors"));
            route.put("duration",      fmtDur(r.path("duration").asDouble()));
            route.put("distance",      String.format(Locale.US, "%.1f km", r.path("distance").asDouble() / 1000));
            route.put("durationSecs",  r.path("duration").asDouble());
            route.put("transfers",     i);
            route.put("totalShops",    shops.size());
            route.put("brands",        brands);
            route.put("badges",        label.get("badges"));
            route.put("waypoints",     waypointMaps);
            route.put("stops",         stops);
            route.put("shops",         shops);
            route.put("originStation", ((String) originGeo.get("display")).split(",")[0]);
            route.put("destStation",   ((String) destGeo.get("display")).split(",")[0]);
            routes.add(route);
        }

        // Sort by safetyScore desc
        routes.sort((a, b) -> Integer.compare((int) b.get("safetyScore"), (int) a.get("safetyScore")));
        return routes;
    }

    // ─── Utilities ────────────────────────────────────────────────────────────
    private double dbl(Map<String, Object> m, String k) { return ((Number) m.get(k)).doubleValue(); }
    private String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private String firstNonNull(JsonNode node, String... keys) {
        for (String k : keys) if (node.has(k)) return node.get(k).asText();
        return null;
    }

    // ─── Icon & color maps ────────────────────────────────────────────────────
    private static final Map<String, String> ICON = Map.ofEntries(
        Map.entry("restaurant","🍽"), Map.entry("cafe","☕"), Map.entry("fast_food","🍔"),
        Map.entry("food_court","🍱"), Map.entry("supermarket","🛒"), Map.entry("mall","🏬"),
        Map.entry("pharmacy","💊"), Map.entry("hospital","🏥"), Map.entry("bank","🏦"),
        Map.entry("atm","🏦"), Map.entry("cinema","🎬"), Map.entry("fuel","⛽"),
        Map.entry("clothes","👗"), Map.entry("electronics","📱"), Map.entry("bakery","🥐"),
        Map.entry("convenience","🏪"), Map.entry("jewellery","💍"), Map.entry("beauty","💄"),
        Map.entry("hairdresser","💈"), Map.entry("sports","🏃"), Map.entry("books","📚")
    );
    private static final Map<String, String> COLOR = Map.ofEntries(
        Map.entry("restaurant","#ff6b35"), Map.entry("cafe","#6b3a2a"), Map.entry("fast_food","#ff4500"),
        Map.entry("supermarket","#004c97"), Map.entry("mall","#1a1a2e"), Map.entry("pharmacy","#00468b"),
        Map.entry("hospital","#e63946"), Map.entry("bank","#22409a"), Map.entry("atm","#22409a"),
        Map.entry("cinema","#c62a2a"), Map.entry("fuel","#ffb800"), Map.entry("clothes","#d4145a"),
        Map.entry("electronics","#3563e9")
    );
}
