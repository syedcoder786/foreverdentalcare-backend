package com.clinic.whatsapp;

import com.clinic.config.PatientAuthProperties;
import com.clinic.entity.WhatsAppSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WhatsApp Cloud API (Meta Graph API) client: reads template structures, lists approved templates and sends
 * template messages. Template structures are cached for 10 minutes.
 */
@Slf4j
@Component
public class WhatsAppClient {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\d+)\\s*}}");
    private static final Duration TEMPLATE_CACHE_TTL = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiVersion;
    private final Map<String, CachedTemplate> templateCache = new ConcurrentHashMap<>();

    /** A button of a template. {@code needsValue}: URL button with {{1}}, Authentication code button or coupon button. */
    public record ButtonInfo(int index, String type, String text, boolean needsValue) {
    }

    /** Structure of an approved (or pending) Meta template. */
    public record TemplateInfo(
            String name, String language, String status, String category,
            /** TEXT, IMAGE, VIDEO, DOCUMENT, LOCATION or null when there is no header. */
            String headerFormat,
            String headerText,
            int headerParams,
            String bodyText,
            int bodyParams,
            String footerText,
            List<ButtonInfo> buttons
    ) {
        public boolean approved() {
            return "APPROVED".equalsIgnoreCase(status);
        }

        public boolean mediaHeader() {
            return "IMAGE".equals(headerFormat) || "VIDEO".equals(headerFormat) || "DOCUMENT".equals(headerFormat);
        }

        public List<ButtonInfo> valueButtons() {
            return buttons.stream().filter(ButtonInfo::needsValue).toList();
        }
    }

    /** Values to send. {@code buttons} holds one value per button that needs one, in button order. */
    public record TemplateValues(List<String> header, String headerMediaUrl, List<String> body, List<String> buttons) {
    }

    private record CachedTemplate(TemplateInfo info, Instant fetchedAt) {
    }

    public WhatsAppClient(PatientAuthProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Generous connect timeout: connections to graph.facebook.com occasionally stall on some networks.
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(properties.whatsappApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.apiVersion = properties.whatsappApiVersion();
    }

    // ---------- sign-in codes ----------

    /**
     * Sends a sign-in code with the settings' OTP template: the code fills every body placeholder and code button.
     *
     * @param requireCode true for real sign-ins: refuse templates that have no placeholder for the code
     * @return the template structure used (empty if it couldn't be read; then the Authentication format is assumed)
     */
    public Optional<TemplateInfo> sendOtp(WhatsAppSettings settings, String e164Phone, String code, boolean requireCode) {
        String name = settings.getOtpTemplateName().trim();
        String language = settings.getOtpTemplateLanguage().trim();
        Optional<TemplateInfo> info = hasBusinessAccount(settings) ? Optional.of(describe(settings, name, language)) : Optional.empty();
        if (info.isPresent()) {
            TemplateInfo t = info.get();
            requireApproved(t);
            if (requireCode && t.bodyParams() == 0) {
                throw new WhatsAppException("Template '" + t.name() + "' has no {{1}} placeholder, so it can't carry the sign-in code. "
                        + "Use a template of category Authentication.");
            }
        }
        int bodyParams = info.map(TemplateInfo::bodyParams).orElse(1);
        int buttonValues = info.map(t -> t.valueButtons().size()).orElse(1);
        List<String> header = info.map(t -> repeat(code, t.headerParams())).orElse(List.of());
        TemplateValues values = new TemplateValues(header, null, repeat(code, bodyParams), repeat(code, buttonValues));
        List<ButtonInfo> buttons = info.map(TemplateInfo::buttons).orElse(List.of(new ButtonInfo(0, "OTP", "Copy code", true)));
        post(settings, e164Phone, name, language, info.map(TemplateInfo::headerFormat).orElse(null), buttons, values);
        return info;
    }

    // ---------- any template ----------

    /** Sends a template whose structure has already been read with {@link #describe}. */
    public void sendTemplate(WhatsAppSettings settings, String e164Phone, TemplateInfo template, TemplateValues values) {
        requireApproved(template);
        post(settings, e164Phone, template.name(), template.language(), template.headerFormat(), template.buttons(), values);
    }

    /** Reads one template's structure (needs the WhatsApp Business Account ID). */
    public TemplateInfo describe(WhatsAppSettings s, String name, String language) {
        requireBusinessAccount(s);
        String key = s.getBusinessAccountId() + "|" + name + "|" + language + "|" + s.getAccessToken().hashCode();
        CachedTemplate cached = templateCache.get(key);
        if (cached != null && cached.fetchedAt().plus(TEMPLATE_CACHE_TTL).isAfter(Instant.now())) {
            return cached.info();
        }
        List<TemplateInfo> matches = fetch(s, name);
        List<String> languages = new ArrayList<>();
        for (TemplateInfo t : matches) {
            languages.add(t.language());
            if (language.equals(t.language())) {
                templateCache.put(key, new CachedTemplate(t, Instant.now()));
                return t;
            }
        }
        throw new WhatsAppException(languages.isEmpty()
                ? "There is no template named '" + name + "' in this WhatsApp Business Account. Create it in WhatsApp Manager or check the name."
                : "Template '" + name + "' exists in " + String.join(", ", languages) + " but not in " + language + ". Change the language to one of those.");
    }

    /** All templates in the WhatsApp Business Account (for the admin template picker). */
    public List<TemplateInfo> listTemplates(WhatsAppSettings s) {
        requireBusinessAccount(s);
        return fetch(s, null);
    }

    // ---------- internals ----------

    private List<TemplateInfo> fetch(WhatsAppSettings s, String name) {
        List<TemplateInfo> result = new ArrayList<>();
        String after = null;
        try {
            for (int page = 0; page < 10; page++) {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("version", apiVersion);
                vars.put("waba", s.getBusinessAccountId().trim());
                StringBuilder uri = new StringBuilder("/{version}/{waba}/message_templates?fields=name,language,status,category,components&limit=100");
                if (name != null) {
                    uri.append("&name={name}");
                    vars.put("name", name);
                }
                if (after != null) {
                    uri.append("&after={after}");
                    vars.put("after", after);
                }
                String json = restClient.get()
                        .uri(uri.toString(), vars)
                        .headers(h -> h.setBearerAuth(s.getAccessToken().trim()))
                        .retrieve()
                        .body(String.class);
                JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
                for (JsonNode t : root.path("data")) {
                    if (name == null || name.equals(t.path("name").asText())) {
                        result.add(toInfo(t));
                    }
                }
                after = root.path("paging").path("cursors").path("after").asText(null);
                if (after == null || root.path("paging").path("next").isMissingNode()) {
                    break;
                }
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new WhatsAppException("Could not read templates from WhatsApp: " + metaErrorMessage(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new WhatsAppException("WhatsApp could not be reached. Please try again.");
        } catch (Exception ex) {
            throw new WhatsAppException("Could not read templates from WhatsApp.");
        }
    }

    private void post(WhatsAppSettings settings, String e164Phone, String name, String language, String headerFormat,
                      List<ButtonInfo> buttons, TemplateValues values) {
        List<Map<String, Object>> components = new ArrayList<>();
        if (headerFormat != null && values.headerMediaUrl() != null && !values.headerMediaUrl().isBlank()
                && List.of("IMAGE", "VIDEO", "DOCUMENT").contains(headerFormat)) {
            String type = headerFormat.toLowerCase();
            components.add(Map.of("type", "header",
                    "parameters", List.of(Map.of("type", type, type, Map.of("link", values.headerMediaUrl().trim())))));
        } else if (!values.header().isEmpty()) {
            components.add(Map.of("type", "header", "parameters", textParams(values.header())));
        }
        if (!values.body().isEmpty()) {
            components.add(Map.of("type", "body", "parameters", textParams(values.body())));
        }
        int next = 0;
        for (ButtonInfo button : buttons) {
            if (!button.needsValue() || next >= values.buttons().size()) {
                continue;
            }
            String value = values.buttons().get(next++);
            if ("COPY_CODE".equals(button.type())) {
                components.add(Map.of("type", "button", "sub_type", "copy_code", "index", String.valueOf(button.index()),
                        "parameters", List.of(Map.of("type", "coupon_code", "coupon_code", value))));
            } else {
                components.add(Map.of("type", "button", "sub_type", "url", "index", String.valueOf(button.index()),
                        "parameters", List.of(Map.of("type", "text", "text", value))));
            }
        }

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", name);
        template.put("language", Map.of("code", language));
        if (!components.isEmpty()) {
            template.put("components", components);
        }
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", e164Phone.replaceAll("\\D", ""),
                "type", "template",
                "template", template);
        try {
            sendWithConnectRetry(settings, body);
        } catch (RestClientResponseException ex) {
            String message = metaErrorMessage(ex.getResponseBodyAsString());
            log.warn("WhatsApp API rejected template '{}' (HTTP {}): {}", name, ex.getStatusCode().value(), message);
            templateCache.clear();
            throw new WhatsAppException(message);
        } catch (RestClientException ex) {
            log.warn("WhatsApp API unreachable: {}", ex.getMessage());
            throw new WhatsAppException("WhatsApp could not be reached. Please try again.");
        }
    }

    /**
     * Posts the message, retrying once if the connection could not be opened. A failed connect means the request never
     * reached WhatsApp, so retrying cannot send the message twice. Read timeouts and API errors are not retried.
     */
    private void sendWithConnectRetry(WhatsAppSettings settings, Map<String, Object> body) {
        for (int attempt = 1; ; attempt++) {
            try {
                restClient.post()
                        .uri("/{version}/{phoneNumberId}/messages", apiVersion, settings.getPhoneNumberId().trim())
                        .headers(h -> h.setBearerAuth(settings.getAccessToken().trim()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (org.springframework.web.client.ResourceAccessException ex) {
                if (attempt >= 2 || !isConnectFailure(ex)) {
                    throw ex;
                }
                log.info("WhatsApp API connection failed ({}), retrying once", ex.getMostSpecificCause().getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private static boolean isConnectFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || t instanceof java.net.NoRouteToHostException) {
                return true;
            }
            if (t instanceof java.net.SocketTimeoutException && String.valueOf(t.getMessage()).toLowerCase().contains("connect")) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> textParams(List<String> values) {
        return values.stream()
                .map(v -> Map.<String, Object>of("type", "text", "text", v == null || v.isBlank() ? "-" : v))
                .toList();
    }

    private static List<String> repeat(String value, int times) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            list.add(value);
        }
        return list;
    }

    private static int highestPlaceholder(String text) {
        int max = 0;
        Matcher m = PLACEHOLDER.matcher(text == null ? "" : text);
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    private static TemplateInfo toInfo(JsonNode t) {
        String headerFormat = null;
        String headerText = null;
        String bodyText = "";
        String footerText = null;
        List<ButtonInfo> buttons = new ArrayList<>();
        for (JsonNode c : t.path("components")) {
            switch (c.path("type").asText()) {
                case "HEADER" -> {
                    headerFormat = c.path("format").asText("TEXT");
                    headerText = c.path("text").asText(null);
                }
                case "BODY" -> bodyText = c.path("text").asText("");
                case "FOOTER" -> footerText = c.path("text").asText(null);
                case "BUTTONS" -> {
                    int i = 0;
                    for (JsonNode b : c.path("buttons")) {
                        String type = b.path("type").asText();
                        boolean needsValue = "OTP".equals(type) || "COPY_CODE".equals(type)
                                || ("URL".equals(type) && PLACEHOLDER.matcher(b.path("url").asText("")).find());
                        buttons.add(new ButtonInfo(i++, type, b.path("text").asText(""), needsValue));
                    }
                }
                default -> {
                    // CAROUSEL and other component types are not supported.
                }
            }
        }
        return new TemplateInfo(t.path("name").asText(), t.path("language").asText(), t.path("status").asText(),
                t.path("category").asText(), headerFormat, headerText,
                "TEXT".equals(headerFormat) ? highestPlaceholder(headerText) : 0,
                bodyText, highestPlaceholder(bodyText), footerText, buttons);
    }

    private static void requireApproved(TemplateInfo t) {
        if (!t.approved()) {
            throw new WhatsAppException("Template '" + t.name() + "' (" + t.language() + ") is " + t.status()
                    + " in WhatsApp Manager. It must be APPROVED before it can be sent.");
        }
    }

    private static boolean hasBusinessAccount(WhatsAppSettings s) {
        return s.getBusinessAccountId() != null && !s.getBusinessAccountId().isBlank();
    }

    private static void requireBusinessAccount(WhatsAppSettings s) {
        if (!hasBusinessAccount(s)) {
            throw new WhatsAppException("Enter the WhatsApp Business Account ID (Messaging account ID) in WhatsApp setup first.");
        }
    }

    /** Extracts {@code error.error_user_msg} / {@code error.message} from a Graph API error body. */
    private String metaErrorMessage(String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String userMessage = error.path("error_user_msg").asText("");
            String message = !userMessage.isBlank() ? userMessage : error.path("message").asText("");
            String details = error.path("error_data").path("details").asText("");
            if (!details.isBlank() && !message.contains(details)) {
                message = message.isBlank() ? details : message + " (" + details + ")";
            }
            return message.isBlank() ? "WhatsApp rejected the message." : message;
        } catch (Exception ignored) {
            return "WhatsApp rejected the message.";
        }
    }

    /** Message delivery failed; the message is safe to show to admins (not to patients). */
    public static class WhatsAppException extends RuntimeException {
        public WhatsAppException(String message) {
            super(message);
        }
    }
}
