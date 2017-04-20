import java.io.IOException;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;

public class ImageExtractor extends PDFStreamEngine {

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

    static void processFile(Path path) throws IOException {
        PDDocument doc = PDDocument.load(path.toFile());
        String outPath = path.toString().replace(".pdf", ".image");

        Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), "UTF-8"));
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            ImageExtractor ie = new ImageExtractor();
            ie.processPage(doc.getPage(i));
            ie.filter();
            ie.writeAll(w, i+1);
        }
        w.close();
    }

    class Position {
        float x, y, w, h;

        Position(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    List<Position> positions = new ArrayList<>();

    public ImageExtractor() throws IOException {
        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetMatrix());
    }

    void writeAll(Writer w, int pageNum) throws IOException {
        for (Position p : positions) {
            List<String> l = new ArrayList<>();
            l.add(String.valueOf(pageNum));
            l.add(String.valueOf(p.x));
            l.add(String.valueOf(p.y));
            l.add(String.valueOf(p.w));
            l.add(String.valueOf(p.h));
            w.write(String.join("\t", l));
            w.write("\n");
        }
    }

    void filter() throws IOException {
        // filter adjacent images
        List<Position> temp = new ArrayList<>();
        for (Position p1 : positions) {
            boolean isAdjacent = false;
            int i = 0;
            for (Position p2 : positions) {
                boolean isSameX = p1.x == p2.x;
                boolean isSameY = p1.y == p2.y;

                float threshold = 2;
                boolean near_bottom = 0 < p1.y - (p2.y + p2.h) && p1.y - (p2.y + p2.h) < threshold;
                boolean near_top = 0 < (p1.y + p1.h) - p2.y && (p1.y + p1.h) - p2.y < threshold;
                boolean near_right = 0 < (p1.x + p1.w) - p2.x && (p1.x + p1.w) - p2.x < threshold;
                boolean near_left = 0 < p1.x - (p2.x + p2.w) && p1.x - (p2.x + p2.w) < threshold;
                isAdjacent = (i > 0) && ((isSameX && (near_bottom || near_top)) || (isSameY && (near_left || near_right)));
                i++;
            }
            if (!isAdjacent) temp.add(p1);
        }
        positions = temp;

        // filter small image
        temp = new ArrayList<>();
        for (Position p : positions) {
            if (!(p.w < 50 || p.h < 50)) temp.add(p);
        }
        positions = temp;

        // filter same coordinate
        temp = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            Position p1 = positions.get(i);
            boolean isSame = false;
            for (int j = 0; j < positions.size(); j++) {
                Position p2 = positions.get(j);
                if (i != j && p1.x == p2.x && p1.y == p2.y && p1.w == p2.w && p1.h == p2.h) {
                    isSame = true;
                }
            }
            if (!isSame) temp.add(p1);
        }
        positions = temp;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if ("Do".equals(operation)) {
            COSName objectName = (COSName)operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject)xobject;

                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                float x = ctmNew.getTranslateX();
                float y = ctmNew.getTranslateY();
                float w = ctmNew.getScalingFactorX();
                float h = ctmNew.getScalingFactorY();
                positions.add(new Position(x, y, w, h));
            }
            else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject)xobject;
                showForm(form);
            }
        }
        else {
            super.processOperator(operator, operands);
        }
    }
}