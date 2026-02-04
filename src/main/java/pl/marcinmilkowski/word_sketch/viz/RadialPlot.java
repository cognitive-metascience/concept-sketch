package pl.marcinmilkowski.word_sketch.viz;

import pl.marcinmilkowski.word_sketch.query.SnowballCollocations.Edge;

import java.util.*;

/**
 * Generate radial collocation plot (spiral/snail visualization) for a single word.
 * Produces publication-quality SVG output matching viz_correct format.
 * 
 * Algorithm (from viz_correct analysis):
 * - Items arranged in spiral pattern - distance increases as you go around
 * - Circle radius = score value (e.g., score 12.0 → r=12.00)
 * - First item starts at angle 0 (right), radius ~120px
 * - Spiral expands outward, last item at ~300px+ radius
 * - Labels positioned 10px above (if above center) or 18px below (if below center)
 * - Grayscale coloring: higher score = darker (#505050 to #e6e6e6)
 */
public class RadialPlot {
    
    private static class Collocate {
        String word;
        double score;
        double radius;       // distance from center in pixels
        double angle;
        double x, y;         // computed position
        
        Collocate(String word, double score) {
            this.word = word;
            this.score = score;
        }
    }
    
    /**
     * Lightweight item representation for server-driven radials
     */
    public static class Item {
        public final String label;
        public final double score;
        public Item(String label, double score) {
            this.label = label;
            this.score = score;
        }
    }
    
    private final String centerWord;
    private final List<Collocate> collocates;
    private final int width;
    private final int height;
    private final boolean signedMode;

    public RadialPlot(String centerWord, List<Edge> edges, int width, int height) {
        this(centerWord, edges, width, height, false);
    }

    public RadialPlot(String centerWord, List<Edge> edges, int width, int height, boolean signedMode) {
        this.centerWord = centerWord;
        this.width = width;
        this.height = height;
        this.signedMode = signedMode;

        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double scale = Math.min(width, height) / 800.0;

        // Extract and sort collocates by score (descending)
        collocates = new ArrayList<>();
        for (Edge e : edges) {
            collocates.add(new Collocate(e.target, e.weight));
        }
        collocates.sort((a, b) -> Double.compare(b.score, a.score));

        // Take top 30 for clarity
        if (collocates.size() > 30) {
            collocates.subList(30, collocates.size()).clear();
        }

        // Calculate positions: spiral pattern matching viz_correct
        // First item at angle 0 (right of center), then spiral outward
        if (!collocates.isEmpty()) {
            int n = collocates.size();
            
            // Spiral parameters based on viz_correct analysis (800x800 baseline)
            // First item at 120px, then increase by ~7.93px per item
            double startRadius = 120.0 * scale;
            double radiusStep = 7.93 * scale;
            
            for (int i = 0; i < n; i++) {
                Collocate c = collocates.get(i);
                
                // Spiral: radius increases by a fixed step per item
                c.radius = startRadius + (i * radiusStep);
                
                // Evenly distribute around circle, starting from right (angle 0)
                c.angle = i * (2 * Math.PI / n);
                
                // Compute position
                c.x = centerX + c.radius * Math.cos(c.angle);
                c.y = centerY + c.radius * Math.sin(c.angle);
            }
        }
    }

    private String colorForSigned(double score, double maxAbs) {
        if (maxAbs <= 0) return "rgb(238,238,238)";
        double s = Math.min(1.0, Math.abs(score) / maxAbs);
        if (s <= 0) return "rgb(238,238,238)";
        if (score > 0) {
            int r = (int) Math.round(255 - (255 - 43) * s);
            int g = (int) Math.round(255 - (255 - 131) * s);
            int b = (int) Math.round(255 - (255 - 186) * s);
            return String.format("rgb(%d,%d,%d)", r, g, b);
        } else {
            int r = (int) Math.round(255 - (255 - 215) * s);
            int g = (int) Math.round(255 - (255 - 25) * s);
            int b = (int) Math.round(255 - (255 - 28) * s);
            return String.format("rgb(%d,%d,%d)", r, g, b);
        }
    }

    public static String renderFromItems(String centerWord, List<Item> items, int width, int height) {
        return renderFromItems(centerWord, items, width, height, "");
    }

    public static String renderFromItems(String centerWord, List<Item> items, int width, int height, String mode) {
        List<Edge> edges = new ArrayList<>();
        for (Item it : items) {
            edges.add(new Edge("", it.label, it.score, "generated"));
        }
        boolean signed = "signed".equalsIgnoreCase(mode) || "compare".equalsIgnoreCase(mode);
        RadialPlot plot = new RadialPlot(centerWord, edges, width, height, signed);
        return plot.toSVG();
    }
    
