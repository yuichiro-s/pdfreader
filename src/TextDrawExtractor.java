import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
                        if (file.toString().endsWith(".pdf")) processFile(file);
                        return FileVisitResult.CONTINUE;
                    }
                };
                Files.walkFileTree(p, visitor);
            }
            else processFile(p);
        }
    }

    private static void processFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        String outPath = path.toString().replace(".pdf", ".feats");

        List<List<String>> objects = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDFGraphicsStreamEngine gse = new MyPDFGraphicsStreamEngine(doc.getPage(i), objects);
            gse.processPage(doc.getPage(i));
            for (int s = start; s < objects.size(); s++) {
                List<String> obj = objects.get(s);
                if (obj != null) obj.add(0, String.valueOf(i+1));
            }
            start = objects.size();
        }

        PDFTextStripper ts = new MyPDFTextStripper(objects);
        ts.writeText(doc, new OutputStreamWriter(new ByteArrayOutputStream()));
        writeAll(outPath, objects);
        doc.close();
    }

    static void writeAll(String outPath, List<List<String>> objects) throws IOException {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {
            for (List<String> obj : objects) {
                if (obj == null) {
                    w.write("null\n");
                } else {
                    w.write(String.join("\t", obj));
                    w.write("\n");
                }
            }
        }
    }

    private static class MyPDFTextStripper extends PDFTextStripper {
        int cursor = 0;
        List<List<String>> objects;

        MyPDFTextStripper(List<List<String>> objects) throws IOException {
            this.objects = objects;
        }

        void addText(String[] strings) {
            List<String> obj = new ArrayList<>();
            for (String s : strings) obj.add(s);
            objects.add(cursor, obj);
            cursor++;
        }
        void addText(String string) { addText(new String[] { string }); }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
            int prevCursor = cursor;
            while (cursor< objects.size() && objects.get(cursor) != null) cursor++;
            if (prevCursor != cursor) addText("[EOT]");

            for (TextPosition p : textPositions) {
                String unicode = p.getUnicode();
                for (char c : unicode.toCharArray()) {
                    if (objects.get(cursor) != null) {
                        writeAll("temp.feats", objects);
                        throw new IllegalStateException("Something wrong with draw extraction.");
                    }
                    List<String> obj = new ArrayList<>();
                    obj.add(String.valueOf(getCurrentPageNo()));
                    obj.add(String.valueOf(c));
                    obj.add(String.valueOf(p.getXDirAdj()));
                    obj.add(String.valueOf(p.getYDirAdj()));
                    obj.add(String.valueOf(p.getWidthDirAdj()));
                    obj.add(String.valueOf(p.getHeightDir()));
                    obj.add(p.getFont().getName());
                    obj.add(String.valueOf(p.getFontSize()));
                    obj.add(String.valueOf(p.getWidthOfSpace()));
                    objects.set(cursor, obj);
                    cursor++;
                }
            }
        }

        @Override
        protected void writeWordSeparator() throws IOException {
            addText("[EOT]");
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            addText("[EOL]");
        }
    }

    private static class MyPDFGraphicsStreamEngine extends PDFGraphicsStreamEngine {

        final List<List<String>> objects;
        final float pageHeight;

        MyPDFGraphicsStreamEngine (PDPage page, List<List<String>> objects) {
            super(page);
            this.objects = objects;
            pageHeight = page.getMediaBox().getHeight();
        }

        void addDraw(String[] ops) {
            List<String> obj = new ArrayList<>();
            for (String op : ops) obj.add(op);
            objects.add(obj);
        }
        void addDraw(String op) { addDraw(new String[] { op }); }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {
            // from allenai/pdffigures2/GraphicBBDetector.scala
            moveTo((float)p0.getX(), (float)p0.getY());
            lineTo((float)p1.getX(), (float)p1.getY());
            moveTo((float)p2.getX(), (float)p2.getY());
            lineTo((float)p3.getX(), (float)p3.getY());
            closePath();
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            assert false;
        }

        @Override
        public void clip(int i) throws IOException {
            addDraw(new String[] { "[CLIP]", String.valueOf(i) });
        }

        @Override
        public void moveTo(float x, float y) throws IOException {
            addDraw(new String[] { "[MOVE_TO]", String.valueOf(x), String.valueOf(pageHeight-y) });
        }

        @Override
        public void lineTo(float x, float y) throws IOException {
            addDraw(new String[] { "[LINE_TO]", String.valueOf(x), String.valueOf(pageHeight-y) });
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException {
            addDraw(new String[] { "[CURVE_TO]", String.valueOf(x1), String.valueOf(pageHeight-y1),
                    String.valueOf(x2), String.valueOf(pageHeight-y2),
                    String.valueOf(x3), String.valueOf(pageHeight-y3)});
        }

        @Override
        public Point2D getCurrentPoint() throws IOException {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() throws IOException { addDraw("[CLOSE_PATH]"); }

        @Override
        public void endPath() throws IOException { addDraw("[END_PATH]"); }

        @Override
        public void strokePath() throws IOException { addDraw("[STROKE_PATH]"); }

        @Override
        public void fillPath(int i) throws IOException { addDraw("[FILL_PATH]"); }

        @Override
        public void fillAndStrokePath(int i) throws IOException {
            addDraw(new String[] { "[FILL_AND_STROKE_PATH]", String.valueOf(i) });
        }

        @Override
        public void shadingFill(COSName cosName) throws IOException {
            addDraw(new String[] { "[SHADING_FILL]", String.valueOf(cosName) });
        }

        /*@Override
        protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode, Vector displacement) throws IOException {
            if (unicode == null) {
                if (font instanceof PDSimpleFont) {
                    String s = new String(new char[] { (char)code });
                    System.out.println(s);
                    objects.add(null);
                }
                else {
                    // Acrobat doesn't seem to coerce composite font's character codes, instead it
                    // skips them. See the "allah2.pdf" TestTextStripper file.
                    return;
                }
            } else {
                System.out.println(unicode);
                for (char c : unicode.toCharArray()) {
                    objects.add(null);
                }
            }
        }*/

        @Override
        public void showText(byte[] string) throws IOException {
            PDGraphicsState state = this.getGraphicsState();
            PDTextState textState = state.getTextState();
            PDFont font = textState.getFont();
            if (font == null) font = PDFontFactory.createDefaultFont();

            for (ByteArrayInputStream in = new ByteArrayInputStream(string); in.available() > 0;) {
                int code = font.readCode(in);
                String unicode = font.toUnicode(code);
                if (unicode == null) {
                    System.out.println(new String(string));
                    objects.add(null);
                }
                else {
                    for (char c : unicode.toCharArray()) {
                        objects.add(null);
                    }
                }
            }
        }
    }
}
