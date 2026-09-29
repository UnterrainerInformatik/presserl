package info.unterrainer.presserl.media;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import info.unterrainer.presserl.api.FieldError;

/**
 * Reads an edit request ({@code POST /api/media/{id}/edit}) strictly. {@link #parse} checks the shape
 * before the media is loaded, {@link #checkBounds} the geometry against the stored image; each reports
 * all violations together.
 */
public final class MediaEditValidator {

    public static final String VERSION = "version";
    public static final String CROP = "crop";
    public static final String PIXELATE = "pixelate";
    public static final int MIN_CROP_SIDE = 16;
    public static final int MAX_ELLIPSES = 50;
    public static final int MIN_RADIUS = 4;

    private static final Set<String> FIELDS = Set.of(VERSION, CROP, PIXELATE);
    private static final List<String> CROP_FIELDS = List.of("x", "y", "width", "height");
    private static final List<String> ELLIPSE_FIELDS = List.of("cx", "cy", "rx", "ry");

    private MediaEditValidator() {
    }

    /**
     * The edit: the version it is based on, the crop ({@code null} for none) and the ellipses in
     * request order.
     */
    public record EditRequest(long version, MediaProcessor.Crop crop, List<MediaProcessor.Ellipse> ellipses) {
    }

    /**
     * @throws MediaException {@code 400} listing every violation of the shape
     */
    public static EditRequest parse(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw MediaException.invalid(List.of(new FieldError(null, "request body must be a JSON object")));
        }
        List<FieldError> errors = new ArrayList<>();
        for (Iterator<String> names = json.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                errors.add(new FieldError(name, "unknown field"));
            }
        }
        JsonNode versionNode = json.get(VERSION);
        long version = 0;
        if (versionNode == null || versionNode.isNull()) {
            errors.add(new FieldError(VERSION, "is required"));
        } else if (!versionNode.isIntegralNumber() || !versionNode.canConvertToLong() || versionNode.asLong() < 0) {
            errors.add(new FieldError(VERSION, "must be a non-negative integer"));
        } else {
            version = versionNode.asLong();
        }
        MediaProcessor.Crop crop = null;
        JsonNode cropNode = json.get(CROP);
        if (cropNode != null && !cropNode.isNull()) {
            int[] values = integers(cropNode, CROP_FIELDS);
            if (values == null) {
                errors.add(new FieldError(CROP, "must be an object with the integers x, y, width and height"));
            } else {
                crop = new MediaProcessor.Crop(values[0], values[1], values[2], values[3]);
            }
        }
        List<MediaProcessor.Ellipse> ellipses = new ArrayList<>();
        JsonNode pixelateNode = json.get(PIXELATE);
        if (pixelateNode != null && !pixelateNode.isNull()) {
            if (!pixelateNode.isArray()) {
                errors.add(new FieldError(PIXELATE, "must be a list of ellipses"));
            } else if (pixelateNode.size() > MAX_ELLIPSES) {
                errors.add(new FieldError(PIXELATE, "must hold at most " + MAX_ELLIPSES + " ellipses"));
            } else {
                for (int i = 0; i < pixelateNode.size(); i++) {
                    int[] values = integers(pixelateNode.get(i), ELLIPSE_FIELDS);
                    if (values == null) {
                        errors.add(new FieldError(PIXELATE + "[" + i + "]",
                                "must be an object with the integers cx, cy, rx and ry"));
                    } else {
                        ellipses.add(new MediaProcessor.Ellipse(values[0], values[1], values[2], values[3]));
                    }
                }
            }
        }
        boolean nothingToDo = crop == null && ellipses.isEmpty();
        boolean operationsValid = errors.stream().noneMatch(e -> e.field() != null
                && (e.field().equals(CROP) || e.field().startsWith(PIXELATE)));
        if (nothingToDo && operationsValid) {
            errors.add(new FieldError(CROP, "a crop or at least one ellipse to pixelate is required"));
        }
        if (!errors.isEmpty()) {
            throw MediaException.invalid(errors);
        }
        return new EditRequest(version, crop, List.copyOf(ellipses));
    }

    /**
     * Checks the geometry against a stored image of {@code width} × {@code height} pixels: the crop
     * lies inside and is at least {@value #MIN_CROP_SIDE} pixels on a side; every ellipse has radii of
     * at least {@value #MIN_RADIUS} pixels and its centre inside the image.
     *
     * @throws MediaException {@code 400} listing every violation
     */
    public static void checkBounds(EditRequest request, int width, int height) {
        List<FieldError> errors = new ArrayList<>();
        MediaProcessor.Crop crop = request.crop();
        if (crop != null) {
            if (crop.width() < MIN_CROP_SIDE || crop.height() < MIN_CROP_SIDE) {
                errors.add(new FieldError(CROP, "must be at least " + MIN_CROP_SIDE + " pixels on a side"));
            } else if (crop.x() < 0 || crop.y() < 0 || (long) crop.x() + crop.width() > width
                    || (long) crop.y() + crop.height() > height) {
                errors.add(new FieldError(CROP, "must lie inside the image of " + width + " x " + height
                        + " pixels"));
            }
        }
        for (int i = 0; i < request.ellipses().size(); i++) {
            MediaProcessor.Ellipse ellipse = request.ellipses().get(i);
            String field = PIXELATE + "[" + i + "]";
            if (ellipse.rx() < MIN_RADIUS || ellipse.ry() < MIN_RADIUS) {
                errors.add(new FieldError(field, "radii must be at least " + MIN_RADIUS + " pixels"));
            } else if (ellipse.cx() < 0 || ellipse.cy() < 0 || ellipse.cx() >= width || ellipse.cy() >= height) {
                errors.add(new FieldError(field, "centre must lie inside the image"));
            }
        }
        if (!errors.isEmpty()) {
            throw MediaException.invalid(errors);
        }
    }

    /**
     * The named integer fields of an object in order; {@code null} when it is not an object, a field is
     * missing or not an int, or it has other fields.
     */
    private static int[] integers(JsonNode node, List<String> names) {
        if (node == null || !node.isObject() || node.size() != names.size()) {
            return null;
        }
        int[] values = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            JsonNode value = node.get(names.get(i));
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
                return null;
            }
            values[i] = value.asInt();
        }
        return values;
    }
}