    public String toSVG() {
        StringBuilder svg = new StringBuilder();
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double scale = Math.min(width, height) / 800.0;
        
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append(String.format(Locale.US, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n",
            width, height, width, height));
        
        // Style matching viz_correct exactly
        svg.append("  <style>\n");
        svg.append("    .background { fill: #fafafa; }\n");
        svg.append("    .guide-circle { fill: none; stroke: #ddd; stroke-width: 0.5; }\n");
        svg.append("    .connector { stroke: #888; stroke-width: 0.8; opacity: 0.4; }\n");
        svg.append("    .center-circle { fill: #2C3E50; stroke: white; stroke-width: 3; }\n");
        svg.append("    .center-text { fill: white; font-family: Arial, sans-serif; font-size: 14px; font-weight: bold; text-anchor: middle; dominant-baseline: middle; }\n");
        svg.append("    .collocate-circle { stroke: white; stroke-width: 1.5; opacity: 0.9; }\n");
        svg.append("    .label { font-family: Arial, sans-serif; font-size: 11px; fill: #333; text-anchor: middle; }\n");
        svg.append("  </style>\n\n");
        
        // Background
        svg.append(String.format(Locale.US, "  <rect class=\"background\" width=\"%d\" height=\"%d\"/>\n\n", width, height));
        
        // Guide circles at fixed radii: 100, 200, 300 (matching viz_correct)
        svg.append("  <g id=\"guides\">\n");
        svg.append(String.format(Locale.US, "    <circle class=\"guide-circle\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\"/>\n", centerX, centerY, 100.0 * scale));
        svg.append(String.format(Locale.US, "    <circle class=\"guide-circle\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\"/>\n", centerX, centerY, 200.0 * scale));
        svg.append(String.format(Locale.US, "    <circle class=\"guide-circle\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\"/>\n", centerX, centerY, 300.0 * scale));
        svg.append("  </g>\n\n");
        
        if (!collocates.isEmpty()) {
            double maxScore = collocates.get(0).score;
            double minScore = collocates.get(collocates.size() - 1).score;
            double scoreRange = maxScore - minScore;
            if (scoreRange == 0) scoreRange = 1;
            
            double maxAbs = 0.0;
            if (signedMode) {
                for (Collocate c : collocates) maxAbs = Math.max(maxAbs, Math.abs(c.score));
                if (maxAbs == 0) maxAbs = 1.0;
            }

            // Draw connectors, circles, and labels together (like viz_correct)
            svg.append("  <g id=\"connectors\">\n");
            for (Collocate c : collocates) {
                // Connector line from center to circle position
                svg.append(String.format(Locale.US, "    <line class=\"connector\" x1=\"%d\" y1=\"%d\" x2=\"%.2f\" y2=\"%.2f\"/>\n",
                    (int)centerX, (int)centerY, c.x, c.y));
                
                // Circle color - grayscale based on score
                String color;
                if (signedMode) {
                    color = colorForSigned(c.score, maxAbs);
                } else {
                    // Grayscale matching viz_correct: #505050 (dark, high score) to #e6e6e6 (light, low score)
                    double scoreNorm = (c.score - minScore) / scoreRange;
                    // Map: scoreNorm 1.0 (highest) -> 0x50, scoreNorm 0.0 (lowest) -> 0xe6
                    int gray = (int)(0xe6 - scoreNorm * (0xe6 - 0x50));
                    color = String.format("#%02x%02x%02x", gray, gray, gray);
                }
                
                // Circle at position - radius = score value (matching viz_correct)
                double circleRadius = c.score * scale;
                svg.append(String.format(Locale.US, "    <circle class=\"collocate-circle\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" fill=\"%s\"/>\n",
                    c.x, c.y, circleRadius, color));
                
                // Label: above if circle is above center, below if below center (matching viz_correct)
                double labelY;
                if (c.y <= centerY) {
                    // Circle at or above center: label 10px above
                    labelY = c.y - (10.0 * scale);
                } else {
                    // Circle below center: label 18px below
                    labelY = c.y + (18.0 * scale);
                }
                
                svg.append(String.format(Locale.US, "    <text class=\"label\" x=\"%.2f\" y=\"%.2f\">%s</text>\n",
                    c.x, labelY, escapeXml(c.word)));
            }
            svg.append("  </g>\n\n");

            // Signed mode legend
            if (signedMode) {
                double legendY = height - 40.0;
                svg.append("  <g id=\"legend\">\n");
                svg.append(String.format(Locale.US, "    <rect x=\"%.0f\" y=\"%.0f\" width=\"12\" height=\"12\" fill=\"%s\" />\n",
                    centerX - 100.0, legendY, colorForSigned(maxAbs, maxAbs)));
                svg.append(String.format(Locale.US, "    <text x=\"%.0f\" y=\"%.0f\" font-size=\"11\" fill=\"#333\">Positive (A&gt;B)</text>\n",
                    centerX - 82.0, legendY + 10.0));
                svg.append(String.format(Locale.US, "    <rect x=\"%.0f\" y=\"%.0f\" width=\"12\" height=\"12\" fill=\"%s\" />\n",
                    centerX + 10.0, legendY, colorForSigned(-maxAbs, maxAbs)));
                svg.append(String.format(Locale.US, "    <text x=\"%.0f\" y=\"%.0f\" font-size=\"11\" fill=\"#333\">Negative (B&gt;A)</text>\n",
                    centerX + 28.0, legendY + 10.0));
                svg.append("  </g>\n\n");
            }
        }
        
        // Center circle with keyword (radius 40 like viz_correct)
        svg.append(String.format(Locale.US, "  <circle class=\"center-circle\" cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\"/>\n",
            centerX, centerY, 40.0 * scale));
        svg.append(String.format(Locale.US, "  <text class=\"center-text\" x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\">%s</text>\n",
            centerX, centerY, 14.0 * scale, escapeXml(centerWord)));
        
        svg.append("</svg>");
        return svg.toString();
    }
    
    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
