import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class TextDrawExtractor {

    public static void main(String[] args) throws IOException {

        for (String path: args) {
            Path p = Paths.get(path);
            if (Files.isDirectory(p)) {
                FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".pdf")) {
                            processFile(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                };
                Files.walkFileTree(p, visitor);
            } else {
                processFile(p);
            }
        }
    }

    private static void processFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        assert doc.getNumberOfPages() == 1;

        String outPath = path.toString().replace(".pdf", ".feats");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {
            List<List<String>> objects = new ArrayList<>();
            PDFGraphicsStreamEngine gse = new MyPDFGraphicsStreamEngine(doc.getPage(0), objects);
            gse.processPage(doc.getPage(0));

            PDFTextStripper ts = new MyPDFTextStripper(objects);
            ts.writeText(doc, w);
        }
    }

    private static class MyPDFTextStripper extends PDFTextStripper {

        private int cursor;
        List<List<String>> objects;

        MyPDFTextStripper(List<List<String>> objects) throws IOException {
            this.cursor = 0;
            this.objects = objects;
        }

        private void writeChar(TextPosition p, char c) throws IOException {
            output.write("C"); output.write("\t");
            output.write(c); output.write("\t");
            output.write(String.valueOf(p.getXDirAdj())); output.write("\t");
            output.write(String.valueOf(p.getYDirAdj())); output.write("\t");
            output.write(String.valueOf(p.getWidthDirAdj())); output.write("\t");
            output.write(String.valueOf(p.getHeightDir())); output.write("\t");
            output.write(p.getFont().getName()); output.write("\t");
            output.write(String.valueOf(p.getFontSize())); output.write("\t");
            output.write(String.valueOf(p.getWidthOfSpace())); output.write("\n");
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
            for (TextPosition p : textPositions) {
                String s = p.getUnicode();

                // NFKD normalization
                String s_norm = Normalizer.normalize(s, Normalizer.Form.NFKD);

                for (char c: s_norm.toCharArray()) {
                    writeChar(p, c);
                }

                // print draw features
                cursor++;
                while (cursor < objects.size() && objects.get(cursor) != null) {
                    // non-character
                    output.write("\n");
                    List<String> lst = objects.get(cursor);
                    for (int i = 0; i < lst.size(); i++) {
                        output.write(lst.get(i));
                        if (i < lst.size() - 1) {
                            output.write("\t");
                        } else {
                            output.write("\n");
                        }
                    }
                    cursor++;
                }
            }
        }

        @Override
        protected void writeWordSeparator() throws IOException {
        }
    }

    private static class MyPDFGraphicsStreamEngine extends PDFGraphicsStreamEngine {

        private final List<List<String>> objects;
        private float stroke_x1;
        private float stroke_y1;
        private float stroke_x2;
        private float stroke_y2;

        MyPDFGraphicsStreamEngine(PDPage page, List<List<String>> objects) {
            super(page);
            this.objects = objects;
        }

        @Override
        public void appendRectangle(Point2D point2D, Point2D point2D1, Point2D point2D2, Point2D point2D3) throws IOException {
            assert false;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            assert false;
        }

        @Override
        public void clip(int i) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[CLIP]");
            obj.add(String.valueOf(i));
            objects.add(obj);
        }

        @Override
        public void moveTo(float x, float y) throws IOException {
            stroke_x1 = x;
            stroke_y1 = y;
        }

        @Override
        public void lineTo(float x, float y) throws IOException {
            stroke_x2 = x;
            stroke_y2 = y;
        }

        @Override
        public void curveTo(float v, float v1, float v2, float v3, float v4, float v5) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[CURVE_TO]");
            obj.add(String.valueOf(v));
            obj.add(String.valueOf(v1));
            obj.add(String.valueOf(v2));
            obj.add(String.valueOf(v3));
            obj.add(String.valueOf(v4));
            obj.add(String.valueOf(v5));
            objects.add(obj);
        }

        @Override
        public Point2D getCurrentPoint() throws IOException {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[CLOSE_PATH]");
            objects.add(obj);
        }

        @Override
        public void endPath() throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[END_PATH]");
            objects.add(obj);
        }

        @Override
        public void strokePath() throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("L");
            obj.add(String.valueOf(stroke_x1));
            obj.add(String.valueOf(stroke_y1));
            obj.add(String.valueOf(stroke_x2));
            obj.add(String.valueOf(stroke_y2));
            objects.add(obj);
        }

        @Override
        public void fillPath(int i) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[FILL_PATH]");
            obj.add(String.valueOf(i));
            objects.add(obj);
        }

        @Override
        public void fillAndStrokePath(int i) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[FILL_AND_STROKE_PATH]");
            obj.add(String.valueOf(i));
            objects.add(obj);
        }

        @Override
        public void shadingFill(COSName cosName) throws IOException {
            List<String> obj = new ArrayList<>();
            obj.add("[SHADING_FILL]");
            obj.add(String.valueOf(cosName));
            objects.add(obj);
        }

        @Override
        public void showText(byte[] string) throws IOException {
            for (byte b : string) {
                objects.add(null);
            }
        }
    }

}
