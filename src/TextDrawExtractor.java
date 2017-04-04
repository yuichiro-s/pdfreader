import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class TextDrawExtractor extends PDFGraphicsStreamEngine {

    static final Log LOG = LogFactory.getLog(TextDrawExtractor.class);

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

        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"))) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDFGraphicsStreamEngine gse = new TextDrawExtractor(doc.getPage(i), w, i+1);
                gse.processPage(doc.getPage(i));
            }
        }
    }

    Writer writer;
    int pageIndex;
    int pageRotation;
    PDRectangle pageSize;
    Matrix translateMatrix;
    final GlyphList glyphList;

    public TextDrawExtractor(PDPage page, Writer writer, int pageIndex) throws IOException {
        super(page);
        this.writer = writer;
        this.pageIndex = pageIndex;

        String path = "org/apache/pdfbox/resources/glyphlist/additional.txt";
        InputStream input = GlyphList.class.getClassLoader().getResourceAsStream(path);
        this.glyphList = new GlyphList(GlyphList.getAdobeGlyphList(), input);

        this.pageRotation = page.getRotation();
        this.pageSize = page.getCropBox();
        if(this.pageSize.getLowerLeftX() == 0.0F && this.pageSize.getLowerLeftY() == 0.0F) {
            this.translateMatrix = null;
        } else {
            this.translateMatrix = Matrix.getTranslateInstance(-this.pageSize.getLowerLeftX(), -this.pageSize.getLowerLeftY());
        }
    }

    float getPageHeight() { return getPage().getMediaBox().getHeight(); }

    void writeOps(Object... ops) throws IOException {
        for (int i = 0; i < ops.length; i++) {
            writer.write(ops[i].toString());
            if (i < ops.length - 1) writer.write("\t");
            else writer.write("\n");
        }
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {
        // from "allenai/pdffigures2/GraphicBBDetector.scala"
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
    public void clip(int i) throws IOException { writeOps("[CLIP]", i); }

    @Override
    public void moveTo(float x, float y) throws IOException { writeOps("[MOVE_TO]", x, getPageHeight()-y); }

    @Override
    public void lineTo(float x, float y) throws IOException { writeOps("[LINE_TO]", x, getPageHeight()-y); }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException {
        writeOps("[CURVE_TO]", x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() throws IOException {
        return new Point2D.Float(0, 0);
    }

    @Override
    public void closePath() throws IOException { writeOps("[CLOSE_PATH]"); }

    @Override
    public void endPath() throws IOException { writeOps("[END_PATH]"); }

    @Override
    public void strokePath() throws IOException { writeOps("[STROKE_PATH]"); }

    @Override
    public void fillPath(int i) throws IOException { writeOps("[FILL_PATH]"); }

    @Override
    public void fillAndStrokePath(int i) throws IOException { writeOps("[FILL_AND_STROKE_PATH]", i); }

    @Override
    public void shadingFill(COSName cosName) throws IOException { writeOps("[SHADING_FILL]", cosName); }

    @Override
    public void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code, String unicode, Vector displacement) throws IOException {
        PDGraphicsState state = this.getGraphicsState();
        Matrix ctm = state.getCurrentTransformationMatrix();
        float fontSize = state.getTextState().getFontSize();
        float horizontalScaling = state.getTextState().getHorizontalScaling() / 100.0F;
        Matrix textMatrix = this.getTextMatrix();
        BoundingBox bbox = font.getBoundingBox();
        if(bbox.getLowerLeftY() < -32768.0F) {
            bbox.setLowerLeftY(-(bbox.getLowerLeftY() + 65536.0F));
        }

        float glyphHeight = bbox.getHeight() / 2.0F;
        PDFontDescriptor fontDescriptor = font.getFontDescriptor();
        float height;
        if(fontDescriptor != null) {
            height = fontDescriptor.getCapHeight();
            if(height != 0.0F && (height < glyphHeight || glyphHeight == 0.0F)) {
                glyphHeight = height;
            }
        }

        height = glyphHeight / 1000.0F;

        float displacementX = displacement.getX();
        if(font.isVertical()) {
            displacementX = font.getWidth(code) / 1000.0F;
            TrueTypeFont ttf = null;
            if(font instanceof PDTrueTypeFont) {
                ttf = ((PDTrueTypeFont)font).getTrueTypeFont();
            } else if(font instanceof PDType0Font) {
                PDCIDFont cidFont = ((PDType0Font)font).getDescendantFont();
                if(cidFont instanceof PDCIDFontType2) {
                    ttf = ((PDCIDFontType2)cidFont).getTrueTypeFont();
                }
            }

            if(ttf != null && ttf.getUnitsPerEm() != 1000) {
                displacementX *= 1000.0F / (float)ttf.getUnitsPerEm();
            }
        }

        float tx = displacementX * fontSize * horizontalScaling;
        float ty = displacement.getY() * fontSize;
        Matrix td = Matrix.getTranslateInstance(tx, ty);
        Matrix nextTextRenderingMatrix = td.multiply(textMatrix).multiply(ctm);
        float nextX = nextTextRenderingMatrix.getTranslateX();
        float nextY = nextTextRenderingMatrix.getTranslateY();
        float dxDisplay = nextX - textRenderingMatrix.getTranslateX();
        float dyDisplay = height * textRenderingMatrix.getScalingFactorY();
        float glyphSpaceToTextSpaceFactor = 0.001F;
        if(font instanceof PDType3Font) {
            glyphSpaceToTextSpaceFactor = font.getFontMatrix().getScaleX();
        }

        float spaceWidthText = 0.0F;

        try {
            spaceWidthText = font.getSpaceWidth() * glyphSpaceToTextSpaceFactor;
        } catch (Throwable e) {
            LOG.warn(e, e);
        }

        if(spaceWidthText == 0.0F) {
            spaceWidthText = font.getAverageFontWidth() * glyphSpaceToTextSpaceFactor;
            spaceWidthText *= 0.8F;
        }

        if(spaceWidthText == 0.0F) {
            spaceWidthText = 1.0F;
        }

        float spaceWidthDisplay = spaceWidthText * textRenderingMatrix.getScalingFactorX();
        unicode = font.toUnicode(code, this.glyphList);
        if(unicode == null) {
            if(!(font instanceof PDSimpleFont)) {
                return;
            }

            char c = (char)code;
            unicode = new String(new char[]{c});
        }

        Matrix translatedTextRenderingMatrix;
        if(this.translateMatrix == null) {
            translatedTextRenderingMatrix = textRenderingMatrix;
        } else {
            translatedTextRenderingMatrix = Matrix.concatenate(this.translateMatrix, textRenderingMatrix);
            nextX -= this.pageSize.getLowerLeftX();
            nextY -= this.pageSize.getLowerLeftY();
        }

        TextPosition p = new TextPosition(this.pageRotation, this.pageSize.getWidth(), this.pageSize.getHeight(),
                translatedTextRenderingMatrix, nextX, nextY, Math.abs(dyDisplay), dxDisplay, Math.abs(spaceWidthDisplay),
                unicode, new int[]{code}, font, fontSize, (int) (fontSize * textMatrix.getScalingFactorX()));

        writer.write(String.valueOf(pageIndex));
        writer.write("\t");
        writer.write(unicode);
        writer.write("\t");
        writer.write(String.valueOf(p.getXDirAdj()));
        writer.write("\t");
        writer.write(String.valueOf(p.getYDirAdj()));
        writer.write("\t");
        writer.write(String.valueOf(p.getWidthDirAdj()));
        writer.write("\t");
        writer.write(String.valueOf(p.getHeightDir()));
        writer.write("\t");
        writer.write(font.getName());
        writer.write("\t");
        writer.write(String.valueOf(fontSize));
        writer.write("\t");
        writer.write(String.valueOf(p.getWidthOfSpace()));
        writer.write("\n");
    }
}
