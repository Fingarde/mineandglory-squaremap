package eu.mineandglory.squaremapclaim;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Main extends JavaPlugin {
    public record Chunk(int x, int y) {
        public List<Point> toPoints() {
            return Arrays.asList(
                    Point.of(x, y),
                    Point.of(x, y + 1),
                    Point.of(x + 1, y + 1),
                    Point.of(x + 1, y)
            );
        }
    }

    private static Main instance;
    private Squaremap squaremap;

@Override
public void onEnable() {
    instance = this;
    squaremap = SquaremapProvider.get();

    WorldIdentifier worldIdentifier = WorldIdentifier.parse("minecraft:overworld");
    MapWorld mapWorld = squaremap.getWorldIfEnabled(worldIdentifier).orElseThrow(() -> new RuntimeException("World not found"));

    System.out.println("World is enabled");

    SimpleLayerProvider layerProvider = SimpleLayerProvider.builder("Mineandglory Claim").build();

    Area area = getArea();

    // Filled area is used to match only the chunks inside the perimeter of the area
    Area filledArea = getFilledArea(area);

    // Bounds are used to get the top left and bottom right points of the area
    Rectangle bounds = area.getBounds();

    Point topLeftPoint = Point.of(bounds.x, bounds.y);
    Point bottomRightPoint = Point.of(bounds.x + bounds.width, bounds.y + bounds.height);


    List<List<Point>> negativeParts = new ArrayList<>();

    // For each chunk from top left to bottom right check if it is inside the area
    for (int x = (int) topLeftPoint.x(); x <= (int) bottomRightPoint.x(); x++) {
        for (int y = (int) topLeftPoint.z(); y <= (int) bottomRightPoint.z(); y++) {

            // First check if the point is inside the perimeter of the area
            if (!filledArea.contains(x, y, 1, 1)) continue;

            // Then we remove the chunks outside that are excluded from the area
            if (area.contains(x, y, 1, 1)) continue;

            // Here we are multiplied by 16 because we have a Point and not an area that can be scaled alter on
            negativeParts.add(Arrays.asList(
                    Point.of(x * 16, y * 16),
                    Point.of(x * 16, y * 16 + 16),
                    Point.of(x * 16 + 16, y * 16 + 16),
                    Point.of(x * 16 + 16, y * 16)
            ));
        }
    }

    // Scale the area to match chunk size
    AffineTransform scaleTransform = AffineTransform.getScaleInstance(16, 16);
    filledArea.transform(scaleTransform);

    Marker marker = Marker.polygon(toPoints(filledArea), negativeParts);
    marker.markerOptions(MarkerOptions.builder()
            .strokeColor(Color.decode("#ef233c"))
            .strokeWeight(3)
            .strokeOpacity(1)
            .fillColor(Color.decode("#ef233c"))
            .fillOpacity(0.2)
            .build());


    mapWorld.layerRegistry().register(Key.of("mineandglory_claim"), layerProvider);
    layerProvider.addMarker(Key.key("mineandglory_claim_1"), marker);
}

    private static @NotNull Area getArea() {
        ArrayList<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk(0, 0));
        chunks.add(new Chunk(0, 1));
        chunks.add(new Chunk(0, 2));

        chunks.add(new Chunk(1, 2));
        chunks.add(new Chunk(2, 2));
        chunks.add(new Chunk(1, 1));


        chunks.add(new Chunk(2, 1));
        chunks.add(new Chunk(2, 0));
        chunks.add(new Chunk(1, 0));

        chunks.add(new Chunk(2, 3));
        chunks.add(new Chunk(0, 3));
        chunks.add(new Chunk(0, 4));
        chunks.add(new Chunk(0, 5));
        chunks.add(new Chunk(0, 6));
        chunks.add(new Chunk(1, 4));
        chunks.add(new Chunk(2, 4));

        Area area = new Area();

        chunks.forEach(chunk -> mergeArea(area, chunk.toPoints()));
        return area;
    }

    @Override
    public void onDisable() {
    }


    public static Main getInstance() {
        return instance;
    }

    private static List<Point> merge(List<Point> pointsA, List<Point> pointsB) {
        Area aa = new Area(toShape(pointsA));

        aa.add(new Area(toShape(pointsB)));
        return toPoints(aa);
    }

    private static void mergeArea(Area are, List<Point> pointsB) {
        are.add(new Area(toShape(pointsB)));
    }

    private static Shape toShape(List<Point> points) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            if (i == 0) {
                path.moveTo(p.x(), p.z());
            } else {
                path.lineTo(p.x(), p.z());
            }
        }
        path.closePath();
        return path;
    }

    private static List<Point> toPoints(Shape shape) {
        List<Point> result = new ArrayList<Point>();
        PathIterator pi = shape.getPathIterator(null, 0.0);
        double[] coords = new double[6];
        while (!pi.isDone()) {
            int segment = pi.currentSegment(coords);
            switch (segment) {
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    result.add(Point.of(coords[0], coords[1]));
                    break;
            }
            pi.next();
        }
        return result;
    }

    public static Area getFilledArea(Area area) {
        Area filledArea = new Area();
        double[] coords = new double[6];
        GeneralPath tmpPath = new GeneralPath();

        PathIterator pathIterator = area.getPathIterator(null);
        for (; !pathIterator.isDone(); pathIterator.next()) {
            int type = pathIterator.currentSegment(coords);
            switch (type) {
                case PathIterator.WIND_EVEN_ODD:
                    tmpPath.reset();
                    tmpPath.moveTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    tmpPath.lineTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_QUADTO:
                    tmpPath.quadTo(coords[0], coords[1], coords[2], coords[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    tmpPath.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    break;
                case PathIterator.SEG_CLOSE:
                    filledArea.add(new Area(tmpPath));
                    break;
                default:
                    System.err.println("Unhandled type " + type);
                    break;
            }
        }
        return filledArea;
    }

}
